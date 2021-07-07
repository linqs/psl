/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.model.formula;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.ListUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts a {@link Formula} to a simplified Disjunctive Normal Form view
 * and makes the clauses available.
 */
public class FormulaAnalysis implements Serializable {
    private final Formula formula;
    private final List<DNFClause> clauses;

    public FormulaAnalysis(Formula formula) {
        this.formula = formula;

        // Converts the Formula to Disjunctive Normal Form and collects the clauses
        formula = formula.getDNF();
        Formula[] rawClauses;
        if (formula instanceof Disjunction) {
            Disjunction disj = (Disjunction)formula.flatten();
            rawClauses = new Formula[disj.length()];
            for (int i = 0; i < rawClauses.length; i++)
                rawClauses[i] = disj.get(i);
        } else {
            rawClauses = new Formula[] {formula};
        }

        // Processes each clause
        clauses = new ArrayList<DNFClause>(rawClauses.length);

        List<Atom> posLiterals = new ArrayList<Atom>(4);
        List<Atom> negLiterals = new ArrayList<Atom>(4);

        for (int i = 0; i < rawClauses.length; i++) {
            // Extracts the positive and negative literals from the clause
            if (rawClauses[i] instanceof Conjunction) {
                Conjunction c = (Conjunction)rawClauses[i].flatten();
                for (int j = 0; j < c.length(); j++) {
                    if (c.get(j) instanceof Atom) {
                        posLiterals.add((Atom) c.get(j));
                    } else if (c.get(j) instanceof Negation) {
                        Negation n = (Negation) c.get(j);
                        if (n.getFormula() instanceof Atom) {
                            negLiterals.add((Atom) n.getFormula());
                        } else {
                            throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
                        }
                    } else {
                        throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
                    }
                }
            } else if (rawClauses[i] instanceof Atom) {
                posLiterals.add((Atom) rawClauses[i]);
            } else if (rawClauses[i] instanceof Negation) {
                Negation n = (Negation) rawClauses[i];
                if (n.getFormula() instanceof Atom) {
                    negLiterals.add((Atom) n.getFormula());
                } else {
                    throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
                }
            } else {
                throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
            }

            // Stores the DNFClause.
            clauses.add(new DNFClause(posLiterals, negLiterals));
            posLiterals.clear();
            negLiterals.clear();
        }
    }

    /**
     * @return the original Formula that was analyzed
     */
    public Formula getFormula() {
        return formula;
    }

    /**
     * @return the number of clauses in the Formula after it has been converted to Disjunctive Normal Form.
     */
    public int getNumDNFClauses() {
        return clauses.size();
    }

    /**
     * Returns the specified clause of the Formula after it has been converted
     * to Disjunctive Normal Form.
     *
     * @param index  the clause's index
     * @return the DNF clause
     */
    public DNFClause getDNFClause(int index) {
        return clauses.get(index);
    }

    public static class DNFClause implements Serializable {
        private List<Atom> posLiterals;
        private List<Atom> negLiterals;
        private Formula query;
        private Set<Variable> unboundVariables;
        private boolean isGround;

        public DNFClause(List<Atom> posLiterals, List<Atom> negLiterals) {
            this.posLiterals = Collections.unmodifiableList(new ArrayList<Atom>(posLiterals));
            this.negLiterals = Collections.unmodifiableList(new ArrayList<Atom>(negLiterals));
            this.unboundVariables = new HashSet<Variable>();

            Set<Variable> allowedVariables = new HashSet<Variable>();

            // Checks if all Variables in the clause appear in a positive literal with a StandardPredicate.
            Set<Variable> setToAdd;

            for (Atom atom : posLiterals) {
                if (atom.getPredicate() instanceof StandardPredicate) {
                    setToAdd = allowedVariables;
                } else {
                    setToAdd = unboundVariables;
                }

                for (Term term : atom.getArguments()) {
                    if (term instanceof Variable) {
                        setToAdd.add((Variable)term);
                    }
                }
            }

            for (Atom atom : negLiterals) {
                for (Term term : atom.getArguments()) {
                    if (term instanceof Variable) {
                        unboundVariables.add((Variable) term);
                    }
                }
            }

            isGround = (allowedVariables.size() + unboundVariables.size() == 0) ? true : false;

            // Remove any allowed (bound) variables from the list of unbound variables.
            unboundVariables.removeAll(allowedVariables);

            // The unbound variables has been populated, now pin its contents.
            unboundVariables = Collections.unmodifiableSet(unboundVariables);

            if (posLiterals.size() == 0) {
                query = null;
            } else if (posLiterals.size() == 1) {
                query = (unboundVariables.isEmpty()) ? posLiterals.get(0) : null;
            } else {
                query = (unboundVariables.isEmpty()) ? new Conjunction(posLiterals.toArray(new Formula[posLiterals.size()])) : null;
            }
        }

        /**
         * @return the positive literals, i.e., Atoms not negated, in the clause
         */
        public List<Atom> getPosLiterals() {
            return posLiterals;
        }

        /**
         * @return the negative literals, i.e., negated Atoms, in the clause
         */
        public List<Atom> getNegLiterals() {
            return negLiterals;
        }

        /**
         * Returns any unbound variables.
         * A bound variable is a varible in the clause that appears at least once in a
         * positive literal with a {@link StandardPredicate}.
         * <p>
         * If all Variables are bound, then {@link DatabaseQuery DatabaseQueries}
         * can identify all groundings of the clause with possibly non-zero truth
         * values in a {@link Database}.
         *
         * @return an unmodifiable set containing any unbound variables, or an empty set.
         */
        public Set<Variable> getUnboundVariables() {
            return unboundVariables;
        }

        public boolean isGround() {
            return isGround;
        }

        public boolean isQueriable() {
            return (query != null);
        }

        public Formula getQueryFormula() {
            if (query != null) {
                return query;
            }

            throw new IllegalStateException("Clause is not queriable.");
        }

        public String toString() {
            List<String> allLiterals = new ArrayList<>();

            for (Atom posLit : getPosLiterals()) {
                allLiterals.add(posLit.toString());
            }

            for (Atom negLit : getNegLiterals()) {
                allLiterals.add("~" + negLit.toString());
            }

            return ListUtils.join(" & ", allLiterals);
        }
    }
}
