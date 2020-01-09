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

import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Learns the parameters of a HL-MRF with latent variables, using a maximum-likelihood
 * technique that interleaves updates of the parameters and inference steps for
 * fast training. See
 *
 * "Paired-Dual Learning for Fast Training of Latent Variable Hinge-Loss MRFs"
 * Stephen H. Bach, Bert Huang, Jordan Boyd-Graber, and Lise Getoor
 * International Conference on Machine Learning (ICML) 2015
 * for details.
 */
public class PairedDualLearner extends ExpectationMaximization {
    private static final Logger log = LoggerFactory.getLogger(PairedDualLearner.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "pairedduallearner";

    /**
     * Key for Integer property that indicates how many rounds of paired-dual
     * learning to run before beginning to update the weights (parameter K in
     * the ICML paper)
     */
    public static final String WARMUP_ROUNDS_KEY = CONFIG_PREFIX + ".warmuprounds";
    public static final int WARMUP_ROUNDS_DEFAULT = 0;

    /**
     * Key for Integer property that indicates how many steps of ADMM to run
     * for each inner objective before each gradient iteration (parameter N in the ICML paper)
     */
    public static final String ADMM_STEPS_KEY = CONFIG_PREFIX + ".admmsteps";
    public static final int ADMM_STEPS_DEFAULT = 1;

    private final int warmupRounds;
    private final int admmIterations;

    public PairedDualLearner(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public PairedDualLearner(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        warmupRounds = Config.getInt(WARMUP_ROUNDS_KEY, WARMUP_ROUNDS_DEFAULT);
        if (warmupRounds < 0) {
            throw new IllegalArgumentException(WARMUP_ROUNDS_KEY + " must be a nonnegative integer.");
        }

        admmIterations = Config.getInt(ADMM_STEPS_KEY, ADMM_STEPS_DEFAULT);
        if (admmIterations < 1) {
            throw new IllegalArgumentException(ADMM_STEPS_KEY + " must be a positive integer.");
        }
    }

    @Override
    protected void computeExpectedIncompatibility() {
        computeMPEState();

        // Zero out the expected incompatibility first.
        for (int i = 0; i < expectedIncompatibility.length; i++) {
            expectedIncompatibility[i] = 0.0;
        }

        ADMMReasoner admmReasoner = (ADMMReasoner)reasoner;
        float[] consensusBuffer = new float[((ADMMTermStore)termStore).getNumGlobalVariables()];

        // Compute the dual incompatbility for each ground rule.
        for (int i = 0; i < mutableRules.size(); i++) {
            for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
                expectedIncompatibility[i] += admmReasoner.getDualIncompatibility(groundRule, (ADMMTermStore)termStore, consensusBuffer);
            }
        }
    }

    @Override
    protected void computeObservedIncompatibility() {
        setLabeledRandomVariables();
        computeLatentMPEState();

        // Zero out the observed incompatibility first.
        for (int i = 0; i < observedIncompatibility.length; i++) {
            observedIncompatibility[i] = 0.0;
        }

        ADMMReasoner admmReasoner = (ADMMReasoner)reasoner;
        float[] consensusBuffer = new float[((ADMMTermStore)latentTermStore).getNumGlobalVariables()];

        // Computes the observed incompatibilities.
        for (int i = 0; i < mutableRules.size(); i++) {
            for (GroundRule groundRule : latentGroundRuleStore.getGroundRules(mutableRules.get(i))) {
                observedIncompatibility[i] += admmReasoner.getDualIncompatibility(groundRule, (ADMMTermStore)latentTermStore, consensusBuffer);
            }
        }
    }

    @Override
    protected void doLearn() {
        if (!(reasoner instanceof ADMMReasoner)) {
            throw new IllegalArgumentException(String.format(
                    "PairedDualLearning can only be used with ADMMReasoner, found %s.",
                    reasoner.getClass().getName()));
        }

        if (!(termStore instanceof ADMMTermStore)) {
            throw new IllegalArgumentException(String.format(
                    "PairedDualLearning can only be used with ADMMTermStore, found %s.",
                    termStore.getClass().getName()));
        }

        if (!(latentTermStore instanceof ADMMTermStore)) {
            throw new IllegalArgumentException(String.format(
                    "PairedDualLearning (latent) can only be used with ADMMTermStore, found %s.",
                    latentTermStore.getClass().getName()));
        }

        ADMMReasoner admmReasoner = (ADMMReasoner)reasoner;

        int oldMaxIter = admmReasoner.getMaxIter();
        admmReasoner.setMaxIter(admmIterations);

        if (warmupRounds > 0) {
            log.debug("Warming up optimizer with {} iterations.", warmupRounds * admmIterations);
            for (int i = 0; i < warmupRounds; i++) {
                reasoner.optimize(termStore);
                reasoner.optimize(latentTermStore);
            }
        }

        subgrad();

        admmReasoner.setMaxIter(oldMaxIter);
    }

    private void subgrad() {
        log.info("Starting optimization");

        double [] weights = new double[mutableRules.size()];
        for (int i = 0; i < mutableRules.size(); i++) {
            weights[i] = mutableRules.get(i).getWeight();
        }

        double [] gradient = new double[mutableRules.size()];
        for (int i = 0; i < mutableRules.size(); i++) {
            gradient[i] = 1.0;
        }

        double [] avgWeights = new double[mutableRules.size()];
        double objective = 0;

        for (emIteration = 0; emIteration < iterations; emIteration++) {
            objective = getValueAndGradient(gradient, weights);

            double gradNorm = 0;
            double change = 0;

            for (int i = 0; i < mutableRules.size(); i++) {
                gradNorm += Math.pow(weights[i] - Math.max(0, weights[i] - gradient[i]), 2);

                double coeff = baseStepSize;
                double delta = Math.max(-weights[i], -coeff * gradient[i]);
                weights[i] += delta;

                // use gradient array to store change
                gradient[i] = delta;
                change += Math.pow(delta, 2);

                avgWeights[i] = (1 - (1.0 / (double) (emIteration + 1.0))) * avgWeights[i] + (1.0 / (double) (emIteration + 1.0)) * weights[i];
            }

            gradNorm = Math.sqrt(gradNorm);
            change = Math.sqrt(change);

            log.debug("Iter {}, obj: {}, norm grad: {}, change: {}", emIteration, objective, gradNorm, change);

            if (change < tolerance) {
                log.info("Change in w ({}) is less than tolerance. Finishing subgrad.", change);
                break;
            }
        }

        log.info("Learning finished with final objective value {}", objective);

        for (int i = 0; i < mutableRules.size(); i++) {
            if (averageSteps) {
                weights[i] = avgWeights[i];
            }
            mutableRules.get(i).setWeight(weights[i]);
        }

        // The weights have changed, so we are no longer in an MPE state.
        inMPEState = false;
        inLatentMPEState = false;
    }

    private double getValueAndGradient(double[] gradient, double[] weights) {
        for (int i = 0; i < mutableRules.size(); i++) {
            if (gradient[i] != 0.0) {
                mutableRules.get(i).setWeight(weights[i]);
            }
        }

        // The weights have changed, so we are no longer in an MPE state.
        inMPEState = false;
        inLatentMPEState = false;

        ADMMReasoner admmReasoner = (ADMMReasoner)reasoner;

        computeObservedIncompatibility();
        double eStepLagrangianPenalty = admmReasoner.getLagrangianPenalty();
        double eStepAugLagrangianPenalty = admmReasoner.getAugmentedLagrangianPenalty();

        computeExpectedIncompatibility();
        double mStepLagrangianPenalty = admmReasoner.getLagrangianPenalty();
        double mStepAugLagrangianPenalty = admmReasoner.getAugmentedLagrangianPenalty();

        double loss = 0.0;
        for (int i = 0; i < mutableRules.size(); i++) {
            loss += weights[i] * (observedIncompatibility[i] - expectedIncompatibility[i]);
        }
        loss += eStepLagrangianPenalty + eStepAugLagrangianPenalty - mStepLagrangianPenalty - mStepAugLagrangianPenalty;

        for (int i = 0; i < mutableRules.size(); i++) {
            log.debug("Incompatibility for rule {}", mutableRules.get(i));
            log.debug("Truth incompatbility {}, expected incompatibility {}", observedIncompatibility[i], expectedIncompatibility[i]);
        }

        log.debug("E Penalty: {}, E Aug Penalty: {}, M Penalty: {}, M Aug Penalty: {}",
                eStepLagrangianPenalty, eStepAugLagrangianPenalty,
                mStepLagrangianPenalty, mStepAugLagrangianPenalty);

        double regularizer = computeRegularizer();

        if (gradient != null) {
            for (int i = 0; i < mutableRules.size(); i++) {
                gradient[i] = observedIncompatibility[i] - expectedIncompatibility[i];
                if (scaleGradient && groundRuleStore.count(mutableRules.get(i)) > 0.0) {
                    gradient[i] /= groundRuleStore.count(mutableRules.get(i));
                }
                gradient[i] += l2Regularization * weights[i] + l1Regularization;
            }
        }

        return loss + regularizer;
    }
}
