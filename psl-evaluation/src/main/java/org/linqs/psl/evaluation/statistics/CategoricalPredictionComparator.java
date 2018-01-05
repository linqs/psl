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
package org.linqs.psl.evaluation.statistics;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.Queries;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.evaluation.statistics.filter.AtomFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compares all observed atoms in a baseline (truth) database against
 * all random variable atoms in a prediction (result) database assuming that
 * all passed in predicates are categorical.
 *
 * Categorical atoms are normal atoms that are interpreted to have two parts: the base and the category.
 * Ex: HasOS(x, 'Linux'), HasOS(x, 'BSD'), HasOS(x, 'Mac'), ...
 * Both the base and category can consist of more than one variable.
 * Ex: HasClimate(location, time, 'Cold', 'Rain'), HasClimate(location, time, 'Cold', 'Snow'), ...
 * However, each atom can only have one category.
 * Any arguments not in a category will be considered to be in the base.
 *
 * The best (highest truth value) category for each base will be chosen.
 * Then the truth database will be iterated over for atoms with a 1.0 truth value.
 * If the truth atom was chosen as the best category, then that is a hit.
 * Anything else is a miss.
 *
 * If an atom from the baseline (truth) database is not observed, it will be skipped.
 */
public class CategoricalPredictionComparator implements PredictionComparator {
	private final Database result;
	private Database baseline;
	private Set<Integer> catIndexSet;

	public CategoricalPredictionComparator(Database result) {
		this(result, null, null);
	}

	public CategoricalPredictionComparator(Database result, Database baseline, int[] categoryIndexes) {
		this.result = result;
		this.baseline = baseline;

		catIndexSet = new HashSet<Integer>();
		if (categoryIndexes != null) {
			for (int catIndex : categoryIndexes) {
				catIndexSet.add(catIndex);
			}
		}
	}

	public void setCategoryIndexes(int[] categoryIndexes) {
		catIndexSet.clear();
		if (categoryIndexes != null) {
			for (int catIndex : categoryIndexes) {
				catIndexSet.add(catIndex);
			}
		}
	}

	@Override
	public void setBaseline(Database db) {
		this.baseline = db;
	}

	@Override
	public void setResultFilter(AtomFilter af) {
		throw new UnsupportedOperationException();
	}

	@Override
	public CategoricalPredictionStatistics compare(StandardPredicate predicate) {
		if (catIndexSet.size() == 0) {
			throw new IllegalStateException("No category indexes have been proveded.");
		}

		if (catIndexSet.size() >= predicate.getArity()) {
			throw new IllegalStateException(String.format(
					"Too many category indexes for %s. Found: %d, Max: %d.",
					predicate.getName(),
					catIndexSet.size(),
					predicate.getArity() - 1));
		}

		int hits = 0;
		int misses = 0;

		Set<GroundAtom> bestCats = getBestCategories(predicate);

		for (GroundAtom truthAtom : Queries.getAllAtoms(baseline, predicate)) {
			if (!(truthAtom instanceof ObservedAtom)) {
				continue;
			}

			if (truthAtom.getValue() < 1.0) {
				continue;
			}

			if (bestCats.contains(truthAtom)) {
				hits++;
			} else {
				misses++;
			}
		}

		return new CategoricalPredictionStatistics(hits, misses);
	}

	/**
	 * Build up a set that has all the atoms that represet the best categorical assignments.
	 */
	private Set<GroundAtom> getBestCategories(StandardPredicate predicate) {
		int numArgs = predicate.getArity();

		// This map will be as deep as the number of category arguments.
		// The value will either be a GroundAtom representing the currently best category,
		// or another Map<Constant, Object>, and so on.
		Map<Constant, Object> bestCats = null;

		for (GroundAtom atom : Queries.getAllAtoms(result, predicate)) {
			@SuppressWarnings("unchecked")
			Map<Constant, Object> ignoreWarning = (Map<Constant, Object>)putBestCats(bestCats, atom, 0);
			bestCats = ignoreWarning;
		}

		Set<GroundAtom> rtn = new HashSet<GroundAtom>();
		collectBestCats(bestCats, rtn);

		return rtn;
	}

	/**
	 * Recursively descend into the map and put the atom in if it is a best category.
	 * Return what should be at the map where we descended (classic tree building style).
	 */
	private Object putBestCats(Object currentNode, GroundAtom atom, int argIndex) {
		assert(argIndex <= atom.getArity());

		// Skip this arg if it is a category.
		if (catIndexSet.contains(argIndex)) {
			return putBestCats(currentNode, atom, argIndex + 1);
		}

		// If we have coverd all the arguments, then we are either looking at a null
		// if there was no previous best or the previous best.
		if (argIndex == atom.getArity()) {
			if (currentNode == null) {
				return atom;
			}

			@SuppressWarnings("unchecked")
			GroundAtom oldBest = (GroundAtom)currentNode;

			if (atom.getValue() > oldBest.getValue()) {
				return atom;
			} else {
				return oldBest;
			}
		}

		// We still have further to descend.

		Map<Constant, Object> bestCats;
		if (currentNode == null) {
			bestCats = new HashMap<Constant, Object>();
		} else {
			@SuppressWarnings("unchecked")
			Map<Constant, Object> ignoreWarning = (Map<Constant, Object>)currentNode;
			bestCats = ignoreWarning;
		}

		Constant arg = atom.getArguments()[argIndex];
		bestCats.put(arg, putBestCats(bestCats.get(arg), atom, argIndex + 1));

		return bestCats;
	}

	private void collectBestCats(Map<Constant, Object> bestCats, Set<GroundAtom> result) {
		for (Object value : bestCats.values()) {
			if (value instanceof GroundAtom) {
				result.add((GroundAtom)value);
			} else {
				@SuppressWarnings("unchecked")
				Map<Constant, Object> ignoreWarning = (Map<Constant, Object>)value;
				collectBestCats(ignoreWarning, result);
			}
		}
	}
}
