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
package edu.umd.cs.psl.application.learning.weight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.local.LocalProcessMonitor;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;

/**
 * Learns new weights for the {@link CompatibilityKernel CompatibilityKernels}
 * in a {@link Model} using Metropolis-Hastings EM RandOM learning.
 * <p>
 * TODO: description
 * 
 * @author Steve Bach <bach@cs.umd.edu>
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class MetropolisHastingsRandOM implements ModelApplication {

	private static final Logger log = LoggerFactory.getLogger(MetropolisHastingsRandOM.class);

	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "random";

	/**
	 * Key for mean change threshold
	 */
	public static final String CHANGE_THRESHOLD = CONFIG_PREFIX + ".changethreshold";
	/** Default value for CHANGE_THRESHOLD */
	public static final double CHANGE_THRESHOLD_DEFAULT = 1e-3;
	
	/**
	 * Key for target sample accept rate
	 */
	public static final String TARGET_ACCEPT_RATE = CONFIG_PREFIX + ".targetacceptrate";
	/** Default value for TARGET_ACCEPT_RATE */
	public static final double TARGET_ACCEPT_RATE_DEFAULT = 0.25;

	/**
	 * Key for growth rate for adjusting proposal variance
	 */
	public static final String GROWTH_RATE = CONFIG_PREFIX + ".growthrate";
	/** Default value for GROWTH_RATE */
	public static final double GROWTH_RATE_DEFAULT = 1.5;

	/**
	 * Key for initial proposal variance
	 */
	public static final String PROPOSAL_VARIANCE = CONFIG_PREFIX + ".proposalvariance";
	/** Default value for PROPOSAL_VARIANCE */
	public static final double PROPOSAL_VARIANCE_DEFAULT = 0.001;
	
	/**
	 * Key for maximum iterations
	 */
	public static final String MAX_ITER = CONFIG_PREFIX + ".max_iter";
	/** Default value for MAX_ITER */
	public static final int MAX_ITER_DEFAULT = 10;


	/**
	 * Key for length of Markov chain
	 */
	public static final String NUM_SAMPLES = CONFIG_PREFIX + ".num_samples";
	/** Default value for NUM_SAMPLES */
	public static final int NUM_SAMPLES_DEFAULT = 1000;

	/**
	 * Number of burn-in samples
	 */
	public static final String BURN_IN = CONFIG_PREFIX + ".burn_in";
	/** Default value for BURN_IN */
	public static final int BURN_IN_DEFAULT = 100;

	/**
	 * Key for {@link Factory} or String property.
	 * <p>
	 * Should be set to a {@link ReasonerFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link Reasoner}.
	 */
	public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
	/**
	 * Default value for REASONER_KEY.
	 * <p>
	 * Value is instance of {@link ADMMReasonerFactory}.
	 */
	public static final ReasonerFactory REASONER_DEFAULT = new ADMMReasonerFactory();

	private Model model;
	private Database rvDB, observedDB;
	private ConfigBundle config;

	private final int maxIter;
	private final int burnIn;
	private final int numSamples;
	private final double growthRate;
	private final double changeThreshold;
	private Random rand;
	private double proposalVariance;
	private double targetAcceptRate;
	
	private Map<CompatibilityKernel, Double> weightMeans;


	public MetropolisHastingsRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this.model = model;
		this.rvDB = rvDB;
		this.observedDB = observedDB;
		this.config = config;

		rand = new Random();

		maxIter = config.getInt(MAX_ITER, MAX_ITER_DEFAULT);
		numSamples = config.getInt(NUM_SAMPLES, NUM_SAMPLES_DEFAULT);
		burnIn = config.getInt(BURN_IN, BURN_IN_DEFAULT);
		changeThreshold = config.getDouble(CHANGE_THRESHOLD, CHANGE_THRESHOLD_DEFAULT);

		proposalVariance = config.getDouble(PROPOSAL_VARIANCE, PROPOSAL_VARIANCE_DEFAULT);
		targetAcceptRate = config.getDouble(TARGET_ACCEPT_RATE, TARGET_ACCEPT_RATE_DEFAULT);
		growthRate = config.getDouble(GROWTH_RATE, GROWTH_RATE_DEFAULT);
		
		weightMeans = new HashMap<CompatibilityKernel, Double>();
	}

	/**
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} in the distribution are those
	 * persisted in the Database when this method is called. All RandomVariableAtoms
	 * which the Model might access must be persisted in the Database.
	 * 
	 * @see DatabasePopulator
	 */
	public void learn() 
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		RunningProcess proc = LocalProcessMonitor.get().startProcess();

		/* Set up the ground model */
		Reasoner reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		TrainingMap trainingMap = new TrainingMap(rvDB, observedDB);
		if (trainingMap.getLatentVariables().size() > 0)
			throw new IllegalArgumentException("All RandomVariableAtoms must have " +
					"corresponding ObservedAtoms. Latent variables are not supported " +
					"by MetroplisHastingsRandOM.");
		Grounding.groundAll(model, trainingMap, reasoner);

		List<GroundCompatibilityKernel> groundKernels = new ArrayList<GroundCompatibilityKernel>();
		for (GroundCompatibilityKernel k : Iterables.filter(reasoner.getGroundKernels(), GroundCompatibilityKernel.class))
			groundKernels.add(k);

		/*
		 * Set up kernel index, which stores the indices of ground kernels in 
		 * groundKernels that originate from each kernel
		 */
		Map<CompatibilityKernel, List<Integer>> kernelIndex = new HashMap<CompatibilityKernel, List<Integer>>();
		for (int i = 0; i < groundKernels.size(); i++) {
			GroundCompatibilityKernel gk = groundKernels.get(i);
			CompatibilityKernel k = gk.getKernel();
			List<Integer> groundKernelIndices = (kernelIndex.containsKey(k)) ? 
					kernelIndex.get(k) : new ArrayList<Integer>();
			groundKernelIndices.add(i);
			kernelIndex.put(k, groundKernelIndices);
		}
		
		/*
		 * load current weight means
		 */
		for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class))
			weightMeans.put(k, k.getWeight().getWeight());
		
		
		int outerIter = 0;	
		boolean converged = false;

		while (!converged) {
			// create data structure to store sampled ground weights
			StatCounter weightSamples = new StatCounter(groundKernels.size());

			double [] previous = new double[groundKernels.size()];
			double previousLikelihood = Double.NEGATIVE_INFINITY;
			
			log.debug("Starting outer iteration " + outerIter);
			
			for (int i = 0; i < groundKernels.size(); i++) {
				GroundCompatibilityKernel gk = groundKernels.get(i);
				previous[i] = weightMeans.get(gk.getKernel());
			}
			
			int acceptCount = 0;
			
			int count;
			// sample a chain of ground kernel weights
			for (count = 0; count < numSamples; count++) {
				double [] current = generateNextSample(previous);

				// set weights to new sample
				for (int i = 0; i < groundKernels.size(); i++) {
					GroundCompatibilityKernel gk = groundKernels.get(i);
					gk.setWeight(new PositiveWeight(Math.max(0, current[i])));
				}
				reasoner.changedGroundKernelWeights();
				reasoner.optimize();

				// measure log likelihood
				double newLikelihood = getLikelihood(trainingMap, groundKernels);

				boolean accept = rand.nextDouble() < Math.exp(newLikelihood - previousLikelihood);
				log.debug("Acceptance probability " + Math.exp(newLikelihood - previousLikelihood));

				if (count >= burnIn) {
					weightSamples.add(current);
				} else {
					if (count > 0 && count % 10 == 0) {
						double acceptRate = (double) acceptCount / (double) (count + 1);
						// update proposal variance to try to get acceptRate closer to targetAcceptRate
						
						if (acceptRate < targetAcceptRate)
							proposalVariance /= growthRate;
						else
							proposalVariance *= growthRate;
						
						log.debug("Setting proposal variance to {}", proposalVariance);
					}
				}
				
				if (accept) {
					previous = current;
					previousLikelihood = newLikelihood;
					acceptCount++;
					log.debug("Accepted new weight vector. Sample {} of {}", count, numSamples);
				} else {
					log.debug("Rejected new weight vector. Sample {} of {}", count, numSamples);
				}
				log.debug("Acceptance rate: {}", (double) acceptCount / (double) (count + 1));
			}

			double change = 0.0;
			/* set weights to mean of ground kernels */
			for (CompatibilityKernel k : kernelIndex.keySet()) {
				List<Integer> gkIndices = kernelIndex.get(k);
				int total = (count - burnIn) * gkIndices.size();
				double sum = 0.0;
				for (Integer i : gkIndices) 
					sum += weightSamples.getTotal(i);
				double newWeight = sum / (double) total;
				change += Math.abs(newWeight - weightMeans.get(k));
				weightMeans.put(k, newWeight);
				//log.debug("Weight sum for " + k + ": " + sum + ", denom " + total + " count " + count + " gkIndices.size() " + gkIndices.size());
				log.debug("Weight avg for " + k + ": " + weightMeans.get(k));
			}			
			
			outerIter++;

			/*
			 * Convergence check
			 * TODO: what is a good stopping criterion?
			 */
			if (outerIter >= maxIter || change < changeThreshold)
				converged = true;
		}
		
		
		/*
		 * set final learned weights
		 */
		for (CompatibilityKernel k : kernelIndex.keySet())
			k.setWeight(new PositiveWeight(Math.max(0, weightMeans.get(k))));
		
		proc.terminate();
	}

	/**
	 * returns unnormalized log likelihood of current prediction and ground weights
	 * @param trainingMap
	 * @param reasoner
	 * @return
	 */
	private double getLikelihood(TrainingMap trainingMap, List<GroundCompatibilityKernel> groundKernels) {

		double likelihood = 0.0;
		/* Compute the likelihood of y */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			likelihood -= Math.abs(e.getKey().getValue() - e.getValue().getValue()); 
			//likelihood -= Math.pow(e.getKey().getValue() - e.getValue().getValue(), 2); 
		}

		//log.debug("log P(y | mu) " + likelihood);
		
		/* Compute the likelihood of ground weights */
		for (GroundCompatibilityKernel gk : groundKernels) {
			likelihood -= Math.pow(gk.getWeight().getWeight() - weightMeans.get(gk.getKernel()), 2);
		}
		//TODO: add variance into calculation of ground weight likelihoods

		//log.debug("New log likelihood " + likelihood);
		return likelihood;
	}

	/**
	 * Generates the next candidate weight vector from simple Gaussian proposal distribution
	 * @param mean
	 * @return
	 */
	private double[] generateNextSample(double[] mean) {
		double [] sample = new double[mean.length];

		for (int i = 0; i < mean.length; i++) {
			sample[i] = sampleFromGaussian(mean[i], proposalVariance);
		}
		
		//if (mean.length >= 3)
		//	log.debug("1st 3 dims of new sample " + sample[0] + ", " + sample[1] + ", " + sample[2]);
		return sample;
	}

	
	/**
	 * Samples a univariate Gaussian data point
	 * @param mean
	 * @param variance
	 * @return
	 */
	private double sampleFromGaussian(double mean, double variance) {
		return variance * rand.nextGaussian() + mean; 
		// TODO: check if this is correct for variance or std deviation or neither
	}

	@Override
	public void close() {
		model = null;
		rvDB = null;
		config = null;
	}
	
	/**
	 * Stores sufficient statistics for weight vector sample distribution
	 *
	 */
	private class StatCounter {
		public StatCounter(int dimensions) {
			this.dimensions = dimensions;
			totals = new double[dimensions];
		}
		
		public double getTotal(Integer i) {
			return totals[i];
		}

		public void add(double [] x) {
			for (int i = 0; i < dimensions; i++)
				totals[i] += x[i];
		}
		
		double [] totals;
		int dimensions;
	}

}
