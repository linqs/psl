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
package org.linqs.psl.application.learning.weight.em;

import java.util.Arrays;

import org.linqs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EM algorithm which fits a point distribution to the single most probable
 * assignment of truth values to the latent variables during the E-step.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class HardEM extends ExpectationMaximization  {

	private static final Logger log = LoggerFactory.getLogger(HardEM.class);

	/**
	 * Prefix of property keys used by this class.
	 *
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "hardem";


	/**
	 * Key for Boolean property that indicates whether to use AdaGrad subgradient
	 * scaling, the adaptive subgradient algorithm of
	 * John Duchi, Elad Hazan, Yoram Singer (JMLR 2010).
	 *
	 * If TRUE, will override other step scheduling options (but not scaling).
	 */
	public static final String ADAGRAD_KEY = CONFIG_PREFIX + ".adagrad";
	/** Default value for ADAGRAD_KEY */
	public static final boolean ADAGRAD_DEFAULT = false;

	private final boolean useAdaGrad;

	double[] gradientSum;
	double[] fullObservedIncompatibility, fullExpectedIncompatibility;

	public HardEM(Model model, Database rvDB, Database observedDB,
			ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		useAdaGrad = config.getBoolean(ADAGRAD_KEY, ADAGRAD_DEFAULT);
	}

	/**
	 * Minimizes the KL divergence by setting the latent variables to their
	 * most probable state conditioned on the evidence and the labeled
	 * random variables.
	 * <p>
	 * This method assumes that the inferred truth values will be used
	 * immediately by {@link VotedPerceptron#computeObservedIncomp()}.
	 */
	@Override
	protected void minimizeKLDivergence() {
		inferLatentVariables();
	}

	@Override
	protected double[] computeExpectedIncomp() {
		fullExpectedIncompatibility = new double[mutableRules.size() + immutableRules.size()];

		if (changedRuleWeights) {
			termGenerator.updateWeights(groundRuleStore, termStore);
			changedRuleWeights = false;
		}

		// Computes the MPE state.
		reasoner.optimize(termStore);

		/* Computes incompatibility */
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				fullExpectedIncompatibility[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
			}
		}
		for (int i = 0; i < immutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(immutableRules.get(i))) {
				fullExpectedIncompatibility[mutableRules.size() + i] += ((WeightedGroundRule) groundRule).getIncompatibility();
			}
		}

		return Arrays.copyOf(fullExpectedIncompatibility, mutableRules.size());
	}

	@Override
	protected double[] computeObservedIncomp() {
		numGroundings = new double[mutableRules.size()];
		fullObservedIncompatibility = new double[mutableRules.size() + immutableRules.size()];
		setLabeledRandomVariables();

		/* Computes the observed incompatibilities and numbers of groundings */
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				fullObservedIncompatibility[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
				numGroundings[i]++;
			}
		}
		for (int i = 0; i < immutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(immutableRules.get(i))) {
				fullObservedIncompatibility[mutableRules.size() + i] += ((WeightedGroundRule) groundRule).getIncompatibility();
			}
		}

		return Arrays.copyOf(fullObservedIncompatibility, mutableRules.size());
	}

	@Override
	protected double computeLoss() {
		double loss = 0.0;
		for (int i = 0; i < mutableRules.size(); i++)
			loss += mutableRules.get(i).getWeight() * (fullObservedIncompatibility[i] - fullExpectedIncompatibility[i]);
		for (int i = 0; i < immutableRules.size(); i++)
			loss += immutableRules.get(i).getWeight() * (fullObservedIncompatibility[mutableRules.size() + i] - fullExpectedIncompatibility[mutableRules.size() + i]);
		return loss;
	}

	@Override
	protected void doLearn() {
		gradientSum = new double[mutableRules.size()];

		if (augmentLoss)
			addLossAugmentedRules();
		super.doLearn();
		if (augmentLoss)
			removeLossAugmentedRules();
	}

	@Override
	protected double[] computeScalingFactor() {
		if (!useAdaGrad)
			return super.computeScalingFactor();

		double [] scalingFactor = new double[mutableRules.size()];

		// otherwise accumulate gradient
		for (int i = 0; i < numGroundings.length; i++) {
			double weight = mutableRules.get(i).getWeight();
			double gradient =  (expectedIncompatibility[i] - truthIncompatibility[i]
					- l2Regularization * weight
					- l1Regularization);
			gradientSum[i] += gradient * gradient;
			scalingFactor[i] =  Math.sqrt(gradientSum[i]);

			// don't allow scaling factor to be too small
			if (scalingFactor[i] < 1e-8)
				scalingFactor[i] = 1e-8;
		}
		return scalingFactor;
	}
}
