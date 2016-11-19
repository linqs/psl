/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.filter.AtomFilter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.database.Queries;

public class DiscretePredictionComparator implements PredictionComparator {
	
	public static final double DEFAULT_THRESHOLD = 0.5;

	private final Database result;
	private Database baseline;
	private AtomFilter resultFilter;
	private double threshold;
	
	int tp;
	int fn;
	int tn;
	int fp;
	
	Map<GroundAtom, Double> errors;
	Set<GroundAtom> correctAtoms;

	
	public DiscretePredictionComparator(Database result) {
		this.result = result;
		baseline = null;
		resultFilter = AtomFilter.NoFilter;
		threshold = DEFAULT_THRESHOLD;
	}
	
	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	@Override
	public void setBaseline(Database db) {
		this.baseline = db;
	}
	
	@Override
	public void setResultFilter(AtomFilter af) {
		resultFilter = af;
	}

	/**
	 * Compares the baseline with te inferred result for a given predicate
	 * DOES NOT check the baseline database for atoms. Only use this if all 
	 * possible predicted atoms are active and unfiltered
	 */
	@Override
	public DiscretePredictionStatistics compare(Predicate p) {
		countResultDBStats(p);
		//System.out.println("tp " + tp + " fp " + fp + " tn " + tn + " fn " + fn);
		return new DiscretePredictionStatistics(tp, fp, tn, fn, threshold, errors, correctAtoms);
	}
	
	/**
	 * Compares the baseline with the inferred result for a given predicate. 
	 * Checks the baseline database for atoms
	 * 
	 * @param Predicate p : The predicate to compare
	 * @param int maxAtoms : Defines the maximum number of base atoms that can be found for the given predicate. (This will vary, depending on the predicate and the problem.)
	 */
	@Override
	public DiscretePredictionStatistics compare(Predicate p, int maxBaseAtoms) {
		countResultDBStats(p);
		
		Iterator<GroundAtom> res = resultFilter.filter(Queries.getAllAtoms(baseline, p).iterator());
		double expected;
		while (res.hasNext()) {
			GroundAtom baselineAtom = res.next();

			if (!errors.containsKey(baselineAtom) && !correctAtoms.contains(baselineAtom)) {
				//Missed result
				expected = (baselineAtom.getValue() >= threshold) ? 1.0 : 0.0;

				if (expected != 0.0) {
					errors.put(result.getAtom(baselineAtom.getPredicate(), baselineAtom.getArguments()), expected);
					fn++;
				}
			}
		}
		
		tn = maxBaseAtoms - tp - fp - fn;
		return new DiscretePredictionStatistics(tp, fp, tn, fn, threshold, errors, correctAtoms);
	}
	
	/**
	 * Subroutine used by both compare methods for counting statistics from atoms
	 * stored in result database
	 * @param p Predicate to compare against baseline database
	 */
	private void countResultDBStats(Predicate p) {
		tp = 0;
		fn = 0;
		tn = 0;
		fp = 0;
		
		errors = new HashMap<GroundAtom,Double>();
		correctAtoms = new HashSet<GroundAtom>();
		
		GroundAtom resultAtom, baselineAtom;
		Constant[] args;
		boolean actual, expected;

		Iterator<GroundAtom> iter = resultFilter.filter(Queries.getAllAtoms(result, p).iterator());
		
		while (iter.hasNext()) {
			resultAtom = iter.next();
			args = new Constant[resultAtom.getArity()];
			for (int i = 0; i < args.length; i++)
				args[i] = (Constant) resultAtom.getArguments()[i];
			baselineAtom = baseline.getAtom(resultAtom.getPredicate(), args);
			
			if (baselineAtom instanceof ObservedAtom) {
				actual = (resultAtom.getValue() >= threshold);
				expected = (baselineAtom.getValue() >= threshold);
				if (actual && expected || !actual && !expected) {
					// True negative
					if (!actual)
						tn++;
					// True positive
					else
						tp++;
					correctAtoms.add(resultAtom);
				}
				// False negative
				else if (!actual) {
					fn++;
					errors.put(resultAtom, -1.0);
				}
				// False positive
				else {
					fp++;
					errors.put(resultAtom, 1.0);
				}
			}
		}
	}
	
}
