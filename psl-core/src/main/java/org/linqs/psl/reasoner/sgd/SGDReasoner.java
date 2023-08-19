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
package org.linqs.psl.reasoner.sgd;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.ArrayUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Uses an SGD optimization method to optimize its GroundRules.
 */
public class SGDReasoner extends Reasoner<SGDObjectiveTerm> {
    private static final Logger log = Logger.getLogger(SGDReasoner.class);

    private static final float EPSILON = 1e-8f;

    /**
     * The SGD Extension to use.
     */
    public static enum SGDExtension {
        NONE,
        ADAGRAD,
        ADAM
    }

    /**
     * The SGD learning schedule to use.
     */
    public static enum SGDLearningSchedule {
        CONSTANT,
        STEPDECAY
    }

    private boolean firstOrderBreak;
    private float firstOrderTolerance;
    private float firstOrderNorm;

    private float[] prevGradient;
    private float adamBeta1;
    private float adamBeta2;
    private float[] accumulatedGradientSquares;
    private float[] accumulatedGradientMean;
    private float[] accumulatedGradientVariance;

    private float initialLearningRate;
    private float learningRateInverseScaleExp;
    private boolean coordinateStep;
    private SGDLearningSchedule learningSchedule;
    private SGDExtension sgdExtension;

    public SGDReasoner() {
        maxIterations = Options.SGD_MAX_ITER.getInt();
        firstOrderBreak = Options.SGD_FIRST_ORDER_BREAK.getBoolean();
        firstOrderTolerance = Options.SGD_FIRST_ORDER_THRESHOLD.getFloat();
        firstOrderNorm = Options.SGD_FIRST_ORDER_NORM.getFloat();

        initialLearningRate = Options.SGD_LEARNING_RATE.getFloat();
        learningRateInverseScaleExp = Options.SGD_INVERSE_TIME_EXP.getFloat();
        learningSchedule = SGDLearningSchedule.valueOf(Options.SGD_LEARNING_SCHEDULE.getString().toUpperCase());
        coordinateStep = Options.SGD_COORDINATE_STEP.getBoolean();
        sgdExtension = SGDExtension.valueOf(Options.SGD_EXTENSION.getString().toUpperCase());

        prevGradient = null;
        adamBeta1 = Options.SGD_ADAM_BETA_1.getFloat();
        adamBeta2 = Options.SGD_ADAM_BETA_2.getFloat();
        accumulatedGradientSquares = null;
        accumulatedGradientMean = null;
        accumulatedGradientVariance = null;
    }

