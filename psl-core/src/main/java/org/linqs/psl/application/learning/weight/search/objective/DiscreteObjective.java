/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight.search.objective;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.MathUtils;

import java.util.List;
import java.util.Map;

/**
 * An objective based on the discrete error between the targets and truth.
 * The type of error can be changed through the config.
 * Note that the accuracy metrics improve as they grow larger, so the scores recurned will be flipped.
 */
public class DiscreteObjective implements ObjectiveFunction {
	public static final String STAT_F1 = "F1";
	public static final String STAT_ACCURACY = "accuracy";
	public static final String STAT_PRECISION = "precision";
	public static final String STAT_RECALL = "recall";

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "discreteobjective";

	/**
	 * A comma-separated list of possible weights.
	 */
	public static final String STAT_KEY = CONFIG_PREFIX + ".statistic";
	public static final String STAT_DEFAULT = STAT_F1;

	/**
	 * The threshold determining truth.
	 */
	public static final String THRESHOLD_KEY = CONFIG_PREFIX + ".truththreshold";
	public static final double THRESHOLD_DEFAULT = 0.5;

	private String stat;
	private double threshold;

	public DiscreteObjective(ConfigBundle config) {
		this(config.getString(STAT_KEY, STAT_DEFAULT).toUpperCase(), config.getDouble(THRESHOLD_KEY, THRESHOLD_DEFAULT));
	}

	public DiscreteObjective(String stat, double threshold) {
		if (!(stat.equals(STAT_F1) || stat.equals(STAT_ACCURACY) || stat.equals(STAT_PRECISION) || stat.equals(STAT_RECALL))) {
			throw new IllegalArgumentException("Unknown statistic: " + stat);
		}

		if (threshold < 0 || threshold > 1.0) {
			throw new IllegalArgumentException("Threshold must be in [0, 1], found: " + threshold);
		}

		this.stat = stat;
		this.threshold = threshold;
	}

	public double compute(List<WeightedRule> mutableRules,
			double[] observedIncompatibility, double[] expectedIncompatibility,
			TrainingMap trainingMap) {
		int tp = 0;
		int fp = 0;
		int tn = 0;
		int fn = 0;

		for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getTrainingMap().entrySet()) {
			boolean expected = (entry.getValue().getValue() >= threshold);
			boolean predicated = (entry.getKey().getValue() >= threshold);

			if (predicated && expected) {
				tp++;
			} else if (!predicated && expected) {
				fn++;
			} else if (predicated && !expected) {
				fp++;
			} else {
				tn++;
			}
		}

		if (stat.equals(STAT_F1)) {
			return 1.0 - computeF1(tp, fp, tn, fn);
		} else if (stat.equals(STAT_ACCURACY)) {
			return 1.0 - computeAccuracy(tp, fp, tn, fn);
		} else if (stat.equals(STAT_PRECISION)) {
			return 1.0 - computePrecision(tp, fp, tn, fn);
		} else if (stat.equals(STAT_RECALL)) {
			return 1.0 - computeRecall(tp, fp, tn, fn);
		} else {
			throw new IllegalArgumentException("Unknown statistic: " + stat);
		}
	}

	public double computeF1(int tp, int fp, int tn, int fn) {
		double precision = computePrecision(tp, fp, tn, fn);
		double recall = computeRecall(tp, fp, tn, fn);

		if (MathUtils.isZero(precision + recall)) {
			return 0.0;
		}

		return 2 * (precision * recall) / (precision + recall);
	}

	public double computeAccuracy(int tp, int fp, int tn, int fn) {
		return (double)(tp + tn) / (tp + fp + tn + fn);
	}

	public double computePrecision(int tp, int fp, int tn, int fn) {
		if (tp + fp == 0) {
			return 0.0;
		}

		return tp / (double)(tp + fp);
	}

	public double computeRecall(int tp, int fp, int tn, int fn) {
		if (tp + fn == 0) {
			return 0.0;
		}

		return tp / (double)(tp + fn);
	}
}
