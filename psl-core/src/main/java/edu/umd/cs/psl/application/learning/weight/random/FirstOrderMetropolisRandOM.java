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
package edu.umd.cs.psl.application.learning.weight.random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger log = LoggerFactory.getLogger(FirstOrderMetropolisRandOM.class);

	protected double[] currentWeights, previousWeights, sum, sumSq;
	protected double variance;
	protected int nextKernel;

	public FirstOrderMetropolisRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		variance = initialVariance;
	}

	@Override
	protected void doLearn() {
		currentWeights = new double[kernels.size()];
		previousWeights = new double[kernels.size()];
		for (int i = 0; i < previousWeights.length; i++) {
			currentWeights[i] = kernels.get(i).getWeight().getWeight();
			previousWeights[i] = kernels.get(i).getWeight().getWeight();
		}

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
		nextKernel = 0;
	}

	@Override
	protected void sampleAndSetWeights() {
		//		int i = nextKernel;
		//			currentWeights[nextKernel] = sampleFromGaussian(previousWeights[nextKernel], variance);
		//			currentWeights[nextKernel] = sampleFromGaussian(previousWeights[nextKernel], kernelVariances[nextKernel]);
		//			currentWeights[nextKernel] = sampleFromGaussian(previousWeights[nextKernel], Math.min(kernelVariances[nextKernel], 0.1));
		currentWeights[nextKernel] = sampleFromGaussian(previousWeights[nextKernel], variance); 
		for (int i = 0; i < kernels.size(); i++)
			kernels.get(i).setWeight(new PositiveWeight(Math.max(0.0, currentWeights[i])));
	}

	@Override
	protected double getLogLikelihoodSampledWeights() {
		double likelihood = 0.0;
		for (int i = 0; i < kernels.size(); i++) {
			likelihood -= Math.pow(currentWeights[i] - kernelMeans[i], 2) / (2 * initialVariance);
			//log.info("Current weight : {}, mean : {}", currentWeights[i], kernelMeans[i]);
		}
		//			likelihood -= Math.pow(currentWeights[i] - kernelMeans[i], 2) / (2 * variance);
		//			likelihood -= Math.pow(currentWeights[i] - kernelMeans[i], 2) / (2 * kernelVariances[i]);
		return likelihood;
	}

	@Override
	protected void acceptSample(boolean burnIn) {
		//		log.debug("ACCEPTED: {}", model);
		for (int i = 0; i < kernels.size(); i++) {
			previousWeights[i] = currentWeights[i];
			if (!burnIn) {
				sum[i] += currentWeights[i];
				sumSq[i] += currentWeights[i] * currentWeights[i];
			}
		}
		nextKernel++;
		if (nextKernel == kernels.size())
			nextKernel = 0;
	}

	@Override
	protected void rejectSample(boolean burnIn) {
		//		log.debug("REJECTED: {}", model);
		for (int i = 0; i < kernels.size(); i++) {
			if (!burnIn) {
				sum[i] += previousWeights[i];
				sumSq[i] += previousWeights[i] * previousWeights[i];
			}
		}
		currentWeights[nextKernel] = previousWeights[nextKernel];
		nextKernel++;
		if (nextKernel == kernels.size())
			nextKernel = 0;
	}

	@Override
	protected void finishRound() {
		variance = 0;
		for (int i = 0; i < kernels.size(); i++) {
			kernelMeans[i] = sum[i] / (numSamples - burnIn);
			kernelVariances[i] = (sumSq[i]  - (sum[i] * sum[i] / (numSamples - burnIn))) / (numSamples - burnIn - 1);
			variance += kernelVariances[i];
			log.warn("Variance of {} for kernel {}", kernelVariances[i], kernels.get(i)); 
		}
		variance /= kernels.size();
		variance = Math.max(variance, 1e-3);
		log.warn("Variance: {}", variance);
	}

	@Override
	protected void updateProposalVariance(int accepted, int count) {
		double rate = (double) accepted / (count + 1);
		if (count > 0 && count % 5 == 0) { // && count < burnIn) {
			variance *= rate / 0.5;
		}
		log.info("Acceptance rate is {}.", rate);
		log.info("Current proposal variance: {}", variance);
	}
	
}
