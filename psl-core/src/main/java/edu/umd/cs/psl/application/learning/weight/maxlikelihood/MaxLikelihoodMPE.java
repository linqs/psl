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
package edu.umd.cs.psl.application.learning.weight.maxlikelihood;

import java.util.Arrays;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;

/**
 * Learns weights by optimizing the log likelihood of the data using
 * the voted perceptron algorithm.
 * <p>
 * The expected total incompatibility is estimated with the total incompatibility
 * in the MPE state.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class MaxLikelihoodMPE extends VotedPerceptron {
	
	double[] fullObservedIncompatibility, fullExpectedIncompatibility;

	public MaxLikelihoodMPE(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
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

}
