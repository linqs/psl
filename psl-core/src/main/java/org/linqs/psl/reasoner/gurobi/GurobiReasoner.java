/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.reasoner.gurobi;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.gurobi.term.GurobiObjectiveTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBQuadExpr;
import gurobi.GRBVar;

import java.util.HashMap;
import java.util.List;

public class GurobiReasoner extends Reasoner<GurobiObjectiveTerm> {
    private static final Logger log = LoggerFactory.getLogger(GurobiReasoner.class);
    private static final java.util.Map<FunctionComparator, Character> comparatorMap;

    private final int workLimit;

    static {
        comparatorMap = new HashMap<>();
        comparatorMap.put(FunctionComparator.EQ, GRB.EQUAL);
        comparatorMap.put(FunctionComparator.GTE, GRB.GREATER_EQUAL);
        comparatorMap.put(FunctionComparator.LTE, GRB.LESS_EQUAL);
    }

    public GurobiReasoner() {
        super();

        workLimit = Options.GUROBI_WORK_LIMIT.getInt();
    }

    @Override
    public double optimize(TermStore<GurobiObjectiveTerm> termStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        termStore.initForOptimization();
        int numAtoms = termStore.getNumVariables();

        long start = System.currentTimeMillis();

        try {
            GRBEnv env = new GRBEnv(false);
            env.set("LogToConsole", "0");

            GRBModel model = new GRBModel(env);
            model.set("WorkLimit", Integer.toString(workLimit));

            GRBVar[] varAtoms = model.addVars(numAtoms, GRB.CONTINUOUS);

            boolean hasDecisionVariables = false;
            GroundAtom[] variableAtoms = termStore.getAtomStore().getAtoms();
            for (int i = 0; i < numAtoms; i++) {
                // get ith atom
                GroundAtom atom = variableAtoms[i];
                if (atom.isFixed()) {
                    // set value of ith atom
                    varAtoms[i].set(GRB.DoubleAttr.LB, atom.getValue());
                    varAtoms[i].set(GRB.DoubleAttr.UB, atom.getValue());
                } else {
                    if (atom.getPredicate().isInteger()) {
                        varAtoms[i].set(GRB.CharAttr.VType, GRB.INTEGER);
                    } else {
                        varAtoms[i].set(GRB.DoubleAttr.Start, atom.getValue());
                    }
                    varAtoms[i].set(GRB.DoubleAttr.LB, 0.0);
                    varAtoms[i].set(GRB.DoubleAttr.UB, 1.0);
                    hasDecisionVariables = true;
                }
            }

            if (!hasDecisionVariables) {
                log.trace("No random variable atoms to optimize.");
                ObjectiveResult objectiveResult = parallelComputeObjective(termStore);
                return objectiveResult.objective;
            }

            processTerms(termStore, model, varAtoms);

            model.optimize();

            float[] variableValues = termStore.getVariableValues();
            for (int i = 0; i < numAtoms; i++) {
                variableValues[i] = (float) varAtoms[i].get(GRB.DoubleAttr.X);
            }

            ObjectiveResult objectiveResult = parallelComputeObjective(termStore);

            optimizationComplete(termStore, objectiveResult, System.currentTimeMillis() - start);

            model.dispose();
            env.dispose();

            return objectiveResult.objective;
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
    }

    private void processTerms(TermStore<GurobiObjectiveTerm> termStore, GRBModel model, GRBVar[] varAtoms) throws GRBException {
        GRBQuadExpr objective = new GRBQuadExpr();

        int termIndex = 0;
        for (GurobiObjectiveTerm term : termStore) {
            float[] coefficients = term.getCoefficients();
            int[] termAtomIndexes = term.getAtomIndexes();

            GRBLinExpr linearExpression = new GRBLinExpr();
            for (int i = 0; i < term.size(); i++) {
                linearExpression.addTerm(coefficients[i], varAtoms[termAtomIndexes[i]]);
            }
            linearExpression.addConstant(-term.getConstant());

            if (term.isConstraint()) {
                model.addConstr(linearExpression, comparatorMap.get(term.getComparator()), 0.0, "c" + termIndex);
            } else {
                GRBVar var;
                double weight = term.getWeight();
                if (term.isHinge()) {
                    var = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "slack" + termIndex);
                    model.addConstr(linearExpression, GRB.LESS_EQUAL, var, "c_slack" + termIndex);
                } else {
                    var = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "eq" + termIndex);
                    model.addConstr(linearExpression, GRB.EQUAL, var, "c_eq" + termIndex);
                }
                if (term.isSquared()) {
                    objective.addTerm(weight, var, var);
                } else {
                    objective.addTerm(weight, var);
                }
            }
            termIndex++;
        }
        model.setObjective(objective);
    }
}
