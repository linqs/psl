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
package org.linqs.psl.application.learning.weight.maxlikelihood;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.AtomRegisterGroundRuleStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTerm;
import org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTermGenerator;
import org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTermStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Learns weights by optimizing the pseudo-log-likelihood of the data using
 * the voted perceptron algorithm.
 *
 * This learning uses a ConstraintBlocker, which forces several conditions on the model.
 * See {@link org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTermGenerator ConstraintBlockerTermGenerator} for details on those restrictions.
 */
public class MaxPseudoLikelihood extends VotedPerceptron {
    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "maxspeudolikelihood";

    /**
     * Boolean property. If true, MaxPseudoLikelihood will treat RandomVariableAtoms
     * as boolean valued. Note that this restricts the types of contraints supported.
     */
    public static final String BOOLEAN_KEY = CONFIG_PREFIX + ".bool";
    /**
     * Default value for BOOLEAN_KEY
     */
    public static final boolean BOOLEAN_DEFAULT = false;

    /**
     * Key for positive integer property.
     * MaxPseudoLikelihood will sample this many values to approximate
     * the integrals in the marginal computation.
     */
    public static final String NUM_SAMPLES_KEY = CONFIG_PREFIX + ".numsamples";
    public static final int NUM_SAMPLES_DEFAULT = 10;

    /**
     * Key for positive double property.
     * Used as minimum width for bounds of integration.
     */
    public static final String MIN_WIDTH_KEY = CONFIG_PREFIX + ".minwidth";
    public static final double MIN_WIDTH_DEFAULT = 1e-2;

    private final boolean bool;
    private final double minWidth;

    private final int maxNumSamples;
    private int numSamples;

    public MaxPseudoLikelihood(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public MaxPseudoLikelihood(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        bool = Config.getBoolean(BOOLEAN_KEY, BOOLEAN_DEFAULT);

        maxNumSamples = Config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
        numSamples = maxNumSamples;
        if (numSamples <= 0) {
            throw new IllegalArgumentException("Number of samples must be positive integer.");
        }

        minWidth = Config.getDouble(MIN_WIDTH_KEY, MIN_WIDTH_DEFAULT);
        if (minWidth <= 0) {
            throw new IllegalArgumentException("Minimum width must be positive double.");
        }

        // Force initGroundModel to use a constraint blocker.
        Config.setProperty(GROUND_RULE_STORE_KEY, AtomRegisterGroundRuleStore.class.getName());
        Config.setProperty(TERM_STORE_KEY, ConstraintBlockerTermStore.class.getName());
        Config.setProperty(TERM_GENERATOR_KEY, ConstraintBlockerTermGenerator.class.getName());
        cutObjective = false;
    }

    /**
     * Computes the expected incompatibility using the pseudolikelihood.
     * Uses Monte Carlo integration to approximate definite integrals,
     * since they do not admit a closed-form antiderivative.
     */
    @Override
    protected void computeExpectedIncompatibility() {
        if (!(termStore instanceof ConstraintBlockerTermStore)) {
            throw new IllegalArgumentException("ConstraintBlockerTermStore required.");
        }
        ConstraintBlockerTermStore blocker = (ConstraintBlockerTermStore)termStore;

        // Zero out the expected incompatibility first.
        for (int i = 0; i < expectedIncompatibility.length; i++) {
            expectedIncompatibility[i] = 0.0;
        }

        // Accumulate the expected incompatibility over all atoms.
        for (ConstraintBlockerTerm block : blocker) {
            if (block.size() == 0) {
                continue;
            }

            // Sample numSamples random numbers in the range of integration.
            double[][] samples;
            if (!bool) {
                samples = new double[Math.max(numSamples * block.size(), 150)][];
                SimplexSampler simplexSampler = new SimplexSampler();
                for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
                    samples[sampleIndex] = simplexSampler.getNext(samples.length);
                }
            } else {
                samples = new double[block.getExactlyOne() ? block.size() : block.size() + 1][];
                for (int iRV = 0; iRV < (block.getExactlyOne() ? samples.length : samples.length - 1); iRV++) {
                    samples[iRV] = new double[block.size()];
                    samples[iRV][iRV] = 1.0;
                }

                if (!block.getExactlyOne()) {
                    samples[samples.length - 1] = new double[block.size()];
                }
            }

            // Compute the incompatibility of each sample for each rule.
            HashMap<WeightedRule, double[]> incompatibilities = new HashMap<WeightedRule, double[]>();

            // Saves original state.
            float[] originalState = new float[block.size()];
            for (int i = 0; i < block.size(); i++) {
                originalState[i] = block.getAtoms()[i].getValue();
            }

            // Computes the probability.
            for (GroundRule groundRule : block.getIncidentGRs()) {
                if (!(groundRule instanceof WeightedGroundRule)) {
                    continue;
                }

                WeightedRule rule = (WeightedRule)groundRule.getRule();
                if (!incompatibilities.containsKey(rule)) {
                    incompatibilities.put(rule, new double[samples.length]);
                }

                double[] inc = incompatibilities.get(rule);
                for (int sampleIndex = 0; sampleIndex < samples.length; sampleIndex++) {
                    // Changes the state of the block to the next point.
                    for (int i = 0; i < block.size(); i++) {
                        block.getAtoms()[i].setValue((float)(samples[sampleIndex][i]));
                    }

                    inc[sampleIndex] += ((WeightedGroundRule) groundRule).getIncompatibility();
                }
            }

            // Remember to return the block to its original state!
            for (int i = 0; i < block.size(); i++) {
                block.getAtoms()[i].setValue(originalState[i]);
            }

            // Compute the exp incomp and accumulate the partition for the current atom.
            HashMap<WeightedRule, Double> expIncAtom = new HashMap<WeightedRule, Double>();
            double partition = 0.0;
            for (int j = 0; j < samples.length; j++) {
                // Compute the exponent.
                double sum = 0.0;
                for (Map.Entry<WeightedRule,double[]> e2 : incompatibilities.entrySet()) {
                    WeightedRule rule = e2.getKey();
                    double[] inc = e2.getValue();
                    sum -= rule.getWeight() * inc[j];
                }
                double exp = Math.exp(sum);

                // Add to partition.
                partition += exp;

                // Compute the exp incomp for current atom.
                for (Map.Entry<WeightedRule,double[]> e2 : incompatibilities.entrySet()) {
                    WeightedRule rule = e2.getKey();
                    if (!expIncAtom.containsKey(rule)) {
                        expIncAtom.put(rule, 0.0);
                    }
                    double val = expIncAtom.get(rule).doubleValue();
                    val += exp * incompatibilities.get(rule)[j];
                    expIncAtom.put(rule, val);
                }
            }

            // Finally, we add to the exp incomp for each rule.
            for (int i = 0; i < mutableRules.size(); i++) {
                WeightedRule rule = mutableRules.get(i);
                if (expIncAtom.containsKey(rule) && expIncAtom.get(rule) > 0.0) {
                    expectedIncompatibility[i] += expIncAtom.get(rule) / partition;
                }
            }
        }
    }

    @Override
    public void setBudget(double budget) {
        super.setBudget(budget);

        numSamples = (int)Math.ceil(budget * maxNumSamples);
    }
}
