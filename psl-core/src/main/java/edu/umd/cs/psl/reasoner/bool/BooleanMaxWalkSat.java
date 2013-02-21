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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;

/**
 * Implementation of MaxWalkSat, which searches for a good Boolean assignment
 * of truth values.
 * <p>
 * See "A General Stochastic Approach to Solving Problems with Hard and Soft
 * Constraints," in The Satisfiability Problem: Theory and Applications (1997),
 * pp. 573-586 by Henry Kautz, Bart Selman, Yueyen Jiang.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BooleanMaxWalkSat extends MemoryGroundKernelStore implements Reasoner {
	
	private static final Logger log = LoggerFactory.getLogger(ADMMReasoner.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "booleanmaxwalksat";
	
	/**
	 * Key for positive integer property that is the maximum number of flips
	 * to try during optimization
	 */
	public static final String MAX_FLIPS_KEY = CONFIG_PREFIX + ".maxflips";
	/** Default value for MAX_FLIPS_KEY */
	public static final int MAX_FLIPS_DEFAULT = 100000;
	
	/**
	 * Key for double property in [0,1] that is the probability of randomly
	 * perturbing an atom in a randomly chosen potential
	 */
	public static final String NOISE_KEY = CONFIG_PREFIX + ".noise";
	/** Default value for NOISE_KEY */
	public static final double NOISE_DEFAULT = 0.1;
	
	private Random rand;
	private final int maxFlips;
	private final double noise;
	
	public BooleanMaxWalkSat(ConfigBundle config) {
		super();
		rand = new Random();
		maxFlips = config.getInt(MAX_FLIPS_KEY, MAX_FLIPS_DEFAULT);
		if (maxFlips <= 0 )
			throw new IllegalArgumentException("Max flips must be positive.");
		noise = config.getDouble(NOISE_KEY, NOISE_DEFAULT);
		if (noise < 0.0 || noise > 1.0)
			throw new IllegalArgumentException("Noise must be in [0,1].");
	}
	
	@Override
	public void optimize() {
		List<GroundCompatibilityKernel> unsatGKs = new ArrayList<GroundCompatibilityKernel>();
		List<RandomVariableAtom> rvAtoms = new ArrayList<RandomVariableAtom>();
		RandomVariableAtom atomToFlip;
		double currentTruthValue, newTruthValue;
		double currentIncompatibility, newIncompatibility;
		double bestIncompatibility;
		
		/* Finds initially unsatisfied GroundCompatibilityKernels */
		for (GroundCompatibilityKernel gk : getCompatibilityKernels())
			if (gk.getIncompatibility() > 0.0)
				unsatGKs.add(gk);
		
		/* Flips some variables */
		for (int flip = 0; flip < maxFlips; flip++) {
			GroundCompatibilityKernel gck = unsatGKs.get(rand.nextInt(unsatGKs.size()));
			
			/* Collects the RandomVariableAtoms in gk */
			rvAtoms.clear();
			for (GroundAtom atom : gck.getAtoms())
				if (atom instanceof RandomVariableAtom)
					rvAtoms.add((RandomVariableAtom) atom);
			
			/* With probability noise, flips a variable in gk at random */
			if (rand.nextDouble() <= noise) {
				atomToFlip = rvAtoms.get(rand.nextInt(rvAtoms.size()));
			}
			/* With probability 1 - noise, makes the best flip of a variable in gk */
			else {
				atomToFlip = null;
				bestIncompatibility = Double.POSITIVE_INFINITY;
				
				/* Considers each candidate for flipping */
				for (RandomVariableAtom candidateAtom : rvAtoms) {
					/*
					 * Evaluates the incompatibility of currently satisfied GCKs under a hypothetical flip
					 */
					flipAtom(candidateAtom);
					currentIncompatibility = 0.0;
					for (GroundKernel gk : candidateAtom.getRegisteredGroundKernels()) {
						if (unsatGKs.contains(gk) && ((GroundCompatibilityKernel) gk).getIncompatibility() > 0.0) {
							currentIncompatibility += ((GroundCompatibilityKernel) gk).getWeight().getWeight();
						}
					}
					flipAtom(candidateAtom);
					
					if (currentIncompatibility < bestIncompatibility)
						atomToFlip = candidateAtom;
				}
			}
			
			/* Computes change to set of unsatisfied GroundCompatibilityKernels */
			for (GroundKernel gk : atomToFlip.getRegisteredGroundKernels()) {
				if (gk instanceof GroundCompatibilityKernel) {
					currentIncompatibility = ((GroundCompatibilityKernel) gk).getIncompatibility();
					flipAtom(atomToFlip);
					newIncompatibility = ((GroundCompatibilityKernel) gk).getIncompatibility();
					flipAtom(atomToFlip);
					
					if (currentIncompatibility == 0.0 && newIncompatibility > 0.0)
						unsatGKs.add((GroundCompatibilityKernel) gk);
					else if (currentIncompatibility > 0.0 && newIncompatibility == 0.0)
						unsatGKs.remove(gk);
				}
			}
			
			flipAtom(atomToFlip);
			
			/* Just in case... */
			if (unsatGKs.size() == 0)
				return;
		}
	}
	
	private void flipAtom(RandomVariableAtom atom) {
		atom.setValue((atom.getValue() == 1.0) ? 0.0 : 1.0);
	}

	@Override
	public void close() {
		/* Intentionally blank */
	}

}
