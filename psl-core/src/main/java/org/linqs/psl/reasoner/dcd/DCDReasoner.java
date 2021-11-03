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

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.dcd.term.DCDObjectiveTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Uses a DCD optimization method to optimize its GroundRules.
 */
public class DCDReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(DCDReasoner.class);

    private final int maxIterations;

    private final float c;
    private final boolean truncateEveryStep;

    public DCDReasoner() {
        maxIterations = Options.DCD_MAX_ITER.getInt();
        c = Options.DCD_C.getFloat();
        truncateEveryStep = Options.DCD_TRUNCATE_EVERY_STEP.getBoolean();
    }

    @Override
    public double optimize(TermStore baseTermStore,
            List<Evaluator> evaluators, TrainingMap trainingMap, Set<StandardPredicate> evaluationPredicates) {

        @SuppressWarnings("unchecked")
        TermStore<DCDObjectiveTerm, GroundAtom> termStore = (TermStore<DCDObjectiveTerm, GroundAtom>)baseTermStore;

        termStore.initForOptimization();

        long termCount = 0;
        double change = 0.0;
        double objective = Double.POSITIVE_INFINITY;
        // Starting on the second iteration, keep track of the previous iteration's objective value.
        // The atom values from the term store cannot be used to calculate the objective during an
        // optimization pass because they are being updated in the atomUpdate() method.
        // Note that the number of atoms may change in the first iteration (since grounding may happen then).
        double oldObjective = Double.POSITIVE_INFINITY;
        float[] oldValues = null;

        long totalTime = 0;
        boolean breakDCD = false;
        int iteration = 1;

        while(!breakDCD) {
            long start = System.currentTimeMillis();

            termCount = 0;
            objective = 0.0;

            for (DCDObjectiveTerm term : termStore) {
                if (iteration > 1) {
                    objective += term.evaluate(oldValues) / c;
                }

                termCount++;
                atomUpdate(term, termStore);
            }

            // If we are truncating every step, then the atoms are already in valid state.
            if (!truncateEveryStep) {
                float[] atomValues = termStore.getAtomValues();
                for (int i = 0; i < termStore.getNumAtoms(); i++) {
                    atomValues[i] = Math.max(0.0f, Math.min(1.0f, atomValues[i]));
                }
            }

            evaluate(termStore, iteration, evaluators, trainingMap, evaluationPredicates);

            termStore.iterationComplete();

            breakDCD = breakOptimization(iteration, objective, oldObjective, termCount);

            if (iteration == 1) {
                // Initialize old atom values.
                oldValues = Arrays.copyOf(termStore.getAtomValues(), termStore.getAtomValues().length);
            } else {
                // Update old atom values and objective.
                System.arraycopy(termStore.getAtomValues(), 0, oldValues, 0, oldValues.length);
                oldObjective = objective;
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;

            if (iteration > 1 && log.isTraceEnabled()) {
                log.trace("Iteration {} -- Objective: {}, Normalized Objective: {}, Iteration Time: {}, Total Optimization Time: {}",
                        iteration - 1, objective, objective / termCount, (end - start), totalTime);
            }

            iteration++;
        }

        objective = computeObjective(termStore);
        change = termStore.syncAtoms();

        log.info("Final Objective: {}, Final Normalized Objective: {}, Total Optimization Time: {}, Total Number of Iterations: {}", objective, objective / termCount, totalTime, iteration);
        log.debug("Movement of atoms from initial state: {}", change);
        log.debug("Optimized with {} atoms and {} terms.", termStore.getNumRandomVariableAtoms(), termCount);

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
        if (objectiveBreak && MathUtils.equals(objective / termCount, oldObjective / termCount, tolerance)) {
            return true;
        }

        return false;
    }

    private double computeObjective(TermStore<DCDObjectiveTerm, GroundAtom> termStore) {
        double objective = 0.0;

        // If possible, use a readonly iterator.
        Iterator<DCDObjectiveTerm> termIterator = null;
        if (termStore.isLoaded()) {
            termIterator = termStore.noWriteIterator();
        } else {
            termIterator = termStore.iterator();
        }

        for (DCDObjectiveTerm term : IteratorUtils.newIterable(termIterator)) {
            objective += term.evaluate(termStore.getAtomValues()) / c;
        }

        return objective;
    }

    private void atomUpdate(DCDObjectiveTerm term, TermStore<DCDObjectiveTerm, GroundAtom> termStore) {
        GroundAtom[] atoms = termStore.getAtoms();
        float[] values = termStore.getAtomValues();

        WeightedRule rule = term.getRule();

        float adjustedWeight = rule.getWeight() * c;
        float gradient = term.computeGradient(values);

        if (term.isSquared()) {
            gradient += term.getLagrange() / (2.0f * adjustedWeight);
            atomUpdate(term, gradient, adjustedWeight, Float.POSITIVE_INFINITY, values, atoms);
        } else {
            atomUpdate(term, gradient, adjustedWeight, adjustedWeight, values, atoms);
        }
    }

    private void atomUpdate(DCDObjectiveTerm term, float gradient, float adjustedWeight, float lim, float[] values, GroundAtom[] atoms) {
        float pg = gradient;

        if (MathUtils.isZero(term.getLagrange())) {
            pg = Math.min(0.0f, gradient);
        }

        if (MathUtils.equals(lim, adjustedWeight) && MathUtils.equals(term.getLagrange(), adjustedWeight)) {
            pg = Math.max(0.0f, gradient);
        }

        if (MathUtils.isZero(pg)) {
            return;
        }

        float pa = term.getLagrange();
        int[] atomIndexes = term.getAtomIndexes();
        float[] coefficients = term.getCoefficients();

        term.setLagrange(Math.min(lim, Math.max(0.0f, term.getLagrange() - gradient / term.getQii())));

        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]] instanceof ObservedAtom) {
                continue;
            }

            float val = values[atomIndexes[i]] - ((term.getLagrange() - pa) * coefficients[i]);
            if (truncateEveryStep) {
                val = Math.max(0.0f, Math.min(1.0f, val));
            }
            values[atomIndexes[i]] = val;
        }
    }

    @Override
    public void close() {
    }
}
