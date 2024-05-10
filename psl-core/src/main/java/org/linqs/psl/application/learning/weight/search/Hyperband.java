/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Hyperband.
 * https://arxiv.org/pdf/1603.06560.pdf
 * Some of the math has been adjusted to compute a budget (as a percentage) rather than a number of resources.
 *
 * Total amount of budget used: WLA_HB_BRACKET_SIZE * WLA_HB_NUM_BRACKETS
 * StructuredPerceptron methods typically use a total budget of 25.
 * Number of configurations evaluated: \sum_{i = 0}^{WLA_HB_NUM_BRACKETS} (WLA_HB_BRACKET_SIZE * WLA_HB_SURVIVAL^i / (i + 1))
 *
 * TODO(eriq): Think about inital weights.
 *
 * All extending classes should ensure that values for RVAs are set before evalautions are computed.
 */
public class Hyperband extends WeightLearningApplication {
    private static final Logger log = Logger.getLogger(Hyperband.class);

    public static final double MIN_BUDGET_PROPORTION = 0.001;
    public static final int MIN_BRACKET_SIZE = 1;

    private final int survival;

    private WeightSampler weightSampler;

    private int numBrackets;
    private int baseBracketSize;

    public Hyperband(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                     Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        weightSampler = new WeightSampler(mutableRules.size());

        survival = Options.WLA_HB_SURVIVAL.getInt();
        numBrackets = Options.WLA_HB_NUM_BRACKETS.getInt();
        baseBracketSize = Options.WLA_HB_BRACKET_SIZE.getInt();

        if (this.runValidation) {
            throw new IllegalArgumentException("Validation is not supported by the Hyperband weight learning application.");
        }
    }

    @Override
    protected void doLearn() {
        if (evaluation == null) {
            throw new IllegalStateException(String.format(
                    "No evaluation has been set for weight learning method (%s), which is required for search-based methods.",
                    getClass().getName()));
        }

        double bestObjective = -1;
        Weight[] bestWeights = null;

        String currentLocation = null;

        // The total cost used vs one full round of inference.
        double totalCost = 0.0;
        int numEvaluatedConfigs = 0;
        long totalTime = 0;
        for (int bracket = 0; bracket < numBrackets; bracket++) {
            // TODO(eriq): Swap bracket direction? Start with more?

            if (totalTime > timeout) {
                log.debug("Stopping search due to timeout.");
                break;
            }

            long start = System.currentTimeMillis();

            double bracketProportion = Math.pow(survival, bracket) / (bracket + 1);
            int bracketSize = (int)(Math.max(MIN_BRACKET_SIZE, Math.ceil(bracketProportion * baseBracketSize)));
            numEvaluatedConfigs += bracketSize;

            double bracketBudget = Math.pow(survival, -1.0 * bracket);

            log.debug("Bracket {} / {} -- Size: {} ({}), Budget: {}", bracket + 1, numBrackets, bracketSize, bracketProportion, bracketBudget);

            // Note that each config may get adjusted by internal weight learning methods.
            // (Not in the default behavior, but in child class behavior).
            List<Weight[]> configs = chooseConfigs(bracketSize);

            for (int round = 0; round <= bracket; round++) {
                int roundSize = configs.size();
                double roundBudget = bracketBudget * Math.pow(survival, round);
                setBudget(Math.max(MIN_BUDGET_PROPORTION, Math.min(1.0, roundBudget)));

                log.debug("  Round {} / {} -- Size: {}, Budget: {}", round + 1, bracket + 1, roundSize, roundBudget);

                PriorityQueue<RunResult> results = new PriorityQueue<RunResult>();
                for (Weight[] config : configs) {
                    totalCost += roundBudget;

                    // Set the weights for the current round.
                    for (int i = 0; i < mutableRules.size(); i++) {
                        mutableRules.get(i).setWeight(config[i]);
                    }

                    // Set the current location.
                    currentLocation = StringUtils.join(DELIM, (Object[])config);

                    log.trace("Weights: {}", (Object[])config);

                    // The weights have changed, so we are no longer in an MPE state.
                    inTrainingMAPState = false;

                    double objective = run(config);
                    RunResult result = new RunResult(config, objective);
                    results.add(result);

                    log.debug("Weights: {} -- objective: {}", currentLocation, objective);

                    if (bestWeights == null || objective < bestObjective) {
                        bestObjective = objective;
                        bestWeights = config;
                    }

                    log.debug("Training Objective: {}, Weights: {}", objective, config);
                }

                configs.clear();
                for (int i = 0; i < (int)(Math.floor((float)roundSize / survival)); i++) {
                    configs.add(results.poll().weights);
                }
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;
        }

        // Set the final weights.
        for (int i = 0; i < mutableRules.size(); i++) {
            mutableRules.get(i).setWeight(bestWeights[i]);
        }

        // The weights have changed, so we are no longer in an MPE state.
        inTrainingMAPState = false;

        log.debug("Hyperband complete. Configurations examined: {}. Total budget: {}",  numEvaluatedConfigs, totalCost);
    }

    private List<Weight[]> chooseConfigs(int bracketSize) {
        List<Weight[]> configs = new ArrayList<Weight[]>(bracketSize);

        for (int i = 0; i < bracketSize; i++) {
            Weight[] config = new Weight[mutableRules.size()];

            weightSampler.getRandomWeights(config);

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
     */
    protected double run(Weight[] weights) {
        computeTrainingMAPState();

        evaluation.compute(trainingMap);
        return -1.0 * evaluation.getNormalizedRepMetric();
    }

    private static class RunResult implements Comparable<RunResult> {
        private final Weight[] weights;
        private final double objective;

        public RunResult(Weight[] weights, double objective) {
            this.weights = weights;
            this.objective = objective;
        }

        public Weight[] getWeights() {
            return weights;
        }

        @Override
        public int compareTo(RunResult other) {
            return Double.compare(objective, other.objective);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof RunResult)) {
                return false;
            }

            return MathUtils.equals(objective, ((RunResult)other).objective);
        }

        @Override
        public int hashCode() {
            // Since the objective is fixed, just offset it and truncate it.
            return (int)(objective * 1000000);
        }
    }
}
