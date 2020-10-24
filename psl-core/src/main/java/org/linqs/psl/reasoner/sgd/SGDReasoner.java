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
package org.linqs.psl.reasoner.sgd;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Uses an SGD optimization method to optimize its GroundRules.
 */
public class SGDReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(SGDReasoner.class);

    private int maxIterations;

    private boolean watchMovement;
    private float movementThreshold;

    private float learningRate;
    private float learningRateInverseScaleExp;
    private String learningSchedule;

    private boolean coordinateStep;
    private boolean adaGrad;
    private boolean adam;

    private HashMap<Integer, Float> gradient;
    private HashMap<Integer, Float> accumulatedGradientSquares;
    private HashMap<Integer, Float> accumulatedGradientMean;
    private HashMap<Integer, Float>  accumulatedGradientVariance;

    public SGDReasoner() {
        maxIterations = Options.SGD_MAX_ITER.getInt();

        watchMovement = Options.SGD_MOVEMENT.getBoolean();
        movementThreshold = Options.SGD_MOVEMENT_THRESHOLD.getFloat();

        learningRate = Options.SGD_LEARNING_RATE.getFloat();
        learningSchedule = Options.SGD_LEARNING_SCHEDULE.getString().toUpperCase();
        learningRateInverseScaleExp = Options.SGD_INVERSE_TIME_EXP.getFloat();

        coordinateStep = Options.SGD_COORDINATE_STEP.getBoolean();
        adaGrad = Options.SGD_ADA_GRAD.getBoolean();
        adam = Options.SGD_ADAM.getBoolean();

        gradient = null;
        accumulatedGradientSquares = null;
        accumulatedGradientMean = null;
        accumulatedGradientVariance = null;
    }

    @Override
    public double optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof VariableTermStore)) {
            throw new IllegalArgumentException("SGDReasoner requires a VariableTermStore (found " + baseTermStore.getClass().getName() + ").");
        }

        @SuppressWarnings("unchecked")
        VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore = (VariableTermStore<SGDObjectiveTerm, RandomVariableAtom>)baseTermStore;

        termStore.initForOptimization();

        float meanMovement = 0.0f;
        long termCount = 0;
        double objective = Double.POSITIVE_INFINITY;
        double oldObjective = Double.POSITIVE_INFINITY;

        // Initialize dynamic data structures for optimization.
        float[] oldVariableValues = null;
        gradient = new HashMap<Integer, Float>();
        if (adaGrad) {
            accumulatedGradientSquares = new HashMap<Integer, Float>();
        } else if (adam) {
            accumulatedGradientMean = new HashMap<Integer, Float>();
            accumulatedGradientVariance = new HashMap<Integer, Float>();
        }

        if (log.isTraceEnabled()) {
            objective = computeObjective(termStore);
            log.trace("Iteration {} -- Objective: {}, Normalized Objective: {}, Mean Movement: {}, Iteration Time: {}, Total Optimization Time: {}",
                    0, objective, objective / termStore.size(), 0, 0, 0);
        }

        int iteration = 1;
        long totalTime = 0;
        while (true) {
            long start = System.currentTimeMillis();

            termCount = 0;
            meanMovement = 0.0f;
            objective = 0.0;

            for (SGDObjectiveTerm term : termStore) {
                if (oldVariableValues != null) {
                    objective += term.evaluate(oldVariableValues);
                } else {
                    objective += term.evaluate(termStore.getVariableValues());
                }

                termCount++;
                meanMovement += minimize(term, termStore.getVariableValues(), iteration);
            }

            if (termCount != 0) {
                meanMovement /= termCount;
            }

            if (breakOptimization(iteration, objective, oldObjective, meanMovement, termCount)) {
                totalTime += System.currentTimeMillis() - start;
                break;
            }

            // Keep track of the old variables for a deferred objective computation.
            if (oldVariableValues == null) {
                oldVariableValues = Arrays.copyOf(termStore.getVariableValues(), termStore.getVariableValues().length);
                oldObjective = Double.POSITIVE_INFINITY;
            } else {
                System.arraycopy(termStore.getVariableValues(), 0, oldVariableValues, 0, oldVariableValues.length);
                oldObjective = objective;
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;

            if (log.isTraceEnabled()) {
                log.trace("Iteration {} -- Objective: {}, Normalized Objective: {}, Mean Movement: {}, Iteration Time: {}, Total Optimization Time: {}",
                        iteration, objective, objective / termCount, meanMovement, (end - start), totalTime);
            }

            iteration++;
            termStore.iterationComplete();
        }

        objective = computeObjective(termStore);
        log.info("Optimization completed in {} iterations. Objective: {}, Normalized Objective: {}, Total Optimization Time: {}",
                iteration, objective, objective / termCount, totalTime);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumVariables(), termCount);

        termStore.syncAtoms();

        return objective;
    }

    private float minimize(SGDObjectiveTerm term, float[] variableValues, int iteration) {
        float movement = 0.0f;
        float variableStep = 0.0f;
        float newValue = 0.0f;
        int[] variableIndexes = term.getVariableIndexes();

        term.computeGradient(variableValues, gradient);

        for (int i = 0; i < term.size(); i++) {
            if (coordinateStep) {
                term.computeGradient(variableValues, gradient);
            }
            variableStep = computeVariableStep(variableIndexes[i], iteration);

            newValue = Math.max(0.0f, Math.min(1.0f, variableValues[variableIndexes[i]] - variableStep));
            movement += Math.abs(newValue - variableValues[variableIndexes[i]]);
            variableValues[variableIndexes[i]] = newValue;
        }

        return movement;
    }

    private float computeVariableStep(int variableIndex, int iteration) {
        float beta1 = 0.9f;
        float beta2 = 0.999f;
        float mean_hat = 0.0f;
        float variance_hat = 0.0f;
        float step = 0.0f;
        float adaptedLearningRate = 0.0f;

        if (adaGrad) {
            accumulatedGradientSquares.put(variableIndex, accumulatedGradientSquares.getOrDefault(variableIndex, 0.0f)
                    + (float)Math.pow(gradient.get(variableIndex), 2.0f));
            adaptedLearningRate = calculateAnnealedLearningRate(iteration) / (float)Math.sqrt(accumulatedGradientSquares.get(variableIndex) + 1e-8f);

            step = gradient.get(variableIndex) * adaptedLearningRate;
        } else if (adam) {
            accumulatedGradientMean.put(variableIndex, beta1 * accumulatedGradientMean.getOrDefault(variableIndex, 0.0f)
                    + (1 - beta1) * gradient.get(variableIndex));

            accumulatedGradientVariance.put(variableIndex, beta2 * accumulatedGradientVariance.getOrDefault(variableIndex, 0.0f) +
                    (1 - beta2) * (float)Math.pow(gradient.get(variableIndex), 2.0f));

            mean_hat = accumulatedGradientMean.get(variableIndex) / (1 - beta1);
            variance_hat = accumulatedGradientVariance.get(variableIndex) / (1 - beta2);

            adaptedLearningRate = (calculateAnnealedLearningRate(iteration) / ((float)Math.sqrt(variance_hat) + 1e-8f));
            step = mean_hat * adaptedLearningRate;
        } else {
            step = gradient.get(variableIndex) * calculateAnnealedLearningRate(iteration);
        }

        return step;
    }

    private boolean breakOptimization(int iteration, double objective, double oldObjective, float movement, long termCount) {
        // Always break when the allocated iterations is up.
        if (iteration > (int)(maxIterations * budget)) {
            return true;
        }

        // Run through the maximum number of iterations.
        if (runFullIterations) {
            return false;
        }

        // Do not break if there is too much movement.
        if (watchMovement && movement > movementThreshold) {
            return false;
        }

        // Break if the objective has not changed.
        if (objectiveBreak && MathUtils.equals(objective / termCount, oldObjective / termCount, tolerance)) {
            return true;
        }

        return false;
    }

    private double computeObjective(VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore) {
        double objective = 0.0;

        // If possible, use a readonly iterator.
        Iterator<SGDObjectiveTerm> termIterator = null;
        if (termStore.isLoaded()) {
            termIterator = termStore.noWriteIterator();
        } else {
            termIterator = termStore.iterator();
        }

        float[] variableValues = termStore.getVariableValues();
        for (SGDObjectiveTerm term : IteratorUtils.newIterable(termIterator)) {
            objective += term.evaluate(variableValues);
        }

        return objective;
    }

    private float calculateAnnealedLearningRate(int iteration) {
        switch (learningSchedule) {
            case "INVERSETIME":
                return learningRate / ((float) Math.pow(iteration, learningRateInverseScaleExp));
            case "CONSTANT":
                return learningRate;
            default:
                throw new IllegalArgumentException(String.format("Illegal value found for SGD learning schedule: '%s'", learningSchedule));
        }
    }

    @Override
    public void close() {
    }
}
