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
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    protected float[][] workerRVAtomGradients;
    protected float[][] workerDeepGradients;

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

        workerRVAtomGradients = null;
        workerDeepGradients = null;
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

    public void clear() {
        prevVariableValues = null;

        workerRVAtomGradients = null;
        workerDeepGradients = null;
    }

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
            ObjectiveResult objective = null;
            if (termStore instanceof StreamingTermStore) {
                // Grounding will happen in this call to computeObjective().
                objective = computeObjective(termStore);
            } else {
                objective = parallelComputeObjective(termStore);
            }
            log.trace("Iteration {} -- Objective: {}, Violated Constraints: {}, Total Optimization Time: {}, Total Number of Iterations: {}.",
                    0, objective.objective, objective.violatedConstraints, 0, 0);
        }
    }

    protected void optimizationComplete(TermStore<T> termStore, ObjectiveResult finalObjective, long totalTime) {
        float change = (float)termStore.sync();

        log.info("Final Objective: {}, Violated Constraints: {}, Total Optimization Time: {}",
                finalObjective.objective, finalObjective.violatedConstraints, totalTime);
        log.debug("Movement of variables from initial state: {}", change);

        clear();
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
     * Compute the (sub)gradient of the optimal value of the energy function with respect to the variables.
     * This method does not consider the constraints and it is therefore not guaranteed to be the smallest magnitude subgradient.
     * This method assumes that the terms and variables are already optimized.
     */
    public void computeOptimalValueGradient(TermStore<T> termStore, float[] rvAtomGradient, float[] deepAtomGradient) {
        parallelComputeGradient(termStore, rvAtomGradient, deepAtomGradient);
    }

    /**
     * Compute the (sub)gradient of the energy function with respect to the variables.
     * This method does not consider the constraints and it is therefore not guaranteed to be the subgradient
     * with the smallest magnitude. Therefore, this gradient can only be used for estimating distance to optimality
     * after passing it through the clipGradient method to account for the standard [0, 1] box constraints.
     * Moreover, there should be no constraints other than the box constraints
     */
    public void parallelComputeGradient(TermStore<T> termStore, float[] rvAtomGradient, float[] deepAtomGradient) {
        int blockSize = (int)(termStore.size() / (Parallel.getNumThreads() * 4) + 1);
        int numTermBlocks = (int)Math.ceil(termStore.size() / (double)blockSize);

        if ((workerRVAtomGradients == null) || (workerRVAtomGradients.length < numTermBlocks)
                || (workerRVAtomGradients[0].length < rvAtomGradient.length)
                || (workerDeepGradients == null) || (workerDeepGradients.length < numTermBlocks)
                || (workerDeepGradients[0].length < deepAtomGradient.length)) {
            workerRVAtomGradients = new float[(int)numTermBlocks][];
            workerDeepGradients = new float[(int)numTermBlocks][];
            for (int i = 0; i < numTermBlocks; i++) {
                workerRVAtomGradients[i] = new float[rvAtomGradient.length];
                workerDeepGradients[i] = new float[deepAtomGradient.length];
            }
        }

        Parallel.count(numTermBlocks, new GradientWorker(termStore, workerRVAtomGradients, workerDeepGradients, blockSize));

        Arrays.fill(rvAtomGradient, 0.0f);
        Arrays.fill(deepAtomGradient, 0.0f);
        for(int j = 0; j < numTermBlocks; j++) {
            for(int i = 0; i <= termStore.getAtomStore().getMaxRVAIndex(); i++) {
                rvAtomGradient[i] += workerRVAtomGradients[j][i];
                deepAtomGradient[i] += workerDeepGradients[j][i];
            }
        }
    }

    /**
     * Clip the (sub)gradient to account for [0, 1] box constraints.
     */
    protected void clipGradient(float[] variableValues, float[] gradient) {
        for (int i = 0; i < gradient.length; i++) {
            if (MathUtils.equals(variableValues[i], 0.0f) && gradient[i] > 0.0f) {
                gradient[i] = 0.0f;
            } else if (MathUtils.equals(variableValues[i], 1.0f) && gradient[i] < 0.0f) {
                gradient[i] = 0.0f;
            }
        }
    }

    /**
     * Clip (sub)gradient magnitude.
     */
    protected void clipGradientMagnitude(float[] gradient, float maxMagnitude) {
        float maxGradient = 0.0f;
        for (int i = 0; i < gradient.length; i++) {
            if (Math.abs(gradient[i]) > maxGradient) {
                maxGradient = Math.abs(gradient[i]);
            }
        }

        if (maxGradient > maxMagnitude) {
            for (int i = 0; i < gradient.length; i++) {
                gradient[i] = gradient[i] * maxMagnitude / maxGradient;
            }
        }
    }

    /**
     * Compute the total weighted objective of the terms in their current state.
     */
    public static ObjectiveResult computeObjective(TermStore<? extends ReasonerTerm> termStore) {
        float objective = 0.0f;
        long violatedConstraints = 0;
        float[] variableValues = termStore.getVariableValues();

        for (ReasonerTerm term : termStore) {
            if (!term.isActive()) {
                continue;
            }

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

    protected static ObjectiveResult computeComponentObjective(TermStore<? extends ReasonerTerm> termStore, int componentIndex) {
        assert (termStore instanceof SimpleTermStore);

        SimpleTermStore<? extends ReasonerTerm> simpleTermStore = (SimpleTermStore<? extends ReasonerTerm>)termStore;

        List<? extends ReasonerTerm> component = simpleTermStore.getConnectedComponents().get(componentIndex);

        float objective = 0.0f;
        long violatedConstraints = 0;
        float[] variableValues = termStore.getVariableValues();

        for (ReasonerTerm term : component) {
            if (!term.isActive()) {
                continue;
            }

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

    /**
     * Compute the total weighted objective of the terms in their current state in a distributed manner.
     * This method cannot be called with a StreamingTermStore.
     */
    public ObjectiveResult parallelComputeObjective(TermStore<T> termStore) {
        assert (termStore instanceof SimpleTermStore);

        SimpleTermStore<? extends ReasonerTerm> simpleTermStore = (SimpleTermStore<? extends ReasonerTerm>)termStore;

        int blockSize = (int)(simpleTermStore.size() / (Parallel.getNumThreads() * 4) + 1);
        int numTermBlocks = (int)Math.ceil(simpleTermStore.size() / (double)blockSize);

        float[] workerObjectives = new float[numTermBlocks];
        int[] workerViolatedConstraints = new int[numTermBlocks];

        Parallel.count(numTermBlocks, new ObjectiveWorker(
                simpleTermStore.getAllTerms(), simpleTermStore.getVariableValues(),
                workerObjectives, workerViolatedConstraints, blockSize)
        );

        float objective = 0.0f;
        int violatedConstraints = 0;
        for (int i = 0; i < numTermBlocks; i++) {
            objective += workerObjectives[i];
            violatedConstraints += workerViolatedConstraints[i];
        }

        return new ObjectiveResult(objective, violatedConstraints);
    }

    protected ObjectiveResult parallelComputeComponentObjective(TermStore<T> termStore, int componentIndex) {
        assert (termStore instanceof SimpleTermStore);

        SimpleTermStore<T> simpleTermStore = (SimpleTermStore<T>)termStore;

        Map<Integer, List<T>> connectedComponents = simpleTermStore.getConnectedComponents();
        List<T> component = connectedComponents.get(componentIndex);

        int blockSize = (int)(component.size() / (Parallel.getNumThreads() * 4) + 1);
        int numTermBlocks = (int)Math.ceil(component.size() / (double)blockSize);

        float[] workerObjectives = new float[numTermBlocks];
        int[] workerViolatedConstraints = new int[numTermBlocks];

        Parallel.count(numTermBlocks, new ObjectiveWorker(component, simpleTermStore.getVariableValues(), workerObjectives, workerViolatedConstraints, blockSize));

        float objective = 0.0f;
        int violatedConstraints = 0;
        for (int i = 0; i < numTermBlocks; i++) {
            objective += workerObjectives[i];
            violatedConstraints += workerViolatedConstraints[i];
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

    private static class GradientWorker extends Parallel.Worker<Long> {
        private final TermStore termStore;
        private final int blockSize;
        private final float[] variableValues;
        private final GroundAtom[] variableAtoms;
        private final float[][] rvAtomGradients;
        private final float[][] deepAtomGradients;

        public GradientWorker(TermStore termStore,
                              float[][] rvAtomGradients, float[][] deepAtomGradients, int blockSize) {
            super();

            this.termStore = termStore;
            this.variableValues = termStore.getVariableValues();
            this.variableAtoms = termStore.getVariableAtoms();
            this.rvAtomGradients = rvAtomGradients;
            this.deepAtomGradients = deepAtomGradients;
            this.blockSize = blockSize;
        }

        @Override
        public Object clone() {
            return new GradientWorker(termStore, rvAtomGradients, deepAtomGradients, blockSize);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            long numTerms = termStore.size();

            Arrays.fill(rvAtomGradients[(int)blockIndex], 0.0f);
            Arrays.fill(deepAtomGradients[(int)blockIndex], 0.0f);
            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int termIndex = (int)(blockIndex * blockSize + innerBlockIndex);

                if (termIndex >= numTerms) {
                    break;
                }

                ReasonerTerm term = termStore.get(termIndex);

                if (!term.isActive()) {
                    continue;
                }

                if (term.isConstraint()) {
                    continue;
                }

                int[] atomIndexes = term.getAtomIndexes();
                float innerPotential = term.computeInnerPotential(variableValues);

                for (int i = 0; i < term.size(); i++) {
                    if (variableAtoms[atomIndexes[i]] instanceof ObservedAtom) {
                        continue;
                    }

                    if (variableAtoms[atomIndexes[i]].getPredicate() instanceof DeepPredicate) {
                        deepAtomGradients[(int)blockIndex][atomIndexes[i]] += term.computeVariablePartial(i, innerPotential);
                        continue;
                    }

                    rvAtomGradients[(int)blockIndex][atomIndexes[i]] += term.computeVariablePartial(i, innerPotential);
                }
            }
        }
    }

    private static class ObjectiveWorker extends Parallel.Worker<Long> {
        private final List<? extends ReasonerTerm> terms;
        private final int blockSize;
        private final float[] variableValues;
        private final float[] objectives;
        private final int[] violatedConstraints;

        public ObjectiveWorker(List<? extends ReasonerTerm> terms, float[] variableValues,
                               float[] objectives, int[] violatedConstraints, int blockSize) {
            super();

            this.terms = terms;
            this.variableValues = variableValues;
            this.objectives = objectives;
            this.violatedConstraints = violatedConstraints;
            this.blockSize = blockSize;
        }

        @Override
        public Object clone() {
            return new ObjectiveWorker(terms, variableValues, objectives, violatedConstraints, blockSize);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            int numTerms = (int)terms.size();
            float objective = 0.0f;
            int violatedConstraints = 0;

            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int termIndex = (int)(blockIndex * blockSize + innerBlockIndex);

                if (termIndex >= numTerms) {
                    break;
                }

                ReasonerTerm term = terms.get(termIndex);

                if (!term.isActive()) {
                    continue;
                }

                if (term.isConstraint()) {
                    if(!MathUtils.isZero(term.evaluate(variableValues))) {
                        violatedConstraints++;
                    }
                } else {
                    objective += term.evaluate(variableValues);
                }
            }
            objectives[(int)blockIndex] = objective;
            this.violatedConstraints[(int)blockIndex] = violatedConstraints;
        }
    }


    public static class ObjectiveResult {
        public float objective;
        public long violatedConstraints;

        public ObjectiveResult(float objective, long violatedConstraints) {
            this.objective = objective;
            this.violatedConstraints = violatedConstraints;
        }
    }
}
