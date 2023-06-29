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
package org.linqs.psl.reasoner.admm;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.term.ADMMObjectiveTerm;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Parallel;

import java.util.List;

/**
 * Uses an ADMM optimization method to optimize its GroundRules.
 */
public class ADMMReasoner extends Reasoner<ADMMObjectiveTerm> {
    private static final Logger log = Logger.getLogger(ADMMReasoner.class);

    private static final float LOWER_BOUND = 0.0f;
    private static final float UPPER_BOUND = 1.0f;

    private int computePeriod;

    /**
     * Sometimes called eta or rho,
     */
    private final float stepSize;

    private boolean primalDualBreak;

    private double epsilonRel;
    private double epsilonAbs;

    private double primalRes;
    private double epsilonPrimal;
    private double dualRes;
    private double epsilonDual;

    private double AxNorm;
    private double AyNorm;
    private double BzNorm;

    private long termBlockSize;
    private long variableBlockSize;

    public ADMMReasoner() {
        maxIterations = Options.ADMM_MAX_ITER.getInt();
        primalDualBreak = Options.ADMM_PRIMAL_DUAL_BREAK.getBoolean();

        stepSize = Options.ADMM_STEP_SIZE.getFloat();
        computePeriod = Options.ADMM_COMPUTE_PERIOD.getInt();
        epsilonAbs = Options.ADMM_EPSILON_ABS.getDouble();
        epsilonRel = Options.ADMM_EPSILON_REL.getDouble();
    }

    @Override
    public double optimize(TermStore<ADMMObjectiveTerm> baseTermStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        if (!(baseTermStore instanceof ADMMTermStore)) {
            throw new IllegalArgumentException("ADMMReasoner requires an ADMMTermStore (found " + baseTermStore.getClass().getName() + ").");
        }
        ADMMTermStore termStore = (ADMMTermStore)baseTermStore;
        termStore.initForOptimization();
        initForOptimization(termStore);

        long numTerms = termStore.size();
        int numVariables = termStore.getNumVariables();

        termBlockSize = numTerms / (Parallel.getNumThreads() * 4) + 1;
        variableBlockSize = numVariables / (Parallel.getNumThreads() * 4) + 1;

        long numTermBlocks = (long)Math.ceil(numTerms / (double)termBlockSize);
        long numVariableBlocks = (long)Math.ceil(numVariables / (double)variableBlockSize);

        // Performs inference.
        double epsilonAbsTerm = Math.sqrt(termStore.getNumLocalVariables()) * epsilonAbs;

        ObjectiveResult objective = null;
        ObjectiveResult oldObjective = null;

        boolean breakADMM = false;
        long totalTime = 0;
        int iteration = 1;
        while (!breakADMM) {
            long start = System.currentTimeMillis();

            // Zero out the iteration variables.
            primalRes = 0.0f;
            dualRes = 0.0f;
            AxNorm = 0.0f;
            AyNorm = 0.0f;
            BzNorm = 0.0f;

            // Minimize all the terms.
            Parallel.count(numTermBlocks, new TermWorker(termStore, termBlockSize));

            // Compute new consensus values and residuals.
            Parallel.count(numVariableBlocks, new VariableWorker(termStore, variableBlockSize, numVariables));

            primalRes = Math.sqrt(primalRes);
            dualRes = stepSize * Math.sqrt(dualRes);

            epsilonPrimal = epsilonAbsTerm + epsilonRel * Math.max(Math.sqrt(AxNorm), Math.sqrt(BzNorm));
            epsilonDual = epsilonAbsTerm + epsilonRel * Math.sqrt(AyNorm);

            long end = System.currentTimeMillis();
            totalTime += end - start;

            breakADMM = breakOptimization(iteration, termStore, objective, oldObjective);

            if ((iteration % computePeriod == 0) || breakADMM) {
                oldObjective = objective;
                objective = parallelComputeObjective(termStore);

                if ((objective.violatedConstraints > 0) && (iteration <= (int)(maxIterations * budget))) {
                    // Override the decision to break optimization if the current state is infeasible.
                    breakADMM = false;
                }

                log.trace("Iteration {} -- Objective: {}, Violated Constraints: {}, Primal: {}, Dual: {}, Epsilon Primal: {}, Epsilon Dual: {}, Iteration Time: {}, Total Optimization Time: {}.",
                        iteration, objective.objective, objective.violatedConstraints,
                        primalRes, dualRes, epsilonPrimal, epsilonDual, (end - start), totalTime);

                evaluate(termStore, iteration, evaluations, trainingMap);
            }

            iteration++;
        }

        optimizationComplete(termStore, objective, totalTime);
        return objective.objective;
    }

