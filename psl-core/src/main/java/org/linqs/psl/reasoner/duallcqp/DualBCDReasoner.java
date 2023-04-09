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
package org.linqs.psl.reasoner.duallcqp;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.duallcqp.term.DualLCQPAtom;
import org.linqs.psl.reasoner.duallcqp.term.DualLCQPObjectiveTerm;
import org.linqs.psl.reasoner.duallcqp.term.DualLCQPTermStore;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import java.util.Arrays;
import java.util.List;

/**
 * A reasoner that performs block coordinate descent (BCD) on the dual problem of the
 * regularized LCQP formulation of MAP inference.
 */
public class DualBCDReasoner extends Reasoner<DualLCQPObjectiveTerm> {
    private static final org.linqs.psl.util.Logger log = Logger.getLogger(DualBCDReasoner.class);

    public final float regularizationParameter;

    private final int computePeriod;
    private final boolean firstOrderBreak;
    private final float firstOrderTolerance;
    private final boolean primalDualBreak;
    private final float primalDualTolerance;

    public DualBCDReasoner() {
        regularizationParameter = Options.DUAL_LCQP_REGULARIZATION.getFloat();

        maxIterations = Options.DUAL_LCQP_MAX_ITER.getInt();
        computePeriod = Options.DUAL_LCQP_COMPUTE_PERIOD.getInt();
        firstOrderBreak = Options.DUAL_LCQP_FIRST_ORDER_BREAK.getBoolean();
        firstOrderTolerance = Options.DUAL_LCQP_FIRST_ORDER_THRESHOLD.getFloat();
        primalDualBreak = Options.DUAL_LCQP_PRIMAL_DUAL_BREAK.getBoolean();
        primalDualTolerance = Options.DUAL_LCQP_PRIMAL_DUAL_THRESHOLD.getFloat();
    }

    @Override
    public double optimize(TermStore<DualLCQPObjectiveTerm> baseTermStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        if (!(baseTermStore instanceof DualLCQPTermStore)) {
            throw new IllegalArgumentException("DualBCDReasoner requires an DualLCQPTermStore (found " + baseTermStore.getClass().getName() + ").");
        }
        DualLCQPTermStore termStore = (DualLCQPTermStore)baseTermStore;
        termStore.initForOptimization();
        initForOptimization(termStore);

        ObjectiveResult objective = null;
        ObjectiveResult oldObjective = null;

        long totalTime = 0;
        boolean breakDualBCD = false;
        int iteration = 1;
        while(!breakDualBCD) {
            long start = System.currentTimeMillis();

            for (DualLCQPObjectiveTerm term : termStore) {
                if (!term.getRule().isActive()) {
                    continue;
                }

                dualBlockUpdate(term, termStore, regularizationParameter);
            }

            long end = System.currentTimeMillis();
            totalTime += end - start;

            if (iteration % computePeriod == 0) {
                primalVariableUpdate(termStore);

                oldObjective = objective;
                objective = computeObjective(termStore);

                ObjectiveResult dualObjectiveResult = new ObjectiveResult(0.0f, 0);
                float dualGradientNorm = parallelComputeDualObjectiveAndGradientNorm(termStore, dualObjectiveResult);
                breakDualBCD = breakOptimization(iteration, termStore, objective, oldObjective, dualObjectiveResult, dualGradientNorm);

                log.trace("Iteration {} -- Primal Objective: {}, Violated Constraints: {}, Dual Objective: {}, Primal-dual gap: {}, Iteration Time: {}, Total Optimization Time: {}.",
                        iteration, objective.objective, objective.violatedConstraints,
                        dualObjectiveResult.objective, objective.objective - dualObjectiveResult.objective,
                        (end - start), totalTime);

                evaluate(termStore, iteration, evaluations, trainingMap);
            }

            iteration++;
        }

        optimizationComplete(termStore, objective, totalTime, iteration - 1);

        // Return the un-regularized quantification of the objective for consistency with
        // weight learning objectives and test assertions.
        return super.parallelComputeObjective(termStore).objective;
    }

