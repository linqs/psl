/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.config.Config;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.rdbms.driver.TableStats;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.SpecialPredicate;
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

	public static final String CONFIG_PREFIX = "queryrewriter";

	// TODO(eriq): Config and experimentation.
	public static final double ALLOWED_INCREASE = 2.0;

	// Static only.
	private QueryRewriter() {}

	/**
	 * Rewrite the query to minimize the execution time while trading off query size.
	 */
	public static Formula rewrite(Formula baseFormula, RDBMSDataStore dataStore) {
		// Once validated, we know that the formula is a conjunction or single atom.
		DatabaseQuery.validate(baseFormula);

		// Shortcut for priors (single atoms).
		if (baseFormula instanceof Atom) {
			return baseFormula;
		}

		Set<Atom> usedAtoms = baseFormula.getAtoms(new HashSet<Atom>());
		Set<Atom> passthrough = filterBaseAtoms(usedAtoms);

		Map<Predicate, TableStats> tableStats = fetchTableStats(usedAtoms, dataStore);

		double baseCost = computeQueryCost(usedAtoms, null, tableStats, dataStore);
		double currentCost = baseCost;

		while (true) {
			// The estimated query cost after removing the target atom.
			double bestCost = -1;
			Atom bestAtom = null;

			// TEST
			System.out.println(usedAtoms);

			for (Atom atom : usedAtoms) {
				// TEST
				System.out.println("	" + atom);

				if (canRemove(atom, usedAtoms)) {
					double cost = computeQueryCost(usedAtoms, atom, tableStats, dataStore);

					// TEST
					System.out.println("		Remove: " + atom + " -- " + cost);

					if (bestAtom == null || cost < bestCost) {
						bestAtom = atom;
						bestCost = cost;
					}
				}
			}

			if (bestAtom == null) {
				break;
			}

			// We expect the cost to go up, but will cut it off at some point.
			if (bestCost > (baseCost * ALLOWED_INCREASE)) {
				break;
			}

			usedAtoms.remove(bestAtom);
			currentCost = bestCost;
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

	/**
	 * Estimate the cost of the query (conjunctive query over the given atoms).
	 * Based off of: http://users.csc.calpoly.edu/~dekhtyar/468-Spring2016/lectures/lec17.468.pdf
	 * @param ignore if not null, then do not include it in the cost computation.
	 */
	private static double computeQueryCost(Set<Atom> atoms, Atom ignore, Map<Predicate, TableStats> tableStats, RDBMSDataStore dataStore) {
		double cost = 1.0;

		// Start with the product of the joins.
		for (Atom atom : atoms) {
			if (atom == ignore) {
				continue;
			}

			cost *= tableStats.get(atom.getPredicate()).getCount();
		}

		// Now take out the join factor for each join attribute.
		Set<Variable> variables = getAllUsedVariables(atoms, null);
		for (Variable variable : variables) {
			int cardinalitiesCount = 0;
			int minCardinality = 0;

			for (Atom atom : atoms) {
				if (atom == ignore) {
					continue;
				}

				if (!atom.getVariables().contains(variable)) {
					continue;
				}

				// Get the position of this argument so we can look up the cardinality of the column.
				int columnIndex = -1;
				Term[] args = atom.getArguments();
				for (int i = 0; i < args.length; i++) {
					if (variable.equals(args[i])) {
						columnIndex = i;
						break;
					}
				}

				String columnName = dataStore.getPredicateInfo(atom.getPredicate()).argumentColumns().get(columnIndex);
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
	 * Is it safe to remove the given atom from the given query?
	 */
	private static boolean canRemove(Atom atom, Set<Atom> usedAtoms) {
		// Make sure that we do not remove any variables (ie there is at least one other atom that uses the variable)..
		Set<Variable> remainingVariables = atom.getVariables();
		remainingVariables.removeAll(getAllUsedVariables(usedAtoms, atom));

		return remainingVariables.size() == 0;
	}

	private static Set<Variable> getAllUsedVariables(Set<Atom> atoms, Atom ignore) {
		Set<Variable> variables = new HashSet<Variable>();

		for (Atom atom : atoms) {
			if (atom == ignore) {
				continue;
			}

			variables.addAll(atom.getVariables());
		}

		return variables;
	}

	private static Map<Predicate, TableStats> fetchTableStats(Set<Atom> usedAtoms, RDBMSDataStore dataStore) {
		Set<Predicate> predicates = new HashSet<Predicate>();
		for (Atom atom : usedAtoms) {
			predicates.add(atom.getPredicate());
		}

		Map<Predicate, TableStats> stats = new HashMap<Predicate, TableStats>();
		for (Predicate predicate : predicates) {
			stats.put(predicate, dataStore.getDriver().getTableStats(dataStore.getPredicateInfo(predicate)));
		}

		return stats;
	}

	/**
	 * Filter the initial set of atoms.
	 * Remove external functional prediates and pass through special predicates.
	*/
	private static Set<Atom> filterBaseAtoms(Set<Atom> atoms) {
		Set<Atom> passthrough = new HashSet<Atom>();

		Set<Atom> removeAtoms = new HashSet<Atom>();
		for (Atom atom : atoms) {
			if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
				// Skip. These are handled at instantiation time.
				removeAtoms.add(atom);
			} else if (atom.getPredicate() instanceof SpecialPredicate) {
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
