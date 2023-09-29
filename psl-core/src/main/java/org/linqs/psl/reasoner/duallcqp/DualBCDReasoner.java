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
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A reasoner that performs block coordinate descent (BCD) on the dual problem of the
 * regularized LCQP formulation of MAP inference.
 */
public class DualBCDReasoner extends Reasoner<DualLCQPObjectiveTerm> {
    private static final org.linqs.psl.util.Logger log = Logger.getLogger(DualBCDReasoner.class);

    public static final double regularizationParameter = Options.DUAL_LCQP_REGULARIZATION.getDouble();

    protected final boolean primalDualBreak;
    protected final double primalDualTolerance;

    protected final int computePeriod;

    public DualBCDReasoner() {
        super();

        maxIterations = Options.DUAL_LCQP_MAX_ITER.getInt();
        computePeriod = Options.DUAL_LCQP_COMPUTE_PERIOD.getInt();
        primalDualBreak = Options.DUAL_LCQP_PRIMAL_DUAL_BREAK.getBoolean();
        primalDualTolerance = Options.DUAL_LCQP_PRIMAL_DUAL_THRESHOLD.getDouble();

        assert !variableMovementBreak || ((computePeriod == 1) && (variableMovementNorm == Float.POSITIVE_INFINITY));
    }

    @Override
    public double optimize(TermStore<DualLCQPObjectiveTerm> baseTermStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        if (!(baseTermStore instanceof DualLCQPTermStore)) {
            throw new IllegalArgumentException("DualBCDReasoner requires an DualLCQPTermStore (found " + baseTermStore.getClass().getName() + ").");
        }
        DualLCQPTermStore termStore = (DualLCQPTermStore)baseTermStore;
        termStore.initForOptimization();
        initForOptimization(termStore);

        long totalTime = internalOptimize(termStore, evaluations, trainingMap);

        optimizationComplete(termStore, parallelComputeObjective(termStore), totalTime);

        // Return the un-regularized quantification of the objective for consistency with
        // weight learning objectives and test assertions.
        return super.parallelComputeObjective(termStore).objective;
    }

    protected long internalOptimize(DualLCQPTermStore termStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        log.trace("Starting optimization. Number of connected components: {}.", termStore.getConnectedComponents().size());

        long start = System.currentTimeMillis();
        int blockSize = (int)(termStore.getConnectedComponents().size() / (Parallel.getNumThreads() * 4) + 1);
        int numComponentBlocks = (int)Math.ceil(termStore.getConnectedComponents().size() / (double)blockSize);
        Parallel.count(numComponentBlocks, new ComponentOptimizer(termStore, blockSize));
        long end = System.currentTimeMillis();
        long totalTime = end - start;

        evaluate(termStore, 1, evaluations, trainingMap);

        return totalTime;
    }

    /**
     * Map the current setting of the dual variables to primal variables.
     */
    protected float primalVariableUpdate(DualLCQPTermStore termStore) {
        AtomStore atomStore = termStore.getAtomStore();
        GroundAtom[] atoms = atomStore.getAtoms();
        float[] atomValues = atomStore.getAtomValues();

        float variableMovement = 0.0f;
        for (int i = 0; i < atomStore.size(); i ++) {
            if (atoms[i].isFixed()) {
                continue;
            }

            float oldValue = atomValues[i];
            atomValues[i] = termStore.getDualLCQPAtom(i).getPrimal(regularizationParameter);

            // Update the variable movement to be the largest absolute change in any variable.
            if (Math.abs(atomValues[i] - oldValue) > variableMovement) {
                variableMovement = Math.abs(atomValues[i] - oldValue);
            }
        }

        return variableMovement;
    }

    /**
     * Map the current setting of the dual variables to primal variables.
     */
    protected static float primalVariableComponentUpdate(DualLCQPTermStore termStore, int componentIndex) {
        AtomStore atomStore = termStore.getAtomStore();
        List<Integer> connectedComponentAtomIndexes = atomStore.getConnectedComponentAtomIndexes(componentIndex);
        float[] atomValues = atomStore.getAtomValues();
        GroundAtom[] atoms = atomStore.getAtoms();

        float variableMovement = 0.0f;
        for (Integer atomIndex : connectedComponentAtomIndexes) {
            GroundAtom atom = atoms[atomIndex];

            if (atom.isFixed()) {
                continue;
            }

            float oldValue = atomValues[atomIndex];
            atomValues[atomIndex] = termStore.getDualLCQPAtom(atomIndex).getPrimal(regularizationParameter);

            // Update the variable movement to be the largest absolute change in any variable.
            if (Math.abs(atomValues[atomIndex] - oldValue) > variableMovement) {
                variableMovement = Math.abs(atomValues[atomIndex] - oldValue);
            }
        }

        return variableMovement;
    }

