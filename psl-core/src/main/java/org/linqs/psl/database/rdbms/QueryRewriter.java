/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Try to rewrite a grounding query to make it more efficient.
 * Of course, we would like the query to be faster (more simple) and return less results.
 * However we can't always do that, so we will tolerate more results in exchange for a faster query
 * (since trivial ground rule checking is fast).
 * Note that this class will make heavy use of referential equality.
 */
public class QueryRewriter {
    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    /**
     * The different methods for estimating the join cost.
     */
    public static enum CostEstimator { SIZE, SELECTIVITY, HISTOGRAM }

    private double allowedTotalCostIncrease;
    private double allowedStepCostIncrease;

    private CostEstimator costEstimator;

    public QueryRewriter() {
        allowedTotalCostIncrease = Options.QR_ALLOWED_TOTAL_INCREASE.getDouble();
        allowedStepCostIncrease = Options.QR_ALLOWED_STEP_INCREASE.getDouble();
        costEstimator = CostEstimator.valueOf(Options.QR_COST_ESTIMATOR.getString().toUpperCase());
    }

    /**
     * Rewrite the query to minimize the execution time while trading off query size.
     */
    public Formula rewrite(Formula baseFormula, RDBMSDataStore dataStore) {
        // Once validated, we know that the formula is a conjunction or single atom.
        DatabaseQuery.validate(baseFormula);

        // Shortcut for priors (single atoms).
        if (baseFormula instanceof Atom) {
            return baseFormula;
        }

        Set<Atom> usedAtoms = baseFormula.getAtoms(new HashSet<Atom>());
        Set<Atom> passthrough = filterBaseAtoms(usedAtoms);

        Map<Predicate, TableStats> tableStats = fetchTableStats(usedAtoms, dataStore);

        double baseCost = estimateQuerySize(costEstimator, usedAtoms, null, tableStats, dataStore);
        double currentCost = baseCost;

        log.trace("Starting cost: " + baseCost);

        // TODO(eriq): A DP approach should be better than a greedy one.

        while (true) {
            // The estimated query cost after removing the target atom.
            double bestCost = -1;
            Atom bestAtom = null;

            for (Atom atom : usedAtoms) {
                if (canRemove(atom, usedAtoms)) {
                    double cost = estimateQuerySize(costEstimator, usedAtoms, atom, tableStats, dataStore);
                    if (cost < 0.0) {
                        log.trace("Planned Cost for (" + usedAtoms + " - " + atom + "): MAX");
                        continue;
                    }

                    log.trace("Planned Cost for (" + usedAtoms + " - " + atom + "): " + cost);

                    if (bestAtom == null || cost < bestCost) {
                        bestAtom = atom;
                        bestCost = cost;
                    }
                }
            }

            // We couldn't find any viable plans.
            if (bestAtom == null) {
                break;
            }

            // We expect the cost to go up, but will cut it off at some point.
            if (bestCost > (baseCost * allowedTotalCostIncrease) || bestCost > (currentCost * allowedStepCostIncrease)) {
                break;
            }

            usedAtoms.remove(bestAtom);
            currentCost = bestCost;

            log.trace("Choose plan for iteration: " + usedAtoms + ": " + bestCost);
        }

        usedAtoms.addAll(passthrough);

        Formula query = null;
        if (usedAtoms.size() == 1) {
            query = usedAtoms.iterator().next();
        } else {
            query = new Conjunction(usedAtoms.toArray(new Formula[0]));
        }

        log.debug("Computed cost-based query rewrite for [{}]({}): [{}]({}).", baseFormula, baseCost, query, currentCost);
        return query;
    }

