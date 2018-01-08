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

import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OptimalCover {
	private static final Logger log = LoggerFactory.getLogger(OptimalCover.class);

	// TODO(eriq): Config (global?)

	/**
	 * The cost for a block is divided by this.
	 */
	public static final double BLOCK_ADVANTAGE = 100.0;

	/**
	 * The cost for a JOIN.
	 */
	public static final double JOIN_PENALTY = 2.0;

	// Static only.
	private OptimalCover() {}

	// TODO(eriq): Functional predictaes.
	// TODO(eriq): Our calculations are a bit off because we don't consider partitions in counts.
	// TODO(eriq): Think more about passthroughs: non-standard predicates, atoms w/o variables.

	/**
	 * Given a querable formula (see DatabaseQuery), find a fomula that will return the same variable assignments,
	 * but while minimizing joins and intermitent result set size.
	 */
	public static Formula computeOptimalCover(Formula baseFormula, RDBMSDataStore dataStore) {
		// Once validated, we know that the formula is a conjunction or single atom.
		DatabaseQuery.validate(baseFormula);

		// Shortcut for priors (single atoms).
		if (baseFormula instanceof Atom) {
			return baseFormula;
		}

		Set<Atom> formulaAtoms = baseFormula.getAtoms(new HashSet<Atom>());
		List<Formula> usedAtoms = new ArrayList<Formula>();

		filterBaseAtoms(formulaAtoms, usedAtoms);

		Map<Variable, Set<Atom>> variableUsages = getVariableUsages(formulaAtoms, usedAtoms);
		collectSingletonVariables(usedAtoms, variableUsages);
		computeOptimalCover(usedAtoms, variableUsages, dataStore);

		Formula optimal = null;
		if (usedAtoms.size() == 1) {
			optimal = usedAtoms.get(0);
		} else {
			optimal = new Conjunction(usedAtoms.toArray(new Formula[0]));
		}

		log.debug("Computed optimal cover for [{}]: [{}].", baseFormula, optimal);
		return optimal;
	}

	/**
	 * Compute the optimal cover for any remaining variables.
	 */
	private static void computeOptimalCover(List<Formula> atoms, Map<Variable, Set<Atom>> variableUsages, RDBMSDataStore dataStore) {
		if (variableUsages.size() == 0) {
			return;
		}

		// TODO(eriq): For now, we will just compute the greedy cover.
		greedyCover(atoms, variableUsages, dataStore);
	}

	/**
	 * For each variable, choose the atom with the lowest cost.
	 * This may be suboptimal when there are multiple unused variables per atom.
	 * We will handle one variable at a time in arbitrary order.
	 * Each atom will get assigned a cost = (rowCount / BLOCK_ADVANTAGE) * JOIN_PENALTY^(maxSatisfiableVariables - satisfiedVariables).
	 * Where maxSatisfiableVariables is the most number of variables satisfied by an atom adjacent to the variable.
	 * The last product with satisfable atoms is done because every variable we do not satisfy needs to be satified with another join.
	 */
	private static void greedyCover(List<Formula> atoms, Map<Variable, Set<Atom>> variableUsages, RDBMSDataStore dataStore) {
		Map<Atom, Integer> satisfiableVariablesMap = new HashMap<Atom, Integer>();

		// We will only compute one variable per pass.
		while (variableUsages.size() != 0) {
			Variable variable = variableUsages.keySet().iterator().next();
			Set<Atom> potentialAtoms = variableUsages.get(variable);
			Atom chosenAtom = null;

			// If this is a singleton, then there is no decision.
			if (potentialAtoms.size() == 1) {
				chosenAtom = potentialAtoms.iterator().next();
			} else {
				double bestCost = -1;
				Atom bestAtom = null;

				// Before we can score an atom, we need to know what the maximum number of
				// satisfiable variables across all the atoms adjacent to this variable.
				int maxSatisfiableVariables = 1;
				satisfiableVariablesMap.clear();
				for (Atom potentialAtom : potentialAtoms) {
					int satisfiableVariables = 0;
					for (Term term : potentialAtom.getArguments()) {
						if (term instanceof Variable && variableUsages.containsKey((Variable)term)) {
							satisfiableVariables++;
						}
					}

					satisfiableVariablesMap.put(potentialAtom, new Integer(satisfiableVariables));
					if (satisfiableVariables > maxSatisfiableVariables) {
						maxSatisfiableVariables = satisfiableVariables;
					}
				}

				for (Atom potentialAtom : potentialAtoms) {
					// All non-standard predicates were pulled out earlier.
					StandardPredicate predicate = (StandardPredicate)potentialAtom.getPredicate();
					double cost = dataStore.getPredicateRowCount(predicate);
					if (predicate.isBlock()) {
						cost /= BLOCK_ADVANTAGE;
					}

					cost *= Math.pow(JOIN_PENALTY, maxSatisfiableVariables - satisfiableVariablesMap.get(potentialAtom).intValue());

					if (bestAtom == null || cost < bestCost) {
						bestAtom = potentialAtom;
						bestCost = cost;
					}
				}

				chosenAtom = bestAtom;
			}

			atoms.add(chosenAtom);

			// Remove all variables now satisfied by adding this atom.
			for (Term term : chosenAtom.getArguments()) {
				if (term instanceof Variable) {
					variableUsages.remove((Variable)term);
				}
			}

			for (Set<Atom> atomUsage : variableUsages.values()) {
				atomUsage.remove(chosenAtom);
			}
		}
	}

	/**
	 * Get any variables that only appear in one atom and add them to the used atoms.
	 * As atoms are added to usedAtoms, they will be removed from variableUsages.
	 */
	private static void collectSingletonVariables(List<Formula> atoms, Map<Variable, Set<Atom>> variableUsages) {
		Set<Variable> satisfiedVariables = new HashSet<Variable>();

		// We will make multiple passes until no removals are made.
		// We will satisfy at most one variable each pass.
		boolean done = false;
		while (!done) {
			done = true;
			satisfiedVariables.clear();
			Atom usedAtom = null;

			for (Map.Entry<Variable, Set<Atom>> entry : variableUsages.entrySet()) {
				if (entry.getValue().size() != 1) {
					continue;
				}

				done = false;
				usedAtom = entry.getValue().iterator().next();
				atoms.add(usedAtom);

				for (Term term : usedAtom.getArguments()) {
					if (term instanceof Variable) {
						satisfiedVariables.add((Variable)term);
					}
				}

				break;
			}

			if (!done) {
				// Remove the variable satisfied this round.
				for (Variable satisfiedVariable : satisfiedVariables) {
					variableUsages.remove(satisfiedVariable);
				}

				// Remove the atom chosen in this round.
				// Remember, multiple variables may be using the same atom.
				for (Set<Atom> atomUsage : variableUsages.values()) {
					atomUsage.remove(usedAtom);
				}
			}
		}
	}

	/**
	 * Collect all the atoms that each variable appears in.
	 */
	private static Map<Variable, Set<Atom>> getVariableUsages(Set<Atom> atoms, List<Formula> usedAtoms) {
		Map<Variable, Set<Atom>> usages = new HashMap<Variable, Set<Atom>>();

		for (Atom atom : atoms) {
			boolean hasVariables = false;

			for (Term term : atom.getArguments()) {
				if (!(term instanceof Variable)) {
					continue;
				}

				hasVariables = true;
				Variable variable = (Variable)term;
				if (!usages.containsKey(variable)) {
					usages.put(variable, new HashSet<Atom>());
				}
				usages.get(variable).add(atom);
			}

			// Pass through atoms with no variables.
			if (!hasVariables) {
				usedAtoms.add(atom);
			}
		}

		return usages;
	}

	/**
	 * Filter the initial set of atoms.
	 * Remove external functional prediates and pass through special predicates.
	 */
	private static void filterBaseAtoms(Set<Atom> atoms, List<Formula> usedAtoms) {
		List<Atom> removeAtoms = new ArrayList<Atom>();

		for (Atom atom : atoms) {
			if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
				// Skip. These are handled at instantiation time.
				removeAtoms.add(atom);
			} else if (atom.getPredicate() instanceof SpecialPredicate) {
				// Passthrough.
				removeAtoms.add(atom);
				usedAtoms.add(atom);
			} else if (!(atom.getPredicate() instanceof StandardPredicate)) {
				throw new IllegalStateException("Unknown predicate type: " + atom.getPredicate().getClass().getName());
			}
		}

		atoms.removeAll(removeAtoms);
	}
}
