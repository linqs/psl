/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.optimizer.lbfgs.ConvexFunc;
import edu.umd.cs.psl.optimizer.lbfgs.LBFGSB;

/**
 * EM algorithm which fits a point distribution to the single most probable
 * assignment of truth values to the latent variables during the E-step. 
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class HardEM extends ExpectationMaximization implements ConvexFunc {

	private static final Logger log = LoggerFactory.getLogger(HardEM.class);

	private static boolean useLBFGS = false;
	
	double[] fullObservedIncompatibility, fullExpectedIncompatibility;

	public HardEM(Model model, Database rvDB, Database observedDB,
			ConfigBundle config) {
		super(model, rvDB, observedDB, config);
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
		if (useLBFGS) {
			LBFGSB optimizer = new LBFGSB(iterations, tolerance, kernels.size()-1, this);

			for (int i = 0; i < kernels.size(); i++) {
				optimizer.setLowerBound(i, 0.0);
				optimizer.setBoundSpec(i, 1);
			}

			double [] weights = new double[kernels.size()];
			for (int i = 0; i < kernels.size(); i++)
				weights[i] = kernels.get(i).getWeight().getWeight();
			int [] iter = new int[1];	
			boolean [] error = new boolean[1];	

			double objective = optimizer.minimize(weights, iter, error);

			for (int i = 0; i < kernels.size(); i++) 
				kernels.get(i).setWeight(new PositiveWeight(weights[i]));

			log.info("LBFGS learning finished with final objective value {}", objective);
		} else 
			super.doLearn();

	}

	@Override
	public double getValueAndGradient(double[] gradient, double[] weights) {
		for (int i = 0; i < kernels.size(); i++) {
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
		}
		minimizeKLDivergence();
		computeObservedIncomp();

		reasoner.changedGroundKernelWeights();
		computeExpectedIncomp();

		double loss = 0.0;
		for (int i = 0; i < kernels.size(); i++)
			loss += weights[i] * (fullObservedIncompatibility[i] - fullExpectedIncompatibility[i]);
		for (int i = 0; i < immutableKernels.size(); i++)
			loss += immutableKernels.get(i).getWeight().getWeight() * (fullObservedIncompatibility[kernels.size() + i] - fullExpectedIncompatibility[kernels.size() + i]);

		double regularizer = computeRegularizer();

		for (int i = 0; i < kernels.size(); i++) {
			gradient[i] = (fullObservedIncompatibility[i] - fullExpectedIncompatibility[i]) + l2Regularization * weights[i] + l1Regularization;
		}

		return loss + regularizer;
	}

}