    @Override
    protected boolean breakOptimization(int iteration, TermStore<ADMMObjectiveTerm> termStore,
                                        ObjectiveResult objective, ObjectiveResult oldObjective) {
        if (super.breakOptimization(iteration, termStore, objective, oldObjective)) {
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

        // Break if we have converged according to the primal dual residual stopping criterion.
        if (primalDualBreak && (iteration > 1) && (primalRes < epsilonPrimal) && (dualRes < epsilonDual)) {
            log.trace("Breaking optimization. Primal residual: {} below tolerance: {} and dual residual: {} below tolerance: {}.",
                    primalRes, epsilonPrimal, dualRes, epsilonDual);
            return true;
        }

        return false;
    }

    private synchronized void updateIterationVariables(
            double primalRes, double dualRes,
            double AxNorm, double BzNorm, double AyNorm) {
        this.primalRes += primalRes;
        this.dualRes += dualRes;
        this.AxNorm += AxNorm;
        this.AyNorm += AyNorm;
        this.BzNorm += BzNorm;
    }

    private class TermWorker extends Parallel.Worker<Long> {
        private final ADMMTermStore termStore;
        private final long blockSize;
        private final float[] consensusValues;

        public TermWorker(ADMMTermStore termStore, long blockSize) {
            super();

            this.termStore = termStore;
            this.blockSize = blockSize;

            this.consensusValues = termStore.getVariableValues();
        }

        @Override
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

                ADMMObjectiveTerm term = termStore.get(termIndex);
                if (!term.isActive()) {
                    continue;
                }

                term.updateLagrange(stepSize, consensusValues);
                term.minimize(stepSize, consensusValues);
            }
        }
    }

    private class VariableWorker extends Parallel.Worker<Long> {
        private final ADMMTermStore termStore;
        private final long blockSize;
        private final int numVariables;

        private final float[] consensusValues;
        private final GroundAtom[] consensusAtoms;

        public VariableWorker(ADMMTermStore termStore, long blockSize, int numVariables) {
            super();

            this.termStore = termStore;
            this.blockSize = blockSize;
            this.numVariables = numVariables;

            consensusValues = termStore.getVariableValues();
            consensusAtoms = termStore.getVariableAtoms();
        }

        public Object clone() {
            return new VariableWorker(termStore, blockSize, numVariables);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            double primalResInc = 0.0f;
            double dualResInc = 0.0f;
            double AxNormInc = 0.0f;
            double BzNormInc = 0.0f;
            double AyNormInc = 0.0f;

            // Instead of dividing up the work ahead of time,
            // get one job at a time so the threads will have more even workloads.
            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int variableIndex = (int)(blockIndex * blockSize + innerBlockIndex);

                if (variableIndex >= numVariables) {
                    break;
                }

                List<ADMMTermStore.LocalRecord> localRecords = termStore.getLocalRecords(variableIndex);
                if (localRecords == null) {
                    continue;
                }

                double total = 0.0f;

                // First pass computes newConsensusValue and dual residual fom all local copies.
                int numLocalVariables = 0;
                for (ADMMTermStore.LocalRecord localRecord : localRecords) {
                    ADMMObjectiveTerm term = termStore.get(localRecord.termIndex);
                    if (!term.isActive()) {
                        continue;
                    }

                    float localValue = term.getVariableValue(localRecord.variableIndex);
                    float localLagrange = term.getVariableLagrange(localRecord.variableIndex);

                    total += localValue + localLagrange / stepSize;

                    AxNormInc += localValue * localValue;
                    AyNormInc += localLagrange * localLagrange;

                    numLocalVariables++;
                }

                if (numLocalVariables == 0) {
                    continue;
                }

                float newConsensusValue = 0.0f;
                if (consensusAtoms[variableIndex].isFixed()) {
                    newConsensusValue = consensusValues[variableIndex];
                } else {
                    newConsensusValue = (float)(total / numLocalVariables);
                    newConsensusValue = Math.max(Math.min(newConsensusValue, UPPER_BOUND), LOWER_BOUND);
                }

                float diff = consensusValues[variableIndex] - newConsensusValue;
                // Residual is diff^2 * number of local variables mapped to consensusValues element.
                dualResInc += diff * diff * numLocalVariables;
                BzNormInc += newConsensusValue * newConsensusValue * numLocalVariables;

                consensusValues[variableIndex] = newConsensusValue;

                // Second pass computes primal residuals.

                for (ADMMTermStore.LocalRecord localRecord : localRecords) {
                    ADMMObjectiveTerm term = termStore.get(localRecord.termIndex);

                    if (!term.isActive()) {
                        continue;
                    }

                    float localValue = term.getVariableValue(localRecord.variableIndex);

                    diff = localValue - newConsensusValue;
                    primalResInc += diff * diff;
                }
            }

            updateIterationVariables(primalResInc, dualResInc, AxNormInc, BzNormInc, AyNormInc);
        }
    }
}
