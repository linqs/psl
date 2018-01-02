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
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.inspector.ReasonerInspector;
import org.linqs.psl.reasoner.term.ConstraintBlockerTermStore;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Implementation of MC-Sat, which approximates the marginal probability that each
 * atom has value 1 in a Boolean domain.
 *
 * Marginal probabilities will be set as the atoms' truth values.
 *
 * This class supports free {@link RandomVariableAtom RandomVariableAtoms}
 * and RandomVariableAtoms that are each constrained by a single
 * GroundValueConstraint. It also assumes that all ObservedAtoms
 * have Boolean truth values. Its behavior is not defined otherwise.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BooleanMCSat extends Reasoner {
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

	/**
	 * Default value for NUM_SAMPLES_KEY
	 */
	public static final int NUM_SAMPLES_DEFAULT = 2500;

	/**
	 * Number of burn-in samples
	 */
	public static final String NUM_BURN_IN_KEY = CONFIG_PREFIX + ".numburnin";

	/**
	 * Default value for NUM_BURN_IN_KEY
	 */
	public static final int NUM_BURN_IN_DEFAULT = 500;

	private final Random rand;
	private final int numSamples;
	private final int numBurnIn;

	public BooleanMCSat(ConfigBundle config) {
		super(config);

		rand = new Random();

		numSamples = config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
		if (numSamples <= 0) {
			throw new IllegalArgumentException("Number of samples must be positive.");
		}

		numBurnIn = config.getInt(NUM_BURN_IN_KEY, NUM_BURN_IN_DEFAULT);
		if (numSamples <= 0) {
			throw new IllegalArgumentException("Number of burn in samples must be positive.");
		} else if (numBurnIn >= numSamples) {
			throw new IllegalArgumentException("Number of burn in samples must be less than number of samples.");
		}
	}

	@Override
	public void optimize(TermStore termStore) {
		if (!(termStore instanceof ConstraintBlockerTermStore)) {
			throw new IllegalArgumentException("ConstraintBlockerTermStore required.");
		}
		ConstraintBlockerTermStore blocker = (ConstraintBlockerTermStore)termStore;

		// Randomly initialize the RVs to a feasible state.
		blocker.randomlyInitializeRVs();

		// Put RandomVariableAtoms in 2d array by block.
		RandomVariableAtom[][] rvBlocks = blocker.getRVBlocks();

		// If true, exactly one Atom in the RV block must be 1.0. If false, at most one can.
		boolean[] exactlyOne = blocker.getExactlyOne();

		// Collect GroundCompatibilityRules incident on each block of RandomVariableAtoms
		WeightedGroundRule[][] incidentGKs = blocker.getIncidentGKs();

		// Initialize arrays for totaling samples
		double[][] totals = blocker.getEmptyDouble2DArray();

		log.info("Beginning inference.");

		// Sample RV assignments
		for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
			for (int blockIndex = 0; blockIndex < rvBlocks.length; blockIndex++) {
				if (rvBlocks[blockIndex].length == 0) {
					continue;
				}

				// Compute the probability for every possible discrete assignment to the block.
				// The additional spot at the end is for the all zero assignment (when !exactlyOne[blockIndex]).
				double[] probabilities = new double[(exactlyOne[blockIndex]) ? rvBlocks[blockIndex].length : (rvBlocks[blockIndex].length + 1)];

				// Compute the probability for each possible assignment to the block.
				// Remember that at most 1 atom in a block can be non-zero.
				// If all zeros are allowed, then atomIndex will be past the bounds of the block and
				// no atoms will get activated.
				for (int atomIndex = 0; atomIndex < probabilities.length; atomIndex++) {
					for (int i = 0; i < rvBlocks[blockIndex].length; i++) {
						if (i == atomIndex) {
							rvBlocks[blockIndex][i].setValue(1.0);
						} else {
							rvBlocks[blockIndex][i].setValue(0.0);
						}
					}

					// Compute the probability.
					probabilities[atomIndex] = computeProbability(incidentGKs[blockIndex]);
				}

				// Draw sample.
				double[] sample = sampleWithProbability(probabilities);
				for (int atomIndex = 0; atomIndex < rvBlocks[blockIndex].length; atomIndex++) {
					rvBlocks[blockIndex][atomIndex].setValue(sample[atomIndex]);

					if (sampleIndex >= numBurnIn) {
						totals[blockIndex][atomIndex] += sample[atomIndex];
					}
				}
			}

			// Skip the burn in.
			if (inspector != null && sampleIndex >= numBurnIn) {
				// Sets truth values of RandomVariableAtoms to marginal probabilities.
				for (int blockIndex = 0; blockIndex < rvBlocks.length; blockIndex++) {
					for (int atomIndex = 0; atomIndex < rvBlocks[blockIndex].length; atomIndex++) {
						rvBlocks[blockIndex][atomIndex].setValue(totals[blockIndex][atomIndex] / (numSamples - numBurnIn));
					}
				}

				double incompatibility = GroundRules.getTotalWeightedIncompatibility(blocker.getGroundRuleStore().getCompatibilityRules());
				double infeasbility = GroundRules.getInfeasibilityNorm(blocker.getGroundRuleStore().getConstraintRules());

				if (!inspector.update(this, new MCSatStatus(sampleIndex, incompatibility, infeasbility))) {
					log.info("Stopping MCSat iterations on advice from inspector");
					break;
				}
			}
		}

		log.info("Inference complete.");

		// Sets truth values of RandomVariableAtoms to marginal probabilities.
		for (int blockIndex = 0; blockIndex < rvBlocks.length; blockIndex++) {
			for (int atomIndex = 0; atomIndex < rvBlocks[blockIndex].length; atomIndex++) {
				rvBlocks[blockIndex][atomIndex].setValue(totals[blockIndex][atomIndex] / (numSamples - numBurnIn));
			}
		}
	}

	private double computeProbability(WeightedGroundRule incidentGKs[]) {
		double probability = 0.0;

		for (WeightedGroundRule groundRule : incidentGKs) {
			probability += groundRule.getWeight() * groundRule.getIncompatibility();
		}

		return Math.exp(-1 * probability);
	}

	private double[] sampleWithProbability(double[] distribution) {
		// Normalize the distribution.
		double total = 0.0;
		for (double pValue : distribution) {
			total += pValue;
		}

		for (int i = 0; i < distribution.length; i++) {
			distribution[i] /= total;
		}

		// Draws sample.
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

		// Just in case a rounding error and a very high cutoff prevents the loop
		// from returning, return the last assignment.
		sample[sample.length - 1] = 1.0;
		return sample;
	}

	@Override
	public void close() {
		// Intentionally blank.
	}

	private static class MCSatStatus extends ReasonerInspector.IterativeReasonerStatus {
		public double incompatibility;
		public double infeasbility;

		public MCSatStatus(int iteration, double incompatibility, double infeasbility) {
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
