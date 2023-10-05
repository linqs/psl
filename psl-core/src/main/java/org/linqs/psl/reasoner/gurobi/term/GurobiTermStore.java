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
package org.linqs.psl.reasoner.gurobi.term;

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBQuadExpr;
import gurobi.GRBVar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GurobiTermStore extends SimpleTermStore<GurobiObjectiveTerm> {
    GRBEnv env;
    GRBModel model;

    protected Map<GroundAtom, GRBVar> atomToModelVariableMap;
    protected List<GRBVar> termSlackVariables;

    private static final Map<FunctionComparator, Character> comparatorMap;

    static {
        comparatorMap = new HashMap<>();
        comparatorMap.put(FunctionComparator.EQ, GRB.EQUAL);
        comparatorMap.put(FunctionComparator.GTE, GRB.GREATER_EQUAL);
        comparatorMap.put(FunctionComparator.LTE, GRB.LESS_EQUAL);
    }

    public GurobiTermStore(AtomStore atomStore) {
        super(atomStore, new GurobiTermGenerator());

        try {
            env = new GRBEnv(false);
            env.set("LogToConsole", "0");

            model = new GRBModel(env);
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        atomToModelVariableMap = new HashMap<GroundAtom, GRBVar>();
        termSlackVariables = new ArrayList<GRBVar>();
    }

    public GRBModel getModel() {
        updateModelVariables();
        setModelObjective();

        return model;
    }

    public void syncModelVariables() {
        float[] atomValues = getVariableValues();

        try {
            for (GroundAtom atom : atomToModelVariableMap.keySet()) {
                int atomIndex = atomStore.getAtomIndex(atom);
                atomValues[atomIndex] = (float) atomToModelVariableMap.get(atom).get(GRB.DoubleAttr.X);
            }
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
    }

    private void setModelObjective() {
        GRBQuadExpr objective = new GRBQuadExpr();

        for (int termIndex = 0; termIndex < size(); termIndex++) {
            GurobiObjectiveTerm term = get(termIndex);

            GRBVar slackVariable = termSlackVariables.get(termIndex);

            if (term.isSquared()) {
                objective.addTerm(term.getWeight(), slackVariable, slackVariable);
            } else {
                objective.addTerm(term.getWeight(), slackVariable);
            }
        }

        try {
            model.setObjective(objective, GRB.MINIMIZE);
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
    }

    private void updateModelVariables() {
        GroundAtom[] variableAtoms = atomStore.getAtoms();
        for (int i = 0; i < atomStore.size(); i++) {
            GroundAtom atom = variableAtoms[i];
            if (!atomToModelVariableMap.containsKey(atom)) {
                // This atom was collapsed into the term constant.
                continue;
            }

            try {
                if (atom.isFixed()) {
                    atomToModelVariableMap.get(atom).set(GRB.DoubleAttr.LB, atom.getValue());
                    atomToModelVariableMap.get(atom).set(GRB.DoubleAttr.UB, atom.getValue());
                } else {
                    if (atom.getPredicate().isInteger()) {
                        atomToModelVariableMap.get(atom).set(GRB.CharAttr.VType, GRB.INTEGER);
                    } else {
                        atomToModelVariableMap.get(atom).set(GRB.CharAttr.VType, GRB.CONTINUOUS);
                        atomToModelVariableMap.get(atom).set(GRB.DoubleAttr.Start, atom.getValue());
                    }

                    atomToModelVariableMap.get(atom).set(GRB.DoubleAttr.LB, 0.0);
                    atomToModelVariableMap.get(atom).set(GRB.DoubleAttr.UB, 1.0);
                }
            } catch (GRBException e) {
                throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
            }
        }
    }

    @Override
    public GurobiTermStore copy() {
        GurobiTermStore gurobiTermStoreCopy = new GurobiTermStore(atomStore.copy());

        for (GurobiObjectiveTerm term : allTerms) {
            gurobiTermStoreCopy.add(term.copy());
        }

        return gurobiTermStoreCopy;
    }

    @Override
    public synchronized int add(ReasonerTerm term) {
        super.add(term);

        // Check if the atoms in the term are already in the model. If not, add them.
        for (int atomIndex : term.getAtomIndexes()) {
            GroundAtom atom = atomStore.getAtom(atomIndex);
            if (!atomToModelVariableMap.containsKey(atom)) {
                GRBVar modelVariable = null;

                try {
                    if (atom.isFixed()) {
                        modelVariable = model.addVar(atom.getValue(), atom.getValue(), 0.0, GRB.CONTINUOUS, atom.toString());
                    } else{
                        if (atom.getPredicate().isInteger()) {
                            modelVariable = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, atom.toString());
                        } else {
                            modelVariable = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, atom.toString());
                        }
                    }
                } catch (GRBException e) {
                    throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
                }

                atomToModelVariableMap.put(atom, modelVariable);
            }
        }

        // Add the term to the model.
        float[] coefficients = term.getCoefficients();
        int[] termAtomIndexes = term.getAtomIndexes();

        // Create the linear expression defining the term.
        GRBLinExpr linearExpression = new GRBLinExpr();
        for (int i = 0; i < term.size(); i++) {
            linearExpression.addTerm(coefficients[i], atomToModelVariableMap.get(atomStore.getAtom(termAtomIndexes[i])));
        }
        linearExpression.addConstant(-term.getConstant());

        // Add the term to the model constraints. If the term is a constraint, add it as a constraint, else add it to the objective.
        // Non-constraint terms are added as an inequality constraint with a slack variable.
        GRBVar slackVariable = null;
        try {
            if (term.isConstraint()) {
                model.addConstr(linearExpression, comparatorMap.get(term.getComparator()), 0.0, "c" + size());
            } else {
                if (term.isHinge()) {
                    slackVariable = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "slack" + size());
                    model.addConstr(linearExpression, GRB.LESS_EQUAL, slackVariable, "c_slack" + size());
                } else {
                    slackVariable = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "eq" + size());
                    model.addConstr(linearExpression, GRB.EQUAL, slackVariable, "c_eq" + size());
                }
            }
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        termSlackVariables.add(slackVariable);

        return 1;
    }

    @Override
    public void close() {
        super.close();

        try {
            env.dispose();
            model.dispose();
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
    }
}
