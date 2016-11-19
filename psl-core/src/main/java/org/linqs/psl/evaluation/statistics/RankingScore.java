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

import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.set.ListOrderedSet;
import org.linqs.psl.model.atom.GroundAtom;

/**
 * Methods for scoring a ranking of Atoms against a given ranking.
 */
public enum RankingScore {

	/**
	 * Returns the fraction of pairs in the result that are ordered differently
	 * in the baseline.
	 */
	Kendall {
		@Override
		public double getScore(List<GroundAtom> expected, List<GroundAtom> actual) {
			double score = 0.0;
			int i, j;
			Iterator<GroundAtom> baseItrI, baseItrJ;
			GroundAtom baseAtomI, baseAtomJ;

			baseItrI = expected.iterator();
			baseItrI.next();
			i = 1;
			while (baseItrI.hasNext()) {
				baseAtomI = baseItrI.next();
				i++;
				baseItrJ = expected.iterator();
				j = 0;
				while (j < i) {
					baseAtomJ = baseItrJ.next();
					j++;
					if (actual.indexOf(baseAtomJ) > actual.indexOf(baseAtomI))
						score++;
				}
			}
			return 0.5 + (1 - 4 * score / (expected.size() * (expected.size() - 1))) / 2;
		}
	},

	/**
	 * Assumes actual GroundAtoms are hard truth values
	 * Returns area under the precision recall curve
	 */
	AUPRC {
		private final double threshold = 0.5;

		@Override
		public double getScore(List<GroundAtom> expected, List<GroundAtom> actual) {
			// both lists are sorted
			int totalPositives = 0;
			for (GroundAtom atom : expected)
				if (atom.getValue() > threshold)
					totalPositives++;
			
			double area = 0.0;
			int tp = 0;
			int fp = 0;
			int fn = totalPositives;
			int tn = expected.size() - totalPositives;
			GroundAtom last = actual.get(actual.size() - 1);
			// boolean label = actual.get(actual.indexOf(last)).getValue() > threshold;
			boolean label = getLabel(actual, last, threshold);
			if (label) {
				tp++;
				fn--;
			} else {
				fp++;
				tn--;
			}
			
			double prevPrecision = (double) tp / (double) (tp + fp);
			double prevRecall = (double) tp / (double) totalPositives;
 			double newPrecision, newRecall;	
			for (int i = actual.size() - 2; i >= 0; i--) {
				GroundAtom next = actual.get(i);
				// label = expected.get(expected.indexOf(next)).getValue() > threshold;
				label = getLabel(expected, next, threshold);
				if (label) {
					tp++;
					fn--;
				} else {
					fp++;
					tn--;
				}
				newPrecision = (double) tp / (double) (tp + fp);
				newRecall = (double) tp / (double) totalPositives;
				
				if (tp == 0)
					newPrecision = 0.0;
	 			
				area += 0.5 * (newRecall - prevRecall) * (prevPrecision + newPrecision);
				prevPrecision = newPrecision;
				prevRecall = newRecall;
			}

			// add final trapezoid
			newPrecision = (double) totalPositives / (double) actual.size();
			newRecall = 1.0;
			area += 0.5 * (newRecall - prevRecall) * (prevPrecision + newPrecision);
			return area;
		}
	},
	
	/**
	 * Assumes actual GroundAtoms are hard truth values
	 * Returns area under the precision recall curve for the negative class
	 */
	NegAUPRC {
		private final double threshold = 0.5;

		@Override
		public double getScore(List<GroundAtom> expected, List<GroundAtom> actual) {
			// both lists are sorted
			int totalPositives = 0;
			for (GroundAtom atom : expected)
				if (atom.getValue() > threshold)
					totalPositives++;
			
			int totalNegatives = actual.size() - totalPositives;
			
			double area = 0.0;
			int tp = 0;
			int fp = 0;
			int fn = totalPositives;
			int tn = totalNegatives;
			
			double prevPrecision = (double) tn / (double) (tn + fn);
			double prevRecall = (double) tn / (double) totalNegatives;
 			double newPrecision, newRecall;	
			for (int i = actual.size() - 1; i >= 0; i--) {
				GroundAtom next = actual.get(i);
				// boolean label = expected.get(expected.indexOf(next)).getValue() > threshold;
				boolean label = getLabel(expected, next, threshold);
				if (label) { // we predict positive, true label is positive
					tp++;
					fn--;
				} else { // we predict positive, true label is negative
					fp++;
					tn--;
				}
				newPrecision = (double) tn / (double) (tn + fn);
				newRecall = (double) tn / (double) totalNegatives;

				//System.out.println(next.getValue() + "\t" + expected.get(expected.indexOf(next)).getValue() + " prec " + newPrecision + " rec " + newRecall);
				
				if (tn == 0)
					newPrecision = 0.0;
	 			
				area += 0.5 * (prevRecall - newRecall) * (prevPrecision + newPrecision);
				prevPrecision = newPrecision;
				prevRecall = newRecall;
			}
			return area;
		}
	}, 
	
	/**
	 * Assumes actual GroundAtoms are hard truth values
	 * Returns area under ROC curve
	 */
	AreaROC {
		private final double threshold = 0.5;

		@Override
		public double getScore(List<GroundAtom> expected, List<GroundAtom> actual) {
			// both lists are sorted
			int totalPositives = 0;
			for (GroundAtom atom : expected)
				if (atom.getValue() > threshold)
					totalPositives++;
			
			int totalNegatives = actual.size() - totalPositives;
			
			double area = 0.0;
			int tp = 0;
			int fp = 0;
			int fn = totalPositives;
			int tn = totalNegatives;
			
			double prevY = (double) tp / (double) totalPositives;
			double prevX = (double) fp / (double) totalNegatives;
 			double newX, newY;	
			for (int i = actual.size() - 1; i >= 0; i--) {
				GroundAtom next = actual.get(i);
				// boolean label = expected.get(expected.indexOf(next)).getValue() > threshold;
				boolean label = getLabel(expected, next, threshold);
				if (label) { // we predict positive, true label is positive
					tp++;
					fn--;
				} else { // we predict positive, true label is negative
					fp++;
					tn--;
				}
				newY = (double) tp / (double) totalPositives;
				newX = (double) fp / (double) totalNegatives;
						
				area += 0.5 * (newX - prevX) * (newY + prevY);
				prevY = newY;
				prevX = newX;
			}
			return area;
		}
	};
	
	/**
	 * Just looks up and rounds the ground truth to true or false, and returns
	 * the default false if the atom is not in the list. 
	 */
	private static boolean getLabel(List<GroundAtom> expected, GroundAtom next, double threshold) {
		boolean label;
		int index = expected.indexOf(next);
		if (index == -1)
			label = false;
		else
			label = expected.get(index).getValue() > threshold;
		
		return label;
	}

	/**
	 * Scores a ranking of Atoms given an expected ranking
	 * 
	 * @param baselineList  the expected ranking
	 * @param resultList  the actual ranking
	 * @return the actual ranking's score relative to the expected ranking
	 */
	public abstract double getScore(List<GroundAtom> baselineList, List<GroundAtom> resultList);

}
