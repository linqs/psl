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

    GRBEnv env;
    GRBModel model;

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

        try {
            env = new GRBEnv(false);
//            env.set("LogToConsole", "0");
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        model = null;
    }

    @Override
    public void close() {
        try {
            env.dispose();
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
    }

    @Override
    public double optimize(TermStore<GurobiObjectiveTerm> termStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        termStore.initForOptimization();
        int numAtoms = termStore.getNumVariables();

        ObjectiveResult objectiveResult = null;

        boolean hasDecisionVariables = false;
        GroundAtom[] variableAtoms = termStore.getAtomStore().getAtoms();
        for (int i = 0; i < numAtoms; i++) {
            if (!variableAtoms[i].isFixed()) {
                hasDecisionVariables = true;
                break;
            }
        }

        if (!hasDecisionVariables) {
            log.trace("No random variable atoms to optimize.");
            objectiveResult = parallelComputeObjective(termStore);
            return objectiveResult.objective;
        }

        long start = System.currentTimeMillis();

        log.trace("Building Gurobi model.");

        try {
            log.trace("Building Gurobi model.");
            model = buildModel(termStore);
            log.trace("Finished building Gurobi model.");

            model.optimize();

            GRBVar[] modelVariables = model.getVars();
            float[] variableValues = termStore.getVariableValues();
            for (int i = 0; i < numAtoms; i++) {
                variableValues[i] = (float) modelVariables[i].get(GRB.DoubleAttr.X);
            }

            model.dispose();
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        objectiveResult = parallelComputeObjective(termStore);

        optimizationComplete(termStore, objectiveResult, System.currentTimeMillis() - start);

        return objectiveResult.objective;
    }

    private void updateModelVariables(TermStore<GurobiObjectiveTerm> termStore, GRBVar[] modelVariables) throws GRBException {
        GroundAtom[] variableAtoms = termStore.getAtomStore().getAtoms();
        for (int i = 0; i < termStore.getNumVariables(); i++) {
            GroundAtom atom = variableAtoms[i];
            if (atom.isFixed()) {
                modelVariables[i].set(GRB.DoubleAttr.LB, atom.getValue());
                modelVariables[i].set(GRB.DoubleAttr.UB, atom.getValue());
            } else {
                if (atom.getPredicate().isInteger()) {
                    modelVariables[i].set(GRB.CharAttr.VType, GRB.INTEGER);
                } else {
                    modelVariables[i].set(GRB.CharAttr.VType, GRB.CONTINUOUS);
                    modelVariables[i].set(GRB.DoubleAttr.Start, atom.getValue());
                }

                modelVariables[i].set(GRB.DoubleAttr.LB, 0.0);
                modelVariables[i].set(GRB.DoubleAttr.UB, 1.0);
            }
        }
    }

    private GRBVar[] createModelVariables(TermStore<GurobiObjectiveTerm> termStore, GRBModel model) throws GRBException {
        GRBVar[] modelVariables = model.addVars(termStore.getNumVariables(), GRB.CONTINUOUS);
        updateModelVariables(termStore, modelVariables);

        return modelVariables;
    }

    private GRBModel buildModel(TermStore<GurobiObjectiveTerm> termStore) throws GRBException {
        GRBModel model = new GRBModel(env);
        model.set("WorkLimit", Integer.toString(workLimit));

        GRBVar[] modelVariables = createModelVariables(termStore, model);

        GRBQuadExpr objective = new GRBQuadExpr();
        for (int termIndex = 0; termIndex < termStore.size(); termIndex++) {
            GurobiObjectiveTerm term = termStore.get(termIndex);
            float[] coefficients = term.getCoefficients();
            int[] termAtomIndexes = term.getAtomIndexes();

            // Create the linear expression defining the term.
            GRBLinExpr linearExpression = new GRBLinExpr();
            for (int i = 0; i < term.size(); i++) {
                linearExpression.addTerm(coefficients[i], modelVariables[termAtomIndexes[i]]);
            }
            linearExpression.addConstant(-term.getConstant());

            // Add the term to the model. If the term is a constraint, add it as a constraint, else add it to the objective.
            // Non-constraint terms are added as a inequality constraint with a slack variable.
            if (term.isConstraint()) {
                model.addConstr(linearExpression, comparatorMap.get(term.getComparator()), 0.0, "c" + termIndex);
            } else {
                GRBVar var = null;
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
        }
        model.setObjective(objective);

        return model;
    }
}
