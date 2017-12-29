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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TODO(steve): rewrite class documentation to describe general gradient-based learning algorithms
 *
 * Learns new weights for the weighted rules in a model using the voted perceptron algorithm.
 *
 * The weight-learning objective is to maximize the likelihood according to the distribution:
 *
 * p(X) = 1 / Z(w) * exp{-sum[w * f(X)]}
 *
 * Where:
 *  - X is the set of RandomVariableAtoms,
 *  - f(X) the incompatibility of each GroundRule,
 *  - w is the weight of that GroundRule,
 *  - and Z(w) is a normalization factor.
 *
 * The voted perceptron algorithm starts at the current weights and at each step
 * computes the gradient of the objective, takes that step multiplied by a step size
 * (possibly truncated to stay in the region of feasible weights), and
 * saves the new weights. The components of the gradient are each divided by the
 * number of weighted ground rules from that Rule. The learned weights
 * are the averages of the saved weights.
 *
 * For the gradient of the objective, the expected total incompatibility is
 * computed by subclasses in {@link #computeExpectedIncomp()}.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public abstract class VotedPerceptron extends WeightLearningApplication {
	private static final Logger log = LoggerFactory.getLogger(VotedPerceptron.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "votedperceptron";

	/**
	 * Key for positive double property scaling the L2 regularization
	 * (\lambda / 2) * ||w||^2
	 */
	public static final String L2_REGULARIZATION_KEY = CONFIG_PREFIX + ".l2regularization";
	public static final double L2_REGULARIZATION_DEFAULT = 0.0;

	/**
	 * Key for positive double property scaling the L1 regularization
	 * \gamma * |w|
	 */
	public static final String L1_REGULARIZATION_KEY = CONFIG_PREFIX + ".l1regularization";
	public static final double L1_REGULARIZATION_DEFAULT = 0.0;

	/**
	 * Key for positive double property which will be multiplied with the
	 * objective gradient to compute a step.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
	public static final double STEP_SIZE_DEFAULT = 1.0;

	/**
	 * Key for Boolean property that indicates whether to shrink the stepsize by
	 * a 1/t schedule.
	 */
	public static final String STEP_SCHEDULE_KEY = CONFIG_PREFIX + ".schedule";
	public static final boolean STEP_SCHEDULE_DEFAULT = true;

	/**
	 * Key for Boolean property that indicates whether to scale gradient by
	 * number of groundings
	 */
	public static final String SCALE_GRADIENT_KEY = CONFIG_PREFIX + ".scalegradient";
	public static final boolean SCALE_GRADIENT_DEFAULT = true;

	/**
	 * Key for Boolean property that indicates whether to average all visited
	 * weights together for final output.
	 */
	public static final String AVERAGE_STEPS_KEY = CONFIG_PREFIX + ".averagesteps";
	public static final boolean AVERAGE_STEPS_DEFAULT = true;

	/**
	 * Key for positive integer property. VotedPerceptron will take this many
	 * steps to learn weights.
	 */
	public static final String NUM_STEPS_KEY = CONFIG_PREFIX + ".numsteps";
	/** Default value for NUM_STEPS_KEY */
	public static final int NUM_STEPS_DEFAULT = 25;

	protected double[] numGroundings;

	protected final double stepSize;
	protected final int numSteps;
	protected final double l2Regularization;
	protected final double l1Regularization;
	protected final boolean scheduleStepSize;
	protected final boolean scaleGradient;
	protected final boolean averageSteps;
	protected double[] truthIncompatibility;
	protected double[] expectedIncompatibility;

	/** Learning loss at current point */
	private double loss = Double.POSITIVE_INFINITY;

	public VotedPerceptron(List<Rule> rules, Database rvDB, Database observedDB,
			boolean supportsLatentVariables, ConfigBundle config) {
		super(rules, rvDB, observedDB, supportsLatentVariables, config);

		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		if (stepSize <= 0) {
			throw new IllegalArgumentException("Step size must be positive.");
		}

		numSteps = config.getInt(NUM_STEPS_KEY, NUM_STEPS_DEFAULT);
		if (numSteps <= 0) {
			throw new IllegalArgumentException("Number of steps must be positive.");
		}

		l2Regularization = config.getDouble(L2_REGULARIZATION_KEY, L2_REGULARIZATION_DEFAULT);
		if (l2Regularization < 0) {
			throw new IllegalArgumentException("L2 regularization parameter must be non-negative.");
		}

		l1Regularization = config.getDouble(L1_REGULARIZATION_KEY, L1_REGULARIZATION_DEFAULT);
		if (l1Regularization < 0) {
			throw new IllegalArgumentException("L1 regularization parameter must be non-negative.");
		}

		scheduleStepSize = config.getBoolean(STEP_SCHEDULE_KEY, STEP_SCHEDULE_DEFAULT);
		scaleGradient = config.getBoolean(SCALE_GRADIENT_KEY, SCALE_GRADIENT_DEFAULT);
		averageSteps = config.getBoolean(AVERAGE_STEPS_KEY, AVERAGE_STEPS_DEFAULT);
	}

	protected double getStepSize(int iteration) {
		if (scheduleStepSize) {
			return stepSize / (double)(iteration + 1);
		} else {
			return stepSize;
		}
	}

	@Override
	protected void doLearn() {
		double[] avgWeights = new double[mutableRules.size()];

		// Computes the observed incompatibilities.
		truthIncompatibility = computeObservedIncomp();

		// TODO(eriq): If not overwritten, computeObservedIncomp() calls setLabeledRandomVariables(),
		// which uses trainingMap without checking for null.
		if (trainingMap != null) {
			// Resets random variables to default values for computing expected incompatibility.
			for (RandomVariableAtom atom : trainingMap.getTrainingMap().keySet()) {
				atom.setValue(0.0);
			}

			for (RandomVariableAtom atom : trainingMap.getLatentVariables()) {
				atom.setValue(0.0);
			}
		}

		// Computes the gradient steps.
		for (int step = 0; step < numSteps; step++) {
			log.debug("Starting iteration {}", step + 1);

			// Computes the expected total incompatibility for each CompatibilityRule.
			expectedIncompatibility = computeExpectedIncomp();
			double[] scalingFactor  = computeScalingFactor();
			loss = computeLoss();

			// Updates weights.
			for (int i = 0; i < mutableRules.size(); i++) {
				double weight = mutableRules.get(i).getWeight();
				double currentStep = (expectedIncompatibility[i] - truthIncompatibility[i]
						- l2Regularization * weight
						- l1Regularization) / scalingFactor[i];
				currentStep *= getStepSize(step);

				log.debug("Step of {} for rule {}", currentStep, mutableRules.get(i));
				log.debug(" --- Expected incomp.: {}, Truth incomp.: {}", expectedIncompatibility[i], truthIncompatibility[i]);

				weight += currentStep;
				weight = Math.max(weight, 0.0);

				avgWeights[i] += weight;
				mutableRules.get(i).setWeight(weight);
			}

			// Update the terms with the new weights.
			termGenerator.updateWeights(groundRuleStore, termStore);
		}

		// Sets the weights to their averages.
		if (averageSteps) {
			for (int i = 0; i < mutableRules.size(); i++) {
				mutableRules.get(i).setWeight(avgWeights[i] / numSteps);
			}

			termGenerator.updateWeights(groundRuleStore, termStore);
		}
	}

	protected double[] computeObservedIncomp() {
		numGroundings = new double[mutableRules.size()];
		double[] truthIncompatibility = new double[mutableRules.size()];
		setLabeledRandomVariables();

		// Computes the observed incompatibilities and numbers of groundings.
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				truthIncompatibility[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
				numGroundings[i]++;
			}
		}

		return truthIncompatibility;
	}

	/**
	 * Computes the expected (unweighted) total incompatibility of the
	 * {@link WeightedGroundRule GroundCompatibilityRules} in groundRuleStore
	 * for each {@link WeightedRule}.
	 *
	 * @return expected incompatibilities, ordered according to rules
	 */
	protected abstract double[] computeExpectedIncomp();

	protected double computeRegularizer() {
		if (l1Regularization == 0.0 && l2Regularization == 0.0) {
			return 0.0;
		}

		double l2 = 0.0;
		double l1 = 0.0;

		for (WeightedRule rule : mutableRules) {
			l2 += Math.pow(rule.getWeight(), 2);
			l1 += Math.abs(rule.getWeight());
		}

		return 0.5 * l2Regularization * l2 + l1Regularization * l1;
	}

	/**
	 * Internal method for computing the loss at the current point
	 * before taking a step.
	 *
	 * Returns 0.0 if not overridden by a subclass
	 *
	 * @return current learning loss
	 */
	protected double computeLoss() {
		return Double.POSITIVE_INFINITY;
	}

	public double getLoss() {
		return loss;
	}

	/**
	 * Computes the amount to scale gradient for each rule.
	 * Scales by the number of groundings of each rule
	 * unless the rule is not grounded in the training set, in which case
	 * scales by 1.0.
	 */
	protected double[] computeScalingFactor() {
		double [] factor = new double[numGroundings.length];
		for (int i = 0; i < numGroundings.length; i++) {
			factor[i] = (scaleGradient && numGroundings[i] > 0) ? numGroundings[i] : 1.0;
		}
		return factor;
	}
}
