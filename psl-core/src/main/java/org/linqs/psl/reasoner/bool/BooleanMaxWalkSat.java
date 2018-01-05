/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.reasoner.bool;

import org.linqs.psl.application.groundrulestore.AtomRegisterGroundRuleStore;
import org.linqs.psl.application.util.GroundRules;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.inspector.ReasonerInspector;
import org.linqs.psl.reasoner.term.ConstraintBlockerTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Implementation of MaxWalkSat, which searches for a good Boolean assignment
 * of truth values.
 *
 * See "A General Stochastic Approach to Solving Problems with Hard and Soft
 * Constraints," in The Satisfiability Problem: Theory and Applications (1997),
 * pp. 573-586 by Henry Kautz, Bart Selman, Yueyen Jiang.
 *
 * Supports free {@link RandomVariableAtom RandomVariableAtoms}
 * and RandomVariableAtoms that are each constrained by a single GroundValueConstraint.
 *
 * This differs from the classical MaxWalkSat because instead of just choosing a random
 * ground rule to modify an atom in, this will choose a random ground rule
 * and then a random block associated with that random rule.
 * This will keep the solution feasible because of the semantics of the constraint blocker.
 * Classical MaxWalkSat can possibly become infeasible.
 *
 * It also assumes that all ObservedAtoms have values in {0.0, 1.0}.
 * Its behavior is not defined otherwise.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BooleanMaxWalkSat extends Reasoner {
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

	/**
	 * Default value for MAX_FLIPS_KEY
	 */
	public static final int MAX_FLIPS_DEFAULT = 50000;

	/**
	 * Key for double property in [0,1] that is the probability of randomly
	 * perturbing an atom in a randomly chosen potential
	 */
	public static final String NOISE_KEY = CONFIG_PREFIX + ".noise";

	/**
	 * Default value for NOISE_KEY
	 */
	public static final double NOISE_DEFAULT = 0.01;

	private Random rand;
	private final int maxFlips;
	private final double noise;

	public BooleanMaxWalkSat(ConfigBundle config) {
		super(config);

		rand = new Random();

		maxFlips = config.getInt(MAX_FLIPS_KEY, MAX_FLIPS_DEFAULT);
		if (maxFlips <= 0 ) {
			throw new IllegalArgumentException("Max flips must be positive.");
		}

		noise = config.getDouble(NOISE_KEY, NOISE_DEFAULT);
		if (noise < 0.0 || noise > 1.0) {
			throw new IllegalArgumentException("Noise must be in [0,1].");
		}
	}

	@Override
	public void optimize(TermStore termStore) {
		if (!(termStore instanceof ConstraintBlockerTermStore)) {
			throw new IllegalArgumentException("ConstraintBlockerTermStore required.");
		}
		ConstraintBlockerTermStore blocker = (ConstraintBlockerTermStore)termStore;

		// Randomly initializes the RVs to a feasible state.
		blocker.randomlyInitializeRVs();

		// Puts RandomVariableAtoms in 2d array by block.
		RandomVariableAtom[][] rvBlocks = blocker.getRVBlocks();

		// If true, exactly one Atom in the RV block must be 1.0. If false, at most one can.
		boolean[] exactlyOne = blocker.getExactlyOne();

		// Collects GroundCompatibilityRules incident on each block of RandomVariableAtoms
		WeightedGroundRule[][] incidentGKs = blocker.getIncidentGKs();

		// Maps RandomVariableAtoms to their block index
		Map<RandomVariableAtom, Integer> rvMap = blocker.getRVMap();

		Set<GroundRule> unsatGKs = new HashSet<GroundRule>();
		Set<RandomVariableAtom> rvasToInclude = new HashSet<RandomVariableAtom>();
		Set<Integer> blocksToInclude = new HashSet<Integer>(rvasToInclude.size());

		RandomVariableAtom[][] candidateRVBlocks;
		WeightedGroundRule[][] candidateIncidentGKs;
		boolean[] candidateExactlyOne;

		// The block that will will randomly flip a variable in.
		int blockToChange;
		// The index of the RVA in the block to become positive.
		// All other RVAs in the block will be set to zero.
		// If we want all RVAs in the block to be zero, then set to -1.
		int positiveRVAIndex;

		// Finds initially unsatisfied GroundRules.
		for (GroundRule groundRule : blocker.getGroundRuleStore().getGroundRules()) {
			if (groundRule instanceof WeightedGroundRule && ((WeightedGroundRule) groundRule).getIncompatibility() > 0.0) {
				unsatGKs.add(groundRule);
			}
		}

		// Changes some RV blocks.
		for (int flip = 0; flip < maxFlips; flip++) {
			// Just in case...
			if (unsatGKs.size() == 0) {
				return;
			}

			GroundRule groundRule = (GroundRule)selectAtRandom(unsatGKs);

			// Collect all the RV blocks that have some RVA in common with the randomly selected groundRule.
			rvasToInclude.clear();
			blocksToInclude.clear();

			for (GroundAtom atom : groundRule.getAtoms()) {
				if (!(atom instanceof RandomVariableAtom)) {
					continue;
				}

				Integer blockIndex = rvMap.get(atom);

				// RVAs with no block are constrained and cannot be changed without breaking a hard constraint.
				if (blockIndex != null) {
					rvasToInclude.add((RandomVariableAtom)atom);
					blocksToInclude.add(blockIndex);
				}
			}

			// Restart this flip if we choose a ground rule that has no unconstrained RVAs.
			if (blocksToInclude.size() == 0) {
				flip--;
				continue;
			}

			// TODO(eriq): Don't allocate. Make a single ArrayList outside of loop. clear() here.
			candidateRVBlocks = new RandomVariableAtom[blocksToInclude.size()][];
			candidateIncidentGKs = new WeightedGroundRule[blocksToInclude.size()][];
			candidateExactlyOne = new boolean[blocksToInclude.size()];

			int candidateBlockIndex = 0;
			for (Integer blockIndex : blocksToInclude) {
				candidateRVBlocks[candidateBlockIndex] = rvBlocks[blockIndex];
				candidateExactlyOne[candidateBlockIndex] = exactlyOne[blockIndex];
				candidateIncidentGKs[candidateBlockIndex] = incidentGKs[blockIndex];
				candidateBlockIndex++;
			}

			// With probability noise, change an RV block in groundRule at random.
			if (rand.nextDouble() <= noise) {
				blockToChange = rand.nextInt(candidateRVBlocks.length);
				int blockSize = candidateRVBlocks[blockToChange].length;

				// Choose a random RVA in this block to flip on.
				// If one value in this block must be one, then keep going until we pick an atom that is
				// currently not active.
				do {
					positiveRVAIndex = rand.nextInt(blockSize);
				} while (candidateExactlyOne[blockToChange] && candidateRVBlocks[blockToChange][positiveRVAIndex].getValue() == 1.0);

				// If we want to flip an active RVA (value == 1.0), then set the target index to -1.
				if (candidateRVBlocks[blockToChange][positiveRVAIndex].getValue() == 1.0) {
					positiveRVAIndex = -1;
				}
			} else {
				// With probability (1 - noise), make the best change to an RV block in the selected ground rule.

				blockToChange = -1;
				positiveRVAIndex = -1;
				double bestIncompatibility = Double.POSITIVE_INFINITY;
				double[] savedState;
				double savedStateTotal;

				// Consider each block.
				for (int blockIndex = 0; blockIndex < candidateRVBlocks.length; blockIndex++) {
					// Save the current state of the block.
					savedState = new double[candidateRVBlocks[blockIndex].length];
					savedStateTotal = 0.0;
					for (int i = 0; i < candidateRVBlocks[blockIndex].length; i++) {
						savedState[i] = candidateRVBlocks[blockIndex][i].getValue();
						savedStateTotal += savedState[i];
					}

					// Consider each setting to the block.
					int lastRVIndex = candidateRVBlocks[blockIndex].length;

					// If all 0.0 is a valid assignment (and the block is not currently all zeroes),
					// then try that setting as well by moving the last index past the end of the block.
					if (!candidateExactlyOne[blockIndex]) {
						lastRVIndex++;
					}

					// Be aware that this incrementer may go one past the end of the block.
					for (int currentPositiveRVA = 0; currentPositiveRVA < lastRVIndex; currentPositiveRVA++) {
						// We will check the current (saved) configuration as well.

						// Change to the current setting to consider.
						for (int i = 0; i < candidateRVBlocks[blockIndex].length; i++) {
							if (i == currentPositiveRVA) {
								candidateRVBlocks[blockIndex][i].setValue(1.0);
							} else {
								candidateRVBlocks[blockIndex][i].setValue(0.0);
							}
						}

						// Computes weighted incompatibility.
						double currentIncompatibility = 0.0;
						for (WeightedGroundRule incidentGK : candidateIncidentGKs[blockIndex]) {
							currentIncompatibility += incidentGK.getWeight() * incidentGK.getIncompatibility();
						}

						if (currentIncompatibility < bestIncompatibility) {
							bestIncompatibility = currentIncompatibility;
							blockToChange = blockIndex;
							positiveRVAIndex = currentPositiveRVA;

							// Break out early if we can't do better.
							if (MathUtils.isZero(bestIncompatibility)) {
								break;
							}
						}
					}

					// Restore the saved state.
					for (int i = 0 ; i < candidateRVBlocks[blockIndex].length; i++) {
						candidateRVBlocks[blockIndex][i].setValue(savedState[i]);
					}

					// Break out early if we can't do better.
					if (MathUtils.isZero(bestIncompatibility)) {
						break;
					}
				}
			}

			// Update with block with the decided change.
			for (int i = 0; i < candidateRVBlocks[blockToChange].length; i++) {
				if (i == positiveRVAIndex) {
					candidateRVBlocks[blockToChange][i].setValue(1.0);
				} else {
					candidateRVBlocks[blockToChange][i].setValue(0.0);
				}
			}

			// Add/Remove unsatisfied/satisfied weighted ground rules.
			for (WeightedGroundRule incidentGK : candidateIncidentGKs[blockToChange]) {
				if (incidentGK.getIncompatibility() > 0.0) {
					unsatGKs.add(incidentGK);
				} else {
					unsatGKs.remove(incidentGK);
				}
			}

			if (flip % 5000 == 0) {
				log.info("Flip {}, Total weighted incompatibility: {}, Infeasbility norm: {}", flip,
						GroundRules.getTotalWeightedIncompatibility(blocker.getGroundRuleStore().getCompatibilityRules()),
						GroundRules.getInfeasibilityNorm(blocker.getGroundRuleStore().getConstraintRules()));
			}

			if (inspector != null) {
				double incompatibility = GroundRules.getTotalWeightedIncompatibility(blocker.getGroundRuleStore().getCompatibilityRules());
				double infeasbility = GroundRules.getInfeasibilityNorm(blocker.getGroundRuleStore().getConstraintRules());

				if (!inspector.update(this, new MaxWalkSatStatus(flip, incompatibility, infeasbility))) {
					log.info("Stopping MaxWalkSat iterations on advice from inspector");
					break;
				}
			}
		}
	}

	private Object selectAtRandom(Collection<? extends Object> collection) {
		int i = 0;
		int selection = rand.nextInt(collection.size());

		for (Object o : collection) {
			if (i++ == selection) {
				return o;
			}
		}

		return null;
	}

	@Override
	public void close() {
		// Intentionally blank
	}

	private static class MaxWalkSatStatus extends ReasonerInspector.IterativeReasonerStatus {
		public double incompatibility;
		public double infeasbility;

		public MaxWalkSatStatus(int iteration, double incompatibility, double infeasbility) {
			super(iteration);

			this.incompatibility = incompatibility;
			this.infeasbility = infeasbility;
		}

		@Override
		public String toString() {
			return String.format("%s, incompatibility: %f, infeasbility: %f",
					super.toString(), incompatibility, infeasbility);
		}
	}
}
