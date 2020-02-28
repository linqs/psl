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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.util.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Learns new weights for the weighted rules in a model using the voted perceptron algorithm.
 *
 * The weight-learning objective is to maximize the likelihood according to the distribution:
 *
 * p(X) = 1 / Z(w) * exp{-sum[w * f(X)]}
 *
 * Where:
 *  - X is the set of RandomVariableAtoms,
 *  - f(X) the incompatibility of each GroundRule,
 *  - w is the weight of that GroundRule,
 *  - and Z(w) is a normalization factor.
 *
 * The voted perceptron algorithm starts at the current weights and at each step
 * computes the gradient of the objective, takes that step multiplied by a step size
 * (possibly truncated to stay in the region of feasible weights), and
 * saves the new weights. The components of the gradient are each divided by the
 * number of weighted ground rules from that Rule. The learned weights
 * are the averages of the saved weights.
 *
 * For the gradient of the objective, the expected total incompatibility is
 * computed by subclasses in {@link #computeExpectedIncompatibility()}.
 *
 * Reasonable initial implementations are provided for all methods.
 * Child classes should be able to pick and chose which to override.
 */
public abstract class VotedPerceptron extends WeightLearningApplication {
    private static final Logger log = LoggerFactory.getLogger(VotedPerceptron.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "votedperceptron";

    /**
     * Key for positive double property scaling the L2 regularization
     * (\lambda / 2) * ||w||^2
     */
    public static final String L2_REGULARIZATION_KEY = CONFIG_PREFIX + ".l2regularization";
    public static final double L2_REGULARIZATION_DEFAULT = 0.0;

    /**
     * Key for positive double property scaling the L1 regularization
     * \gamma * |w|
     */
    public static final String L1_REGULARIZATION_KEY = CONFIG_PREFIX + ".l1regularization";
    public static final double L1_REGULARIZATION_DEFAULT = 0.0;

    /**
     * Key for positive double property which will be multiplied with the
     * objective gradient to compute a step.
     */
    public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
    public static final double STEP_SIZE_DEFAULT = 0.2;

    /**
     * The inertia that is used for adaptive step sizes.
     * Should be in [0, 1).
     */
    public static final String INERTIA_KEY = CONFIG_PREFIX + ".inertia";
    public static final double INERTIA_DEFAULT = 0.00;

    /**
     * Key for Boolean property that indicates whether to scale gradient by
     * number of groundings
     */
    public static final String SCALE_GRADIENT_KEY = CONFIG_PREFIX + ".scalegradient";
    public static final boolean SCALE_GRADIENT_DEFAULT = true;

    /**
     * Key for Boolean property that indicates whether to average all visited
     * weights together for final output.
     */
    public static final String AVERAGE_STEPS_KEY = CONFIG_PREFIX + ".averagesteps";
    public static final boolean AVERAGE_STEPS_DEFAULT = false;

    /**
     * Key for positive integer property. VotedPerceptron will take this many
     * steps to learn weights.
     */
    public static final String NUM_STEPS_KEY = CONFIG_PREFIX + ".numsteps";
    public static final int NUM_STEPS_DEFAULT = 25;

    /**
     * If true, then weight will not be allowed to go negative (clipped at zero).
     */
    public static final String CLIP_NEGATIVE_WEIGHTS_KEY = CONFIG_PREFIX + ".clipnegativeweights";
    public static final boolean CLIP_NEGATIVE_WEIGHTS_DEFAULT = true;

    /**
     * If true, then cut the step size in half whenever the objective increases.
     */
    public static final String CUT_OBJECTIVE_KEY = CONFIG_PREFIX + ".cutobjective";
    public static final boolean CUT_OBJECTIVE_DEFAULT = false;

    /**
     * If true, then scale the step size down by the iteration.
     */
    public static final String SCALE_STEP_SIZE_KEY = CONFIG_PREFIX + ".scalestepsize";
    public static final boolean SCALE_STEP_SIZE_DEFAULT = true;

    /**
     * If true, then start all weights at zero for learning.
     */
    public static final String ZERO_INITIAL_WEIGHTS_KEY = CONFIG_PREFIX + ".zeroinitialweights";
    public static final boolean ZERO_INITIAL_WEIGHTS_DEFAULT = false;

    protected final double l2Regularization;
    protected final double l1Regularization;
    protected final boolean scaleGradient;

    protected double baseStepSize;
    protected boolean scaleStepSize;
    protected boolean averageSteps;
    protected boolean zeroInitialWeights;
    protected boolean clipNegativeWeights;
    protected boolean cutObjective;
    protected double inertia;
    protected final int maxNumSteps;
    protected int numSteps;

    /**
     * Learning loss at the current point
     */
    private double currentLoss;

    public VotedPerceptron(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        baseStepSize = Config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
        if (baseStepSize <= 0) {
            throw new IllegalArgumentException("Step size must be positive.");
        }

        inertia = Config.getDouble(INERTIA_KEY, INERTIA_DEFAULT);
        if (inertia < 0 || inertia >= 1) {
            throw new IllegalArgumentException("Inertia must be in [0, 1), found: " + inertia);
        }

        numSteps = Config.getInt(NUM_STEPS_KEY, NUM_STEPS_DEFAULT);
        maxNumSteps = numSteps;
        if (numSteps <= 0) {
            throw new IllegalArgumentException("Number of steps must be positive.");
        }

        l2Regularization = Config.getDouble(L2_REGULARIZATION_KEY, L2_REGULARIZATION_DEFAULT);
        if (l2Regularization < 0) {
            throw new IllegalArgumentException("L2 regularization parameter must be non-negative.");
        }

        l1Regularization = Config.getDouble(L1_REGULARIZATION_KEY, L1_REGULARIZATION_DEFAULT);
        if (l1Regularization < 0) {
            throw new IllegalArgumentException("L1 regularization parameter must be non-negative.");
        }

        scaleGradient = Config.getBoolean(SCALE_GRADIENT_KEY, SCALE_GRADIENT_DEFAULT);
        averageSteps = Config.getBoolean(AVERAGE_STEPS_KEY, AVERAGE_STEPS_DEFAULT);
        scaleStepSize = Config.getBoolean(SCALE_STEP_SIZE_KEY, SCALE_STEP_SIZE_DEFAULT);
        zeroInitialWeights = Config.getBoolean(ZERO_INITIAL_WEIGHTS_KEY, ZERO_INITIAL_WEIGHTS_DEFAULT);
        clipNegativeWeights = Config.getBoolean(CLIP_NEGATIVE_WEIGHTS_KEY, CLIP_NEGATIVE_WEIGHTS_DEFAULT);
        cutObjective = Config.getBoolean(CUT_OBJECTIVE_KEY, CUT_OBJECTIVE_DEFAULT);

        currentLoss = Double.NaN;
    }

    protected void postInitGroundModel() {
        if (trainingMap.getLatentVariables().size() > 0) {
            log.warn("Latent variable(s) found when using a VotedPerceptron-based weight learning method ({})."
                    + " VotedPerceptron uses gradients to update weights, but latent variables may make the gradients less accurate."
                    + " Weight learning may still perform sufficiently."
                    + " Found {} latent variables."
                    + " Example latent variable: [{}].",
                    this.getClass().getName(),
                    trainingMap.getLatentVariables().size(),
                    trainingMap.getLatentVariables().get(0));
        }
    }

    @Override
    protected void doLearn() {
        double[] avgWeights = new double[mutableRules.size()];

        // Computes the observed incompatibilities.
        computeObservedIncompatibility();

        // Reset the RVAs to default values.
        setDefaultRandomVariables();

        if (zeroInitialWeights) {
            for (WeightedRule rule : mutableRules) {
                rule.setWeight(0.0);
            }
        }

        // Compute the initial objective.
        if (log.isDebugEnabled() && evaluator != null) {
            // Compute the MPE state before evaluating so variables have assigned values.
            computeMPEState();

            evaluator.compute(trainingMap);
            double objective = evaluator.getRepresentativeMetric();
            objective = evaluator.isHigherRepresentativeBetter() ? -1.0 * objective : objective;

            log.debug("Initial Training Objective: {}", objective);
        }

        double[] scalingFactor = computeScalingFactor();

        // Keep track of the last steps for each weight so we can apply momentum.
        double[] lastSteps = new double[mutableRules.size()];
        double lastObjective = -1.0;

        double[] lastWeights = new double[mutableRules.size()];
        for (int i = 0; i < mutableRules.size(); i++) {
            lastWeights[i] = mutableRules.get(i).getWeight();
        }

        // Computes the gradient steps.
        for (int step = 0; step < numSteps; step++) {
            log.debug("Starting iteration {}", step);
            currentLoss = Double.NaN;

            // Computes the expected incompatibility.
            computeExpectedIncompatibility();

            double norm = 0.0;

            // Updates weights.
            for (int i = 0; i < mutableRules.size(); i++) {
                double newWeight = mutableRules.get(i).getWeight();
                double currentStep = (expectedIncompatibility[i] - observedIncompatibility[i]
                        - l2Regularization * newWeight
                        - l1Regularization) / scalingFactor[i];

                currentStep *= baseStepSize;

                if (scaleStepSize) {
                    currentStep /= (step + 1);
                }

                // Apply momentum.
                currentStep += inertia * lastSteps[i];

                if (clipNegativeWeights) {
                    newWeight = Math.max(0.0, newWeight + currentStep);
                } else {
                    newWeight = newWeight + currentStep;
                }

                log.trace("Gradient: {} (without momentun: {}), Expected Incomp.: {}, Observed Incomp.: {} -- ({}) {}",
                        currentStep, currentStep - (inertia * lastSteps[i]),
                        expectedIncompatibility[i], observedIncompatibility[i],
                        i, mutableRules.get(i));

                mutableRules.get(i).setWeight(newWeight);
                lastSteps[i] = currentStep;
                avgWeights[i] += newWeight;
                norm += Math.pow(expectedIncompatibility[i] - observedIncompatibility[i], 2);
            }

            inMPEState = false;

            norm = Math.sqrt(norm);

            if (log.isDebugEnabled()) {
                getLoss();
            }

            double objective = -1.0;
            if ((cutObjective || log.isDebugEnabled()) && evaluator != null) {
                // Compute the MPE state before evaluating so variables have assigned values.
                computeMPEState();

                evaluator.compute(trainingMap);
                objective = evaluator.getRepresentativeMetric();
                objective = evaluator.isHigherRepresentativeBetter() ? -1.0 * objective : objective;

                if (cutObjective && step > 0 && objective > lastObjective) {
                    log.trace("Objective increased: {} -> {}, cutting step size: {} -> {}.",
                            lastObjective, objective, baseStepSize, baseStepSize / 2.0);
                    baseStepSize /= 2.0;
                    objective = lastObjective;

                    // Set the weights back to the previous ones.
                    for (int i = 0; i < mutableRules.size(); i++) {
                        lastSteps[i] = 0.0;
                        avgWeights[i] -= mutableRules.get(i).getWeight();

                        mutableRules.get(i).setWeight(lastWeights[i]);
                    }
                } else {
                    lastObjective = objective;
                }
            }

            for (int i = 0; i < mutableRules.size(); i++) {
                lastWeights[i] = mutableRules.get(i).getWeight();
            }

            log.debug("Iteration {} complete. Likelihood: {}. Training Objective: {}, Icomp. L2-norm: {}", step, currentLoss, objective, norm);
            log.trace("Model {} ", mutableRules);
        }

        // Sets the weights to their averages.
        if (averageSteps) {
            for (int i = 0; i < mutableRules.size(); i++) {
                mutableRules.get(i).setWeight(avgWeights[i] / numSteps);
            }
        }
    }

    protected double computeRegularizer() {
        if (l1Regularization == 0.0 && l2Regularization == 0.0) {
            return 0.0;
        }

        double l2 = 0.0;
        double l1 = 0.0;

        for (WeightedRule rule : mutableRules) {
            l2 += Math.pow(rule.getWeight(), 2);
            l1 += Math.abs(rule.getWeight());
        }

        return 0.5 * l2Regularization * l2 + l1Regularization * l1;
    }

    public double getLoss() {
        if (Double.isNaN(currentLoss)) {
            currentLoss = computeLoss();
        }

        return currentLoss;
    }

    /**
     * Computes the amount to scale gradient for each rule.
     * Scales by the number of groundings of each rule
     * unless the rule is not grounded in the training set, in which case
     * scales by 1.0.
     */
    protected double[] computeScalingFactor() {
        double [] factor = new double[mutableRules.size()];
        for (int i = 0; i < factor.length; i++) {
            factor[i] = Math.max(1.0, groundRuleStore.count(mutableRules.get(i)));
        }

        return factor;
    }

    @Override
    public void setBudget(double budget) {
        super.setBudget(budget);

        numSteps = (int)Math.ceil(budget * maxNumSteps);
    }
}
