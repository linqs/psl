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
package org.linqs.psl.application.learning.weight.em;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Abstract superclass for implementations of the expectation-maximization
 * algorithm for learning with latent variables.
 */
public abstract class ExpectationMaximization extends VotedPerceptron {
    private static final Logger log = LoggerFactory.getLogger(ExpectationMaximization.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "em";

    /**
     * Key for positive int property for the number of iterations of expectation
     * maximization to perform
     */
    public static final String ITER_KEY = CONFIG_PREFIX + ".iterations";
    public static final int ITER_DEFAULT = 10;

    /**
     * Key for positive double property for the minimum absolute change in weights
     * such that EM is considered converged
     */
    public static final String TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
    public static final double TOLERANCE_DEFAULT = 1e-3;

    protected final int iterations;
    protected final double tolerance;

    protected int emIteration;

    protected GroundRuleStore latentGroundRuleStore;
    protected TermStore latentTermStore;
    protected boolean inLatentMPEState;

    public ExpectationMaximization(List<Rule> rules, Database rvDB,
            Database observedDB) {
        super(rules, rvDB, observedDB);

        iterations = Config.getInt(ITER_KEY, ITER_DEFAULT);
        tolerance = Config.getDouble(TOLERANCE_KEY, TOLERANCE_DEFAULT);

        inLatentMPEState = false;
    }

    @Override
    protected void initGroundModel() {
        super.initGroundModel();
        initLatentGroundModel();
    }

    @Override
    protected void postInitGroundModel() {}

    /**
     * The same as initGroundModel, but for latent variables.
     * Must be called after initGroundModel().
     * Sets up a rule/term store stack meant for latent variables.
     * The reasoner and TermGenerator can be reused (as they don't hold state).
     * All non-latent variables (from the training map) will be pegged to their truth values.
     */
    protected void initLatentGroundModel() {
        latentGroundRuleStore = (GroundRuleStore)Config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
        latentTermStore = (TermStore)Config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);

        log.info("Grounding out latent model.");
        int groundCount = Grounding.groundAll(allRules, atomManager, latentGroundRuleStore);

        // Add in some constraints to peg the values of the non-latent variables.
        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getLabelMap().entrySet()) {
            latentGroundRuleStore.addGroundRule(new GroundValueConstraint(entry.getKey(), entry.getValue().getValue()));
        }
        groundCount += trainingMap.getLabelMap().size();

        log.debug("Initializing latent objective terms for {} ground rules.", groundCount);
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());
        @SuppressWarnings("unchecked")
        int termCount = termGenerator.generateTerms(latentGroundRuleStore, latentTermStore);
        log.debug("Generated {} latent objective terms from {} ground rules.", termCount, groundCount);
    }

    @SuppressWarnings("unchecked")
    protected void computeLatentMPEState() {
        if (inLatentMPEState) {
            return;
        }

        latentTermStore.clear();
        latentTermStore.ensureVariableCapacity(atomManager.getCachedRVACount());
        termGenerator.generateTerms(latentGroundRuleStore, latentTermStore);

        reasoner.optimize(latentTermStore);

        inLatentMPEState = true;
    }

    @Override
    protected void setLabeledRandomVariables() {
        super.setLabeledRandomVariables();
        inLatentMPEState = false;
    }

    @Override
    protected void setDefaultRandomVariables() {
        super.setDefaultRandomVariables();
        inLatentMPEState = false;
    }

    @Override
    protected void doLearn() {
        double[] previousWeights = new double[mutableRules.size()];
        for (int i = 0; i < previousWeights.length; i++) {
            previousWeights[i] = mutableRules.get(i).getWeight();
        }

        for (emIteration = 0; emIteration < iterations; emIteration++) {
            log.debug("Beginning EM iteration {} of {}", emIteration, iterations);

            eStep();
            mStep();

            // Check if we need to stop (if the weights did not change enough).

            double change = 0;
            for (int i = 0; i < mutableRules.size(); i++) {
                change += Math.pow(previousWeights[i] - mutableRules.get(i).getWeight(), 2);
                previousWeights[i] = mutableRules.get(i).getWeight();
            }
            change = Math.sqrt(change);

            double loss = getLoss();
            double regularizer = computeRegularizer();
            double objective = loss + regularizer;

            log.debug("Finished EM iteration {} with m-step norm {}. Loss: {}, regularizer: {}, objective: {}",
                    emIteration, change, loss, regularizer, objective);

            if (change <= tolerance) {
                log.debug("EM converged.");
                break;
            }
        }
    }

    @Override
    public void close() {
        super.close();

        if (latentGroundRuleStore != null) {
            latentGroundRuleStore.close();
            latentGroundRuleStore = null;
        }

        if (latentTermStore != null) {
            latentTermStore.close();
            latentTermStore = null;
        }
    }

    /**
     * The Expectation step in the EM algorithm.
     * This is a prime target for child override.
     *
     * The default implementation just inferring the latent variables.
     * IE, Minimizes the KL divergence by setting the latent variables to their
     * most probable state conditioned on the evidence and the labeled random variables.
     */
    protected void eStep() {
        computeLatentMPEState();
    }

    /**
     * The Maximization step in the EM algorithm.
     * This is a prime target for child override.
     * The M step is expected to change the weights in mutableRules.
     *
     * The default implementation just calls super.doLearn(), which learns over the non-latent variables.
     */
    protected void mStep() {
        super.doLearn();
    }
}
