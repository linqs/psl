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
package org.linqs.psl.application.learning.weight.gradient.minimizer;

import org.linqs.psl.application.learning.weight.gradient.GradientDescent;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.atom.UnmanagedObservedAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedGroundArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.reasoner.duallcqp.DualBCDReasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.TermState;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Minimizer-based weight learning losses are functions of the MAP predictions made by PSL.
 */
public abstract class Minimizer extends GradientDescent {
    private static final Logger log = Logger.getLogger(Minimizer.class);

    protected float[] mapIncompatibility;

    protected float[] augmentedInferenceIncompatibility;
    protected TermState[] augmentedInferenceTermState;
    protected float[] augmentedInferenceAtomValueState;

    protected float[] latentInferenceIncompatibility;
    protected TermState[] latentInferenceTermState;
    protected float[] latentInferenceAtomValueState;

    protected WeightedArithmeticRule[] proxRules;
    protected UnmanagedObservedAtom[] proxRuleObservedAtoms;
    protected int[] proxRuleObservedAtomIndexes;
    protected float[] proxRuleObservedAtomValueGradient;
    protected final float proxRuleWeight;

    protected int internalIteration;
    protected int outerIteration;

    protected final float truthEnergyObjectiveWeight;
    protected final float initialSquaredPenaltyCoefficient;
    protected float squaredPenaltyCoefficient;
    protected final float initialLinearPenaltyCoefficient;
    protected float linearPenaltyCoefficient;

    protected final float objectiveDifferenceTolerance;
    protected final float initialLagrangianGradientTolerance;
    protected float lagrangianGradientTolerance;

    public Minimizer(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        mapIncompatibility = new float[mutableRules.size()];

        augmentedInferenceIncompatibility = new float[mutableRules.size()];
        augmentedInferenceTermState = null;
        augmentedInferenceAtomValueState = null;

        latentInferenceIncompatibility = new float[mutableRules.size()];
        latentInferenceTermState = null;
        latentInferenceAtomValueState = null;

        proxRules = null;
        proxRuleObservedAtoms = null;
        proxRuleObservedAtomValueGradient = null;
        proxRuleWeight = Options.MINIMIZER_PROX_RULE_WEIGHT.getFloat();

        internalIteration = 0;
        outerIteration = 1;

        truthEnergyObjectiveWeight = Options.MINIMIZER_TRUTH_ENERGY_OBJECTIVE_WEIGHT.getFloat();
        initialSquaredPenaltyCoefficient = Options.MINIMIZER_INITIAL_SQUARED_PENALTY.getFloat();
        squaredPenaltyCoefficient = initialSquaredPenaltyCoefficient;
        initialLinearPenaltyCoefficient = Options.MINIMIZER_INITIAL_LINEAR_PENALTY.getFloat();
        linearPenaltyCoefficient = initialLinearPenaltyCoefficient;

        initialLagrangianGradientTolerance = Options.MINIMIZER_INITIAL_LAGRANGIAN_GRADIENT_TOLERANCE.getFloat();
        lagrangianGradientTolerance = initialLagrangianGradientTolerance;
        objectiveDifferenceTolerance = Options.MINIMIZER_OBJECTIVE_DIFFERENCE_TOLERANCE.getFloat();
    }

