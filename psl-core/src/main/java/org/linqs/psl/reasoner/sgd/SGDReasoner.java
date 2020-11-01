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

        HashMap<Integer, Float> accumulatedGradientSquares = null;
        HashMap<Integer, Float> accumulatedGradientMean = null;
        HashMap<Integer, Float>  accumulatedGradientVariance = null;

        // Initialize dynamic data structures for optimization.
        float[] oldVariableValues = null;
        if (adaGrad) {
            accumulatedGradientSquares = new HashMap<Integer, Float>();
        } else if (adam) {
            accumulatedGradientMean = new HashMap<Integer, Float>();
            accumulatedGradientVariance = new HashMap<Integer, Float>();
        }

        if (printInitialObj && log.isTraceEnabled()) {
            objective = computeObjective(termStore);
            log.trace("Iteration {} -- Objective: {}, Mean Movement: {}, Iteration Time: {}, Total Optimization Time: {}", 0, objective, 0.0f, 0, 0);
        }

        long totalTime = 0;
        boolean converged = false;
        for (int iteration = 1; iteration < (maxIterations * budget) && !converged; iteration++) {
            long start = System.currentTimeMillis();

            termCount = 0;
            meanMovement = 0.0f;
            objective = 0.0;

            for (SGDObjectiveTerm term : termStore) {
                if (oldVariableValues != null) {
                    objective += term.evaluate(oldVariableValues);
                }

                termCount++;
                meanMovement += term.minimize(termStore.getVariableValues(), iteration, calculateAnnealedLearningRate(iteration),
                        accumulatedGradientSquares, accumulatedGradientMean, accumulatedGradientVariance,
                        adaGrad, adam, coordinateStep);
            }

            termStore.iterationComplete();

            if (termCount != 0) {
                meanMovement /= termCount;
            }

            converged = breakOptimization(objective, oldObjective, meanMovement, termCount);

            // Keep track of the old variables for a deferred objective computation.
            if (oldVariableValues == null) {
                oldVariableValues = Arrays.copyOf(termStore.getVariableValues(), termStore.getVariableValues().length);
            } else {
                System.arraycopy(termStore.getVariableValues(), 0, oldVariableValues, 0, oldVariableValues.length);
                oldObjective = objective;
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;

            if (iteration > 1) {
                if (log.isTraceEnabled()) {
                    log.trace("Iteration {} -- Objective: {}, Normalized Objective: {}, Iteration Time: {}, Total Optimization Time: {}",
                            iteration - 1, objective, objective / termCount, (end - start), totalTime);
                }
            }
        }

        objective = computeObjective(termStore);
        log.info("Final Objective: {}, Final Normalized Objective: {}, Total Optimization Time: {}", objective, objective / termCount, totalTime);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumVariables(), termCount);

        termStore.syncAtoms();

        return objective / termCount;
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

    public double computeObjective(VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore) {
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
