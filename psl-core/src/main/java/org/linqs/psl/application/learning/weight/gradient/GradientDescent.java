/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight.gradient;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Learns weights for weighted rules in a model by optimizing an objective via Gradient Descent.
 * Child classes define methods for computing the value and gradient of a loss.
 */
public abstract class GradientDescent extends WeightLearningApplication {
    private static final Logger log = Logger.getLogger(GradientDescent.class);

    /**
     * The Gradient Descent Extension to use.
     * MIRROR_DESCENT(Default): Perform mirror descent (normalized exponeniated gradient descent)
     *     on the chosen loss with unit simplex constrained weights.
     * NONE: Perform standard gradient descent with only lower bound (>=0) constraints on the weights.
     */
    public static enum GDExtension {
        MIRROR_DESCENT,
        NONE
    }

    protected GDExtension gdExtension;

    protected float[] weightGradient;

    protected float baseStepSize;
    protected boolean scaleStepSize;
    protected float maxGradientMagnitude;
    protected float maxGradientNorm;
    protected float stoppingGradientNorm;
    protected boolean clipWeightGradient;

    protected int maxNumSteps;
    protected boolean runFullIterations;
    protected boolean objectiveBreak;
    protected boolean normBreak;
    protected float objectiveTolerance;
    protected float normTolerance;

    protected float l2Regularization;
    protected float logRegularization;
    protected float entropyRegularization;

    public GradientDescent(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        gdExtension = GDExtension.valueOf(Options.WLA_GRADIENT_DESCENT_EXTENSION.getString().toUpperCase());

        weightGradient = new float[mutableRules.size()];

        baseStepSize = Options.WLA_GRADIENT_DESCENT_STEP_SIZE.getFloat();
        scaleStepSize = Options.WLA_GRADIENT_DESCENT_SCALE_STEP.getBoolean();
        clipWeightGradient = Options.WLA_GRADIENT_DESCENT_CLIP_GRADIENT.getBoolean();
        maxGradientMagnitude = Options.WLA_GRADIENT_DESCENT_MAX_GRADIENT.getFloat();
        maxGradientNorm = Options.WLA_GRADIENT_DESCENT_MAX_GRADIENT_NORM.getFloat();

        maxNumSteps = Options.WLA_GRADIENT_DESCENT_NUM_STEPS.getInt();
        runFullIterations = Options.WLA_GRADIENT_DESCENT_RUN_FULL_ITERATIONS.getBoolean();
        objectiveBreak = Options.WLA_GRADIENT_DESCENT_OBJECTIVE_BREAK.getBoolean();
        normBreak = Options.WLA_GRADIENT_DESCENT_NORM_BREAK.getBoolean();
        objectiveTolerance = Options.WLA_GRADIENT_DESCENT_OBJECTIVE_TOLERANCE.getFloat();
        normTolerance = Options.WLA_GRADIENT_DESCENT_NORM_TOLERANCE.getFloat();
        stoppingGradientNorm = Options.WLA_GRADIENT_DESCENT_STOPPING_GRADIENT_NORM.getFloat();

        l2Regularization = Options.WLA_GRADIENT_DESCENT_L2_REGULARIZATION.getFloat();
        logRegularization = Options.WLA_GRADIENT_DESCENT_LOG_REGULARIZATION.getFloat();
        entropyRegularization = Options.WLA_GRADIENT_DESCENT_ENTROPY_REGULARIZATION.getFloat();
    }

    @Override
    protected void doLearn() {
        boolean breakGD = false;
        float objective = 0.0f;
        float oldObjective = Float.POSITIVE_INFINITY;

        log.info("Gradient Descent Weight Learning Start.");
        initForLearning();

        long totalTime = 0;
        int iteration = 1;
        while (!breakGD) {
            log.trace("Model: {}", mutableRules);
            if (log.isTraceEnabled() && evaluation != null) {
                // Compute the MAP state before evaluating so variables have assigned values.
                computeMPEState();
                evaluation.compute(trainingMap);
                log.trace("MAP State Evaluation Metric: {}", evaluation.getNormalizedRepMetric());
            }

            long start = System.currentTimeMillis();

            computeIterationStatistics();
            objective = computeTotalLoss();
            computeTotalWeightGradient();
            if (clipWeightGradient) {
                clipWeightGradient();
            }
            weightGradientStep(iteration);

            breakGD = breakOptimization(iteration, objective, oldObjective);
            oldObjective = objective;

            long end = System.currentTimeMillis();
            totalTime += end - start;

            log.trace("Iteration {} -- Weight Learning Objective: {}, Weight Gradient Magnitude: {}, Iteration Time: {}",
                    iteration, objective, computeGradientNorm(), (end - start));

            iteration++;
        }

        log.info("Gradient Descent Weight Learning Finished.");
        log.info("Final Model {} ", mutableRules);
        if (evaluation != null) {
            // Compute the MAP state before evaluating so variables have assigned values.
            computeMPEState();
            evaluation.compute(trainingMap);
            log.info("Final MAP State Evaluation Metric: {}", evaluation.getNormalizedRepMetric());
        }
        log.info("Final Weight Learning Loss: {}, Final Gradient Magnitude: {}, Total optimization time: {}",
                computeTotalLoss(), computeGradientNorm(), totalTime);
    }

    protected void initForLearning() {
        switch (gdExtension) {
            case MIRROR_DESCENT:
                // Initialize weights to be centered on the unit simplex.
                simplexScaleWeights();
                inMPEState = false;

                break;
            default:
                // Do nothing.
                break;
        }
    }

