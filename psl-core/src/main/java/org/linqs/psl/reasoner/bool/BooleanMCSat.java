/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.config.Config;
import org.linqs.psl.grounding.AtomRegisterGroundRuleStore;
import org.linqs.psl.grounding.GroundRules;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTerm;
import org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTermStore;
import org.linqs.psl.util.RandUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class BooleanMCSat implements Reasoner {
    private static final Logger log = LoggerFactory.getLogger(BooleanMCSat.class);

    /**
     * Prefix of property keys used by this class.
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

    private final int numSamples;
    private final int numBurnIn;

    public BooleanMCSat() {
        numSamples = Config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
        if (numSamples <= 0) {
            throw new IllegalArgumentException("Number of samples must be positive.");
        }

        numBurnIn = Config.getInt(NUM_BURN_IN_KEY, NUM_BURN_IN_DEFAULT);
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
        blocker.randomlyInitialize();

        // Initialize arrays for totaling samples
        double[][] totals = new double[blocker.size()][];
        for (int i = 0; i < blocker.size(); i++) {
            totals[i] = new double[blocker.get(i).size()];
        }

        log.info("Beginning inference.");

        // Sample RV assignments
        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            for (int blockIndex = 0; blockIndex < blocker.size(); blockIndex++) {
                ConstraintBlockerTerm block = blocker.get(blockIndex);

                if (block.size() == 0) {
                    continue;
                }

                // Compute the probability for every possible discrete assignment to the block.
                // The additional spot at the end is for the all zero assignment (when !exactlyOne).
                double[] probabilities = new double[block.getExactlyOne() ? block.size() : (block.size() + 1)];

                // Compute the probability for each possible assignment to the block.
                // Remember that at most 1 atom in a block can be non-zero.
                // If all zeros are allowed, then atomIndex will be past the bounds of the block and
                // no atoms will get activated.
                for (int atomIndex = 0; atomIndex < probabilities.length; atomIndex++) {
                    for (int i = 0; i < block.size(); i++) {
                        if (i == atomIndex) {
                            block.getAtoms()[i].setValue(1.0f);
                        } else {
                            block.getAtoms()[i].setValue(0.0f);
                        }
                    }

                    // Compute the probability.
                    probabilities[atomIndex] = computeProbability(block.getIncidentGRs());
                }

                // Draw sample.
                double[] sample = sampleWithProbability(probabilities);
                for (int atomIndex = 0; atomIndex < block.getAtoms().length; atomIndex++) {
                    block.getAtoms()[atomIndex].setValue((float)sample[atomIndex]);

                    if (sampleIndex >= numBurnIn) {
                        totals[blockIndex][atomIndex] += sample[atomIndex];
                    }
                }
            }
        }

        log.info("Inference complete.");

        // Sets truth values of RandomVariableAtoms to marginal probabilities.
        for (int blockIndex = 0; blockIndex < blocker.size(); blockIndex++) {
            for (int atomIndex = 0; atomIndex < blocker.get(blockIndex).size(); atomIndex++) {
                blocker.get(blockIndex).getAtoms()[atomIndex].setValue((float)(totals[blockIndex][atomIndex] / (numSamples - numBurnIn)));
            }
        }
    }

    private double computeProbability(WeightedGroundRule incidentGRs[]) {
        double probability = 0.0;

        for (WeightedGroundRule groundRule : incidentGRs) {
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
        double cutoff = RandUtils.nextDouble();

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
}
