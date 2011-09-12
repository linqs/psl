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

import de.mathnbits.util.RetrievalSet;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.evaluation.statistics.filter.AtomFilter;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomStore;
import edu.umd.cs.psl.model.predicate.Predicate;

public class SimpleResultComparator implements ResultComparator {

	public static final double DefaultTolerance = 0.1;
	
	private final DatabaseAtomStoreQuery result;
	private DatabaseAtomStoreQuery baseline;
	
	private AtomFilter resultFilter = AtomFilter.NoFilter;
	private AtomFilter baselineFilter = AtomFilter.NoFilter;
	
	private ValueComparator valueCompare = ValueComparator.Threshold;
	private double tolerance = DefaultTolerance;
	
	public SimpleResultComparator(DatabaseAtomStoreQuery outcome) {
		this.result=outcome;
		baseline = null;
	}

	@Override
	public void setValueComparator(ValueComparator comp) {
		this.valueCompare=comp;
	}

	@Override
	public void setBaseline(DatabaseAtomStoreQuery baseline) {
		this.baseline = baseline;
	}
	
	@Override
	public void setBaseline(Database baseline) {
		this.baseline = DatabaseAtomStoreQuery.getIndependentInstance(baseline);
	}
	
	@Override
	public void setTolerance(double tolerance) {
		this.tolerance = tolerance;
	}

	@Override
	public void setResultFilter(AtomFilter af) {
		resultFilter = af;
	}

	@Override
	public void setBaselineFilter(AtomFilter af) {
		baselineFilter = af;
	}
	/**
	 * Compares the baseline with the inferred result for a given predicate.
	 * 
	 * @param Predicate
	 *            p : The predicate to compare
	 * 
	 */
	public ResultComparison compare(Predicate p) {
		RetrievalSet<Atom> resultAtoms = new RetrievalSet<Atom>();
		Iterator<Atom> iter = resultFilter.filter(result.getAtomSet(p).iterator());
		int tp = 0;
		int fn = 0;
		int tn = 0;
		int fp = 0;
		while (iter.hasNext()) {
			resultAtoms.add(iter.next());
		}

		Map<Atom, Double> errors = new HashMap<Atom, Double>();

		Set<Atom> correctAtoms = new HashSet<Atom>();
		Iterator<Atom> baselineAtom = baselineFilter.filter(baseline.getAtomSet(p).iterator());
		while (baselineAtom.hasNext()) {
			Atom bAtom = baselineAtom.next();
			Atom resultAtom = resultAtoms.get(bAtom);
			double[] compValue = null;
			if (resultAtom == null) { // baseline atom not present in result
				compValue = bAtom.getSoftValues();
				double[] resultValues = { 0.0 };
				double diff = valueCompare.getDifference(compValue, resultValues, tolerance);
				if (diff == 0.0) {
					tn++; // true negative
					correctAtoms.add(bAtom);
				} else {
					fn++; // false negative
					errors.put(bAtom, diff);
				}
			} else { // baseline atom present in result
				compValue = bAtom.getSoftValues();
				double diff = valueCompare.getDifference(compValue,
						resultAtom.getSoftValues(), tolerance);
				if (diff == 0.0)
					if (compValue[0] < tolerance) {
						tn++; // true negative
						correctAtoms.add(resultAtom);
					} else {
						tp++; // true positive
						correctAtoms.add(resultAtom);
					}
				else if (compValue[0] < tolerance) {
					fp++; // false positive
					errors.put(resultAtom, diff);
				} else {
					fn++; // false negative
					errors.put(resultAtom, diff);
				}
			}
		}
		int noOfBaseAtoms = tp + fp + tn + fn;
		return new SimpleResultComparison(errors, correctAtoms, tp, fp, tn, fn, noOfBaseAtoms);
	}
	
	/**
	 * Compares the baseline with the inferred result for a given predicate.
	 * 
	 * @param Predicate p : The predicate to compare
	 * @param int maxAtoms : Defines the maximum number of base atoms that can be found for the given predicate. (This will vary, depending on the predicate and the problem.)
	 */
	@Override
	public ResultComparison compare(Predicate p, int maxBaseAtoms) {
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
					errors.put(result.getAtom(p, (GroundTerm[])baseAtom.getArguments()), diff);
					fn++;
				}
			}
		}
		// compute true negatives
		tn = maxBaseAtoms - tp - fp - fn;
		return new SimpleResultComparison(errors,correctAtoms,tp,fp,tn,fn,noBaseAtoms);
	}
	
}