    protected static boolean breakOptimization(int iteration,
                                             ObjectiveResult primalObjectiveResult, ObjectiveResult oldPrimalObjectiveResult,
                                             ObjectiveResult dualObjectiveResult,
                                             int maxIterations, boolean runFullIterations,
                                             boolean objectiveBreak, double objectiveTolerance,
                                             boolean variableMovementBreak, float variableMovementTolerance, float variableMovement,
                                             boolean primalDualBreak, double primalDualTolerance) {
        // Always break when the allocated iterations is up.
        if (iteration > (int)(maxIterations)) {
            log.trace("Breaking inference. Maximum number of iterations has been reached.");
            return true;
        }

        // Run through the maximum number of iterations.
        if (runFullIterations) {
            return false;
        }

        // Don't break if there are violated constraints.
        if (primalObjectiveResult != null && primalObjectiveResult.violatedConstraints > 0) {
            return false;
        }

        // Break if the objective has not changed.
        if (objectiveBreak && (primalObjectiveResult != null) && (oldPrimalObjectiveResult != null)
                && MathUtils.equals(primalObjectiveResult.objective, oldPrimalObjectiveResult.objective, objectiveTolerance)) {
            return true;
        }

        // Break if two consecutive iterates are less than the variable movement tolerance.
        if (variableMovementBreak && (variableMovement < variableMovementTolerance)) {
            return true;
        }

        // Break if we have converged according to the primal dual gap stopping criterion.
        if (primalDualBreak && (primalObjectiveResult != null)
                && MathUtils.compare(primalObjectiveResult.objective, dualObjectiveResult.objective, primalDualTolerance) <= 0) {
            return true;
        }

        return false;
    }

    /**
     * Minimize the dual objective over the dual variables associated with the provided term.
     */
    protected static void dualBlockUpdate(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore) {
        double termDualPartial = computeTermDualPartial(term, termStore);
        double slackLowerDualPartial = computeSlackLowerBoundDualPartial(term);

        double stepSize = computeStepSize(term, termStore, termDualPartial, slackLowerDualPartial);

        // Take a step and project the dual variable onto the feasible set.
        // Only equality constraints do not have lower bounds on their corresponding dual variables.
        double updatedTermDualVariable = term.getDualVariable() - stepSize * termDualPartial;
        if (!term.isEqualityConstraint()) {
            updatedTermDualVariable = Math.max(0.0, term.getDualVariable() - stepSize * termDualPartial);
        }
        double termDualDelta = updatedTermDualVariable - term.getDualVariable();
        term.setDualVariable(updatedTermDualVariable);

        float[] coefficients = term.getCoefficients();
        int[] atomIndexes = term.getAtomIndexes();
        GroundAtom[] atoms = termStore.getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                continue;
            }

