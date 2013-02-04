/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.application.learning.weight.random;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;

/**
 * A {@link MetropolisRandOM} learning algorithm that samples one weight for
 * all {@link GroundCompatibilityKernel GroundCompatibilityKernels} with the
 * same parent {@link CompatibilityKernel}.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class FirstOrderMetropolisRandOM extends MetropolisRandOM {
	
	protected double[] currentWeights, previousWeights, sum, sumSq;

	public FirstOrderMetropolisRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
	}
	
	@Override
	protected void doLearn() {
		currentWeights = new double[kernels.size()];
		previousWeights = new double[kernels.size()];
		for (int i = 0; i < previousWeights.length; i++)
			previousWeights[i] = kernels.get(i).getWeight().getWeight();
		
		sum = new double[kernels.size()];
		sumSq = new double[kernels.size()];
		
		super.doLearn();
	}

	@Override
	protected void prepareForRound() {
		for (int i = 0; i < kernels.size(); i++) {
			sum[i] = 0.0;
			sumSq[i] = 0.0;
		}
	}

	@Override
	protected void sampleAndSetWeights() {
		for (int i = 0; i < kernels.size(); i++) {
//			currentWeights[i] = sampleFromGaussian(previousWeights[i], kernelVariances[i]);
//			currentWeights[i] = sampleFromGaussian(previousWeights[i], Math.min(kernelVariances[i], 0.1));
			currentWeights[i] = sampleFromGaussian(previousWeights[i], 0.1);
			kernels.get(i).setWeight(new PositiveWeight(Math.max(0.0, currentWeights[i])));
		}
	}

	@Override
	protected double getLogLikelihoodSampledWeights() {
		double likelihood = 0.0;
		for (int i = 0; i < kernels.size(); i++)
			likelihood -= Math.pow(currentWeights[i] - kernelMeans[i], 2) / (2 * kernelVariances[i]);
		return likelihood;
	}

	@Override
	protected void acceptSample(boolean burnIn) {
//		if (!burnIn) System.out.println("ACCEPTED: " + model);
		for (int i = 0; i < kernels.size(); i++) {
			previousWeights[i] = currentWeights[i];
			if (!burnIn) {
				sum[i] += currentWeights[i];
				sumSq[i] += currentWeights[i] * currentWeights[i];
			}
		}
	}

	@Override
	protected void rejectSample(boolean burnIn) {
//		if (!burnIn) System.out.println("REJECTED: " + model);
		for (int i = 0; i < kernels.size(); i++) {
			if (!burnIn) {
				sum[i] += previousWeights[i];
				sumSq[i] += previousWeights[i] * previousWeights[i];
			}
		}
	}

	@Override
	protected void finishRound() {
		for (int i = 0; i < kernels.size(); i++) {
			kernelMeans[i] = sum[i] / (numSamples - burnIn);
			kernelVariances[i] = sumSq[i] / (numSamples - burnIn) - kernelMeans[i] * kernelMeans[i];
			System.out.println("Variance of " + kernelVariances[i] + " for kernel " + kernels.get(i)); 
		}
	}

}