    private double estimateQuerySize(CostEstimator costEstimator, Set<Atom> atoms, Atom ignore, Map<Predicate, TableStats> tableStats, RDBMSDataStore dataStore) {
        if (costEstimator == CostEstimator.HISTOGRAM) {
            return estimateQuerySizeWithHistorgram(atoms, ignore, tableStats, dataStore);
        } else if (costEstimator == CostEstimator.SELECTIVITY) {
            return estimateQuerySizeWithSelectivity(atoms, ignore, tableStats, dataStore);
        } else if (costEstimator == CostEstimator.SIZE) {
            return estimateQuerySizeWithSize(atoms, ignore, tableStats, dataStore);
        }

        throw new IllegalStateException("Unknown CostEstimator value: " + costEstimator);
    }

    /**
     * Estimate the cost of the query (conjunctive query over the given atoms) using histogram stats.
     * Based off of: http://consystlab.unl.edu/Documents/StudentReports/Working-Note2-2008.pdf
     * @param ignore if not null, then do not include it in the cost computation.
     * @return negative if we exceed max size and should just discard this plan.
     */
    private double estimateQuerySizeWithHistorgram(Set<Atom> atoms, Atom ignore, Map<Predicate, TableStats> tableStats, RDBMSDataStore dataStore) {
        double cost = 1.0;

        // Start with the product of the joins.
        for (Atom atom : atoms) {
            if (atom == ignore) {
                continue;
            }

            cost *= tableStats.get(atom.getPredicate()).getCount();
        }

        // Now compute for each variable.
        for (Map.Entry<Variable, Set<Atom>> entry : getAllUsedVariables(atoms, null).entrySet()) {
            Variable variable = entry.getKey();
            Set<Atom> involvedAtoms = entry.getValue();

            // Only worry about join columns.
            if (involvedAtoms.size() <= 1) {
                continue;
            }

            // The histogram that we will chain together using all the involved atoms.
            SelectivityHistogram joinHistogram = null;
            double crossProductSize = 1.0;

            for (Atom atom : involvedAtoms) {
                if (atom == ignore) {
                    continue;
                }

                crossProductSize *= tableStats.get(atom.getPredicate()).getCount();

                String columnName = getColumnName(dataStore, atom, variable);
                SelectivityHistogram histogram = tableStats.get(atom.getPredicate()).getHistogram(columnName);

                if (joinHistogram == null) {
                    joinHistogram = histogram;
                } else {
                    @SuppressWarnings("unchecked")
                    SelectivityHistogram ignoreUnchecked = joinHistogram.join(histogram);
                    joinHistogram = ignoreUnchecked;
                }
            }

            long joinSize = joinHistogram.size();
            if (joinSize == Long.MAX_VALUE) {
                return -1.0;
            }

            cost *= (joinHistogram.size() / crossProductSize);
        }

        return cost;
    }

    /**
     * Estimate the cost of the query (conjunctive query over the given atoms).
     * Based off of: http://users.csc.calpoly.edu/~dekhtyar/468-Spring2016/lectures/lec17.468.pdf
     * @param ignore if not null, then do not include it in the cost computation.
     */
    private double estimateQuerySizeWithSelectivity(Set<Atom> atoms, Atom ignore, Map<Predicate, TableStats> tableStats, RDBMSDataStore dataStore) {
        double cost = 1.0;

        // Start with the product of the joins.
        for (Atom atom : atoms) {
            if (atom == ignore) {
                continue;
            }

            cost *= tableStats.get(atom.getPredicate()).getCount();
        }

        // Now take out the join factor for each join attribute.
        for (Variable variable : getAllUsedVariables(atoms, null).keySet()) {
            int cardinalitiesCount = 0;
            int minCardinality = 0;

            for (Atom atom : atoms) {
                if (atom == ignore) {
                    continue;
                }

                if (!atom.getVariables().contains(variable)) {
                    continue;
                }

                String columnName = getColumnName(dataStore, atom, variable);
                int cardinality = tableStats.get(atom.getPredicate()).getCardinality(columnName);

                if (cardinalitiesCount == 0 || cardinality < minCardinality) {
                    minCardinality = cardinality;
                }
                cardinalitiesCount += 1;
            }

            // Only worry about join columns.
            if (cardinalitiesCount <= 1) {
                continue;
            }

            cost /= (double)minCardinality;
        }

        return cost;
    }

