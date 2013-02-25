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
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.predicateconstraint.GroundDomainRangeConstraint;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

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
		/* Collects GroundDomainRangeConstraints */
		Set<GroundDomainRangeConstraint> constraintSet = new HashSet<GroundDomainRangeConstraint>();
		for (GroundConstraintKernel gk : getConstraintKernels()) {
			if (gk instanceof GroundDomainRangeConstraint)
				constraintSet.add((GroundDomainRangeConstraint) gk);
			else
				throw new IllegalStateException("The only supported ConstraintKernels are DomainRangeConstraintKernels.");
		}
		
		/* Collects the free RandomVariableAtoms that remain */
		Set<RandomVariableAtom> freeRVSet = new HashSet<RandomVariableAtom>();
		for (GroundKernel gk : getGroundKernels()) {
			for (GroundAtom atom : gk.getAtoms()) {
				if (atom instanceof RandomVariableAtom) {
					int numConstraints = 0;
					for (GroundKernel incidentGK : atom.getRegisteredGroundKernels())
						if (incidentGK instanceof GroundConstraintKernel)
							numConstraints++;
					if (numConstraints == 0)
						freeRVSet.add(((RandomVariableAtom) atom));
					else if (numConstraints >= 2)
						throw new IllegalStateException("RandomVariableAtoms may only participate in one GroundDomainRangeConstraint.");
				}
			}
		}
		
		int i;
		
		/* Puts RandomVariableAtoms in 2d array by block */
		RandomVariableAtom[][] rvBlocks = new RandomVariableAtom[constraintSet.size() + freeRVSet.size()][];
		/* If true, exactly one Atom in the RV block must be 1.0. If false, at most one can. */
		boolean[] exactlyOne = new boolean[rvBlocks.length];
		
		/* Processes constrained RVs first */
		Set<RandomVariableAtom> constrainedRVSet = new HashSet<RandomVariableAtom>();
		boolean varsAreFree; /* False means that an ObservedAtom is 1.0, forcing others to 0.0 */
		i = 0;
		for (GroundDomainRangeConstraint con : constraintSet) {
			constrainedRVSet.clear();
			varsAreFree = true;
			for (GroundAtom atom : con.getAtoms())
				if (atom instanceof ObservedAtom && atom.getValue() != 0.0)
					varsAreFree = false;
				else if (atom instanceof RandomVariableAtom)
					constrainedRVSet.add((RandomVariableAtom) atom);
			
			if (varsAreFree) {
				rvBlocks[i] = new RandomVariableAtom[constrainedRVSet.size()];
				int j = 0;
				for (RandomVariableAtom atom : constrainedRVSet)
					rvBlocks[i][j++] = atom;
				
				exactlyOne[i] = con.getConstraintDefinition().getComparator().equals(FunctionComparator.Equality);
			}
			else {
				rvBlocks[i] = new RandomVariableAtom[0];
				/*
				 * Sets to true regardless of constraint type to avoid extra steps
				 * that would not work on empty blocks 
				 */
				exactlyOne[i] = true;
			}
			
			i++;
		}
		
		/* Processes free RVs second */
		for (RandomVariableAtom atom : freeRVSet) {
			rvBlocks[i] = new RandomVariableAtom[] {atom};
			exactlyOne[i] = false;
			i++;
		}
		
		/* Collects GroundCompatibilityKernels incident on each block of RandomVariableAtoms */
		GroundCompatibilityKernel[][] incidentGKs = new GroundCompatibilityKernel[rvBlocks.length][];
		Set<GroundCompatibilityKernel> incidentGKSet = new HashSet<GroundCompatibilityKernel>();
		for (i = 0; i < rvBlocks.length; i++) {
			incidentGKSet.clear();
			for (RandomVariableAtom atom : rvBlocks[i])
				for (GroundKernel incidentGK : atom.getRegisteredGroundKernels())
					if (incidentGK instanceof GroundCompatibilityKernel)
						incidentGKSet.add((GroundCompatibilityKernel) incidentGK);
			
			incidentGKs[i] = new GroundCompatibilityKernel[incidentGKSet.size()];
			int j = 0;
			for (GroundCompatibilityKernel incidentGK : incidentGKSet)
				incidentGKs[i][j++] = incidentGK;
		}
		
		/* Initializes arrays for totaling samples */
		double[][] totals = new double[rvBlocks.length][];
		for (i = 0; i < rvBlocks.length; i++)
			totals[i] = new double[rvBlocks[i].length];
		
		/* Samples RV assignments */
		double[] p;
		for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
			for (i = 0; i < rvBlocks.length; i++) {
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
		
		/* Sets truth values of RandomVariableAtoms to marginal probabilities */
		for (i = 0; i < rvBlocks.length; i++)
			for (int j = 0; j < rvBlocks[i].length; j++)
				rvBlocks[i][j].setValue(totals[i][j] / (numSamples - numBurnIn));
	}
	
	private double computeProbability(GroundCompatibilityKernel incidentGKs[]) {
		double probability = 0.0;
		for (GroundCompatibilityKernel gk : incidentGKs)
			probability += ((GroundCompatibilityKernel) gk).getWeight().getWeight()
			* ((GroundCompatibilityKernel) gk).getIncompatibility();
		return probability;
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
