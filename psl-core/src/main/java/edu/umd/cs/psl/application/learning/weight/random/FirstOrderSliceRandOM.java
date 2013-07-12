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
import edu.umd.cs.psl.model.NumericUtilities;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;

public class FirstOrderSliceRandOM extends SliceRandOM {

	private static final Logger log = LoggerFactory.getLogger(FirstOrderSliceRandOM.class);
	
	protected double[] currentWeights, previousWeights, sum, sumSq;
	protected double variance;
	protected int nextKernel;
	protected double current, l, r;
	
	protected final double stepSize = 0.5;
	protected final int maxNumSteps = 20;

	public FirstOrderSliceRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
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
	protected int getNumStepsPerSample() {
		return kernels.size();
	}

	@Override
	protected void stepOut() {
		current = currentWeights[nextKernel];
		l = current - stepSize * rand.nextDouble();
		r = l + stepSize;
		int j = (int) Math.floor(maxNumSteps * rand.nextDouble());
		int k = maxNumSteps - 1 - j;
		
		while (j > 0 && sliceHeight < moveAndCheck(l)) {
			log.info("Stepped left.");
			l -= stepSize;
			j--;
		}
		
		while (k > 0 && sliceHeight < moveAndCheck(r)) {
			log.info("Stepped right.");
			r += stepSize;
			k--;
		}
		
		log.info("L: {}, R: {}", l, r);
		log.info("J: {}, K: {}", j, k);
	}

	@Override
	protected double stepIn() {
		double lNow = l;
		double rNow = r;
		
		/* Just don't loop indefinitely */
		for (int i = 0; i < 100; i++) {
			double weight = lNow + rand.nextDouble() * (rNow - lNow);
			double likelihood = moveAndCheck(weight);
			log.info("Likelihood at weight {}: {}", weight, likelihood);
			if (sliceHeight < likelihood || Math.abs(rNow - lNow) < NumericUtilities.strictEpsilon) {
				if (Math.abs(rNow - lNow) < NumericUtilities.strictEpsilon)
					log.warn("Interval collapsed.");
				nextKernel++;
				if (nextKernel == kernels.size())
					nextKernel = 0;
				return likelihood;
			}
			else if (weight < current)
				lNow = weight;
			else
				rNow = weight;
		}
		
		throw new IllegalStateException("Step in failed.");
	}

	@Override
	protected double getLogLikelihoodSampledWeights() {
		double likelihood = 0.0;
		for (int i = 0; i < kernels.size(); i++) {
			likelihood -= Math.pow(currentWeights[i] - kernelMeans[i], 2) / (2 * initialVariance);
		}
//			likelihood -= Math.pow(currentWeights[i] - kernelMeans[i], 2) / (2 * variance);
//			likelihood -= Math.pow(currentWeights[i] - kernelMeans[i], 2) / (2 * kernelVariances[i]);
		return likelihood;
	}

	@Override
	protected void processSample() {
		for (int i = 0; i < kernels.size(); i++) {
			log.warn("Current weight : {}, mean : {}", currentWeights[i], kernelMeans[i]);
			sum[i] += currentWeights[i];
			sumSq[i] += currentWeights[i] * currentWeights[i];
		}
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
	
	/**
	 * Moves the current kernel to the given weight and computes the log likelihood
	 * 
	 * @param weight  weight to move to
	 * @return log likelihood at the new point
	 */
	private double moveAndCheck(double weight) {
		currentWeights[nextKernel] = weight;
		kernels.get(nextKernel).setWeight(new PositiveWeight(Math.max(weight, 0.0)));
		for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(nextKernel)))
			reasoner.changedGroundKernelWeight((GroundCompatibilityKernel) gk);
		
		reasoner.optimize();
		
		double obsvLikelihood = getLogLikelihoodObservations();
		double weightsLikelihood = getLogLikelihoodSampledWeights();
		
		log.info("Likelihood of observations: {}", obsvLikelihood);
		log.info("likelihood of weights: {}", weightsLikelihood);
		log.info("Total likelihood: {}", obsvLikelihood + weightsLikelihood);
		
		return obsvLikelihood + weightsLikelihood;
//		return getLogLikelihoodObservations() + getLogLikelihoodSampledWeights();
	}

}