    /**
     * Map the current setting of the dual variables to primal variables.
     */
    protected void primalVariableUpdate(DualLCQPTermStore termStore) {
        AtomStore atomStore = termStore.getDatabase().getAtomStore();
        GroundAtom[] atoms = atomStore.getAtoms();
        float[] atomValues = atomStore.getAtomValues();

        for (int i = 0; i < atomStore.size(); i ++) {
            if (atoms[i].isFixed()) {
                continue;
            }

            atomValues[i] = termStore.getDualLCQPAtom(i).getPrimal(regularizationParameter);
        }
    }

    protected boolean breakOptimization(int iteration, TermStore<DualLCQPObjectiveTerm> termStore,
                                        ObjectiveResult objective, ObjectiveResult oldObjective,
                                        ObjectiveResult dualObjectiveResult, float dualGradientNorm) {
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

        // Break if we have converged according to the first order stopping criterion on dual problem.
        if (firstOrderBreak && MathUtils.isZero(dualGradientNorm, firstOrderTolerance)) {
            log.trace("Breaking optimization. Dual gradient magnitude: {} below tolerance: {}.",
                    dualGradientNorm, firstOrderTolerance);
            return true;
        }

        // Break if we have converged according to the primal dual gap stopping criterion.
        if (primalDualBreak && (objective != null)
                && MathUtils.isZero(objective.objective - dualObjectiveResult.objective, primalDualTolerance)) {
            log.trace("Breaking optimization. Primal-dual gap: {} below tolerance: {}.",
                    objective.objective - dualObjectiveResult.objective, primalDualTolerance);
            return true;
        }

        return false;
    }

    /**
     * Minimize the dual objective over the dual variables associated with the provided term.
     */
    protected static void dualBlockUpdate(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore, float regularizationParameter) {
        float termDualPartial = computeTermDualPartial(term, termStore, regularizationParameter);
        float slackLowerDualPartial = computeSlackLowerBoundDualPartial(term, regularizationParameter);

        float stepSize = computeStepSize(term, termStore, termDualPartial, slackLowerDualPartial, regularizationParameter);

        // Take a step and project the dual variable onto the feasible set.
        // Only equality constraints do not have lower bounds on their corresponding dual variables.
        float updatedTermDualVariable = term.getDualVariable() - stepSize * termDualPartial;
        if (!term.isEqualityConstraint()) {
            updatedTermDualVariable = Math.max(0.0f, term.getDualVariable() - stepSize * termDualPartial);
        }
        float termDualDelta = updatedTermDualVariable - term.getDualVariable();
        term.setDualVariable(updatedTermDualVariable);

        float[] coefficients = term.getCoefficients();
        int[] atomIndexes = term.getAtomIndexes();
        GroundAtom[] atoms = termStore.getDatabase().getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                continue;
            }

