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
package org.linqs.psl.application.learning.weight.search;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Hyperband.
 * https://arxiv.org/pdf/1603.06560.pdf
 * Some of the math has been adjusted to compute a budget (as a percentage) rather than a number of resources.
 *
 * Total amount of budget used: BASE_BRACKET_SIZE_KEY * NUM_BRACKETS_KEY
 * VotedPerceptron methods typically use a total budget of 25.
 * Number of configurations evaluated: \sum_{i = 0}^{NUM_BRACKETS_KEY} (BASE_BRACKET_SIZE_KEY * SURVIVAL_KEY^i / (i + 1))
 *
 * TODO(eriq): Think about inital weights.
 *
 * All extending classes should ensure that values for RVAs are set before evaluators are computed.
 */
public class Hyperband extends WeightLearningApplication {
    private static final Logger log = LoggerFactory.getLogger(Hyperband.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "hyperband";

    /**
     * The proportion of configs that survive each round in a brancket.
     */
    public static final String SURVIVAL_KEY = CONFIG_PREFIX + ".survival";
    public static final int SURVIVAL_DEFAULT = 4;

    /**
     * The base number of weight configurations for each brackets.
     */
    public static final String BASE_BRACKET_SIZE_KEY = CONFIG_PREFIX + ".basebracketsize";
    public static final int BASE_BRACKET_SIZE_DEFAULT = 10;

    /**
     * The number of brackets to consider.
     * This is computed in vanilla Hyperband.
     */
    public static final String NUM_BRACKETS_KEY = CONFIG_PREFIX + ".numbrackets";
    public static final int NUM_BRACKETS_DEFAULT = 4;

    public static final double MIN_BUDGET_PROPORTION = 0.001;
    public static final int MIN_BRACKET_SIZE = 1;

    // TODO(eriq): Config
    public static final double MEAN = 0.50;
    public static final double VARIANCE = 0.10;

    private final int survival;

    private double bestObjective;
    private double[] bestWeights;

    private int numBrackets;
    private int baseBracketSize;

    public Hyperband(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public Hyperband(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        survival = Config.getInt(SURVIVAL_KEY, SURVIVAL_DEFAULT);
        if (survival < 1) {
            throw new IllegalArgumentException("Need at least one survival porportion.");
        }

        numBrackets = Config.getInt(NUM_BRACKETS_KEY, NUM_BRACKETS_DEFAULT);
        if (numBrackets < 1) {
            throw new IllegalArgumentException("Need at least one bracket.");
        }

        baseBracketSize = Config.getInt(BASE_BRACKET_SIZE_KEY, BASE_BRACKET_SIZE_DEFAULT);
        if (baseBracketSize < 1) {
            throw new IllegalArgumentException("Need at least one bracket size.");
        }
    }

    @Override
    protected void doLearn() {
        double bestObjective = -1;
        double[] bestWeights = null;

        // Computes the observed incompatibilities.
        computeObservedIncompatibility();

        // The total cost used vs one full round of inference.
        double totalCost = 0.0;
        int numEvaluatedConfigs = 0;

        for (int bracket = 0; bracket < numBrackets; bracket++) {
            // TODO(eriq): Swap bracket direction? Start with more?

            double bracketProportion = Math.pow(survival, bracket) / (bracket + 1);
            int bracketSize = (int)(Math.max(MIN_BRACKET_SIZE, Math.ceil(bracketProportion * baseBracketSize)));
            numEvaluatedConfigs += bracketSize;

            double bracketBudget = Math.pow(survival, -1.0 * bracket);

            log.debug("Bracket {} / {} -- Size: {} ({}), Budget: {}", bracket + 1, numBrackets, bracketSize, bracketProportion, bracketBudget);

            // Note that each config may get adjusted by internal weight learning methods.
            // (Not in the default behavior, but in child class behavior).
            List<double[]> configs = chooseConfigs(bracketSize);

            for (int round = 0; round <= bracket; round++) {
                int roundSize = configs.size();
                double roundBudget = bracketBudget * Math.pow(survival, round);
                setBudget(Math.max(MIN_BUDGET_PROPORTION, Math.min(1.0, roundBudget)));

                log.debug("  Round {} / {} -- Size: {}, Budget: {}", round + 1, bracket + 1, roundSize, roundBudget);

                PriorityQueue<RunResult> results = new PriorityQueue<RunResult>();
                for (double[] config : configs) {
                    totalCost += roundBudget;

                    // Set the weights for the current round.
                    for (int i = 0; i < mutableRules.size(); i++) {
                        mutableRules.get(i).setWeight(config[i]);
                    }

                    // The weights have changed, so we are no longer in an MPE state.
                    inMPEState = false;

                    double objective = run(config);
                    RunResult result = new RunResult(config, objective);

                    results.add(result);

                    if (bestWeights == null || objective < bestObjective) {
                        bestObjective = objective;
                        bestWeights = config;
                    }

                    log.debug("Training Objective: {}, Weights: {}", objective, config);
                }

                configs.clear();
                for (int i = 0; i < (int)(Math.floor((double)roundSize / survival)); i++) {
                    configs.add(results.poll().weights);
                }
            }
        }

        // Set the final weights.
        for (int i = 0; i < mutableRules.size(); i++) {
            mutableRules.get(i).setWeight(bestWeights[i]);
        }

        // The weights have changed, so we are no longer in an MPE state.
        inMPEState = false;

        log.debug("Hyperband complete. Configurations examined: {}. Total budget: {}",  numEvaluatedConfigs, totalCost);
    }

    private List<double[]> chooseConfigs(int bracketSize) {
        List<double[]> configs = new ArrayList<double[]>(bracketSize);

        for (int i = 0; i < bracketSize; i++) {
            double[] config = new double[mutableRules.size()];

            for (int weightIndex = 0; weightIndex < mutableRules.size(); weightIndex++) {
                // Rand give Gaussian with mean = 0.0 and variance = 1.0.
                config[weightIndex] = RandUtils.nextDouble() * Math.sqrt(VARIANCE) + MEAN;
            }

            configs.add(config);
        }

        return configs;
    }

    /**
     * Run and eval on the given weights using the given budget (ratio of max resources) and give back its score (lower is better).
     * This method may modify weights if it wants to store a different set of weights than those initially passed in.
     * The rules have already been set with the given weights, they are only passed in so the method
     * has a chance to modify them before the result is stored.
     * This is a prime method for child classes to override.
     * Implementers should make sure to correct (negate) the value that comes back from the Evaluator
     * if lower is better for that evaluator.
     */
    protected double run(double[] weights) {
        // Reset the RVAs to default values.
        setDefaultRandomVariables();

        // Computes the expected incompatibility.
        computeExpectedIncompatibility();

        evaluator.compute(trainingMap);

        double score = evaluator.getRepresentativeMetric();
        score = evaluator.isHigherRepresentativeBetter() ? -1.0 * score : score;

        return score;
    }

    private static class RunResult implements Comparable<RunResult> {
        public double[] weights;
        public double objective;

        public RunResult(double[] weights, double objective) {
            this.weights = weights;
            this.objective = objective;
        }

        @Override
        public int compareTo(RunResult other) {
            return Double.compare(objective, other.objective);
        }
    }
}
