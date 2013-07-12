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

import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.learning.weight.WeightLearningApplication;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;

/**
 * Abstract class that learns new weights for the
 * {@link CompatibilityKernel CompatibilityKernels} in a {@link Model} using
 * slice-sampling MCEM RandOM learning.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 * @author Bert Huang <bert@cs.umd.edu>
 */
public abstract class SliceRandOM extends WeightLearningApplication {

	private static final Logger log = LoggerFactory.getLogger(SliceRandOM.class);

	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "slicerandom";
	
	/**
	 * Key for maximum iterations of Monte Carlo EM
	 */
	public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiter";
	/** Default value for MAX_ITER_KEY */
	public static final int MAX_ITER_DEFAULT = 10;

	/**
	 * Key for length of Markov chain
	 */
	public static final String NUM_SAMPLES_KEY = CONFIG_PREFIX + ".numsamples";
	/** Default value for NUM_SAMPLES_KEY */
	public static final int NUM_SAMPLES_DEFAULT = 10;

	/**
	 * Number of burn-in samples
	 */
	public static final String BURN_IN_KEY = CONFIG_PREFIX + ".burnin";
	/** Default value for BURN_IN_KEY */
	public static final int BURN_IN_DEFAULT = 2;
	
	/**
	 * Key for positive double to be used as the initial variance for each
	 * Kernel's weight
	 */
	public static final String INITIAL_VARIANCE_KEY = CONFIG_PREFIX + ".initialvariance";
	/** Default value for INITIAL_VARIANCE_KEY */
	public static final double INITIAL_VARIANCE_DEFAULT = 1.0;
	
	/**
	 * Key for positive double property to divide the unnormalized
	 * log likelihood of observations (unless this behavior is overridden by
	 * an implementation). 
	 */
	public static final String OBSERVATION_DENSITY_SCALE_KEY = CONFIG_PREFIX + ".observationscale";
	/** Default value for OBSERVATION_DENSITY_SCALE_KEY */
	public static final double OBSERVATION_DENSITY_SCALE_DEFAULT = 1.0;

	/**
	 * Key for double property to be multiplied with square root of number of
	 * CompatibilityKernels to form mean change stopping criterion
	 */
	public static final String CHANGE_THRESHOLD_KEY = CONFIG_PREFIX + ".changethreshold";
	/** Default value for CHANGE_THRESHOLD_KEY */
	public static final double CHANGE_THRESHOLD_DEFAULT = 0.00005;

	protected final Random rand;
	protected double[] kernelMeans, kernelVariances;
	protected double sliceHeight;

	protected final int maxIter;
	protected final int numSamples;
	protected final int burnIn;
	protected final double initialVariance;
	protected final double observationScale;
	protected final double changeThresholdFactor;

	public SliceRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);

		rand = new Random();

		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		numSamples = config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
		burnIn = config.getInt(BURN_IN_KEY, BURN_IN_DEFAULT);
		initialVariance = config.getDouble(INITIAL_VARIANCE_KEY, INITIAL_VARIANCE_DEFAULT);
		if (initialVariance <= 0.0)
			throw new IllegalArgumentException("Initial variance must be positive.");
		observationScale = config.getDouble(OBSERVATION_DENSITY_SCALE_KEY, OBSERVATION_DENSITY_SCALE_DEFAULT);
		if (observationScale <= 0.0)
			throw new IllegalArgumentException("Observation density scale must be positive.");
		changeThresholdFactor = config.getDouble(CHANGE_THRESHOLD_KEY, CHANGE_THRESHOLD_DEFAULT);
	}

	@Override
	protected void doLearn() {
		/* Loads initial weight means and variances */
		kernelMeans = new double[kernels.size()];
		kernelVariances = new double[kernels.size()];
		double[] oldKernelMeans = new double[kernels.size()];
		for (int i = 0; i < kernelMeans.length; i++) {
			kernelMeans[i] = kernels.get(i).getWeight().getWeight();
			oldKernelMeans[i] = kernelMeans[i];
			kernelVariances[i] = initialVariance;
		}
		reasoner.optimize();

		/* Performs rounds of Monte Carlo EM */
		int mcemIter = 1;
		double changeInWeightMeans;
		do {
			log.info("Starting Monte Carlo EM round " + mcemIter + ".");
			prepareForRound();
			reasoner.optimize();
			
			/* Samples along a Markov chain */
			double likelihood = getLogLikelihoodObservations() + getLogLikelihoodSampledWeights();
			for (int count = 0; count < numSamples; count++) {
				for (int i = 0; i < getNumStepsPerSample(); i++) {
					log.info("Current likelihood: {}", likelihood);
					sliceHeight = likelihood + Math.log(1 - rand.nextDouble());
					log.info("New slice height: {}", sliceHeight);
					stepOut();
					likelihood = stepIn();
				}
				if (count >= burnIn)
					processSample();
			}
			
			finishRound();
			
			changeInWeightMeans = 0.0;
			for (int i = 0; i < kernels.size(); i++) {
				double diff = kernelMeans[i] - oldKernelMeans[i];
				changeInWeightMeans += diff * diff;
				oldKernelMeans[i] = kernelMeans[i];
				log.warn("Mean of {} for kernel {}, ", kernelMeans[i], kernels.get(i));
			}
			
			changeInWeightMeans = Math.sqrt(changeInWeightMeans);
			log.warn("Change in weight means: {}", changeInWeightMeans);
			mcemIter++;
		}
		while (mcemIter <= maxIter && changeInWeightMeans > changeThresholdFactor * Math.sqrt(kernels.size()));
		
		
		/* Sets final learned weights */
		for (int i = 0; i < kernels.size(); i++)
			kernels.get(i).setWeight(new PositiveWeight(Math.max(0, kernelMeans[i])));
	}
	
	protected abstract void prepareForRound();
	
	protected abstract int getNumStepsPerSample();
	
	/**
	 * Finds a region containing most or all of the slice at the current
	 * value of {@link #sliceHeight}.
	 * 
	 * @param sliceHeight  height of the slice to contain
	 */
	protected abstract void stepOut();
	
	/**
	 * Samples a point from the slice inside the region.
	 * 
	 * @return log-likelihood of sampled point
	 */
	protected abstract double stepIn();
	
	/**
	 * @return likelihood of observations given minimizer of energy function
	 */
	protected double getLogLikelihoodObservations() {
		double likelihood = 0.0;
		
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			likelihood -= Math.abs(e.getKey().getValue() - e.getValue().getValue()) / observationScale;
//			likelihood -= Math.abs(e.getKey().getValue() - e.getValue().getValue()) / (0.25 + 3 * e.getKey().getValue());
//			likelihood -= Math.pow(e.getKey().getValue() - e.getValue().getValue(), 2) / (2 *  observationScale);
//			likelihood -= Math.pow(e.getKey().getValue() - e.getValue().getValue(), 2) / (0.25 + 3 * e.getKey().getValue());
			
//			if (e.getKey().getValue() == 0.0)
//				likelihood -= Math.abs(e.getKey().getValue() - e.getValue().getValue()) / 0.09;
//			else
//				likelihood -= Math.abs(e.getKey().getValue() - e.getValue().getValue()) / 0.91;
		}
//		log.info("Num interesting atoms: {}", numInteresting);
		
		return likelihood;
	}
	
	protected abstract double getLogLikelihoodSampledWeights();
	
	protected abstract void processSample();
	
	protected abstract void finishRound();

}