            dualLCQPAtoms[atomIndexes[i]].update(termDualDelta, coefficients[i], regularizationParameter, stepSize);
        }

        // Only linear hinge potentials have bounds on the slack variable.
        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            term.setSlackBoundDualVariable(Math.max(0.0f, term.getSlackBoundDualVariable() - stepSize * slackLowerDualPartial));
        }
    }

    /**
     * Compute the largest step size that preserves dual feasibility.
     */
    protected static float computeStepSize(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore,
                                              float termDualPartial, float slackLowerDualPartial,
                                              float regularizationParameter) {
        float subproblemMinimizer = computeStepSizeSubproblemMinimizer(term, termStore,
                termDualPartial, slackLowerDualPartial, regularizationParameter);

        // Project subproblem solution such that dual iterates are always feasible.
        float minDualGradientRatio = Float.POSITIVE_INFINITY;

        // Dual variables for inequality constraints and (squared) hinge-loss potentials must be non-negative.
        if ((termDualPartial > 0.0f) && !term.isEqualityConstraint()) {
            minDualGradientRatio = term.getDualVariable() / termDualPartial;
        }

        // Dual variables for slack bound constraints must be non-negative.
        if (slackLowerDualPartial > 0.0f) {
            float slackLowerDualGradientRatio = term.getSlackBoundDualVariable() / slackLowerDualPartial;
            if (slackLowerDualGradientRatio < minDualGradientRatio) {
                minDualGradientRatio = slackLowerDualGradientRatio;
            }
        }

        // Dual variables for atom bound constraints must be non-negative.
        int[] atomIndexes = term.getAtomIndexes();
        GroundAtom[] atoms = termStore.getDatabase().getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                continue;
            }

            DualLCQPAtom dualLCQPAtom = dualLCQPAtoms[atomIndexes[i]];

            float lowerBoundDualGradient = dualLCQPAtom.getLowerBoundPartial(regularizationParameter);
            if (lowerBoundDualGradient > 0.0f) {
                float atomLowerBoundDualGradientRatio = dualLCQPAtom.getLowerBoundDualVariable() / lowerBoundDualGradient;
                if (atomLowerBoundDualGradientRatio < minDualGradientRatio) {
                    minDualGradientRatio = atomLowerBoundDualGradientRatio;
                }
            }

            float upperBoundDualGradient = dualLCQPAtom.getUpperBoundPartial(regularizationParameter);
            if (upperBoundDualGradient > 0.0f) {
                float atomUpperBoundDualGradientRatio = dualLCQPAtom.getUpperBoundDualVariable() / upperBoundDualGradient;
                if (atomUpperBoundDualGradientRatio < minDualGradientRatio) {
                    minDualGradientRatio = atomUpperBoundDualGradientRatio;
                }
            }
        }

        return Math.min(minDualGradientRatio, subproblemMinimizer);
    }

    /**
     * Find the step size that would minimize the dual objective in the direction
     * of steepest descent if there were no dual constraints.
     */
    protected static float computeStepSizeSubproblemMinimizer(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore,
                                                              float termDualPartial, float slackLowerDualPartial,
                                                              float regularizationParameter) {
        float numerator = 0.0f;
        float denominator = 0.0f;

        float potentialAtomLowerBoundSum = 0.0f;
        float potentialAtomUpperBoundSum = 0.0f;

        float[] coefficients = term.getCoefficients();
        int[] atomIndexes = term.getAtomIndexes();
        GroundAtom[] atoms = termStore.getDatabase().getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();

        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                continue;
            }

            DualLCQPAtom dualLCQPAtom = dualLCQPAtoms[atomIndexes[i]];
            float atomLowerBoundPartial = dualLCQPAtom.getLowerBoundPartial(regularizationParameter);
            float atomUpperBoundPartial = dualLCQPAtom.getUpperBoundPartial(regularizationParameter);

            potentialAtomLowerBoundSum += coefficients[i] * atomLowerBoundPartial;
            potentialAtomUpperBoundSum += coefficients[i] * atomUpperBoundPartial;

            numerator += Math.pow(atomLowerBoundPartial, 2.0f);
            numerator += Math.pow(atomUpperBoundPartial, 2.0f);

            denominator += (atomLowerBoundPartial - atomUpperBoundPartial - coefficients[i] * termDualPartial) * atomLowerBoundPartial;
            denominator += (atomUpperBoundPartial - atomLowerBoundPartial + coefficients[i] * termDualPartial) * atomUpperBoundPartial;
        }
        denominator = denominator / regularizationParameter;

        float potentDualUpdateStat = (termDualPartial * term.computeSelfInnerProduct()
                - potentialAtomLowerBoundSum + potentialAtomUpperBoundSum) / regularizationParameter;

        if (term.termType.equals(ReasonerTerm.TermType.SquaredHingeLossTerm)) {
            potentDualUpdateStat += termDualPartial / (regularizationParameter + term.getWeight());
        } else if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            potentDualUpdateStat += termDualPartial / regularizationParameter;
        }

        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            potentDualUpdateStat += slackLowerDualPartial / regularizationParameter;

            numerator += Math.pow(slackLowerDualPartial, 2.0f);
            denominator += ((slackLowerDualPartial + termDualPartial) / regularizationParameter) * slackLowerDualPartial;
        }

        numerator += Math.pow(termDualPartial, 2.0f);
        denominator += potentDualUpdateStat * termDualPartial;

        if ((numerator == 0.0f) || (denominator == 0.0f)) {
            return 0.0f;
        }

        return numerator / denominator;
    }

    /**
     * Compute the partial derivative of the dual variable associated with the term.
     */
    protected static float computeTermDualPartial(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore,
                                                  float regularizationParameter) {
        float termDualPartial = 0.0f;
        float observedConstant = 0.0f;

        GroundAtom[] atoms = termStore.getDatabase().getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        float[] coefficients = term.getCoefficients();
        int[] atomIndexes = term.getAtomIndexes();

        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                observedConstant += coefficients[i] * atoms[atomIndexes[i]].getValue();
                continue;
            }

            termDualPartial += coefficients[i] * dualLCQPAtoms[atomIndexes[i]].getMessage();
        }

        termDualPartial = termDualPartial / regularizationParameter;

        if (term.termType.equals(ReasonerTerm.TermType.SquaredHingeLossTerm)) {
            termDualPartial += (1.0f / (regularizationParameter + term.getWeight())) * term.getDualVariable();
        } else if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            termDualPartial += (1.0f / regularizationParameter) * term.getDualVariable();
            termDualPartial += (1.0f / regularizationParameter) * term.getSlackBoundDualVariable();
            termDualPartial += (-1.0f / regularizationParameter) * term.getWeight();
        }

        termDualPartial += 2.0f * (term.getConstant() - observedConstant);

        // Clip gradients for lower bound constraint on dual variables.
        if ((!term.isEqualityConstraint()) && (termDualPartial > 0.0f) && MathUtils.isZero(term.getDualVariable())) {
            termDualPartial = 0.0f;
        }

        return termDualPartial;
    }

    protected static float computeSlackLowerBoundDualPartial(DualLCQPObjectiveTerm term, float regularizationParameter) {
        float slackLowerDualPartial = 0.0f;

        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            slackLowerDualPartial = (term.getSlackBoundDualVariable() + term.getDualVariable() - term.getWeight()) / regularizationParameter;

            // Clip gradient for lower bound constraint.
            if ((slackLowerDualPartial > 0.0f) && MathUtils.isZero(term.getSlackBoundDualVariable())) {
                // Linear weighted potential.
                slackLowerDualPartial = 0.0f;
            }
        }

        return slackLowerDualPartial;
    }

    protected static float evaluateDualTerm(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore, float regularizationParameter) {
        float potentialDualObjective = 0.0f;
        float observedConstant = 0.0f;

        GroundAtom[] atoms = termStore.getDatabase().getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        float[] coefficients = term.getCoefficients();
        int[] atomIndexes = term.getAtomIndexes();

        // Dual objective value contributions from the primal variables in the term.
        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                observedConstant += coefficients[i] * atoms[atomIndexes[i]].getValue();
                continue;
            }

            potentialDualObjective += coefficients[i] * dualLCQPAtoms[atomIndexes[i]].getMessage();
        }
        potentialDualObjective = term.getDualVariable() * potentialDualObjective / (2.0f * regularizationParameter);

        // Dual objective value contribution from the slack variable in the term.
        if (term.termType.equals(ReasonerTerm.TermType.SquaredHingeLossTerm)) {
            potentialDualObjective += (1.0f / (2.0f * (regularizationParameter + term.getWeight()))) * Math.pow(term.getDualVariable(), 2.0f);
        } else if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            potentialDualObjective += (1.0f / (2.0f * regularizationParameter)) * Math.pow(term.getDualVariable(), 2.0f);
            potentialDualObjective += (1.0f / (2.0f * regularizationParameter)) * term.getDualVariable() * term.getSlackBoundDualVariable();
            potentialDualObjective += (-1.0f / regularizationParameter) * term.getWeight() * term.getDualVariable();
            potentialDualObjective += (1.0f / (2.0f * regularizationParameter) * Math.pow(term.getWeight(), 2.0f));
        }

        // Dual objective value contribution from the constant in the term.
        potentialDualObjective += 2.0f * (term.getConstant() - observedConstant) * term.getDualVariable();

        return potentialDualObjective;
    }

    protected static float evaluateDualSlackLowerBound(DualLCQPObjectiveTerm term, float regularizationParameter) {
        float slackLowerBoundDualObjective = 0.0f;

        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            slackLowerBoundDualObjective += term.getSlackBoundDualVariable() * term.getDualVariable() / (2.0f * regularizationParameter);
            slackLowerBoundDualObjective += Math.pow(term.getSlackBoundDualVariable(), 2.0f) / (2.0f * regularizationParameter);
            slackLowerBoundDualObjective -= term.getSlackBoundDualVariable() * term.getWeight() / regularizationParameter;
        }

        return slackLowerBoundDualObjective;
    }

    @Override
    protected ObjectiveResult computeObjective(TermStore<DualLCQPObjectiveTerm> termStore) {
        ObjectiveResult objectiveResult = super.computeObjective(termStore);
        objectiveResult.objective += computePrimalVariableRegularization(termStore);
        return objectiveResult;
    }

    private float computePrimalVariableRegularization(TermStore<DualLCQPObjectiveTerm> termStore) {
        AtomStore atomStore = termStore.getDatabase().getAtomStore();
        GroundAtom[] atoms = atomStore.getAtoms();
        float[] atomValues = atomStore.getAtomValues();

        float atomValueRegularization = 0.0f;
        for (int i = 0; i < atomStore.size(); i ++) {
            if (atoms[i].isFixed()) {
                continue;
            }

            atomValueRegularization += regularizationParameter * Math.pow(atomValues[i], 2.0f);
        }

        return atomValueRegularization;
    }

    @Override
    public void computeOptimalValueGradient(TermStore<DualLCQPObjectiveTerm> termStore, float[] rvAtomGradient, float[] deepAtomGradient) {
        AtomStore atomStore = termStore.getDatabase().getAtomStore();
        GroundAtom[] atoms = atomStore.getAtoms();

        Arrays.fill(rvAtomGradient, 0.0f);
        Arrays.fill(deepAtomGradient, 0.0f);

        for (DualLCQPObjectiveTerm term : termStore) {
            if (!term.getRule().isActive()) {
                continue;
            }

            float[] coefficients = term.getCoefficients();
            for (int atomIndex : term.getAtomIndexes()) {
                GroundAtom atom = atoms[atomIndex];

                if (atom instanceof ObservedAtom) {
                    continue;
                }

                if (atoms[atomIndex].getPredicate() instanceof DeepPredicate) {
                    deepAtomGradient[atomIndex] += term.getDualVariable() * coefficients[atomIndex];
                }
            }
        }
    }

    protected float parallelComputeDualObjectiveAndGradientNorm(DualLCQPTermStore termStore, ObjectiveResult objectiveResult) {
        int blockSize = (int)(termStore.size() / (Parallel.getNumThreads() * 4) + 1);
        int numTermBlocks = (int)Math.ceil(termStore.size() / (float)blockSize);

        float[] workerGradientMagnitudes = new float[numTermBlocks];
        float[] workerObjectives = new float[numTermBlocks];

        Parallel.count(numTermBlocks, new DualTermObjectiveAndGradientMagnitudeWorker(
                termStore, workerGradientMagnitudes, workerObjectives,
                blockSize, regularizationParameter));

        float maxAbsGradient = 0.0f;
        float objectiveValue = 0.0f;
        for(int i = 0; i < numTermBlocks; i++) {
            objectiveValue += workerObjectives[i];
            if (maxAbsGradient < workerGradientMagnitudes[i]) {
                maxAbsGradient = workerGradientMagnitudes[i];
            }
        }

        // The upper and lower bound constraints on the atoms are not stored in the term store,
        // so we need to compute their objective and gradient contribution here.
        GroundAtom[] atoms = termStore.getDatabase().getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        for(int i = 0; i < dualLCQPAtoms.length; i++) {
            if (atoms[i].isFixed()) {
                continue;
            }

            objectiveValue += dualLCQPAtoms[i].getLowerBoundObjective(regularizationParameter);
            float absLowerBoundPartial = Math.abs(dualLCQPAtoms[i].getLowerBoundPartial(regularizationParameter));
            if (maxAbsGradient < absLowerBoundPartial) {
                maxAbsGradient = absLowerBoundPartial;
            }

            objectiveValue += dualLCQPAtoms[i].getUpperBoundObjective(regularizationParameter);
            float absUpperBoundPartial = Math.abs(dualLCQPAtoms[i].getUpperBoundPartial(regularizationParameter));
            if (maxAbsGradient < absUpperBoundPartial) {
                maxAbsGradient = absUpperBoundPartial;
            }
        }

        objectiveResult.objective = -0.5f * objectiveValue;
        return maxAbsGradient;
    }

    private static class DualTermObjectiveAndGradientMagnitudeWorker extends Parallel.Worker<Long> {
        private final DualLCQPTermStore termStore;
        private final int blockSize;
        private final float regularizationParameter;
        private final float[] maxAbsGradients;
        private final float[] objectives;

        public DualTermObjectiveAndGradientMagnitudeWorker(DualLCQPTermStore termStore,
                                                           float[] maxAbsGradients, float[] objectives,
                                                           int blockSize, float regularizationParameter) {
            super();

            this.termStore = termStore;
            this.maxAbsGradients = maxAbsGradients;
            this.objectives = objectives;
            this.blockSize = blockSize;
            this.regularizationParameter = regularizationParameter;
        }

        @Override
        public Object clone() {
            return new DualTermObjectiveAndGradientMagnitudeWorker(termStore, maxAbsGradients, objectives, blockSize, regularizationParameter);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            long numTerms = termStore.size();
            int blockIntIndex = (int)blockIndex;

            maxAbsGradients[blockIntIndex] = 0.0f;
            objectives[blockIntIndex] = 0.0f;
            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int termIndex = blockIntIndex * blockSize + innerBlockIndex;

                if (termIndex >= numTerms) {
                    break;
                }

                DualLCQPObjectiveTerm term = termStore.get(termIndex);

                if (!term.getRule().isActive()) {
                    continue;
                }

                objectives[blockIntIndex] += evaluateDualTerm(term, termStore, regularizationParameter);
                objectives[blockIntIndex] += evaluateDualSlackLowerBound(term, regularizationParameter);

                float potentialDualAbsGradient = Math.abs(computeTermDualPartial(term, termStore, regularizationParameter));
                if (maxAbsGradients[blockIntIndex] < potentialDualAbsGradient) {
                    maxAbsGradients[blockIntIndex] = potentialDualAbsGradient;
                }

                float slackLowerBoundDualVariable = Math.abs(computeSlackLowerBoundDualPartial(term, regularizationParameter));
                if (maxAbsGradients[blockIntIndex] < slackLowerBoundDualVariable) {
                    maxAbsGradients[blockIntIndex] = slackLowerBoundDualVariable;
                }
            }
        }
    }
}
