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

import java.util.Iterator;

/**
 * Uses an SGD optimization method to optimize its GroundRules.
 */
public class SGDReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(SGDReasoner.class);

    private int maxIterations;

    private float tolerance;
    private boolean printObj;
    private boolean printInitialObj;
    private boolean objectiveBreak;

    public SGDReasoner() {
        maxIterations = Options.SGD_MAX_ITER.getInt();
        objectiveBreak = Options.SGD_OBJECTIVE_BREAK.getBoolean();
        printObj = Options.SGD_PRINT_OBJECTIVE.getBoolean();
        printInitialObj = Options.SGD_PRINT_INITIAL_OBJECTIVE.getBoolean();
        tolerance = Options.SGD_TOLERANCE.getFloat();
    }

    @Override
    public void optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof VariableTermStore)) {
            throw new IllegalArgumentException("SGDReasoner requires a VariableTermStore (found " + baseTermStore.getClass().getName() + ").");
        }

        @SuppressWarnings("unchecked")
        VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore = (VariableTermStore<SGDObjectiveTerm, RandomVariableAtom>)baseTermStore;

        termStore.initForOptimization();

        float objective = -1.0f;
        float oldObjective = Float.POSITIVE_INFINITY;

        int iteration = 1;
        if (printObj) {
            log.trace("objective:Iterations,Time(ms),Objective");

            if (printInitialObj) {
                objective = computeObjective(termStore);
                log.trace("objective:{},{},{}", 0, 0, objective);
            }
        }

        long time = 0;
        while (iteration <= (int)(maxIterations * budget)
                && (!objectiveBreak || (iteration == 1 || !MathUtils.equals(objective, oldObjective, tolerance)))) {
            long start = System.currentTimeMillis();

            for (SGDObjectiveTerm term : termStore) {
                term.minimize(iteration, termStore);
            }

            long end = System.currentTimeMillis();
            oldObjective = objective;
            objective = computeObjective(termStore);
            time += end - start;

            if (printObj) {
                log.info("objective:{},{},{}", iteration, time, objective);
            }

            iteration++;
            termStore.iterationComplete();
        }

        termStore.syncAtoms();

        log.info("Optimization completed in {} iterations. Objective.: {}", iteration - 1, objective);
        log.debug("Optimized with {} variables and {} terms.", termStore.getNumVariables(), termStore.size());
    }

    public float computeObjective(VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore) {
        float objective = 0.0f;

        // If possible, use a readonly iterator.
        Iterator<SGDObjectiveTerm> termIterator = null;
        if (termStore.isLoaded()) {
            termIterator = termStore.noWriteIterator();
        } else {
            termIterator = termStore.iterator();
        }

        for (SGDObjectiveTerm term : IteratorUtils.newIterable(termIterator)) {
            objective += term.evaluate(termStore);
        }

        return objective / termStore.size();
    }

    @Override
    public void close() {
    }
}
