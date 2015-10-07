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
package edu.umd.cs.psl.application.learning.weight.em;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;

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
		fullExpectedIncompatibility = new double[kernels.size() + immutableKernels.size()];

		/* Computes the MPE state */
		reasoner.optimize();

		/* Computes incompatibility */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				fullExpectedIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}
		for (int i = 0; i < immutableKernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(immutableKernels.get(i))) {
				fullExpectedIncompatibility[kernels.size() + i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}

		return Arrays.copyOf(fullExpectedIncompatibility, kernels.size());
	}

	@Override
	protected double[] computeObservedIncomp() {
		numGroundings = new double[kernels.size()];
		fullObservedIncompatibility = new double[kernels.size() + immutableKernels.size()];
		setLabeledRandomVariables();

		/* Computes the observed incompatibilities and numbers of groundings */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				fullObservedIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
				numGroundings[i]++;
			}
		}
		for (int i = 0; i < immutableKernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(immutableKernels.get(i))) {
				fullObservedIncompatibility[kernels.size() + i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}

		return Arrays.copyOf(fullObservedIncompatibility, kernels.size());
	}

	@Override
	protected double computeLoss() {
		double loss = 0.0;
		for (int i = 0; i < kernels.size(); i++)
			loss += kernels.get(i).getWeight().getWeight() * (fullObservedIncompatibility[i] - fullExpectedIncompatibility[i]);
		for (int i = 0; i < immutableKernels.size(); i++)
			loss += immutableKernels.get(i).getWeight().getWeight() * (fullObservedIncompatibility[kernels.size() + i] - fullExpectedIncompatibility[kernels.size() + i]);
		return loss;
	}

	@Override
	protected void doLearn() {
		gradientSum = new double[kernels.size()];

		if (augmentLoss)
			addLossAugmentedKernels();
		super.doLearn();
		if (augmentLoss)
			removeLossAugmentedKernels();
	}
	
	@Override
	protected double[] computeScalingFactor() {
		if (!useAdaGrad)
			return super.computeScalingFactor();

		double [] scalingFactor = new double[kernels.size()];
	
		// otherwise accumulate gradient
		for (int i = 0; i < numGroundings.length; i++) {
			double weight = kernels.get(i).getWeight().getWeight();
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