    @Override
    protected void postInitGroundModel() {
        assert inference.getReasoner() instanceof DualBCDReasoner;

        AtomStore atomStore = inference.getTermStore().getDatabase().getAtomStore();

        // TODO(Charles): Do not create proximity terms for atoms that are fixed for inference (deep or observed).
        //  or do and think about how to set/update the constant.

        // Create and add the augmented inference proximity terms.
        int originalAtomCount = atomStore.size();
        proxRules = new WeightedArithmeticRule[originalAtomCount];
        proxRuleObservedAtoms = new UnmanagedObservedAtom[originalAtomCount];
        proxRuleObservedAtomIndexes = new int[originalAtomCount];
        proxRuleObservedAtomValueGradient = new float[originalAtomCount];
        for (int i = 0; i < originalAtomCount; i++) {
            // Create a new predicate for the proximity terms prefixed with the hashcode.
            // Users cannot create predicates prefixed with digits so this should be safe.
            Predicate proxPredicate = StandardPredicate.get(
                    String.format("augmented%s", atomStore.getAtom(i).getPredicate().getName()),
                            atomStore.getAtom(i).getPredicate().getArgumentTypes());
            Predicate.registerPredicate(proxPredicate);

            proxRuleObservedAtoms[i] = new UnmanagedObservedAtom(proxPredicate,
                    atomStore.getAtom(i).getArguments(), atomStore.getAtom(i).getValue());
            atomStore.addAtom(proxRuleObservedAtoms[i]);
        }

        for (int i = 0; i < originalAtomCount; i++) {
            proxRuleObservedAtomIndexes[i] = atomStore.getAtomIndex(proxRuleObservedAtoms[i]);
        }

        // Add the proximity terms to the term store but do not merge the constants.
        // This is because we want to be able to update the constants without having to re-ground the rules.
        // If another process is creating terms then this procedure can cause terms to be created without merged constants.
        boolean originalMergeConstants = inference.getTermStore().getTermGenerator().getMergeConstants();
        inference.getTermStore().getTermGenerator().setMergeConstants(false);

        for (int i = 0; i < proxRules.length; i++) {
            proxRules[i] = new WeightedArithmeticRule(new ArithmeticRuleExpression(
                    Arrays.asList(new ConstantNumber(1.0f), (new ConstantNumber(-1.0f))),
                    Arrays.asList(atomStore.getAtom(i), proxRuleObservedAtoms[i]),
                    FunctionComparator.EQ, new ConstantNumber(0.0f)), 0.0f, true);

            inference.getTermStore().add(new WeightedGroundArithmeticRule(
                    proxRules[i], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atomStore.getAtom(i), proxRuleObservedAtoms[i]),
                    FunctionComparator.LTE, 0.0f));
            inference.getTermStore().add(new WeightedGroundArithmeticRule(
                    proxRules[i], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atomStore.getAtom(i), proxRuleObservedAtoms[i]),
                    FunctionComparator.GTE, 0.0f));
        }

        inference.getTermStore().getTermGenerator().setMergeConstants(originalMergeConstants);

        super.postInitGroundModel();

        // Initialize augmented and latent inference warm start state objects.
        augmentedInferenceTermState = inference.getTermStore().saveState();
        float[] atomValues = atomStore.getAtomValues();
        augmentedInferenceAtomValueState = Arrays.copyOf(atomValues, atomValues.length);

        latentInferenceTermState = inference.getTermStore().saveState();
        latentInferenceAtomValueState = Arrays.copyOf(atomValues, atomValues.length);
    }

    @Override
    protected boolean breakOptimization(int iteration, float objective, float oldObjective) {
        float totalObjectiveDifference = computeObjectiveDifference();

        if (iteration > maxNumSteps) {
            return true;
        }

        if (runFullIterations) {
            return false;
        }

        // Do not break if the objective difference is greater than the tolerance.
        if (totalObjectiveDifference > objectiveDifferenceTolerance) {
            return false;
        }

        return super.breakOptimization(iteration, objective, oldObjective);
    }

    @Override
    protected void gradientStep(int iteration) {
        super.gradientStep(internalIteration);
        internalIteration++;
    }

    @Override
    protected void internalParameterGradientStep(int iteration) {
        float stepSize = computeStepSize(internalIteration);

        float gradientNorm = computeGradientNorm();

        if ((iteration > 1) && (gradientNorm < lagrangianGradientTolerance)) {
            // Update the penalty coefficients and tolerance.
            float totalObjectiveDifference = computeObjectiveDifference();

            log.trace("Outer iteration: {}, Objective Difference: {}, Squared Penalty Coefficient: {}, Linear Penalty Coefficient: {}, Lagrangian Gradient Tolerance: {}, ",
                    outerIteration, totalObjectiveDifference, squaredPenaltyCoefficient, linearPenaltyCoefficient, lagrangianGradientTolerance);

            linearPenaltyCoefficient = linearPenaltyCoefficient + 2 * squaredPenaltyCoefficient * totalObjectiveDifference;
            squaredPenaltyCoefficient = 2.0f * squaredPenaltyCoefficient;
            lagrangianGradientTolerance = initialLagrangianGradientTolerance / (outerIteration);

            internalIteration = 1;
            outerIteration++;
        }

        // Take a step in the direction of the negative gradient of the proximity rule constants
        // and project back onto box constraints.
        float[] atomValues = inference.getTermStore().getDatabase().getAtomStore().getAtomValues();
        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            proxRuleObservedAtoms[i]._assumeValue(Math.min(Math.max(
                    proxRuleObservedAtoms[i].getValue() - stepSize * proxRuleObservedAtomValueGradient[i], 0.0f), 1.0f));
            atomValues[proxRuleObservedAtomIndexes[i]] = proxRuleObservedAtoms[i].getValue();
            augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[i]] = proxRuleObservedAtoms[i].getValue();
        }
    }

    @Override
    protected void computeIterationStatistics() {
        log.trace("Running Full Inference.");
        computeFullInferenceStatistics();

        if ((internalIteration == 1) && (outerIteration == 1)) {
            // Initialize the prox rule constants to the full inference values.
            float[] atomValues = inference.getTermStore().getDatabase().getAtomStore().getAtomValues();
            for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
                proxRuleObservedAtoms[i]._assumeValue(mpeAtomValueState[i]);
                atomValues[proxRuleObservedAtomIndexes[i]] = proxRuleObservedAtoms[i].getValue();
                augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[i]] = proxRuleObservedAtoms[i].getValue();
            }
        }

        log.trace("Running Augmented Inference.");
        computeAugmentedInferenceStatistics();
        log.trace("Running Latent Inference.");
        computeLatentInferenceStatistics();

        computeProxRuleObservedAtomValueGradient();
    }

    protected void computeProxRuleObservedAtomValueGradient() {
        Arrays.fill(proxRuleObservedAtomValueGradient, 0.0f);

        addSupervisedProxRuleObservedAtomValueGradient();
        addAugmentedLagrangianProxRuleConstantsGradient();
    }

    protected abstract void addSupervisedProxRuleObservedAtomValueGradient();

    /**
     * Compute the augmented inference problem solution incompatibility.
     * The weights of the proximity terms augmenting the MAP inference problem are set to a positive value.
     */
    protected void computeAugmentedInferenceStatistics() {
        setAugmentedInferenceProxTerms();

        computeMPEStateWithWarmStart(augmentedInferenceTermState, augmentedInferenceAtomValueState);
        computeCurrentIncompatibility(augmentedInferenceIncompatibility);

        resetAugmentedInferenceProxTerms();
    }

    /**
     * Set the proximity term constants for the augmented inference problem.
     */
    private void setAugmentedInferenceProxTerms() {
        for (WeightedArithmeticRule augmentedInferenceProxRule : proxRules) {
            augmentedInferenceProxRule.setWeight(proxRuleWeight);
        }

        inMPEState = false;
    }

    /**
     * Reset the proximity term constants for the augmented inference problem back to zero.
     */
    private void resetAugmentedInferenceProxTerms() {
        for (WeightedArithmeticRule augmentedInferenceProxRule : proxRules) {
            augmentedInferenceProxRule.setWeight(0.0f);
        }

        inMPEState = false;
    }

    /**
     * Compute the latent inference problem solution incompatibility.
     * RandomVariableAtoms with labels are fixed to their observed (truth) value.
     */
    protected void computeLatentInferenceStatistics() {
        fixLabeledRandomVariables();

        computeMPEStateWithWarmStart(latentInferenceTermState, latentInferenceAtomValueState);
        computeCurrentIncompatibility(latentInferenceIncompatibility);

        unfixLabeledRandomVariables();
    }

    /**
     * Set RandomVariableAtoms with labels to their observed (truth) value.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void fixLabeledRandomVariables() {
        AtomStore atomStore = inference.getTermStore().getDatabase().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            atomStore.getAtoms()[atomIndex] = observedAtom;
            atomStore.getAtomValues()[atomIndex] = observedAtom.getValue();
            latentInferenceAtomValueState[atomIndex] = observedAtom.getValue();
            randomVariableAtom.setValue(observedAtom.getValue());
        }

        inMPEState = false;
    }

    /**
     * Set RandomVariableAtoms with labels to their unobserved state.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void unfixLabeledRandomVariables() {
        AtomStore atomStore = inference.getDatabase().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            atomStore.getAtoms()[atomIndex] = randomVariableAtom;
        }

        inMPEState = false;
    }

    /**
     * Compute the incompatibility of the MAP state.
     */
    private void computeFullInferenceStatistics() {
        computeMPEStateWithWarmStart(mpeTermState, mpeAtomValueState);
        computeCurrentIncompatibility(mapIncompatibility);
    }

    @Override
    protected float computeLearningLoss() {
        float totalObjectiveDifference = computeObjectiveDifference();
        float truthEnergy = computeTruthEnergy();
        float supervisedLoss = computeSupervisedLoss();

        log.trace("Total objective difference: {}, Truth Energy: {}, Supervised Loss: {}",
                totalObjectiveDifference, truthEnergy, supervisedLoss);

        return (squaredPenaltyCoefficient / 2.0f) * (float)Math.pow(totalObjectiveDifference, 2.0f)
                + linearPenaltyCoefficient * (totalObjectiveDifference) + truthEnergyObjectiveWeight * truthEnergy + supervisedLoss;
    }

    protected float computeTruthEnergy() {
        float energy = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            energy += mutableRules.get(i).getWeight() * latentInferenceIncompatibility[i];
        }

        return energy;
    }

    private float computeObjectiveDifference() {
        float totalEnergyDifference = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            totalEnergyDifference += mutableRules.get(i).getWeight() * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
        }

        // LCQP reasoners add a regularization to the energy function to ensure strong convexity.
        GroundAtom[] atoms = inference.getDatabase().getAtomStore().getAtoms();
        float regularizationParameter = ((DualBCDReasoner)inference.getReasoner()).regularizationParameter;
        float augmentedInferenceLCQPRegularization = 0.0f;
        float fullInferenceLCQPRegularization = 0.0f;
        for (int i = 0; i < augmentedInferenceAtomValueState.length; i++) {
            if (atoms[i] instanceof ObservedAtom) {
                continue;
            }

            augmentedInferenceLCQPRegularization += regularizationParameter * Math.pow(augmentedInferenceAtomValueState[i], 2.0f);
            fullInferenceLCQPRegularization += regularizationParameter * Math.pow(mpeAtomValueState[i], 2.0f);
        }
        totalEnergyDifference += augmentedInferenceLCQPRegularization - fullInferenceLCQPRegularization;

        float totalProxValue = 0.0f;
        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            totalProxValue += Math.pow(proxRuleObservedAtoms[i].getValue() - augmentedInferenceAtomValueState[i], 2.0f);
        }
        totalProxValue = proxRuleWeight * totalProxValue;

        return totalEnergyDifference + totalProxValue;
    }

    protected abstract float computeSupervisedLoss();

    protected void addAugmentedLagrangianProxRuleConstantsGradient() {
        float totalEnergyDifference = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            totalEnergyDifference += mutableRules.get(i).getWeight() * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
        }

        // LCQP reasoners add a regularization to the energy function to ensure strong convexity.
        GroundAtom[] atoms = inference.getDatabase().getAtomStore().getAtoms();
        float regularizationParameter = ((DualBCDReasoner)inference.getReasoner()).regularizationParameter;
        float augmentedInferenceLCQPRegularization = 0.0f;
        float fullInferenceLCQPRegularization = 0.0f;
        for (int i = 0; i < augmentedInferenceAtomValueState.length; i++) {
            if (atoms[i] instanceof ObservedAtom) {
                continue;
            }

            augmentedInferenceLCQPRegularization += regularizationParameter * Math.pow(augmentedInferenceAtomValueState[i], 2.0f);
            fullInferenceLCQPRegularization += regularizationParameter * Math.pow(mpeAtomValueState[i], 2.0f);
        }
        totalEnergyDifference += augmentedInferenceLCQPRegularization - fullInferenceLCQPRegularization;

        float[] proxRuleObservedAtomValueMoreauGradient = new float[proxRuleObservedAtoms.length];
        float totalProxValue = 0.0f;
        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            float proxRuleIncompatibility = proxRuleObservedAtoms[i].getValue() - augmentedInferenceAtomValueState[i];
            proxRuleObservedAtomValueMoreauGradient[i] = 2.0f * proxRuleWeight * proxRuleIncompatibility;
            totalProxValue += Math.pow(proxRuleIncompatibility, 2.0f);
        }
        totalProxValue = proxRuleWeight * totalProxValue;

        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            proxRuleObservedAtomValueGradient[i] += linearPenaltyCoefficient * proxRuleObservedAtomValueMoreauGradient[i];
            proxRuleObservedAtomValueGradient[i] += squaredPenaltyCoefficient
                    * (totalEnergyDifference + totalProxValue)
                    * proxRuleObservedAtomValueMoreauGradient[i];
        }
    }

    @Override
    protected void addLearningLossWeightGradient() {
        float[] incompatibilityDifference = new float[mutableRules.size()];
        float totalEnergyDifference = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            incompatibilityDifference[i] = augmentedInferenceIncompatibility[i] - mapIncompatibility[i];
            totalEnergyDifference += mutableRules.get(i).getWeight() * incompatibilityDifference[i];
        }

        // LCQP reasoners add a regularization to the energy function to ensure strong convexity.
        GroundAtom[] atoms = inference.getDatabase().getAtomStore().getAtoms();
        float regularizationParameter = ((DualBCDReasoner)inference.getReasoner()).regularizationParameter;
        float augmentedInferenceLCQPRegularization = 0.0f;
        float fullInferenceLCQPRegularization = 0.0f;
        for (int i = 0; i < augmentedInferenceAtomValueState.length; i++) {
            if (atoms[i] instanceof ObservedAtom) {
                continue;
            }

            augmentedInferenceLCQPRegularization += regularizationParameter * Math.pow(augmentedInferenceAtomValueState[i], 2.0f);
            fullInferenceLCQPRegularization += regularizationParameter * Math.pow(mpeAtomValueState[i], 2.0f);
        }
        totalEnergyDifference += augmentedInferenceLCQPRegularization - fullInferenceLCQPRegularization;

        float totalProxValue = 0.0f;
        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            totalProxValue += Math.pow(proxRuleObservedAtoms[i].getValue() - augmentedInferenceAtomValueState[i], 2.0f);
        }
        totalProxValue = proxRuleWeight * totalProxValue;

        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += linearPenaltyCoefficient * incompatibilityDifference[i];
            weightGradient[i] += squaredPenaltyCoefficient * (totalEnergyDifference + totalProxValue) * incompatibilityDifference[i];
            weightGradient[i] += truthEnergyObjectiveWeight * latentInferenceIncompatibility[i];
        }
    }

    private void clipProxRuleObservedAtomValueGradient(float[] gradient) {
        for (int i = 0; i < gradient.length; i++) {
            if (MathUtils.isZero(proxRuleObservedAtoms[i].getValue()) && gradient[i] > 0.0f) {
                gradient[i] = 0.0f;
            } else if (MathUtils.equals(proxRuleObservedAtoms[i].getValue(), 1.0f) && gradient[i] < 0.0f) {
                gradient[i] = 0.0f;
            }
        }
    }

    protected float computeGradientNorm() {
        float gradientNorm = super.computeGradientNorm();

        log.trace("Weight gradient: {}", weightGradient);
        log.trace("Weight gradient norm: {}", gradientNorm);

        float[] boxClippedProxRuleObservedAtomValueGradient = proxRuleObservedAtomValueGradient.clone();
        clipProxRuleObservedAtomValueGradient(boxClippedProxRuleObservedAtomValueGradient);
        float boxClippedProxRuleObservedAtomValueGradientNorm = MathUtils.pNorm(boxClippedProxRuleObservedAtomValueGradient, stoppingGradientNorm);

        log.trace("Proximity variable gradient norm: {}", boxClippedProxRuleObservedAtomValueGradientNorm);

        gradientNorm += boxClippedProxRuleObservedAtomValueGradientNorm;

        return gradientNorm;
    }
}
