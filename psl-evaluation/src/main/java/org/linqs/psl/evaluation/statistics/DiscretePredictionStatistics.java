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

public class DiscretePredictionStatistics implements PredictionStatistics {
	public enum BinaryClass {
		NEGATIVE,
		POSITIVE
	}

	private final int tp;
	private final int fp;
	private final int fn;
	private final int tn;
	private final double threshold;

	public DiscretePredictionStatistics(int tp, int fp, int tn, int fn, double threshold) {
		this.tp = tp;
		this.fp = fp;
		this.tn = tn;
		this.fn = fn;
		this.threshold = threshold;
	}

	public double getThreshold() {
		return threshold;
	}

	public double getPrecision(BinaryClass binaryClass) {
		int hits;
		int misses;

		if (binaryClass == BinaryClass.POSITIVE) {
			hits = tp;
			misses = fp;
		} else {
			hits = tn;
			misses = fn;
		}

		if (hits + misses == 0) {
			return 0.0;
		}

		return hits / (double)(hits + misses);
	}

	public double getRecall(BinaryClass binaryClass) {
		int hits;
		int misses;

		if (binaryClass == BinaryClass.POSITIVE) {
			hits = tp;
			misses = fn;
		} else {
			hits = tn;
			misses = fp;
		}

		if (hits + misses == 0) {
			return 0.0;
		}

		return hits / (double)(hits + misses);
	}

	public double getF1(BinaryClass binaryClass) {
		double prec = getPrecision(binaryClass);
		double rec = getRecall(binaryClass);

		double sum = prec + rec;
		if (sum == 0.0) {
			return 0.0;
		}

		return 2 * (prec * rec) / sum;
	}

	public double getAccuracy() {
		int numAtoms = getNumAtoms();
		if (numAtoms == 0) {
			return 0.0;
		}

		return (tp + tn) / (double)numAtoms;
	}

	@Override
	public double getError() {
		return fp + fn;
	}

	@Override
	public int getNumAtoms() {
		return tp + fp + tn + fn;
	}
}
