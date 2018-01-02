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
package org.linqs.psl.application.learning.weight.maxmargin;

import org.linqs.psl.application.learning.weight.LossAugmentingGroundRule;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements the batch Frank-Wolfe algorithm for StructSVM
 * (Lacoste-Julian et al., 2013).
 *
 * This application of the algorithm diverges from the original
 * in that loss-augmented inference returns a real-valued solution,
 * rather than integral. This *should* be OK, since we are solving
 * the primal QP, and therefore don't need to account for the
 * infinite number of dual variables. However, a formal analysis
 * to support this claim is still pending.
 *
 * @author blondon
 */
public class FrankWolfe extends WeightLearningApplication {
	private static final Logger log = LoggerFactory.getLogger(FrankWolfe.class);

	/**
	 * Prefix of property keys used by this class.
	 *
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "frankwolfe";

	/**
	 * Key for double property, cutting plane tolerance
	 */
	public static final String CONVERGENCE_TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
	/** Default value for CONVERGENCE_TOLERANCE_KEY */
	public static final double CONVERGENCE_TOLERANCE_DEFAULT = 1e-5;

	/**
	 * Key for positive integer, maximum iterations
	 */
	public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiter";
	/** Default value for MAX_ITER_KEY */
	public static final int MAX_ITER_DEFAULT = 500;

	/**
	 * Key for boolean property. If true, algorithm will output average weights when
	 * learning exceeds maximum number of iterations.
	 */
	public static final String AVERAGE_WEIGHTS_KEY = CONFIG_PREFIX + ".averageweights";
	/** Default value for AVERAGE_WEIGHTS_KEY */
	public static final boolean AVERAGE_WEIGHTS_DEFAULT = false;

	/**
	 * Key for boolean property. If true, only non-negative weights will be learned.
	 */
	public static final String NONNEGATIVE_WEIGHTS_KEY = CONFIG_PREFIX + ".nonnegativeweights";
	/** Default value for NONNEGATIVE_WEIGHTS_KEY */
	public static final boolean NONNEGATIVE_WEIGHTS_DEFAULT = true;

	/**
	 * Key for boolean property. If true, loss and gradient will be normalized by number of labels.
	 */
	public static final String NORMALIZE_KEY = CONFIG_PREFIX + ".normalize";
	/** Default value for NORMALIZE_KEY */
	public static final boolean NORMALIZE_DEFAULT = true;

	/**
	 * Key for double property, regularization parameter \lambda, where objective is \lambda*||w|| + (slack)
	 */
	public static final String REG_PARAM_KEY = CONFIG_PREFIX + ".regparam";
	/** Default value for REG_PARAM_KEY */
	public static final double REG_PARAM_DEFAULT = 1;

	protected final double tolerance;
	protected final int maxIter;
	protected final boolean averageWeights;
	protected final boolean nonnegativeWeights;
	protected final boolean normalize;
	protected double regParam;

	public FrankWolfe(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		tolerance = config.getDouble(CONVERGENCE_TOLERANCE_KEY, CONVERGENCE_TOLERANCE_DEFAULT);
		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		averageWeights = config.getBoolean(AVERAGE_WEIGHTS_KEY, AVERAGE_WEIGHTS_DEFAULT);
		nonnegativeWeights = config.getBoolean(NONNEGATIVE_WEIGHTS_KEY, NONNEGATIVE_WEIGHTS_DEFAULT);
		normalize = config.getBoolean(NORMALIZE_KEY, NORMALIZE_DEFAULT);
		regParam = config.getDouble(REG_PARAM_KEY, REG_PARAM_DEFAULT);
	}

