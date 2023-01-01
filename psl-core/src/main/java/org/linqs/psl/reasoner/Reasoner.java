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
package org.linqs.psl.reasoner;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;

import java.util.List;
import java.util.Set;

/**
 * An oprimizer to minimize the total weighted incompatibility
 * of the terms provided by a TermStore.
 */
public abstract class Reasoner<T extends ReasonerTerm> {
    private static final Logger log = Logger.getLogger(Reasoner.class);

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
    public double optimize(TermStore<T> termStore) {
        return optimize(termStore, null, null);
    }

    /**
     * Minimizes the total weighted incompatibility of the terms in the provided TermStore.
     * If available, use the provided evaluation materials during optimization.
     * @return the objective the reasoner uses.
     */
    public abstract double optimize(TermStore<T> termStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap);

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

    protected void evaluate(TermStore<T> termStore, int iteration, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        if (!evaluate) {
            return;
        }

        if (trainingMap == null || evaluations == null || evaluations.size() == 0) {
            return;
        }

        // Sync variables before evaluation.
        termStore.sync();

        for (EvaluationInstance evaluation : evaluations) {
            evaluation.compute(trainingMap);
            log.info("Iteration {} -- {}.", iteration, evaluation.getOutput());
        }
    }
}
