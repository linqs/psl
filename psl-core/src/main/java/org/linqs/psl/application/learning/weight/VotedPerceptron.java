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
 * Reasonable initial implementations are provided for all methods.
 * Child classes should be able to pick and chose which to override.
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

	protected final double baseStepSize;
	protected final int numSteps;
	protected final double l2Regularization;
	protected final double l1Regularization;
	protected final boolean scheduleStepSize;
	protected final boolean scaleGradient;
	protected final boolean averageSteps;

	/**
	 * Corresponds 1-1 with mutableRules.
	 */
	protected double[] observedIncompatibility;
	protected double[] expectedIncompatibility;

	/**
	 * Learning loss at the current point
	 */
	private double currentLoss = Double.POSITIVE_INFINITY;

	public VotedPerceptron(List<Rule> rules, Database rvDB, Database observedDB,
			boolean supportsLatentVariables, ConfigBundle config) {
		super(rules, rvDB, observedDB, supportsLatentVariables, config);

		baseStepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		if (baseStepSize <= 0) {
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

		observedIncompatibility = new double[mutableRules.size()];
		expectedIncompatibility = new double[mutableRules.size()];
	}

	@Override
	protected void doLearn() {
		double[] avgWeights = new double[mutableRules.size()];

		// Computes the observed incompatibilities.
		computeObservedIncomp();

		// Reset the RVAs to default values.
		setDefaultRandomVariables();

		double[] scalingFactor  = computeScalingFactor();

		// Computes the gradient steps.
		for (int step = 0; step < numSteps; step++) {
			log.debug("Starting iteration {}", step);

			// Computes the expected incompatibility.
			computeExpectedIncomp();

			currentLoss = computeLoss();

			// Updates weights.
			double stepSize = getStepSize(step);
			for (int i = 0; i < mutableRules.size(); i++) {
				double weight = mutableRules.get(i).getWeight();
				double currentStep = (expectedIncompatibility[i] - observedIncompatibility[i]
						- l2Regularization * weight
						- l1Regularization) / scalingFactor[i];

				currentStep *= stepSize;

				log.debug("Step of {} for rule {}", currentStep, mutableRules.get(i));
				log.debug(" --- Expected incomp.: {}, Truth incomp.: {}", expectedIncompatibility[i], observedIncompatibility[i]);

				weight = Math.max(weight + currentStep, 0.0);
				avgWeights[i] += weight;
				mutableRules.get(i).setWeight(weight);
			}
		}

		// Sets the weights to their averages.
		if (averageSteps) {
			for (int i = 0; i < mutableRules.size(); i++) {
				mutableRules.get(i).setWeight(avgWeights[i] / numSteps);
			}
		}
	}

	/**
	 * Compute the incompatibility in the model using the labels (truth values) from the observed (truth) database.
	 * This method is responsible for filling the observedIncompatibility member variable.
	 * This may call setLabeledRandomVariables() and not reset any ground atoms to their original value.
	 *
	 * The default implementation just calls setLabeledRandomVariables() and sums the incompatibility for each rule.
	 */
	protected void computeObservedIncomp() {
		setLabeledRandomVariables();

		// Zero out the observed incompatibility first.
		for (int i = 0; i < observedIncompatibility.length; i++) {
			observedIncompatibility[i] = 0.0;
		}

		// Sums up the incompatibilities.
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				observedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
			}
		}
	}

	/**
	 * Compute the incompatibility in the model.
	 * This method is responsible for filling the expectedIncompatibility member variable.
	 *
	 * The default implementation is the total incompatibility in the MPE state.
	 * IE, just calls computeMPEState() and then sums the incompatibility for each rule.
	 */
	protected void computeExpectedIncomp() {
		computeMPEState();

		// Zero out the expected incompatibility first.
		for (int i = 0; i < expectedIncompatibility.length; i++) {
			expectedIncompatibility[i] = 0.0;
		}

		// Sums up the incompatibilities.
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				expectedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected void computeMPEState() {
		termGenerator.updateWeights(groundRuleStore, termStore);
		reasoner.optimize(termStore);
	}

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
	 * Internal method for computing the loss at the current point before taking a step.
	 * Child methods may override.
	 *
	 * The default implementation just sums the product of the difference between the expected and observed incompatibility.
	 *
	 * @return current learning loss
	 */
	protected double computeLoss() {
		if (currentLoss == Double.POSITIVE_INFINITY) {
			return currentLoss;
		}

		double loss = 0.0;
		for (int i = 0; i < mutableRules.size(); i++) {
			loss += mutableRules.get(i).getWeight() * (observedIncompatibility[i] - expectedIncompatibility[i]);
		}

		return loss;
	}

	public double getLoss() {
		return currentLoss;
	}

	protected double getStepSize(int iteration) {
		if (scheduleStepSize) {
			return baseStepSize / (double)(iteration + 1);
		}

		return baseStepSize;
	}

	/**
	 * Computes the amount to scale gradient for each rule.
	 * Scales by the number of groundings of each rule
	 * unless the rule is not grounded in the training set, in which case
	 * scales by 1.0.
	 */
	protected double[] computeScalingFactor() {
		double [] factor = new double[mutableRules.size()];
		for (int i = 0; i < factor.length; i++) {
			factor[i] = Math.max(1.0, groundRuleStore.count(mutableRules.get(i)));
		}

		return factor;
	}
}