            dualLCQPAtoms[atomIndexes[i]].update(termDualDelta, coefficients[i], regularizationParameter, stepSize);
        }

        // Only linear hinge potentials have bounds on the slack variable.
        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            term.setSlackBoundDualVariable(Math.max(0.0, term.getSlackBoundDualVariable() - stepSize * slackLowerDualPartial));
        }
    }

    /**
     * Compute the largest step size that preserves dual feasibility.
     */
    protected static double computeStepSize(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore,
                                            double termDualPartial, double slackLowerDualPartial) {
        double subproblemMinimizer = computeStepSizeSubproblemMinimizer(term, termStore, termDualPartial, slackLowerDualPartial);

        // Project subproblem solution such that dual iterates are always feasible.
        double minDualGradientRatio = Double.POSITIVE_INFINITY;

        // Dual variables for inequality constraints and (squared) hinge-loss potentials must be non-negative.
        if ((termDualPartial > 0.0) && !term.isEqualityConstraint()) {
            minDualGradientRatio = term.getDualVariable() / termDualPartial;
        }

        // Dual variables for slack bound constraints must be non-negative.
        if (slackLowerDualPartial > 0.0) {
            double slackLowerDualGradientRatio = term.getSlackBoundDualVariable() / slackLowerDualPartial;
            if (slackLowerDualGradientRatio < minDualGradientRatio) {
                minDualGradientRatio = slackLowerDualGradientRatio;
            }
        }

        // Dual variables for atom bound constraints must be non-negative.
        int[] atomIndexes = term.getAtomIndexes();
        GroundAtom[] atoms = termStore.getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                continue;
            }

            DualLCQPAtom dualLCQPAtom = dualLCQPAtoms[atomIndexes[i]];

            double lowerBoundDualGradient = dualLCQPAtom.getLowerBoundPartial(regularizationParameter);
            if (lowerBoundDualGradient > 0.0) {
                double atomLowerBoundDualGradientRatio = dualLCQPAtom.getLowerBoundDualVariable() / lowerBoundDualGradient;
                if (atomLowerBoundDualGradientRatio < minDualGradientRatio) {
                    minDualGradientRatio = atomLowerBoundDualGradientRatio;
                }
            }

            double upperBoundDualGradient = dualLCQPAtom.getUpperBoundPartial(regularizationParameter);
            if (upperBoundDualGradient > 0.0) {
                double atomUpperBoundDualGradientRatio = dualLCQPAtom.getUpperBoundDualVariable() / upperBoundDualGradient;
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
    protected static double computeStepSizeSubproblemMinimizer(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore,
                                                               double termDualPartial, double slackLowerDualPartial) {
        double numerator = 0.0;
        double denominator = 0.0;

        double potentialAtomLowerBoundSum = 0.0;
        double potentialAtomUpperBoundSum = 0.0;

        float[] coefficients = term.getCoefficients();
        int[] atomIndexes = term.getAtomIndexes();
        GroundAtom[] atoms = termStore.getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();

        for (int i = 0; i < term.size(); i++) {
            if (atoms[atomIndexes[i]].isFixed()) {
                continue;
            }

            DualLCQPAtom dualLCQPAtom = dualLCQPAtoms[atomIndexes[i]];
            double atomLowerBoundPartial = dualLCQPAtom.getLowerBoundPartial(regularizationParameter);
            double atomUpperBoundPartial = dualLCQPAtom.getUpperBoundPartial(regularizationParameter);

            potentialAtomLowerBoundSum += coefficients[i] * atomLowerBoundPartial;
            potentialAtomUpperBoundSum += coefficients[i] * atomUpperBoundPartial;

            numerator += Math.pow(atomLowerBoundPartial, 2.0);
            numerator += Math.pow(atomUpperBoundPartial, 2.0);

            denominator += (atomLowerBoundPartial - atomUpperBoundPartial - coefficients[i] * termDualPartial) * atomLowerBoundPartial;
            denominator += (atomUpperBoundPartial - atomLowerBoundPartial + coefficients[i] * termDualPartial) * atomUpperBoundPartial;
        }
        denominator = denominator / regularizationParameter;

        double potentDualUpdateStat = (termDualPartial * term.computeSelfInnerProduct()
                - potentialAtomLowerBoundSum + potentialAtomUpperBoundSum) / regularizationParameter;

        if (term.termType.equals(ReasonerTerm.TermType.SquaredHingeLossTerm)) {
            potentDualUpdateStat += termDualPartial / (regularizationParameter + term.getWeight());
        } else if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            potentDualUpdateStat += termDualPartial / regularizationParameter;
        }

        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            potentDualUpdateStat += slackLowerDualPartial / regularizationParameter;

            numerator += Math.pow(slackLowerDualPartial, 2.0);
            denominator += ((slackLowerDualPartial + termDualPartial) / regularizationParameter) * slackLowerDualPartial;
        }

        numerator += Math.pow(termDualPartial, 2.0);
        denominator += potentDualUpdateStat * termDualPartial;

        if ((numerator == 0.0) || (denominator == 0.0)) {
            return 0.0;
        }

        return numerator / denominator;
    }

    /**
     * Compute the partial derivative of the dual variable associated with the term.
     */
    protected static double computeTermDualPartial(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore) {
        double termDualPartial = 0.0;
        double observedConstant = 0.0;

        GroundAtom[] atoms = termStore.getAtomStore().getAtoms();
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
            termDualPartial += (1.0 / (regularizationParameter + term.getWeight())) * term.getDualVariable();
        } else if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            termDualPartial += (1.0 / regularizationParameter) * term.getDualVariable();
            termDualPartial += (1.0 / regularizationParameter) * term.getSlackBoundDualVariable();
            termDualPartial += (-1.0 / regularizationParameter) * term.getWeight();
        }

        termDualPartial += 2.0 * (term.getConstant() - observedConstant);

        // Clip gradients for lower bound constraint on dual variables.
        if ((!term.isEqualityConstraint()) && (termDualPartial > 0.0) && MathUtils.isZero(term.getDualVariable(), MathUtils.STRICT_EPSILON)) {
            termDualPartial = 0.0;
        }

        return termDualPartial;
    }

    protected static double computeSlackLowerBoundDualPartial(DualLCQPObjectiveTerm term) {
        double slackLowerDualPartial = 0.0;

        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            slackLowerDualPartial = (term.getSlackBoundDualVariable() + term.getDualVariable() - term.getWeight()) / regularizationParameter;

            // Clip gradient for lower bound constraint.
            if ((slackLowerDualPartial > 0.0) && MathUtils.isZero(term.getSlackBoundDualVariable(), MathUtils.STRICT_EPSILON)) {
                // Linear weighted potential.
                slackLowerDualPartial = 0.0;
            }
        }

        return slackLowerDualPartial;
    }

    protected static double evaluateDualTerm(DualLCQPObjectiveTerm term, DualLCQPTermStore termStore) {
        double potentialDualObjective = 0.0;
        double observedConstant = 0.0;

        GroundAtom[] atoms = termStore.getAtomStore().getAtoms();
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
        potentialDualObjective = term.getDualVariable() * potentialDualObjective / (2.0 * regularizationParameter);

        // Dual objective value contribution from the slack variable in the term.
        if (term.termType.equals(ReasonerTerm.TermType.SquaredHingeLossTerm)) {
            potentialDualObjective += (1.0 / (2.0 * (regularizationParameter + term.getWeight()))) * Math.pow(term.getDualVariable(), 2.0);
        } else if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            potentialDualObjective += (1.0 / (2.0 * regularizationParameter)) * Math.pow(term.getDualVariable(), 2.0);
            potentialDualObjective += (1.0 / (2.0 * regularizationParameter)) * term.getDualVariable() * term.getSlackBoundDualVariable();
            potentialDualObjective += (-1.0 / regularizationParameter) * term.getWeight() * term.getDualVariable();
            potentialDualObjective += (1.0 / (2.0 * regularizationParameter) * Math.pow(term.getWeight(), 2.0));
        }

        // Dual objective value contribution from the constant in the term.
        potentialDualObjective += 2.0 * (term.getConstant() - observedConstant) * term.getDualVariable();

        return potentialDualObjective;
    }

    @Override
    public void computeOptimalValueGradient(TermStore<DualLCQPObjectiveTerm> termStore, float[] rvAtomGradient, float[] deepAtomGradient) {
        AtomStore atomStore = termStore.getAtomStore();
        GroundAtom[] atoms = atomStore.getAtoms();

        Arrays.fill(rvAtomGradient, 0.0f);
        Arrays.fill(deepAtomGradient, 0.0f);

        for (DualLCQPObjectiveTerm term : termStore) {
            if (!term.isActive()) {
                continue;
            }

            float[] coefficients = term.getCoefficients();
            int[] atomIndexes = term.getAtomIndexes();
            for (int i = 0; i < term.size(); i++) {
                GroundAtom atom = atoms[atomIndexes[i]];

                if (atom instanceof ObservedAtom) {
                    continue;
                }

                if (atoms[atomIndexes[i]].getPredicate() instanceof DeepPredicate) {
                    deepAtomGradient[atomIndexes[i]] += (float)(term.getDualVariable() * coefficients[i]);
                }
            }
        }
    }

    protected static double evaluateDualSlackLowerBound(DualLCQPObjectiveTerm term) {
        double slackLowerBoundDualObjective = 0.0;

        if (term.termType.equals(ReasonerTerm.TermType.HingeLossTerm)) {
            slackLowerBoundDualObjective += term.getSlackBoundDualVariable() * term.getDualVariable() / (2.0 * regularizationParameter);
            slackLowerBoundDualObjective += Math.pow(term.getSlackBoundDualVariable(), 2.0) / (2.0 * regularizationParameter);
            slackLowerBoundDualObjective -= term.getSlackBoundDualVariable() * term.getWeight() / regularizationParameter;
        }

        return slackLowerBoundDualObjective;
    }

    public static ObjectiveResult computeObjective(TermStore<? extends ReasonerTerm> termStore) {
        ObjectiveResult objectiveResult = Reasoner.computeObjective(termStore);
        objectiveResult.objective += computePrimalVariableRegularization((TermStore<DualLCQPObjectiveTerm>)termStore);
        return objectiveResult;
    }

    @Override
    public ObjectiveResult parallelComputeObjective(TermStore<DualLCQPObjectiveTerm> termStore) {
        ObjectiveResult objectiveResult = super.parallelComputeObjective(termStore);
        objectiveResult.objective += computePrimalVariableRegularization(termStore);
        return objectiveResult;
    }

    protected static ObjectiveResult computeComponentObjective(TermStore<? extends ReasonerTerm> termStore, int componentIndex) {
        ObjectiveResult objectiveResult = Reasoner.computeComponentObjective(termStore, componentIndex);
        objectiveResult.objective += computePrimalVariableComponentRegularization((TermStore<DualLCQPObjectiveTerm>)termStore, componentIndex);
        return objectiveResult;
    }

    @Override
    protected ObjectiveResult parallelComputeComponentObjective(TermStore<DualLCQPObjectiveTerm> termStore, int componentIndex) {
        ObjectiveResult objectiveResult = super.parallelComputeComponentObjective(termStore, componentIndex);
        objectiveResult.objective += computePrimalVariableComponentRegularization(termStore, componentIndex);
        return objectiveResult;
    }

    private static double computePrimalVariableRegularization(TermStore<DualLCQPObjectiveTerm> termStore) {
        AtomStore atomStore = termStore.getAtomStore();
        GroundAtom[] atoms = atomStore.getAtoms();
        float[] atomValues = atomStore.getAtomValues();

        double atomValueRegularization = 0.0;
        for (int i = 0; i < atomStore.size(); i ++) {
            if (atoms[i].isFixed()) {
                continue;
            }

            atomValueRegularization += regularizationParameter * Math.pow(atomValues[i], 2.0);
        }

        return atomValueRegularization;
    }

    private static double computePrimalVariableComponentRegularization(TermStore<DualLCQPObjectiveTerm> termStore, int componentIndex) {
        AtomStore atomStore = termStore.getAtomStore();
        List<Integer> connectedComponentAtomIndexes = atomStore.getConnectedComponentAtomIndexes(componentIndex);
        float[] atomValues = atomStore.getAtomValues();
        GroundAtom[] atoms = atomStore.getAtoms();

        double atomValueRegularization = 0.0;
        for (Integer atomIndex : connectedComponentAtomIndexes) {
            GroundAtom atom = atoms[atomIndex];

            if (atom.isFixed()) {
                continue;
            }

            atomValueRegularization += regularizationParameter * Math.pow(atomValues[atomIndex], 2.0);
        }

        return atomValueRegularization;
    }

    protected ObjectiveResult parallelComputeDualObjective(DualLCQPTermStore termStore) {
        int blockSize = (int)(termStore.size() / (Parallel.getNumThreads() * 4) + 1);
        int numTermBlocks = (int)Math.ceil(termStore.size() / (float)blockSize);

        double[] workerObjectives = new double[numTermBlocks];

        Parallel.count(numTermBlocks, new DualTermObjectiveWorker(termStore, workerObjectives, blockSize));

        double objectiveValue = 0.0;
        for(int i = 0; i < numTermBlocks; i++) {
            objectiveValue += workerObjectives[i];
        }

        // The upper and lower bound constraints on the atoms are not stored in the term store,
        // so we need to compute their objective and gradient contribution here.
        GroundAtom[] atoms = termStore.getAtomStore().getAtoms();
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        for(int i = 0; i < dualLCQPAtoms.length; i++) {
            if (atoms[i] == null || atoms[i].isFixed()) {
                continue;
            }

            objectiveValue += dualLCQPAtoms[i].getLowerBoundObjective(regularizationParameter);
            objectiveValue += dualLCQPAtoms[i].getUpperBoundObjective(regularizationParameter);
        }

        return new ObjectiveResult((float)(-0.5 * objectiveValue), 0);
    }

    protected static ObjectiveResult computeComponentDualObjective(DualLCQPTermStore termStore, int componentIndex) {
        Map<Integer, List<DualLCQPObjectiveTerm>> connectedComponents = ((SimpleTermStore<DualLCQPObjectiveTerm>)termStore).getConnectedComponents();
        List<DualLCQPObjectiveTerm> component = connectedComponents.get(componentIndex);

        double objectiveValue = 0.0;
        for (DualLCQPObjectiveTerm term : component) {
            if (!term.isActive()) {
                continue;
            }

            objectiveValue += evaluateDualTerm(term, termStore);
            objectiveValue += evaluateDualSlackLowerBound(term);
        }

        // The upper and lower bound constraints on the atoms are not stored in the term store,
        // so we need to compute their objective and gradient contribution here.
        AtomStore atomStore = termStore.getAtomStore();
        GroundAtom[] atoms = atomStore.getAtoms();
        List<Integer> connectedComponentAtomIndexes = atomStore.getConnectedComponentAtomIndexes(componentIndex);
        DualLCQPAtom[] dualLCQPAtoms = termStore.getDualLCQPAtoms();
        for (Integer atomIndex : connectedComponentAtomIndexes) {
            GroundAtom atom = atoms[atomIndex];

            if (atom.isFixed()) {
                continue;
            }

            objectiveValue += dualLCQPAtoms[atomIndex].getLowerBoundObjective(regularizationParameter);
            objectiveValue += dualLCQPAtoms[atomIndex].getUpperBoundObjective(regularizationParameter);
        }

        return new ObjectiveResult((float)(-0.5 * objectiveValue), 0);
    }

    private class ComponentOptimizer extends Parallel.Worker<Long> {
        private final DualLCQPTermStore termStore;
        private final List<Integer> componentIds;
        private final int blockSize;

        public ComponentOptimizer(DualLCQPTermStore termStore, int blockSize) {
            super();

            this.termStore = termStore;
            this.componentIds = termStore.getConnectedComponentKeys();
            this.blockSize = blockSize;
        }

        @Override
        public Object clone() {
            return new ComponentOptimizer(termStore, blockSize);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            int numComponents = componentIds.size();

            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int componentIndex = (int) (blockIndex * blockSize + innerBlockIndex);

                if (componentIndex >= numComponents) {
                    break;
                }

                int compenentId = componentIds.get(componentIndex);

                List<DualLCQPObjectiveTerm> component = termStore.getConnectedComponents().get(compenentId);

                ObjectiveResult primalObjectiveResult = null;
                ObjectiveResult oldPrimalObjectiveResult = null;
                int iteration = 1;
                boolean breakDualBCD = false;
                while (!breakDualBCD) {
                    for (DualLCQPObjectiveTerm term : component) {
                        if (!term.isActive()) {
                            continue;
                        }

                        dualBlockUpdate(term, termStore);
                    }

                    if ((iteration - 1) % computePeriod == 0) {
                        float variableMovement = primalVariableComponentUpdate(termStore, compenentId);

                        oldPrimalObjectiveResult = primalObjectiveResult;
                        primalObjectiveResult = DualBCDReasoner.computeComponentObjective(termStore, compenentId);
                        ObjectiveResult dualObjectiveResult = computeComponentDualObjective(termStore, compenentId);

                        breakDualBCD = breakOptimization(iteration, primalObjectiveResult, oldPrimalObjectiveResult, dualObjectiveResult,
                                maxIterations, runFullIterations, objectiveBreak, objectiveTolerance,
                                variableMovementBreak, variableMovementTolerance, variableMovement,
                                primalDualBreak, primalDualTolerance);
                    }

                    iteration++;
                }
            }
        }
    }

    private static class DualTermObjectiveWorker extends Parallel.Worker<Long> {
        private final DualLCQPTermStore termStore;
        private final int blockSize;
        private final double[] objectives;

        public DualTermObjectiveWorker(DualLCQPTermStore termStore, double[] objectives, int blockSize) {
            super();

            this.termStore = termStore;
            this.objectives = objectives;
            this.blockSize = blockSize;
        }

        @Override
        public Object clone() {
            return new DualTermObjectiveWorker(termStore, objectives, blockSize);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            long numTerms = termStore.size();
            int blockIntIndex = (int)blockIndex;

            objectives[blockIntIndex] = 0.0;
            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int termIndex = blockIntIndex * blockSize + innerBlockIndex;

                if (termIndex >= numTerms) {
                    break;
                }

                DualLCQPObjectiveTerm term = termStore.get(termIndex);

                if (!term.isActive()) {
                    continue;
                }

                objectives[blockIntIndex] += evaluateDualTerm(term, termStore);
                objectives[blockIntIndex] += evaluateDualSlackLowerBound(term);
            }
        }
    }
}
