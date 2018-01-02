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

import org.linqs.psl.model.atom.GroundAtom;

import java.util.Iterator;
import java.util.List;

/**
 * Methods for scoring a ranking of Atoms against a given ranking.
 * The lists passed in are sorted in DESC order by truth value.
 */
public class RankingScore {
	private static final boolean DEFAULT_SKIP_MISSING_TRUTH = true;

	private List<GroundAtom> truth;
	private List<GroundAtom> predicted;
	private double threshold;

	/**
	 * If true, we will just skip over predictions that do not have an associated truth value.
	 */
	private boolean skipMissingTruth;

	public RankingScore(List<GroundAtom> truth, List<GroundAtom> predicted, double threshold) {
		this(truth, predicted, threshold, DEFAULT_SKIP_MISSING_TRUTH);
	}

	public RankingScore(List<GroundAtom> truth, List<GroundAtom> predicted,
			double threshold, boolean skipMissingTruth) {
		this.truth = truth;
		this.predicted = predicted;
		this.threshold = threshold;
		this.skipMissingTruth = skipMissingTruth;
	}

	/**
	 * Returns the fraction of pairs in the result that are ordered differently
	 * in the baseline.
	 *
	 * TODO(eriq): This is more opaque than it needs to be and should be cleaned up (without iterators).
	 * We also changed the order of the input lists, but that shouldn't matter.
	 */
	public double kendall() {
		double score = 0.0;
		int i, j;
		Iterator<GroundAtom> baseItrI, baseItrJ;
		GroundAtom baseAtomI, baseAtomJ;

		baseItrI = truth.iterator();
		baseItrI.next();
		i = 1;
		while (baseItrI.hasNext()) {
			baseAtomI = baseItrI.next();
			i++;

			baseItrJ = truth.iterator();
			j = 0;
			while (j < i) {
				baseAtomJ = baseItrJ.next();
				j++;

				if (predicted.indexOf(baseAtomJ) > predicted.indexOf(baseAtomI)) {
					score++;
				}
			}
		}

		return 0.5 + (1 - 4 * score / (truth.size() * (truth.size() - 1))) / 2;
	}

	/**
	 * Returns area under the precision recall curve.
	 * This is a simple implementation that assumes all the ground truth is 0/1
	 * and does not make any effort to approximate the first point.
	 */
	public double auprc() {
		// both lists are sorted
		int totalPositives = 0;
		for (GroundAtom atom : truth) {
			if (atom.getValue() > threshold) {
				totalPositives++;
			}
		}

		if (totalPositives == 0) {
			return 0.0;
		}

		double area = 0.0;
		int tp = 0;
		int fp = 0;

		// Precision is along the Y-axis.
		double prevY = 1.0;
		// Recall is along the X-axis.
		double prevX = 0.0;

		// Go through the atoms from highest truth value to lowest.
		for (GroundAtom atom : predicted) {
			Boolean label = getLabel(atom);
			if (skipMissingTruth && label == null) {
				continue;
			}

			// Assume we predicted everything positive.
			if (label != null && label) {
				tp++;
			} else {
				fp++;
			}

			double newY = tp / (double)(tp + fp);
			double newX = tp / (double)totalPositives;

			area += 0.5 * (newX - prevX) * (newY + prevY);
			prevY = newY;
			prevX = newX;
		}

		// Add the final piece.
		area += 0.5 * (1.0 - prevX) * (0.0 + prevY);

		return area;
	}

	/**
	 * Returns area under the precision recall curve for the negative class.
	 * The same stipulations for AUPRC hold here.
	 */
	public double negAUPRC() {
		// both lists are sorted
		int totalPositives = 0;
		for (GroundAtom atom : truth) {
			if (atom.getValue() > threshold) {
				totalPositives++;
			}
		}

		int totalNegatives;
		if (skipMissingTruth) {
			totalNegatives = truth.size() - totalPositives;
		} else {
			totalNegatives = predicted.size() - totalPositives;
		}

		if (totalNegatives == 0) {
			return 0.0;
		}

		double area = 0.0;
		// Assume we have already predicted everything false, and correct as we go.
		int fn = totalPositives;
		int tn = totalNegatives;

		// Precision is along the Y-axis.
		double prevY = tn / (double)(tn + fn);
		// Recall is along the X-axis.
		double prevX = 1.0;

		// Go through the atoms from highest truth value to lowest.
		for (GroundAtom atom : predicted) {
			Boolean label = getLabel(atom);
			if (skipMissingTruth && label == null) {
				continue;
			}

			if (label != null && label) {
				fn--;
			} else {
				tn--;
			}

			double newY = 0.0;
			if (tn + fn > 0) {
				newY = tn / (double)(tn + fn);
			}

			double newX = tn / (double)totalNegatives;

			area += 0.5 * (prevX - newX) * (prevY + newY);
			prevY = newY;
			prevX = newX;
		}

		return area;
	}

	/**
	 * Returns area under ROC curve.
	 * Assumes predicted GroundAtoms are hard truth values.
	 */
	public double auroc() {
		int totalPositives = 0;
		for (GroundAtom atom : truth) {
			if (atom.getValue() > threshold) {
				totalPositives++;
			}
		}

		int totalNegatives;
		if (skipMissingTruth) {
			totalNegatives = truth.size() - totalPositives;
		} else {
			totalNegatives = predicted.size() - totalPositives;
		}

		double area = 0.0;
		int tp = 0;
		int fp = 0;

		// True positrive rate (TPR) is along the Y-axis.
		double prevY = 0.0;
		// False positive rate (FPR) is along the X-axis.
		double prevX = 0.0;

		// Go through the atoms from highest truth value to lowest.
		for (GroundAtom atom : predicted) {
			Boolean label = getLabel(atom);
			if (skipMissingTruth && label == null) {
				continue;
			}

			// Assume we predicted everything positive.
			if (label != null && label) {
				tp++;
			} else {
				fp++;
			}

			double newY = (double)tp / (double)totalPositives;
			double newX = (double)fp / (double)totalNegatives;

			area += 0.5 * (newX - prevX) * (newY + prevY);
			prevY = newY;
			prevX = newX;
		}

		// Add the final piece.
		area += 0.5 * (1.0 - prevX) * (1.0 + prevY);

		return area;
	}

	/**
	 * If the atom exists in the truth, return it's boolean value.
	 * Otherwise return null.
	 */
	private Boolean getLabel(GroundAtom atom) {
		int index = truth.indexOf(atom);
		if (index == -1) {
			return null;
		}

		return truth.get(index).getValue() > threshold;
	}
}
