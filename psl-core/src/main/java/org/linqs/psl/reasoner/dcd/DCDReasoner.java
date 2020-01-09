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

import org.linqs.psl.config.Config;
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
public class DCDReasoner implements Reasoner {
    private static final Logger log = LoggerFactory.getLogger(DCDReasoner.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "dcd";

    /**
     * The maximum number of iterations of SGD to perform in a round of inference.
     */
    public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiterations";
    public static final int MAX_ITER_DEFAULT = 200;

    /**
     * Stop if the objective has not changed since the last logging period (see LOG_PERIOD).
     */
    public static final String OBJECTIVE_BREAK_KEY = CONFIG_PREFIX + ".objectivebreak";
    public static final boolean OBJECTIVE_BREAK_DEFAULT = true;

    /**
     * The maximum number of iterations of SGD to perform in a round of inference.
     */
    public static final String OBJ_TOL_KEY = CONFIG_PREFIX + ".tolerance";
    public static final float OBJ_TOL_DEFAULT = 0.000001f;

    public static final String C_KEY = CONFIG_PREFIX + ".C";
    public static final float C_DEFAULT = 10.0f;

    public static final String TRUNCATE_EVERY_STEP_KEY = CONFIG_PREFIX + ".truncateeverystep";
    public static final boolean TRUNCATE_EVERY_STEP_DEFAULT = false;

    public static final String PRINT_OBJECTIVE_KEY = CONFIG_PREFIX + ".printobj";
    public static final boolean PRINT_OBJECTIVE_DEFAULT = true;

    /**
     * Print the objective before any optimization.
     * Note that this will require a pass through all the terms,
     * and therefore may affect performance.
     * Has no effect if printobj is false.
     */
    public static final String PRINT_INITIAL_OBJECTIVE_KEY = CONFIG_PREFIX + ".printinitialobj";
    public static final boolean PRINT_INITIAL_OBJECTIVE_DEFAULT = false;

    private int maxIter;

    private float tolerance;
    private boolean printObj;
    private boolean printInitialObj;
    private boolean objectiveBreak;
    private float c;
    private boolean truncateEveryStep;

    public DCDReasoner() {
        maxIter = Config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
        objectiveBreak = Config.getBoolean(OBJECTIVE_BREAK_KEY, OBJECTIVE_BREAK_DEFAULT);
        printObj = Config.getBoolean(PRINT_OBJECTIVE_KEY, PRINT_OBJECTIVE_DEFAULT);
        printInitialObj = Config.getBoolean(PRINT_INITIAL_OBJECTIVE_KEY, PRINT_INITIAL_OBJECTIVE_DEFAULT);
        tolerance = Config.getFloat(OBJ_TOL_KEY, OBJ_TOL_DEFAULT);
        c = Config.getFloat(C_KEY, C_DEFAULT);
        truncateEveryStep = Config.getBoolean(TRUNCATE_EVERY_STEP_KEY, TRUNCATE_EVERY_STEP_DEFAULT);
    }

    public int getMaxIter() {
        return maxIter;
    }

    public void setMaxIter(int maxIter) {
        this.maxIter = maxIter;
    }

    @Override
    public void optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof VariableTermStore)) {
            throw new IllegalArgumentException("DCDReasoner requires an VariableTermStore (found " + baseTermStore.getClass().getName() + ").");
        }

        @SuppressWarnings("unchecked")
        VariableTermStore<DCDObjectiveTerm, RandomVariableAtom> termStore = (VariableTermStore<DCDObjectiveTerm, RandomVariableAtom>)baseTermStore;

        // This must be called after the term store has to correct variable capacity.
        // A reallocation can cause this array to become out-of-date.
        float[] variableValues = termStore.getVariableValues();

        float objective = -1.0f;
        float oldObjective = Float.POSITIVE_INFINITY;

        int iteration = 1;
        if (printObj) {
            log.trace("objective:Iterations,Time(ms),Objective");

            if (printInitialObj) {
                objective = computeObjective(termStore, variableValues);
                log.trace("objective:{},{},{}", 0, 0, objective);
            }
        }

        long time = 0;
        while (iteration <= maxIter
                && (!objectiveBreak || (iteration == 1 || !MathUtils.equals(objective, oldObjective, tolerance)))) {
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
            time += end - start;

            if (printObj) {
                log.trace("objective:{},{},{}", iteration, time, objective);
            }

            iteration++;
        }

        termStore.syncAtoms();

        log.info("Optimization completed in {} iterations. Objective.: {}", iteration - 1, objective);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumVariables(), termStore.size());
    }

    private float computeObjective(VariableTermStore<DCDObjectiveTerm, RandomVariableAtom> termStore, float[] variableValues) {
        float objective = 0.0f;
        int termCount = 0;

        // If possible, use a readonly iterator.
        Iterator<DCDObjectiveTerm> termIterator = null;
        if (termStore.isLoaded()) {
            termIterator = termStore.noWriteIterator();
        } else {
            termIterator = termStore.iterator();
        }

        for (DCDObjectiveTerm term : IteratorUtils.newIterable(termIterator)) {
            objective += term.evaluate(variableValues) / c;
            termCount++;
        }

        return objective / termCount;
    }

    @Override
    public void close() {
    }
}
