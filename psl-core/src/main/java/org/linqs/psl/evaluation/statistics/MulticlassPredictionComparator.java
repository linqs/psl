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
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.filter.AtomFilter;
import org.linqs.psl.evaluation.statistics.filter.MaxValueFilter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.database.Queries;

/**
 * Computes statistics for multiclass prediction.
 * 
 * NOTE: Currently only works with binary predicates of the form (example, label) or (label, example),
 * where example is a single {@link Constant} and label is a single {@link IntegerAttribute}.
 * 
 * @author Ben
 *
 */
public class MulticlassPredictionComparator implements ResultComparator {

	private final Database predDB;
	private Database truthDB;

	/**
	 * Constructor
	 * 
	 * @param predDB Predictions database. Target predicates must be closed.
	 */
	public MulticlassPredictionComparator(Database predDB) {
		this.predDB = predDB;
		this.truthDB = null;
	}
	
	/**
	 * Sets the ground truth database.
	 * 
	 * @param truthDB Ground truth database. Target predicates must be closed.
	 */
	@Override
	public void setBaseline(Database truthDB) {
		this.truthDB = truthDB;
	}

	/**
	 * TODO: Does it make sense to have this method for this class?
	 */
	@Override
	public void setResultFilter(AtomFilter af) {
		// TODO Auto-generated method stub
	}
	
	/**
	 * Returns prediction statistics in the form of a confusion matrix.
	 * 
	 * @param p A predicate
	 * @param labelMap A map indicating the mapping from the label to an index.
	 * @param labelIndex The index of the label in each example's terms.
	 * @return A {@link MulticlassPredictionStatistics}.
	 */
	public PredictionStatistics compare(Predicate p, Map<Constant,Integer> labelMap, int labelIndex) {
		/* Allocate a square confusion matrix. */
		int numClass = labelMap.size();
		int[][] cm = new int[numClass][numClass];
		
		/* Get all predicted atoms using max-score label. */
		Map<Example,Integer> pred = getAllMaxScoreAtoms(predDB, p, labelMap, labelIndex);
		
		/* Get all of the ground truth atoms. */
		Map<Example,Integer> truth = getAllMaxScoreAtoms(truthDB, p, labelMap, labelIndex);
		
		/* Iterate over all prediction and compare to ground truth. */
		for (Entry<Example,Integer> entry : pred.entrySet()) {
			Example ex = entry.getKey();
			if (!truth.containsKey(ex))
				throw new RuntimeException("Missing ground truth for example " + ex.toString());
			int predLabel = entry.getValue().intValue();
			int trueLabel = truth.get(ex);
			++cm[trueLabel][predLabel];
		}
		
		return new MulticlassPredictionStatistics(new ConfusionMatrix(cm));
	}
	
	/**
	 * Returns all of the examples for a given predicate, along with their max-scoring labels.
	 * 
	 * @param db
	 * @param p
	 * @param labelMap
	 * @param labelIndex
	 * @return
	 */
	private Map<Example,Integer> getAllMaxScoreAtoms(Database db, Predicate p, Map<Constant,Integer> labelMap, int labelIndex) {
		Map<Example,Integer> atoms = new HashMap<Example,Integer>();
		AtomFilter maxFilter = new MaxValueFilter(p, labelIndex);
		Iterator<GroundAtom> iter = maxFilter.filter(Queries.getAllAtoms(db, p).iterator());
		while (iter.hasNext()) {
			GroundAtom predAtom = iter.next();
			if (predAtom.getValue() == 0.0)
				throw new RuntimeException("Max value does not exist.");
			Constant[] terms = predAtom.getArguments();
			Constant[] exTerms = new Constant[terms.length-1];
			int i = 0;
			for (int j = 0; j < terms.length; j++) {
				if (j != labelIndex)
					exTerms[i++] = terms[j];
			}
			Constant label = terms[labelIndex];
			atoms.put(new Example(exTerms), labelMap.get(label));
		}
		return atoms;
	}
	
	/**
	 * Wrapper for groupings of ground terms dubbed "examples".
	 * 
	 * TODO: Current version assumes that the example is composed of a single term.
	 * Needs to be changed to generalize this code.
	 * 
	 * @author Ben
	 */
	class Example {
		
		private final Constant[] terms;
		
		public Example(Constant[] terms) {
			this.terms = terms;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Example))
				return false;
			Example other = (Example) obj;
			if (this.terms.length != other.terms.length)
				return false;
			for (int i = 0; i < terms.length; i++) {
				if (!this.terms[i].equals(other.terms[i]))
					return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return terms[0].hashCode();
		}
		
		@Override
		public String toString() {
			return terms[0].toString();
		}
	}
}