    /**
     * Estimate the cost of the query (conjunctive query over the given atoms) using just the size of the involved tables.
     * @param ignore if not null, then do not include it in the cost computation.
     */
    private double estimateQuerySizeWithSize(Set<Atom> atoms, Atom ignore, Map<Predicate, TableStats> tableStats, RDBMSDataStore dataStore) {
        double cost = 1.0;

        // Just use the product of the joins.
        for (Atom atom : atoms) {
            if (atom == ignore) {
                continue;
            }

            cost *= tableStats.get(atom.getPredicate()).getCount();
        }

        return cost;
    }

    private String getColumnName(RDBMSDataStore dataStore, Atom atom, Variable variable) {
        int columnIndex = -1;
        Term[] args = atom.getArguments();
        for (int i = 0; i < args.length; i++) {
            if (variable.equals(args[i])) {
                columnIndex = i;
                break;
            }
        }

        if (columnIndex == -1) {
            throw new java.util.NoSuchElementException(String.format("Could not find column name for variable %s in atom %s.", variable, atom));
        }

        return dataStore.getPredicateInfo(atom.getPredicate()).argumentColumns().get(columnIndex);
    }

    /**
     * Is it safe to remove the given atom from the given query?
     */
    private boolean canRemove(Atom atom, Set<Atom> usedAtoms) {
        // Make sure that we do not remove any variables (ie there is at least one other atom that uses the variable)..
        Set<Variable> remainingVariables = atom.getVariables();
        remainingVariables.removeAll(getAllUsedVariables(usedAtoms, atom).keySet());

        return remainingVariables.size() == 0;
    }

    private Map<Variable, Set<Atom>> getAllUsedVariables(Set<Atom> atoms, Atom ignore) {
        Map<Variable, Set<Atom>> variables = new HashMap<Variable, Set<Atom>>();

        for (Atom atom : atoms) {
            if (atom == ignore) {
                continue;
            }

            for (Variable variable : atom.getVariables()) {
                if (!variables.containsKey(variable)) {
                    variables.put(variable, new HashSet<Atom>());
                }

                variables.get(variable).add(atom);
            }
        }

        return variables;
    }

    private Map<Predicate, TableStats> fetchTableStats(Set<Atom> usedAtoms, RDBMSDataStore dataStore) {
        Set<Predicate> predicates = new HashSet<Predicate>();
        for (Atom atom : usedAtoms) {
            predicates.add(atom.getPredicate());
        }

        Map<Predicate, TableStats> stats = new HashMap<Predicate, TableStats>();
        for (Predicate predicate : predicates) {
            stats.put(predicate, dataStore.getPredicateInfo(predicate).getTableStats(dataStore.getDriver()));
        }

        return stats;
    }

    /**
     * Filter the initial set of atoms.
     * Remove external functional prediates and pass through grounding only predicates.
    */
    private Set<Atom> filterBaseAtoms(Set<Atom> atoms) {
        Set<Atom> passthrough = new HashSet<Atom>();

        Set<Atom> removeAtoms = new HashSet<Atom>();
        for (Atom atom : atoms) {
            if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
                // Skip. These are handled at instantiation time.
                removeAtoms.add(atom);
            } else if (atom.getPredicate() instanceof GroundingOnlyPredicate) {
                // Passthrough.
                removeAtoms.add(atom);
                passthrough.add(atom);
            } else if (!(atom.getPredicate() instanceof StandardPredicate)) {
                throw new IllegalStateException("Unknown predicate type: " + atom.getPredicate().getClass().getName());
            }
        }

        atoms.removeAll(removeAtoms);
        return passthrough;
    }
}