    @Override
    public double optimize(TermStore<SGDObjectiveTerm> termStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        termStore.initForOptimization();
        initForOptimization(termStore);

        float learningRate = 0.0f;
        float objective = 0.0f;
        // Starting on the second iteration, keep track of the previous iteration's objective value.
        // The variable values from the term store cannot be used to calculate the objective during an
        // optimization pass because they are being updated in the variableUpdate() method.
        // Note that the number of variables may change in the first iteration (since grounding may happen then).
        float oldObjective = Float.POSITIVE_INFINITY;
        float[] prevVariableValues = null;
        // Save and use the variable values with the lowest computed objective.
        float lowestObjective = Float.POSITIVE_INFINITY;
        float[] lowestVariableValues = null;

        long totalTime = 0;
        boolean breakSGD = false;
        int iteration = 1;
        while(!breakSGD) {
            long start = System.currentTimeMillis();

            objective = 0.0f;
            learningRate = calculateAnnealedLearningRate(iteration);

            if (iteration > 1) {
                // Reset gradients for next round.
                Arrays.fill(prevGradient, 0.0f);
            }

            for (SGDObjectiveTerm term : termStore) {
                if (!term.isActive()) {
                    continue;
                }

                if (iteration > 1) {
                    objective += term.evaluate(prevVariableValues);
                    addTermGradient(term, prevGradient, prevVariableValues, termStore.getVariableAtoms());
                }

                variableUpdate(term, termStore, iteration, learningRate);
            }

            evaluate(termStore, iteration, evaluations, trainingMap);

            if (iteration == 1) {
                // Initialize old variables values and gradient.
                prevGradient = new float[termStore.getVariableValues().length];
                prevVariableValues = Arrays.copyOf(termStore.getVariableValues(), termStore.getVariableValues().length);
                lowestVariableValues = Arrays.copyOf(termStore.getVariableValues(), termStore.getVariableValues().length);
            } else {
                clipGradient(prevVariableValues, prevGradient);
                breakSGD = breakOptimization(iteration, termStore,
                        new ObjectiveResult(objective, 0),
                        new ObjectiveResult(oldObjective, 0));

                // Update lowest objective and variable values.
                if (objective < lowestObjective) {
                    lowestObjective = objective;
                    System.arraycopy(prevVariableValues, 0, lowestVariableValues, 0, lowestVariableValues.length);
                }

                // Update old variables values and objective.
                System.arraycopy(termStore.getVariableValues(), 0, prevVariableValues, 0, prevVariableValues.length);
                oldObjective = objective;
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;

            if (iteration > 1) {
                log.trace("Iteration {} -- Objective: {}, Violated Constraints: 0, Gradient Norm: {}, Iteration Time: {}, Total Optimization Time: {}",
                        iteration - 1, objective, MathUtils.pNorm(prevGradient, firstOrderNorm), (end - start), totalTime);
            }

            iteration++;
        }

        // Compute final objective and update the lowest variable values, then set termStore values with lowest values.
        ObjectiveResult finalObjective = computeObjective(termStore);
        if (finalObjective.objective < lowestObjective) {
            lowestObjective = finalObjective.objective;
            lowestVariableValues = prevVariableValues;
        }

        float[] variableValues = termStore.getVariableValues();
        System.arraycopy(lowestVariableValues, 0, variableValues, 0, variableValues.length);

        optimizationComplete(termStore, new ObjectiveResult(lowestObjective, 0), totalTime);
        return lowestObjective;
    }

    protected void initForOptimization(TermStore<SGDObjectiveTerm> termStore) {
        super.initForOptimization(termStore);

        switch (sgdExtension) {
            case NONE:
                break;
            case ADAGRAD:
                accumulatedGradientSquares = new float[termStore.getVariableCounts().unobserved];
                break;
            case ADAM:
                int unobservedCount = termStore.getVariableCounts().unobserved;
                accumulatedGradientMean = new float[unobservedCount];
                accumulatedGradientVariance = new float[unobservedCount];
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported SGD Extensions: '%s'", sgdExtension));
        }
    }

    @Override
    protected void optimizationComplete(TermStore<SGDObjectiveTerm> termStore, ObjectiveResult finalObjective, long totalTime) {
        super.optimizationComplete(termStore, finalObjective, totalTime);

        prevGradient = null;
        accumulatedGradientSquares = null;
        accumulatedGradientMean = null;
        accumulatedGradientVariance = null;
    }

    @Override
    protected boolean breakOptimization(int iteration, TermStore<SGDObjectiveTerm> termStore,
                                        ObjectiveResult objective, ObjectiveResult oldObjective) {
        if (super.breakOptimization(iteration, termStore, objective, oldObjective)) {
            return true;
        }

        // Run through the maximum number of iterations.
        if (runFullIterations) {
            return false;
        }

        // Don't break if there are violated constraints.
        if (objective != null && objective.violatedConstraints > 0) {
            return false;
        }

        // Break if the norm of the gradient is zero.
        if (firstOrderBreak
                && MathUtils.equals(MathUtils.pNorm(prevGradient, firstOrderNorm), 0.0f, firstOrderTolerance)) {
            log.trace("Breaking optimization. Gradient magnitude: {} below tolerance: {}.",
                    MathUtils.pNorm(prevGradient, firstOrderNorm), firstOrderTolerance);
            return true;
        }

        return false;
    }

    private void addTermGradient(SGDObjectiveTerm term, float[] gradient, float[] variableValues, GroundAtom[] variableAtoms) {
        int size = term.size();
        int[] variableIndexes = term.getAtomIndexes();
        float innerPotential = term.computeInnerPotential(variableValues);

        for (int i = 0 ; i < size; i++) {
            if (variableAtoms[variableIndexes[i]].isFixed()) {
                continue;
            }

            gradient[variableIndexes[i]] += term.computeVariablePartial(i, innerPotential);
        }
    }

    private float calculateAnnealedLearningRate(int iteration) {
        switch (learningSchedule) {
            case CONSTANT:
                return initialLearningRate;
            case STEPDECAY:
                return initialLearningRate / ((float)Math.pow(iteration, learningRateInverseScaleExp));
            default:
                throw new IllegalArgumentException(String.format("Illegal value found for SGD learning schedule: '%s'", learningSchedule));
        }
    }

    /**
     * Update the random variables by taking a step in the direction of the negative gradient of the term.
     */
    private void variableUpdate(SGDObjectiveTerm term, TermStore termStore,
                                int iteration, float learningRate) {
        float variableStep = 0.0f;
        float newValue = 0.0f;
        float partial = 0.0f;

        GroundAtom[] variableAtoms = termStore.getVariableAtoms();
        float[] variableValues = termStore.getVariableValues();

        int size = term.size();
        int[] variableIndexes = term.getAtomIndexes();
        float innerPotential = term.computeInnerPotential(variableValues);

        for (int i = 0 ; i < size; i++) {
            if (variableAtoms[variableIndexes[i]].isFixed()) {
                continue;
            }

            partial = term.computeVariablePartial(i, innerPotential);
            variableStep = computeVariableStep(variableIndexes[i], iteration, learningRate, partial);

            newValue = Math.max(0.0f, Math.min(1.0f, variableValues[variableIndexes[i]] - variableStep));
            variableValues[variableIndexes[i]] = newValue;

            if (coordinateStep) {
                innerPotential = term.computeInnerPotential(variableValues);
            }
        }
    }

    /**
     * Compute the step for a single variable according SGD or one of it's extensions.
     * For details on the math behind the SGD extensions see the corresponding papers listed below:
     *  - AdaGrad: https://jmlr.org/papers/volume12/duchi11a/duchi11a.pdf
     *  - Adam: https://arxiv.org/pdf/1412.6980.pdf
     */
    private float computeVariableStep(int variableIndex, int iteration, float learningRate, float partial) {
        float step = 0.0f;
        float adaptedLearningRate = 0.0f;

        switch (sgdExtension) {
            case NONE:
                step = partial * learningRate;
                break;
            case ADAGRAD:
                accumulatedGradientSquares = ArrayUtils.ensureCapacity(accumulatedGradientSquares, variableIndex);
                accumulatedGradientSquares[variableIndex] = accumulatedGradientSquares[variableIndex] + partial * partial;

                adaptedLearningRate = learningRate / (float)Math.sqrt(accumulatedGradientSquares[variableIndex] + EPSILON);
                step = partial * adaptedLearningRate;
                break;
            case ADAM:
                float biasedGradientMean = 0.0f;
                float biasedGradientVariance = 0.0f;

                accumulatedGradientMean = ArrayUtils.ensureCapacity(accumulatedGradientMean, variableIndex);
                accumulatedGradientMean[variableIndex] = adamBeta1 * accumulatedGradientMean[variableIndex] + (1.0f - adamBeta1) * partial;

                accumulatedGradientVariance = ArrayUtils.ensureCapacity(accumulatedGradientVariance, variableIndex);
                accumulatedGradientVariance[variableIndex] = adamBeta2 * accumulatedGradientVariance[variableIndex] + (1.0f - adamBeta2) * partial * partial;

                biasedGradientMean = accumulatedGradientMean[variableIndex] / (1.0f - (float)Math.pow(adamBeta1, iteration));
                biasedGradientVariance = accumulatedGradientVariance[variableIndex] / (1.0f - (float)Math.pow(adamBeta2, iteration));
                adaptedLearningRate = learningRate / ((float)Math.sqrt(biasedGradientVariance) + EPSILON);
                step = biasedGradientMean * adaptedLearningRate;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported SGD Extensions: '%s'", sgdExtension));
        }

        return step;
    }
}
