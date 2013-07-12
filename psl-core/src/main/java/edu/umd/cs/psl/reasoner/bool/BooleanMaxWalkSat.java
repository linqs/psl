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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore;
import edu.umd.cs.psl.application.util.GroundKernels;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.predicateconstraint.GroundDomainRangeConstraint;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.util.model.ConstraintBlocker;

/**
 * Implementation of MaxWalkSat, which searches for a good Boolean assignment
 * of truth values.
 * <p>
 * See "A General Stochastic Approach to Solving Problems with Hard and Soft
 * Constraints," in The Satisfiability Problem: Theory and Applications (1997),
 * pp. 573-586 by Henry Kautz, Bart Selman, Yueyen Jiang.
 * <p>
 * Supports free {@link RandomVariableAtom RandomVariableAtoms}
 * and RandomVariableAtoms that are each constrained by a single
 * {@link GroundDomainRangeConstraint}.
 * <p>
 * It also assumes that all ObservedAtoms have values in {0.0, 1.0}.
 * Its behavior is not defined otherwise.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BooleanMaxWalkSat extends MemoryGroundKernelStore implements Reasoner {
	
	private static final Logger log = LoggerFactory.getLogger(BooleanMaxWalkSat.class);
	
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
	public static final int MAX_FLIPS_DEFAULT = 50000;
	
	/**
	 * Key for double property in [0,1] that is the probability of randomly
	 * perturbing an atom in a randomly chosen potential
	 */
	public static final String NOISE_KEY = CONFIG_PREFIX + ".noise";
	/** Default value for NOISE_KEY */
	public static final double NOISE_DEFAULT = (double) 1 / 100;
	
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
		ConstraintBlocker blocker = new ConstraintBlocker(this);
		blocker.prepareBlocks(true);
		
		/* Puts RandomVariableAtoms in 2d array by block */
		RandomVariableAtom[][] rvBlocks = blocker.getRVBlocks();
		/* If true, exactly one Atom in the RV block must be 1.0. If false, at most one can. */
		boolean[] exactlyOne = blocker.getExactlyOne();
		/* Collects GroundCompatibilityKernels incident on each block of RandomVariableAtoms */
		GroundCompatibilityKernel[][] incidentGKs = blocker.getIncidentGKs();
		/* Maps RandomVariableAtoms to their block index */
		Map<RandomVariableAtom, Integer> rvMap = blocker.getRVMap();
		
		/* Randomly initializes the RVs to a feasible state */
		blocker.randomlyInitializeRVs();
		
		Set<GroundKernel> unsatGKs = new HashSet<GroundKernel>();
		Set<RandomVariableAtom> rvsToInclude = new HashSet<RandomVariableAtom>();
		Set<Integer> blocksToInclude = new HashSet<Integer>(rvsToInclude.size());
		RandomVariableAtom[][] candidateRVBlocks;
		GroundCompatibilityKernel[][] candidateIncidentGKs;
		boolean[] candidateExactlyOne;
		double currentIncompatibility;
		double bestIncompatibility;
		int changeBlock;
		int newBlockSetting;
		
		/* Finds initially unsatisfied GroundKernels */
		for (GroundKernel gk : getGroundKernels())
			if (gk instanceof GroundCompatibilityKernel && ((GroundCompatibilityKernel) gk).getIncompatibility() > 0.0)
				unsatGKs.add(gk);
		
		/* Changes some RV blocks */
		for (int flip = 0; flip < maxFlips; flip++) {
			GroundKernel gk = (GroundKernel) selectAtRandom(unsatGKs);
			
			/* Collects the RV blocks with at least one RV in gk */
			rvsToInclude.clear();
			blocksToInclude.clear();
			for (GroundAtom atom : gk.getAtoms()) {
				if (atom instanceof RandomVariableAtom) {
					Integer blockIndex = rvMap.get(atom);
					/* Ignore RVs forced to 0.0 */
					if (blockIndex != null)
						rvsToInclude.add((RandomVariableAtom) atom);
				}
			}
			for (RandomVariableAtom atom : rvsToInclude)
				blocksToInclude.add(rvMap.get(atom));
			candidateRVBlocks = new RandomVariableAtom[blocksToInclude.size()][];
			candidateIncidentGKs = new GroundCompatibilityKernel[blocksToInclude.size()][];
			candidateExactlyOne = new boolean[blocksToInclude.size()];
			int i = 0;
			for (Integer blockIndex : blocksToInclude) {
				candidateRVBlocks[i] = rvBlocks[blockIndex];
				candidateExactlyOne[i] = exactlyOne[blockIndex];
				candidateIncidentGKs[i++] = incidentGKs[blockIndex];
			}
			
			if (candidateRVBlocks.length == 0) {
				flip--;
				continue;
			}
			
			/* With probability noise, changes an RV block in gk at random */
			if (rand.nextDouble() <= noise) {
				changeBlock = rand.nextInt(candidateRVBlocks.length);
				int blockSize = candidateRVBlocks[changeBlock].length;
								
				do {
					newBlockSetting = rand.nextInt(blockSize);
				}
				while (candidateExactlyOne[changeBlock] && candidateRVBlocks[changeBlock][newBlockSetting].getValue() == 1.0);
				
				/* 
				 * If the random setting is the current setting, but all 0.0 is also valid,
				 * switches to that
				 */
				if (candidateRVBlocks[changeBlock][newBlockSetting].getValue() == 1.0)
					newBlockSetting = candidateRVBlocks[changeBlock].length;
			}
			/* With probability 1 - noise, makes the best change to an RV block in gk */
			else {
				changeBlock = 0;
				newBlockSetting = 0;
				bestIncompatibility = Double.POSITIVE_INFINITY;
				double[] currentState;
				double currentStateTotal;
				
				/* Considers each block */
				for (int iBlock = 0; iBlock < candidateRVBlocks.length; iBlock++) {
					/* Saves current state of block */
					currentState = new double[candidateRVBlocks[iBlock].length];
					currentStateTotal = 0.0;
					for (int iRV = 0 ; iRV < candidateRVBlocks[iBlock].length; iRV++) {
						currentState[iRV] = candidateRVBlocks[iBlock][iRV].getValue();
						currentStateTotal += currentState[iRV];
					}
					
					/* Considers each setting to the block */
					int lastRVIndex = candidateRVBlocks[iBlock].length;
					/* If all 0.0 is a valid assignment and not the current one, tries that too */
					if (!candidateExactlyOne[iBlock] && currentStateTotal > 0.0)
						lastRVIndex++;
					for (int iSetRV = 0; iSetRV < lastRVIndex; iSetRV++) {
						/* Only considers this setting if it is not the current setting*/
						if (iSetRV == candidateRVBlocks[iBlock].length || currentState[iSetRV] != 1.0) {
							/* Changes to the current setting to consider */
							for (int iChangeRV = 0; iChangeRV < candidateRVBlocks[iBlock].length; iChangeRV++)
								candidateRVBlocks[iBlock][iChangeRV].setValue((iChangeRV == iSetRV) ? 1.0 : 0.0);
							
							/* Computes weighted incompatibility */
							currentIncompatibility = 0.0;
							for (GroundCompatibilityKernel incidentGK : candidateIncidentGKs[iBlock]) {
								if (!unsatGKs.contains(incidentGK)) {
									if (incidentGK.getIncompatibility() > 0.0)
										currentIncompatibility += ((GroundCompatibilityKernel) incidentGK).getWeight().getWeight() * ((GroundCompatibilityKernel) incidentGK).getIncompatibility();
								}
							}
							
							if (currentIncompatibility < bestIncompatibility) {
								bestIncompatibility = currentIncompatibility;
								changeBlock = iBlock;
								newBlockSetting = iSetRV;
							}
						}
					}
					
					/* Restores current state */
					for (int iRV = 0 ; iRV < candidateRVBlocks[iBlock].length; iRV++)
						candidateRVBlocks[iBlock][iRV].setValue(currentState[iRV]);
				}
			}
			
			/* Changes assignment to RV block */
			for (int iChangeRV = 0; iChangeRV < candidateRVBlocks[changeBlock].length; iChangeRV++)
				candidateRVBlocks[changeBlock][iChangeRV].setValue((iChangeRV == newBlockSetting) ? 1.0 : 0.0);
			
			/* Computes change to set of unsatisfied GroundCompatibilityKernels */
			for (GroundCompatibilityKernel incidentGK : candidateIncidentGKs[changeBlock])
				if (incidentGK.getIncompatibility() > 0.0)
					unsatGKs.add(incidentGK);
				else
					unsatGKs.remove(incidentGK);
			
			/* Just in case... */
			if (unsatGKs.size() == 0)
				return;
			
			if (flip == 0 || (flip+1) % 5000 == 0) {
				log.info("Total weighted incompatibility: {}, Infeasbility norm: {}",
						GroundKernels.getTotalWeightedIncompatibility(getCompatibilityKernels()),
						GroundKernels.getInfeasibilityNorm(getConstraintKernels()));
			}
		}
	}
	
	private Object selectAtRandom(Collection<? extends Object> collection) {
		int i = 0;
		int selection = rand.nextInt(collection.size());
		for (Object o : collection)
			if (i++ == selection)
				return o;
		
		return null;
	}

	@Override
	public void close() {
		/* Intentionally blank */
	}

}
