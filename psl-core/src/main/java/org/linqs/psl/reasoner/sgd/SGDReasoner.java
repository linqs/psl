/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
import org.linqs.psl.model.atom.GroundAtom;
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
import java.util.Map;

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
    private boolean coordinateStep;
    private SGDLearningSchedule learningSchedule;
    private SGDExtension sgdExtension;

    public SGDReasoner() {
        maxIterations = Options.SGD_MAX_ITER.getInt();

        watchMovement = Options.SGD_MOVEMENT.getBoolean();
        movementThreshold = Options.SGD_MOVEMENT_THRESHOLD.getFloat();

        learningRate = Options.SGD_LEARNING_RATE.getFloat();
        learningRateInverseScaleExp = Options.SGD_INVERSE_TIME_EXP.getFloat();
        coordinateStep = Options.SGD_COORDINATE_STEP.getBoolean();
        learningSchedule = SGDLearningSchedule.valueOf(Options.SGD_LEARNING_SCHEDULE.getString().toUpperCase());
        sgdExtension = SGDExtension.valueOf(Options.SGD_EXTENSION.getString().toUpperCase());
    }

    @Override
    public double optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof VariableTermStore)) {
            throw new IllegalArgumentException("SGDReasoner requires a VariableTermStore (found " + baseTermStore.getClass().getName() + ").");
        }

        @SuppressWarnings("unchecked")
        VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore = (VariableTermStore<SGDObjectiveTerm, GroundAtom>)baseTermStore;

        termStore.initForOptimization();

        long termCount = 0;
        float meanMovement = 0.0f;
        double change = 0.0;
        double objective = 0.0;
        // Starting on the second iteration, keep track of the previous iteration's objective value.
        // The variable values from the term store cannot be used to calculate the objective during an
        // optimization pass because they are being updated in the term.minimize() method.
        // Note that the number of variables may change in the first iteration (since grounding may happen then).
        double oldObjective = Double.POSITIVE_INFINITY;
        float[] oldVariableValues = null;

        Map<Integer, Float> accumulatedGradientSquares = null;
        Map<Integer, Float> accumulatedGradientMean = null;
        Map<Integer, Float> accumulatedGradientVariance = null;

        switch (sgdExtension) {
            case NONE:
                break;
            case ADAGRAD:
                accumulatedGradientSquares = new HashMap<Integer, Float>(termStore.getNumRandomVariables());
                break;
            case ADAM:
                accumulatedGradientMean = new HashMap<Integer, Float>(termStore.getNumRandomVariables());
                accumulatedGradientVariance = new HashMap<Integer, Float>(termStore.getNumRandomVariables());
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported SGD extension: %s", sgdExtension.getName()));
        }

        long totalTime = 0;
        boolean converged = false;
        int iteration = 1;

        for (; iteration < (maxIterations * budget) && !converged; iteration++) {
            long start = System.currentTimeMillis();

            termCount = 0;
            meanMovement = 0.0f;
            objective = 0.0;

            for (SGDObjectiveTerm term : termStore) {
                if (iteration > 1) {
                    objective += term.evaluate(oldVariableValues);
                }

                termCount++;
                meanMovement += term.minimize(iteration, termStore, calculateAnnealedLearningRate(iteration),
                        accumulatedGradientSquares, accumulatedGradientMean, accumulatedGradientVariance,
                        sgdExtension, coordinateStep);
            }

            termStore.iterationComplete();

            if (termCount != 0) {
                meanMovement /= termCount;
            }

            converged = breakOptimization(objective, oldObjective, meanMovement, termCount);

            if (iteration == 1) {
                // Initialize old variables values.
                oldVariableValues = Arrays.copyOf(termStore.getVariableValues(), termStore.getVariableValues().length);
            } else {
                // Update old variables values and objective.
                System.arraycopy(termStore.getVariableValues(), 0, oldVariableValues, 0, oldVariableValues.length);
                oldObjective = objective;
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;

            if (iteration > 1 && log.isTraceEnabled()) {
                log.trace("Iteration {} -- Objective: {}, Normalized Objective: {}, Iteration Time: {}, Total Optimization Time: {}",
                        iteration - 1, objective, objective / termCount, (end - start), totalTime);
            }
        }

        objective = computeObjective(termStore);
        change = termStore.syncAtoms();

        log.info("Final Objective: {}, Final Normalized Objective: {}, Total Optimization Time: {}, Total Number of Iterations: {}", objective, objective / termCount, totalTime, iteration);
        log.debug("Movement of variables from initial state: {}", change);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumRandomVariables(), termCount);

        return objective;
    }

    private boolean breakOptimization(double objective, double oldObjective, float movement, long termCount) {
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

    private double computeObjective(VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore) {
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
            case STEPDECAY:
                return learningRate / ((float) Math.pow(iteration, learningRateInverseScaleExp));
            case CONSTANT:
                return learningRate;
            default:
                throw new IllegalArgumentException(String.format("Illegal value found for SGD learning schedule: '%s'", learningSchedule));
        }
    }

    @Override
    public void close() {
    }
}
