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
package edu.umd.cs.psl.reasoner.bool;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.predicateconstraint.GroundDomainRangeConstraint;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.util.model.ConstraintBlocker;

/**
 * Implementation of MC-Sat, which approximates the marginal probability that each
 * atom has value 1 in a Boolean domain.
 * <p>
 * Marginal probabilities will be set as the atoms' truth values.
 * <p>
 * This class supports free {@link RandomVariableAtom RandomVariableAtoms}
 * and RandomVariableAtoms that are each constrained by a single
 * {@link GroundDomainRangeConstraint}. It also assumes that all ObservedAtoms
 * have Boolean truth values. Its behavior is not defined otherwise.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BooleanMCSat extends MemoryGroundKernelStore implements Reasoner {
	
	private static final Logger log = LoggerFactory.getLogger(BooleanMCSat.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "booleanmcsat";
	
	/**
	 * Key for length of Markov chain
	 */
	public static final String NUM_SAMPLES_KEY = CONFIG_PREFIX + ".numsamples";
	/** Default value for NUM_SAMPLES_KEY */
	public static final int NUM_SAMPLES_DEFAULT = 2500;
	
	/**
	 * Number of burn-in samples
	 */
	public static final String NUM_BURN_IN_KEY = CONFIG_PREFIX + ".numburnin";
	/** Default value for NUM_BURN_IN_KEY */
	public static final int NUM_BURN_IN_DEFAULT = 500;
	
	private final Random rand;
	private final int numSamples;
	private final int numBurnIn;
	
	public BooleanMCSat(ConfigBundle config) {
		super();
		rand = new Random();
		numSamples = config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
		if (numSamples <= 0)
			throw new IllegalArgumentException("Number of samples must be positive.");
		numBurnIn = config.getInt(NUM_BURN_IN_KEY, NUM_BURN_IN_DEFAULT);
		if (numSamples <= 0)
			throw new IllegalArgumentException("Number of burn in samples must be positive.");
		if (numBurnIn >= numSamples)
			throw new IllegalArgumentException("Number of burn in samples must be less than number of samples.");
	}
	
	@Override
	public void optimize() {
		ConstraintBlocker blocker = new ConstraintBlocker(this);
		blocker.prepareBlocks(false);
		
		/* Puts RandomVariableAtoms in 2d array by block */
		RandomVariableAtom[][] rvBlocks = blocker.getRVBlocks();
		/* If true, exactly one Atom in the RV block must be 1.0. If false, at most one can. */
		boolean[] exactlyOne = blocker.getExactlyOne();
		/* Collects GroundCompatibilityKernels incident on each block of RandomVariableAtoms */
		GroundCompatibilityKernel[][] incidentGKs = blocker.getIncidentGKs();
		/* Initializes arrays for totaling samples */
		double[][] totals = blocker.getEmptyDouble2DArray();
		
		/* Randomly initializes the RVs to a feasible state */
		blocker.randomlyInitializeRVs();
		
		/* Samples RV assignments */
		log.info("Beginning inference.");
		double[] p;
		for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
			for (int i = 0; i < rvBlocks.length; i++) {
				if (rvBlocks.length == 0)
					continue;
				
				p = new double[(exactlyOne[i]) ? rvBlocks[i].length : (rvBlocks[i].length + 1)];
				
				/* Computes probability for assignment of 1.0 to each RV */
				for (int j = 0; j < rvBlocks[i].length; j++) {
					/* Sets RVs */
					for (int k = 0; k < rvBlocks[i].length; k++)
						rvBlocks[i][k].setValue((k == j) ? 1.0 : 0.0);
					
					/* Computes probability */
					p[j] = computeProbability(incidentGKs[i]);
				}
				
				/* If all RVs in block assigned 0.0 is valid, computes probability */
				if (!exactlyOne[i]) {
					/* Sets all RVs to 0.0 */
					for (RandomVariableAtom atom : rvBlocks[i])
						atom.setValue(0.0);
					
					/* Computes probability */
					p[p.length - 1] = computeProbability(incidentGKs[i]);
				}
				
				/* Draws sample */
				double[] sample = sampleWithProbability(p);
				for (int j = 0; j < rvBlocks[i].length; j++) {
					rvBlocks[i][j].setValue(sample[j]);
					
					if (sampleIndex >= numBurnIn)
						totals[i][j] += sample[j];
				}
			}
		}
		
		log.info("Inference complete.");
		
		/* Sets truth values of RandomVariableAtoms to marginal probabilities */
		for (int i = 0; i < rvBlocks.length; i++)
			for (int j = 0; j < rvBlocks[i].length; j++)
				rvBlocks[i][j].setValue(totals[i][j] / (numSamples - numBurnIn));
	}
	
	private double computeProbability(GroundCompatibilityKernel incidentGKs[]) {
		double probability = 0.0;
		for (GroundCompatibilityKernel gk : incidentGKs) {
			probability += ((GroundCompatibilityKernel) gk).getWeight().getWeight()
					* ((GroundCompatibilityKernel) gk).getIncompatibility();
		}
		return Math.exp(-1 * probability);
	}
	
	private double[] sampleWithProbability(double[] distribution) {
		/* Just in case an RV block is empty */
		if (distribution.length == 0)
			return new double[0];
		
		/* Normalizes distribution */
		double total = 0.0;
		for (double pValue : distribution)
			total += pValue;
		for (int i = 0; i < distribution.length; i++)
			distribution[i] /= total;
		
		/* Draws sample */
		double[] sample = new double[distribution.length];
		double cutoff = rand.nextDouble();
		total = 0.0;
		for (int i = 0; i < distribution.length; i++) {
			total += distribution[i];
			if (total >= cutoff) {
				sample[i] = 1.0;
				return sample;
			}
		}

		/*
		 * Just in case a rounding error and a very high cutoff prevents the loop
		 * from returning, returns the last assignment
		 */
		sample[sample.length-1] = 1.0;
		return sample;
	}
	
	@Override
	public void close() {
		/* Intentionally blank */
	}

}
