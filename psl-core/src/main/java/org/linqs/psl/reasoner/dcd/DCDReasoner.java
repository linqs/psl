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
package org.linqs.psl.reasoner.dcd;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.dcd.term.DCDObjectiveTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Uses a DCD optimization method to optimize its GroundRules.
 */
public class DCDReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(DCDReasoner.class);

    private int maxIterations;

    private float c;
    private boolean truncateEveryStep;

    public DCDReasoner() {
        maxIterations = Options.DCD_MAX_ITER.getInt();
        c = Options.DCD_C.getFloat();
        truncateEveryStep = Options.DCD_TRUNCATE_EVERY_STEP.getBoolean();
    }

    @Override
    public double optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof VariableTermStore)) {
            throw new IllegalArgumentException("DCDReasoner requires an VariableTermStore (found " + baseTermStore.getClass().getName() + ").");
        }

        @SuppressWarnings("unchecked")
        VariableTermStore<DCDObjectiveTerm, GroundAtom> termStore = (VariableTermStore<DCDObjectiveTerm, GroundAtom>)baseTermStore;

        termStore.initForOptimization();

        long termCount = 0;
        double objective = Double.POSITIVE_INFINITY;

        // Starting the second round of iteration, keep track of the old objective.
        // Note that the number of variables may change in the first iteration (since grounding happens then).
        double oldObjective = Double.POSITIVE_INFINITY;
        float[] oldVariableValues = null;

        int iteration = 1;
        long totalTime = 0;
        while (true) {
            long start = System.currentTimeMillis();

            termCount = 0;
            objective = 0.0;

            for (DCDObjectiveTerm term : termStore) {
                if (oldVariableValues != null) {
                    objective += term.evaluate(oldVariableValues);
                }

                termCount++;
                term.minimize(truncateEveryStep, termStore.getVariableValues(), termStore.getVariableAtoms());
            }

            // If we are truncating every step, then the variables are already in valid state.
            if (!truncateEveryStep) {
                float[] variableValues = termStore.getVariableValues();
                for (int i = 0; i < termStore.getNumVariables(); i++) {
                    variableValues[i] = Math.max(0.0f, Math.min(1.0f, variableValues[i]));
                }
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;

            if (log.isTraceEnabled()) {
                log.trace("Iteration {} -- Objective: {}, Normalized Objective: {}, Iteration Time: {}, Total Optimization Time: {}",
                        iteration, objective, objective / termCount, (end - start), totalTime);
            }

            iteration++;
            termStore.iterationComplete();

            if (breakOptimization(iteration, objective, oldObjective, termCount)) {
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
        }

        objective = computeObjective(termStore);
        log.info("Optimization completed in {} iterations. Objective: {}, Normalized Objective: {}, Total Optimization Time: {}",
                iteration, objective, objective / termCount, totalTime);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumRandomVariables(), termCount);

        termStore.syncAtoms();

        return objective;
    }

    private boolean breakOptimization(int iteration, double objective, double oldObjective, long termCount) {
        // Always break when the allocated iterations is up.
        if (iteration > (int)(maxIterations * budget)) {
            return true;
        }

        // Run through the maximum number of iterations.
        if (runFullIterations) {
            return false;
        }

        // Break if the objective has not changed.
        if (oldObjective != Double.POSITIVE_INFINITY && objectiveBreak && MathUtils.equals(objective / termCount, oldObjective / termCount, tolerance)) {
            return true;
        }

        return false;
    }

    private double computeObjective(VariableTermStore<DCDObjectiveTerm, GroundAtom> termStore) {
        double objective = 0.0;

        // If possible, use a readonly iterator.
        Iterator<DCDObjectiveTerm> termIterator = null;
        if (termStore.isLoaded()) {
            termIterator = termStore.noWriteIterator();
        } else {
            termIterator = termStore.iterator();
        }

        for (DCDObjectiveTerm term : IteratorUtils.newIterable(termIterator)) {
            objective += term.evaluate(termStore.getVariableValues()) / c;
        }

        return objective;
    }

    @Override
    public void close() {
    }
}