	@Override
	protected void doLearn() {
		// Inits local copy of weights and avgWeights to user-specified values.
		double[] weights = new double[mutableRules.size()];
		double[] avgWeights = new double[mutableRules.size()];

		for (int i = 0; i < weights.length; i++) {
			weights[i] = mutableRules.get(i).getWeight();
			avgWeights[i] = weights[i];
		}

		// Inits loss to zero.
		double loss = 0;

		// Computes the observed incompatibilities and number of groundings.
		double[] truthIncompatibility = new double[mutableRules.size()];
		int[] numGroundings = new int[mutableRules.size()];
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}

		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				truthIncompatibility[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
				++numGroundings[i];
			}
		}

		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(0.0);
		}

		// Compute (approximate?) number of labels, for normalizing loss, gradient.
		int numLabels = trainingMap.getTrainingMap().entrySet().size();

		// Sets up loss augmenting ground rules.
		log.debug("Weighting loss of positive (value = 1.0) examples by {} and negative examples by {}", -1.0, -1.0);

		List<LossAugmentingGroundRule> lossRules = new ArrayList<LossAugmentingGroundRule>(trainingMap.getTrainingMap().size());
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			double truth = e.getValue().getValue();
			// If ground truth is not integral, this will throw exception.
			LossAugmentingGroundRule groundRule = new LossAugmentingGroundRule(e.getKey(), truth, -1.0);
			groundRuleStore.addGroundRule(groundRule);
			lossRules.add(groundRule);
		}

		// MAIN LOOP

		boolean converged = false;
		int iter = 0;
		while (!converged && iter++ < maxIter) {
			if (changedRuleWeights) {
				termGenerator.updateWeights(groundRuleStore, termStore);
				changedRuleWeights = false;
			}

			// Runs loss-augmented inference with current weights.
			reasoner.optimize(termStore);

			// Computes L1 distance to ground truth.
			double l1Distance = 0.0;
			for (LossAugmentingGroundRule groundRule : lossRules) {
				double truth = trainingMap.getTrainingMap().get(groundRule.getAtom()).getValue();
				double lossaugValue = groundRule.getAtom().getValue();
				l1Distance += Math.abs(truth - lossaugValue);
			}

			// Computes loss-augmented incompatibilities.
			double[] lossaugIncompatibility = new double[mutableRules.size()];
			for (int i = 0; i < mutableRules.size(); i++) {
				for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
					if (groundRule instanceof LossAugmentingGroundRule)
						continue;
					lossaugIncompatibility[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
				}
			}

			// Computes gradient of weights, where:
			//  gradient = (-1 / regParam) * (truthIncompatibilities - lossaugIncompatibilities)
			// Note: this is the negative of the formula in the paper,
			// because these are incompatibilities, not compatibilities.
			double[] gradient = new double[weights.length];
			for (int i = 0; i < weights.length; ++i) {
				gradient[i] = (-1.0 / regParam) * (truthIncompatibility[i] - lossaugIncompatibility[i]);
			}

			// Normalizes L1 distance and gradient by numLabels.
			if (normalize) {
				l1Distance /= (double)numLabels;
				for (int i = 0; i < weights.length; ++i) {
					gradient[i] /= (double)numLabels;
				}
			}

			// Computes step size.
			double numerator = 0.0;
			double denominator = 0.0;
			double stepSize = 0.0;
			for (int i = 0; i < weights.length; ++i) {
				double delta = weights[i] - gradient[i];
				numerator += weights[i] * delta;
				denominator += delta * delta;
			}

			numerator += (l1Distance - loss) / regParam;
			if (denominator != 0.0) {
				stepSize = numerator / denominator;
				if (stepSize > 1.0) {
					stepSize = 1.0;
				} else if (stepSize < 0.0) {
					stepSize = 0.0;
				}
			} else if (numerator > 0) {
				stepSize = 1.0;
			} else {
				stepSize = 0.0;
			}

			// Takes step.
			for (int i = 0; i < weights.length; ++i) {
				// Updates weights.
				weights[i] = (1.0 - stepSize) * weights[i] + stepSize * gradient[i];
				if (nonnegativeWeights && weights[i] < 0.0) {
					weights[i] = 0.0;
				}

				mutableRules.get(i).setWeight(weights[i]);

				// Updates average weights.
				avgWeights[i] = (double)iter / ((double)iter + 2.0) * avgWeights[i]
							  + 2.0 / ((double)iter + 2.0) * weights[i];
			}

			changedRuleWeights = true;

			loss = (1.0 - stepSize) * loss + stepSize * l1Distance;

			// Compute duality gap.
			double gap = regParam * numerator;
			if (gap < tolerance) {
				converged = true;
			}

			// Log
			log.debug("Iter {}: L1 distance of worst violator: {}", iter, l1Distance);
			log.debug("Iter {}: numerator: {}", iter, numerator);
			log.debug("Iter {}: denominator: {}", iter, denominator);
			log.debug("Iter {}: stepSize: {}", iter, stepSize);
			log.debug("Iter {}: duality gap: {}", iter, gap);

			for (int i = 0; i < weights.length; ++i) {
				log.debug(String.format("Iter %d: i=%d: w_i=%f, g_i=%f", iter, i, weights[i], gradient[i]));
			}
		}

		// POST-PROCESSING

		// If not converged, use average weights.
		if (!converged) {
			log.info("Learning did not converge after {} iterations", maxIter);

			if (averageWeights) {
				log.info("Using average weights");
				for (int i = 0; i < avgWeights.length; ++i) {
					mutableRules.get(i).setWeight(avgWeights[i]);
				}

				changedRuleWeights = true;
			}
		} else {
			log.info("Learning converged after {} iterations", iter);
		}
	}
}
