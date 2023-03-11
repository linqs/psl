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
import org.linqs.psl.util.MathUtils;

import java.util.Arrays;
import java.util.List;

/**
 * An optimizer to minimize the total weighted incompatibility
 * of the terms provided by a TermStore.
 */
public abstract class Reasoner<T extends ReasonerTerm> {
    private static final Logger log = Logger.getLogger(Reasoner.class);

    protected double budget;

    protected boolean evaluate;

    protected int maxIterations;
    protected boolean runFullIterations;
    protected boolean objectiveBreak;
    protected float objectiveTolerance;
    protected boolean variableMovementBreak;
    protected float variableMovementTolerance;
    protected float variableMovementNorm;

    protected float[] prevVariableValues;

    public Reasoner() {
        budget = 1.0;

        evaluate = Options.REASONER_EVALUATE.getBoolean();

        runFullIterations = Options.REASONER_RUN_FULL_ITERATIONS.getBoolean();
        objectiveBreak = Options.REASONER_OBJECTIVE_BREAK.getBoolean();
        objectiveTolerance = Options.REASONER_OBJECTIVE_TOLERANCE.getFloat();
        variableMovementBreak = Options.REASONER_VARIABLE_MOVEMENT_BREAK.getBoolean();
        variableMovementTolerance = Options.REASONER_VARIABLE_MOVEMENT_TOLERANCE.getFloat();
        variableMovementNorm = Options.REASONER_VARIABLE_MOVEMENT_NORM.getFloat();

        prevVariableValues = null;
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
    public void close() {}

    /**
     * Set a budget (given as a proportion of the max budget).
     */
    public void setBudget(double budget) {
        this.budget = budget;
    }

    protected void initForOptimization(TermStore<T> termStore) {
        log.debug("Performing optimization with {} variables and {} terms.",
                termStore.getVariableCounts(), termStore.size());

        if (log.isTraceEnabled()) {
            // If a streaming term store is being used,
            // then grounding will happen in this call to computeObjective().
            ObjectiveResult objective = computeObjective(termStore);
            log.trace("Iteration {} -- Objective: {}, Feasible: {}.",
                    0, objective.objective, (objective.violatedConstraints == 0));
        }
    }

    protected void optimizationComplete(TermStore<T> termStore, ObjectiveResult finalObjective,
                                        long totalTime, int iteration) {
        float change = (float)termStore.sync();

        log.info("Final Objective: {}, Violated Constraints: {}, Total Optimization Time: {}, Total Number of Iterations: {}",
                finalObjective.objective, finalObjective.violatedConstraints, totalTime, iteration);
        log.debug("Movement of variables from initial state: {}", change);
    }

    /**
     * Determine if the stopping criterion has been met and optimization should be stopped.
     */
    protected boolean breakOptimization(int iteration, TermStore<T> termStore,
                                        ObjectiveResult objective, ObjectiveResult oldObjective) {
        // Always break when the allocated iterations is up.
        if (iteration > (int)(maxIterations * budget)) {
            log.trace("Breaking optimization. Max iterations exceeded.");
            return true;
        }

        // Run through the maximum number of iterations.
        if (runFullIterations) {
            return false;
        }

        // Don't break if there are violated constraints.
        if (objective != null && objective.violatedConstraints > 0) {
            return false;
        }

        // Break if the objective has not changed.
        if (objectiveBreak && (objective != null) && (oldObjective != null)
                && MathUtils.equals(objective.objective, oldObjective.objective, objectiveTolerance)) {
            log.trace("Breaking optimization. Objective change: {} below tolerance: {}.",
                    Math.abs(objective.objective - oldObjective.objective), objectiveTolerance);
            return true;
        }

        // Break if two consecutive iterates are less than the variable movement tolerance.
        if (variableMovementBreak) {
            float[] variableValues = termStore.getVariableValues();

            if (prevVariableValues != null) {
                float[] movement = Arrays.copyOf(prevVariableValues, prevVariableValues.length);
                for (int i = 0; i < prevVariableValues.length; i++) {
                    movement[i] = prevVariableValues[i] - variableValues[i];
                }

                float distance = MathUtils.pNorm(movement, variableMovementNorm);
                if (distance < variableMovementTolerance) {
                    log.trace("Breaking optimization. Movement of variables: {} below tolerance: {}.",
                            distance, variableMovementTolerance);
                    return true;
                }
            }

            prevVariableValues = Arrays.copyOf(variableValues, variableValues.length);
        }

        return false;
    }

    /**
     * Compute the total weighted objective of the terms in their current state.
     */
    protected ObjectiveResult computeObjective(TermStore<T> termStore) {
        float objective = 0.0f;
        long violatedConstraints = 0;
        float[] variableValues = termStore.getVariableValues();

        for (ReasonerTerm term : termStore) {
            if (term.isConstraint()) {
                if (term.evaluate(variableValues) > 0.0f) {
                    violatedConstraints++;
                }
            } else {
                objective += term.evaluate(variableValues);
            }
        }

        return new ObjectiveResult(objective, violatedConstraints);
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

    protected static class ObjectiveResult {
        public final float objective;
        public final long violatedConstraints;

        public ObjectiveResult(float objective, long violatedConstraints) {
            this.objective = objective;
            this.violatedConstraints = violatedConstraints;
        }
    }
}
