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
package org.linqs.psl.reasoner.dcd;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.dcd.term.DCDObjectiveTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Uses an SGD optimization method to optimize its GroundRules.
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
        VariableTermStore<DCDObjectiveTerm, RandomVariableAtom> termStore = (VariableTermStore<DCDObjectiveTerm, RandomVariableAtom>)baseTermStore;

        termStore.initForOptimization();

        // This must be called after the term store has to correct variable capacity.
        // A reallocation can cause this array to become out-of-date.
        float[] variableValues = termStore.getVariableValues();

        double objective = -1.0;
        double oldObjective = Double.POSITIVE_INFINITY;

        if (printInitialObj && log.isTraceEnabled()) {
            objective = computeObjective(termStore, variableValues);
            log.trace("Iteration {} -- Objective: {}, Iteration Time: {}, Total Optimiztion Time: {}", 0, objective, 0, 0);
        }

        int iteration = 1;
        long totalTime = 0;
        while (true) {
            long start = System.currentTimeMillis();

            for (DCDObjectiveTerm term : termStore) {
                term.minimize(truncateEveryStep, variableValues);
            }

            // If we are truncating every step, then the variables are already in valid state.
            if (!truncateEveryStep) {
                for (RandomVariableAtom variable : termStore.getVariables()) {
                    variable.setValue(Math.max(Math.min(variable.getValue(), 1.0f), 0.0f));
                }
            }

            long end = System.currentTimeMillis();

            oldObjective = objective;
            objective = computeObjective(termStore, variableValues);
            totalTime += end - start;

            if (log.isTraceEnabled()) {
                log.trace("Iteration {} -- Objective: {}, Iteration Time: {}, Total Optimiztion Time: {}",
                        iteration, objective, (end - start), totalTime);
            }

            iteration++;
            termStore.iterationComplete();

            if (breakOptimization(iteration, objective, oldObjective)) {
                break;
            }
        }

        log.info("Optimization completed in {} iterations. Objective: {}, Total Optimiztion Time: {}",
                iteration - 1, objective, totalTime);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumVariables(), termStore.size());

        termStore.syncAtoms();

        return objective;
    }

    private boolean breakOptimization(int iteration, double objective, double oldObjective) {
        // Always break when the allocated iterations is up.
        if (iteration > (int)(maxIterations * budget)) {
            return true;
        }

        // Run through the maximum number of iterations.
        if (runFullIterations) {
            return false;
        }

        // Break if the objective has not changed.
        if (objectiveBreak && MathUtils.equals(objective, oldObjective, tolerance)) {
            return true;
        }

        return false;
    }

    private double computeObjective(VariableTermStore<DCDObjectiveTerm, RandomVariableAtom> termStore, float[] variableValues) {
        double objective = 0.0;

        // If possible, use a readonly iterator.
        Iterator<DCDObjectiveTerm> termIterator = null;
        if (termStore.isLoaded()) {
            termIterator = termStore.noWriteIterator();
        } else {
            termIterator = termStore.iterator();
        }

        for (DCDObjectiveTerm term : IteratorUtils.newIterable(termIterator)) {
            objective += term.evaluate(variableValues) / c;
        }

        return objective;
    }

    @Override
    public void close() {
    }
}
