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
package edu.umd.cs.psl.reasoner.bool;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.Reasoner;

/**
 * Implementation of MC-Sat, which approximates the marginal probability that each
 * atom has value 1 in a Boolean domain.
 * <p>
 * Marginal probabilities will be set as the atoms' truth values.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BooleanMCSat extends MemoryGroundKernelStore implements Reasoner {
	
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
	public static final int NUM_SAMPLES_DEFAULT = 5000;
	
	/**
	 * Number of burn-in samples
	 */
	public static final String NUM_BURN_IN_KEY = CONFIG_PREFIX + ".numburnin";
	/** Default value for NUM_BURN_IN_KEY */
	public static final int NUM_BURN_IN_DEFAULT = 500;
	
	private Random rand;
	private int numSamples;
	private int numBurnIn;
	
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
		/* Collects the RandomVariableAtoms */
		Set<RandomVariableAtom> rvSet = new HashSet<RandomVariableAtom>();
		for (GroundKernel gk : getGroundKernels())
			for (GroundAtom atom : gk.getAtoms())
				if (atom instanceof RandomVariableAtom)
					rvSet.add(((RandomVariableAtom) atom));
		
		RandomVariableAtom[] rvs = new RandomVariableAtom[rvSet.size()];
		double[] totals = new double[rvSet.size()];
		int i = 0;
		for (RandomVariableAtom atom : rvSet)
			rvs[i++] = atom;
		
		for (int sample = 0; sample < numSamples; sample++) {
			for (i = 0; i < rvs.length; i++) {
				rvs[i].setValue(1.0);
				double probTrue = 0.0;
				for (GroundKernel gk : rvs[i].getRegisteredGroundKernels())
					if (gk instanceof GroundCompatibilityKernel)
						probTrue += ((GroundCompatibilityKernel) gk).getWeight().getWeight()
								* ((GroundCompatibilityKernel) gk).getIncompatibility();
				probTrue = Math.exp(-1 * probTrue);
				
				rvs[i].setValue(0.0);
				double probFalse = 0.0;
				for (GroundKernel gk : rvs[i].getRegisteredGroundKernels())
					if (gk instanceof GroundCompatibilityKernel)
						probTrue += ((GroundCompatibilityKernel) gk).getWeight().getWeight()
								* ((GroundCompatibilityKernel) gk).getIncompatibility();
				probFalse = Math.exp(-1 * probFalse);
				
				probTrue = probTrue / (probTrue  + probFalse);
				
				double sampledValue = (rand.nextDouble() < probTrue) ? 1.0 : 0.0;
				rvs[i].setValue(sampledValue);

				if (sample >= numBurnIn)
					totals[i] += sampledValue;
			}
		}
		
		for (i = 0; i < rvs.length; i++)
			rvs[i].setValue(totals[i] / (numSamples - numBurnIn));
	}
	
	@Override
	public void close() {
		/* Intentionally blank */
	}

}
