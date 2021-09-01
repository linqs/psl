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
package org.linqs.psl.reasoner;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * An oprimizer to minimize the total weighted incompatibility
 * of the terms provided by a TermStore.
 */
public abstract class Reasoner {
    private static final Logger log = LoggerFactory.getLogger(Reasoner.class);

    protected double budget;

    protected boolean evaluate;
    protected boolean objectiveBreak;
    protected boolean runFullIterations;

    protected float tolerance;

    protected boolean nonconvex;
    protected int nonconvexPeriod;
    protected int nonconvexRounds;

    public Reasoner() {
        budget = 1.0;

        evaluate = Options.REASONER_EVALUATE.getBoolean();
        objectiveBreak = Options.REASONER_OBJECTIVE_BREAK.getBoolean();
        runFullIterations = Options.REASONER_RUN_FULL_ITERATIONS.getBoolean();

        tolerance = Options.REASONER_TOLERANCE.getFloat();

        nonconvex = Options.REASONER_NONCONVEX.getBoolean();
        nonconvexPeriod = Options.REASONER_NONCONVEX_PERIOD.getInt();
        nonconvexRounds = Options.REASONER_NONCONVEX_ROUNDS.getInt();
    }

    /**
     * Optimize without any evaluation.
     */
    public double optimize(TermStore termStore) {
        return optimize(termStore, null, null, null);
    }

    /**
     * Minimizes the total weighted incompatibility of the terms in the provided TermStore.
     * If available, use the provided evaluation materials during optimization.
     * @return the objective the reasoner uses.
     */
    public abstract double optimize(TermStore termStore,
            List<Evaluator> evaluators, TrainingMap trainingMap, Set<StandardPredicate> evaluationPredicates);

    /**
     * Releases all resources acquired by this Reasoner.
     */
    public abstract void close();

    /**
     * Set a budget (given as a proportion of the max budget).
     */
    public void setBudget(double budget) {
        this.budget = budget;
    }

    protected void evaluate(TermStore termStore, int iteration,
            List<Evaluator> evaluators, TrainingMap trainingMap, Set<StandardPredicate> evaluationPredicates) {
        if (!evaluate) {
            return;
        }

        if (trainingMap == null
                || evaluators == null || evaluators.size() == 0
                || evaluationPredicates == null || evaluationPredicates.size() == 0) {
            return;
        }

        // Sync variables before evaluation.
        termStore.syncAtoms();

        for (Evaluator evaluator : evaluators) {
            for (StandardPredicate predicate : evaluationPredicates) {
                evaluator.compute(trainingMap, predicate);
                log.info(
                        "Iteration {} -- Evaluator: {}, Predicate: {}, Results -- {}.",
                        iteration, evaluator.getClass().getSimpleName(), predicate.getName(), evaluator.getAllStats());
            }
        }
    }
}