    private boolean breakOptimization(int iteration, float objective, float oldObjective) {
        // Always break when the allocated iterations is up.
        if (iteration > maxNumSteps) {
            return true;
        }

        if (runFullIterations) {
            return false;
        }

        if (objectiveBreak && MathUtils.equals(objective, oldObjective, objectiveTolerance)) {
            return true;
        }

        if (normBreak) {
            return MathUtils.equals(computeGradientNorm(), 0.0f, normTolerance);
        }

        return false;
    }

    /**
     * Clip weight gradients to avoid numerical errors.
     */
    private void clipWeightGradient() {
        float gradientMagnitude = MathUtils.pNorm(weightGradient, maxGradientNorm);

        if (gradientMagnitude > maxGradientMagnitude) {
            log.trace("Clipping gradient. Original gradient magnitude: {} exceeds limit: {} in L_{} space.",
                    gradientMagnitude, maxGradientMagnitude, maxGradientNorm);
            for (int i = 0; i < mutableRules.size(); i++) {
                weightGradient[i] = maxGradientMagnitude * weightGradient[i] / gradientMagnitude;
            }
        }
    }

    /**
     * Take a step in the direction of the negative gradient of the weights.
     */
    private void weightGradientStep(int iteration) {
        float stepSize = computeStepSize(iteration);

        switch (gdExtension) {
            case MIRROR_DESCENT:
                float exponentiatedGradientSum = 0.0f;
                for (int j = 0; j < mutableRules.size(); j++) {
                    exponentiatedGradientSum += mutableRules.get(j).getWeight() * Math.exp(-1.0f * stepSize * weightGradient[j]);
                }

                for (int j = 0; j < mutableRules.size(); j++) {
                    mutableRules.get(j).setWeight(
                            (float)((mutableRules.get(j).getWeight()
                                    * Math.exp(-1.0f * stepSize * weightGradient[j]))
                                    / exponentiatedGradientSum));
                }

                break;
            default:
                for (int j = 0; j < mutableRules.size(); j++) {
                    // Clip negative weights.
                    mutableRules.get(j).setWeight(mutableRules.get(j).getWeight() - weightGradient[j] * stepSize);
                }

                break;
        }

        inMPEState = false;
    }

    private float computeStepSize(int iteration) {
        float stepSize = baseStepSize;

        if (scaleStepSize) {
            stepSize /= iteration;
        }

        return stepSize;
    }

    protected float computeGradientNorm() {
        float norm = 0.0f;

        switch (gdExtension) {
            case MIRROR_DESCENT:
                // The norm of simplex constrained weights is KL-Divergence between
                // the distribution of the weight gradient in the dual space
                // and the discrete uniform distribution.
                float exponentiatedGradientSum = 0.0f;
                for (int i = 0; i < mutableRules.size(); i ++) {
                    exponentiatedGradientSum += Math.exp(weightGradient[i]);
                }

                for (int i = 0; i < mutableRules.size(); i ++) {
                    float mappedWeightGradient = (float)Math.exp(weightGradient[i]) / exponentiatedGradientSum;
                    norm += mappedWeightGradient * (float)Math.log(mappedWeightGradient * mutableRules.size());
                }

                break;
            default:
                for (WeightedRule mutableRule : mutableRules) {
                    norm += (float)Math.pow(mutableRule.getWeight(), stoppingGradientNorm);
                }
                norm = (float)Math.pow(norm, 1.0f / stoppingGradientNorm);

                break;
        }

        return norm;
    }

    private void simplexScaleWeights() {
        // Initialize weights to be centered on the unit simplex.
        float totalWeight = 0.0f;
        for (WeightedRule mutableRule : mutableRules) {
            totalWeight += mutableRule.getWeight();
        }

        for (WeightedRule mutableRule : mutableRules) {
            mutableRule.setWeight(mutableRule.getWeight() / totalWeight);
        }
    }

    /**
     * Method called at the start of every gradient descent iteration to
     * compute statistics needed for loss and gradient computations.
     */
    protected abstract void computeIterationStatistics();

    /**
     * Method for computing the total regularized loss.
     */
    protected float computeTotalLoss() {
        float learningLoss = computeLearningLoss();
        float regularization = computeRegularization();

        log.trace("Learning Loss: {}, Regularization: {}", learningLoss, regularization);
        
        return learningLoss + regularization;
    }

    /**
     * Compute the learning loss.
     */
    protected abstract float computeLearningLoss();

    /**
     * Compute the regularization.
     */
    protected float computeRegularization() {
        float regularization = 0.0f;
        for (WeightedRule mutableRule : mutableRules) {
            regularization += l2Regularization * (float)Math.pow(mutableRule.getWeight(), 2)
                    - logRegularization * (float)Math.log(mutableRule.getWeight())
                    + entropyRegularization * mutableRule.getWeight() * (float)Math.log(mutableRule.getWeight());
        }

        return regularization;
    }

    /**
     * Compute the gradient of the regularized learning loss with respect to the weights.
     */
    protected void computeTotalWeightGradient() {
        Arrays.fill(weightGradient, 0.0f);

        addLearningLossWeightGradient();
        addRegularizationWeightGradient();
    }

    /**
     * Add the gradient of the learning loss with respect to the weights.
     */
    protected abstract void addLearningLossWeightGradient();

    /**
     * Add the gradient of the regularization with respect to the weights.
     */
    protected void addRegularizationWeightGradient() {
        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += 2.0f * l2Regularization * mutableRules.get(i).getWeight()
                    - logRegularization / mutableRules.get(i).getWeight()
                    + entropyRegularization * ((float)Math.log(mutableRules.get(i).getWeight()) + 1);
        }
    }
}
