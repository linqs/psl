/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.evaluation.statistics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.evaluation.statistics.filter.AtomFilter;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.util.database.Queries;

public class DiscretePredictionComparator implements PredictionComparator {
	
	public static final double DEFAULT_THRESHOLD = 0.5;

	private final Database result;
	private Database baseline;
	private AtomFilter resultFilter;
	private double threshold;
	
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

	@Override
	public DiscretePredictionStatistics compare(Predicate p) {
		int tp = 0;
		int fn = 0;
		int tn = 0;
		int fp = 0;
		Map<GroundAtom, Double> errors = new HashMap<GroundAtom, Double>();
		Set<GroundAtom> correctAtoms = new HashSet<GroundAtom>();
		
		GroundAtom resultAtom, baselineAtom;
		GroundTerm[] args;
		double actual, expected, diff;
		
		Iterator<GroundAtom> iter = resultFilter.filter(Queries.getAllAtoms(result, p).iterator());
		
		while (iter.hasNext()) {
			resultAtom = iter.next();
			args = new GroundTerm[resultAtom.getArity()];
			for (int i = 0; i < args.length; i++)
				args[i] = (GroundTerm) resultAtom.getArguments()[i];
			baselineAtom = baseline.getAtom(resultAtom.getPredicate(), args);
			
			if (baselineAtom instanceof ObservedAtom) {
				actual = (resultAtom.getValue() >= threshold) ? 1.0 : 0.0;
				expected = (baselineAtom.getValue() >= threshold) ? 1.0 : 0.0;
				diff = actual - expected;
				if (diff == 0.0) {
					// True negative
					if (actual == 0.0)
						tn++;
					// True positive
					else
						tp++;
					correctAtoms.add(resultAtom);
				}
				// False negative
				else if (diff == -1.0) {
					fn++;
					errors.put(resultAtom, diff);
				}
				// False positive
				else {
					fp++;
					errors.put(resultAtom, diff);
				}
			}
		}
		
		return new DiscretePredictionStatistics(tp, fp, tn, fn, threshold, errors, correctAtoms);
	}
	
	/**
	 * Compares the baseline with the inferred result for a given predicate.
	 * 
	 * @param Predicate p : The predicate to compare
	 * @param int maxAtoms : Defines the maximum number of base atoms that can be found for the given predicate. (This will vary, depending on the predicate and the problem.)
	 */
	@Override
	public DiscretePredictionStatistics compare(Predicate p, int maxBaseAtoms) {
		int tp = 0;
		int fn = 0;
		int tn = 0;
		int fp = 0;
		
		Map<GroundAtom, Double> errors = new HashMap<GroundAtom,Double>();
		Set<GroundAtom> correctAtoms = new HashSet<GroundAtom>();
		
		double actual, expected, diff;
		Iterator<GroundAtom> res = resultFilter.filter(Queries.getAllAtoms(result, p).iterator());
		while (res.hasNext()) {
			GroundAtom resultAtom = (RandomVariableAtom) res.next();
			
			GroundAtom baselineAtom = baseline.getAtom(resultAtom.getPredicate(), resultAtom.getArguments());
			
			if (baselineAtom instanceof ObservedAtom) {
				actual = (resultAtom.getValue() >= threshold) ? 1.0 : 0.0;
				expected = (baselineAtom.getValue() >= threshold) ? 1.0 : 0.0;
				diff = actual - expected;
				if (diff == 0.0) {
					// True negative
					if (actual == 0.0)
						tn++;
					// True positive
					else
						tp++;
					correctAtoms.add(resultAtom);
				}
				// False negative
				else if (diff == -1.0) {
					fn++;
					errors.put(resultAtom, diff);
				}
				// False positive
				else {
					fp++;
					errors.put(resultAtom, diff);
				}
			}
		}
		
		res = resultFilter.filter(Queries.getAllAtoms(baseline, p).iterator());
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
		
		/*
		RetrievalSet<Atom> baseAtoms = new RetrievalSet<Atom>();
		Iterator<Atom> iter = baselineFilter.filter(baseline.getAtomSet(p).iterator());
		int noBaseAtoms = 0;
		int tp = 0;
		int fn = 0;
		int tn = 0;
		int fp = 0;
		while (iter.hasNext()) {
			noBaseAtoms++;
			baseAtoms.add(iter.next());
		}
		
		Map<Atom,Double> errors = new HashMap<Atom,Double>();
		
		int noResultAtoms = 0;
		Set<Atom> correctAtoms = new HashSet<Atom>();
		Iterator<Atom> res = resultFilter.filter(result.getAtomSet(p).iterator());
		while(res.hasNext()) {
			Atom resultAtom = res.next();
			noResultAtoms++;
			Atom baseAtom=baseAtoms.get(resultAtom);
			double[] compValue = null;
			if (baseAtom==null) {
				//Default value
				compValue = p.getDefaultValues();
			}  else {
				compValue = baseAtom.getSoftValues();
			}
			double diff = valueCompare.getDifference(compValue, resultAtom.getSoftValues(), tolerance);
			if (diff!=0.0) {
				assert !errors.containsKey(resultAtom);
				errors.put(resultAtom, diff);
				fp++;
			} else {
				correctAtoms.add(resultAtom);
				tp++;
			}
		}
		for (Atom baseAtom : baseAtoms) {
			if (!errors.containsKey(baseAtom) && !correctAtoms.contains(baseAtom)) {
				//Missed result
				double diff = valueCompare.getDifference(baseAtom.getSoftValues(), p.getDefaultValues(), tolerance);
				if (diff!=0.0) {
					errors.put(result.getAtomRecord(p, (GroundTerm[])baseAtom.getArguments()), diff);
					fn++;
				}
			}
		}
		// compute true negatives
		tn = maxBaseAtoms - tp - fp - fn;
		return new SimplePredictionStatistics(errors,correctAtoms,tp,fp,tn,fn,noBaseAtoms);
		*/
	}
	
}
