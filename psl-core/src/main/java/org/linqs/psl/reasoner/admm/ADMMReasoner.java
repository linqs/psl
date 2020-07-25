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
package org.linqs.psl.reasoner.admm;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.term.ADMMObjectiveTerm;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.LocalVariable;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses an ADMM optimization method to optimize its GroundRules.
 */
public class ADMMReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(ADMMReasoner.class);

    private static final float LOWER_BOUND = 0.0f;
    private static final float UPPER_BOUND = 1.0f;

    private int computePeriod;

    /**
     * Sometimes called eta or rho,
     */
    private final float stepSize;

    private double epsilonRel;
    private double epsilonAbs;

    private double primalRes;
    private double epsilonPrimal;
    private double dualRes;
    private double epsilonDual;

    private double AxNorm;
    private double AyNorm;
    private double BzNorm;
    private double lagrangePenalty;
    private double augmentedLagrangePenalty;

    private int maxIterations;

    private long termBlockSize;
    private long variableBlockSize;

    public ADMMReasoner() {
        maxIterations = Options.ADMM_MAX_ITER.getInt();
        stepSize = Options.ADMM_STEP_SIZE.getFloat();
        computePeriod = Options.ADMM_COMPUTE_PERIOD.getInt();
        epsilonAbs = Options.ADMM_EPSILON_ABS.getDouble();
        epsilonRel = Options.ADMM_EPSILON_REL.getDouble();
    }

    public double getEpsilonRel() {
        return epsilonRel;
    }

    public void setEpsilonRel(double epsilonRel) {
        this.epsilonRel = epsilonRel;
    }

    public double getEpsilonAbs() {
        return epsilonAbs;
    }

    public void setEpsilonAbs(double epsilonAbs) {
        this.epsilonAbs = epsilonAbs;
    }

    public double getLagrangianPenalty() {
        return this.lagrangePenalty;
    }

    public double getAugmentedLagrangianPenalty() {
        return this.augmentedLagrangePenalty;
    }

    @Override
    public double optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof ADMMTermStore)) {
            throw new IllegalArgumentException("ADMMReasoner requires an ADMMTermStore (found " + baseTermStore.getClass().getName() + ").");
        }
        ADMMTermStore termStore = (ADMMTermStore)baseTermStore;

        termStore.initForOptimization();

        long numTerms = termStore.size();
        int numVariables = termStore.getNumConsensusVariables();

        log.debug("Performing optimization with {} variables and {} terms.", numVariables, numTerms);

        termBlockSize = numTerms / (Parallel.getNumThreads() * 4) + 1;
        variableBlockSize = numVariables / (Parallel.getNumThreads() * 4) + 1;

        long numTermBlocks = (long)Math.ceil(numTerms / (double)termBlockSize);
        long numVariableBlocks = (long)Math.ceil(numVariables / (double)variableBlockSize);

        // Performs inference.
        double epsilonAbsTerm = Math.sqrt(termStore.getNumLocalVariables()) * epsilonAbs;

        ObjectiveResult objective = null;
        ObjectiveResult oldObjective = null;

        if (log.isTraceEnabled()) {
            objective = computeObjective(termStore);
            log.trace(
                    "Iteration {} -- Objective: {}, Feasible: {}.",
                    0, objective.objective, (objective.violatedConstraints == 0));
        }

        int iteration = 1;
        while (true) {
            // Zero out the iteration variables.
            primalRes = 0.0f;
            dualRes = 0.0f;
            AxNorm = 0.0f;
            AyNorm = 0.0f;
            BzNorm = 0.0f;
            lagrangePenalty = 0.0f;
            augmentedLagrangePenalty = 0.0f;

            // Minimize all the terms.
            Parallel.count(numTermBlocks, new TermWorker(termStore, termBlockSize));

            // Compute new consensus values and residuals.
            Parallel.count(numVariableBlocks, new VariableWorker(termStore, variableBlockSize));

            primalRes = Math.sqrt(primalRes);
            dualRes = stepSize * Math.sqrt(dualRes);

            epsilonPrimal = epsilonAbsTerm + epsilonRel * Math.max(Math.sqrt(AxNorm), Math.sqrt(BzNorm));
            epsilonDual = epsilonAbsTerm + epsilonRel * Math.sqrt(AyNorm);

            if (iteration % computePeriod == 0) {
                if (!objectiveBreak) {
                    log.trace(
                            "Iteration {} -- Primal: {}, Dual: {}, Epsilon Primal: {}, Epsilon Dual: {}.",
                            iteration, primalRes, dualRes, epsilonPrimal, epsilonDual);
                } else {
                    oldObjective = objective;
                    objective = computeObjective(termStore);

                    log.trace(
                            "Iteration {} -- Objective: {}, Feasible: {}, Primal: {}, Dual: {}, Epsilon Primal: {}, Epsilon Dual: {}.",
                            iteration, objective.objective, (objective.violatedConstraints == 0),
                            primalRes, dualRes, epsilonPrimal, epsilonDual);
                }

                termStore.iterationComplete();
            }

            iteration++;

            if (breakOptimization(iteration, objective, oldObjective)) {
                // Before we break, compute the objective so we can look for violated constraints.
                objective = computeObjective(termStore);

                // Check one more time if we should actually break.
                if (breakOptimization(iteration, objective, oldObjective)) {
                    break;
                }
            }
        }

        log.info("Optimization completed in {} iterations. Objective: {}, Feasible: {}, Primal res.: {}, Dual res.: {}",
                iteration - 1, objective.objective, (objective.violatedConstraints == 0), primalRes, dualRes);

        if (objective.violatedConstraints > 0) {
            log.warn("No feasible solution found. {} constraints violated.", objective.violatedConstraints);
            computeObjective(termStore);
        }

        // Sync the consensus values back to the atoms.
        termStore.syncAtoms();

        return objective.objective;
    }

    private boolean breakOptimization(int iteration, ObjectiveResult objective, ObjectiveResult oldObjective) {
        // Always break when the allocated iterations is up.
        if (iteration > (int)(maxIterations * budget)) {
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

        // Break if we have converged.
        if (iteration > 1 && primalRes < epsilonPrimal && dualRes < epsilonDual) {
            return true;
        }

        // Break if the objective has not changed.
        if (objectiveBreak && oldObjective != null && MathUtils.equals(objective.objective, oldObjective.objective, tolerance)) {
            return true;
        }

        return false;
    }

    @Override
    public void close() {
    }

    private ObjectiveResult computeObjective(ADMMTermStore termStore) {
        double objective = 0.0f;
        long violatedConstraints = 0;
        float[] consensusValues = termStore.getConsensusValues();

        for (ADMMObjectiveTerm term : termStore) {
            if (term.isConstraint()) {
                if (term.evaluate(consensusValues) > 0.0f) {
                    violatedConstraints++;
                }
            } else {
                objective += term.evaluate(consensusValues);
            }
        }

        return new ObjectiveResult(objective, violatedConstraints);
    }

    private synchronized void updateIterationVariables(
            double primalRes, double dualRes,
            double AxNorm, double BzNorm, double AyNorm,
            double lagrangePenalty, double augmentedLagrangePenalty) {
        this.primalRes += primalRes;
        this.dualRes += dualRes;
        this.AxNorm += AxNorm;
        this.AyNorm += AyNorm;
        this.BzNorm += BzNorm;
        this.lagrangePenalty += lagrangePenalty;
        this.augmentedLagrangePenalty += augmentedLagrangePenalty;
    }

    private class TermWorker extends Parallel.Worker<Long> {
        private final ADMMTermStore termStore;
        private final long blockSize;
        private final float[] consensusValues;

        public TermWorker(ADMMTermStore termStore, long blockSize) {
            super();

            this.termStore = termStore;
            this.blockSize = blockSize;
            this.consensusValues = termStore.getConsensusValues();
        }

        public Object clone() {
            return new TermWorker(termStore, blockSize);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            long numTerms = termStore.size();

            // Minimize each local function (wrt the local variable copies).
            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                long termIndex = blockIndex * blockSize + innerBlockIndex;

                if (termIndex >= numTerms) {
                    break;
                }

                termStore.get(termIndex).updateLagrange(stepSize, consensusValues);
                termStore.get(termIndex).minimize(stepSize, consensusValues);
            }
        }
    }

    private class VariableWorker extends Parallel.Worker<Long> {
        private final ADMMTermStore termStore;
        private final long blockSize;
        private final float[] consensusValues;

        public VariableWorker(ADMMTermStore termStore, long blockSize) {
            super();

            this.termStore = termStore;
            this.blockSize = blockSize;
            this.consensusValues = termStore.getConsensusValues();
        }

        public Object clone() {
            return new VariableWorker(termStore, blockSize);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            int numVariables = termStore.getNumConsensusVariables();

            double primalResInc = 0.0f;
            double dualResInc = 0.0f;
            double AxNormInc = 0.0f;
            double BzNormInc = 0.0f;
            double AyNormInc = 0.0f;
            double lagrangePenaltyInc = 0.0f;
            double augmentedLagrangePenaltyInc = 0.0f;

            // Instead of dividing up the work ahead of time,
            // get one job at a time so the threads will have more even workloads.
            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int variableIndex = (int)(blockIndex * blockSize + innerBlockIndex);

                if (variableIndex >= numVariables) {
                    break;
                }

                double total = 0.0f;
                int numLocalVariables = termStore.getLocalVariables(variableIndex).size();

                // First pass computes newConsensusValue and dual residual fom all local copies.
                for (int localVarIndex = 0; localVarIndex < numLocalVariables; localVarIndex++) {
                    LocalVariable localVariable = termStore.getLocalVariables(variableIndex).get(localVarIndex);
                    total += localVariable.getValue() + localVariable.getLagrange() / stepSize;

                    AxNormInc += localVariable.getValue() * localVariable.getValue();
                    AyNormInc += localVariable.getLagrange() * localVariable.getLagrange();
                }

                float newConsensusValue = (float)(total / numLocalVariables);
                newConsensusValue = Math.max(Math.min(newConsensusValue, UPPER_BOUND), LOWER_BOUND);

                float diff = consensusValues[variableIndex] - newConsensusValue;
                // Residual is diff^2 * number of local variables mapped to consensusValues element.
                dualResInc += diff * diff * numLocalVariables;
                BzNormInc += newConsensusValue * newConsensusValue * numLocalVariables;

                consensusValues[variableIndex] = newConsensusValue;

                // Second pass computes primal residuals.

                for (int localVarIndex = 0; localVarIndex < numLocalVariables; localVarIndex++) {
                    LocalVariable localVariable = termStore.getLocalVariables(variableIndex).get(localVarIndex);

                    diff = localVariable.getValue() - newConsensusValue;
                    primalResInc += diff * diff;

                    // compute Lagrangian penalties
                    lagrangePenaltyInc += localVariable.getLagrange() * (localVariable.getValue() - consensusValues[variableIndex]);
                    augmentedLagrangePenaltyInc += 0.5 * stepSize * Math.pow(localVariable.getValue() - consensusValues[variableIndex], 2);
                }
            }

            updateIterationVariables(primalResInc, dualResInc, AxNormInc, BzNormInc, AyNormInc, lagrangePenaltyInc, augmentedLagrangePenaltyInc);
        }
    }

    private static class ObjectiveResult {
        public final double objective;
        public final long violatedConstraints;

        public ObjectiveResult(double objective, long violatedConstraints) {
            this.objective = objective;
            this.violatedConstraints = violatedConstraints;
        }
    }
}
