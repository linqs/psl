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
import org.linqs.psl.model.predicate.DeepPredicate;
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

import java.util.ArrayList;
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

    protected float[] augmentedRVAtomGradient;
    protected float[] augmentedDeepAtomGradient;

    protected List<Integer> rvAtomIndexToProxIndex;
    protected List<Integer> proxIndexToRVAtomIndex;
    protected WeightedArithmeticRule[] proxRules;
    protected UnmanagedObservedAtom[] proxRuleObservedAtoms;
    protected int[] proxRuleObservedAtomIndexes;
    protected float[] proxRuleObservedAtomValueGradient;
    protected final float proxRuleWeight;

    protected int internalIteration;
    protected int outerIteration;
    protected int numInternalIterations;

    protected final float initialSquaredPenaltyCoefficient;
    protected float squaredPenaltyCoefficient;
    protected float squaredPenaltyCoefficientDelta;
    protected final float initialLinearPenaltyCoefficient;
    protected float linearPenaltyCoefficient;

    protected final float objectiveDifferenceTolerance;

    public Minimizer(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        mapIncompatibility = new float[mutableRules.size()];

        augmentedInferenceIncompatibility = new float[mutableRules.size()];
        augmentedInferenceTermState = null;
        augmentedInferenceAtomValueState = null;

        rvAtomIndexToProxIndex = new ArrayList<Integer>();
        proxIndexToRVAtomIndex = new ArrayList<Integer>();
        proxRules = null;
        proxRuleObservedAtoms = null;
        proxRuleObservedAtomValueGradient = null;
        proxRuleWeight = Options.MINIMIZER_PROX_RULE_WEIGHT.getFloat();

        internalIteration = 0;
        outerIteration = 1;
        numInternalIterations = Options.MINIMIZER_NUM_INTERNAL_ITERATIONS.getInt();

        initialSquaredPenaltyCoefficient = Options.MINIMIZER_INITIAL_SQUARED_PENALTY.getFloat();
        squaredPenaltyCoefficient = initialSquaredPenaltyCoefficient;
        squaredPenaltyCoefficientDelta = Options.MINIMIZER_SQUARED_PENALTY_DELTA.getFloat();
        initialLinearPenaltyCoefficient = Options.MINIMIZER_INITIAL_LINEAR_PENALTY.getFloat();
        linearPenaltyCoefficient = initialLinearPenaltyCoefficient;

        objectiveDifferenceTolerance = Options.MINIMIZER_OBJECTIVE_DIFFERENCE_TOLERANCE.getFloat();
    }

    @Override
    protected void postInitGroundModel() {
        assert inference.getReasoner() instanceof DualBCDReasoner;

        AtomStore atomStore = inference.getTermStore().getDatabase().getAtomStore();

        // Create and add the augmented inference proximity terms.
        int unFixedAtomCount = 0;
        for (GroundAtom atom : atomStore) {
            if (atom.isFixed()) {
                continue;
            }

            unFixedAtomCount++;
        }

        // Add the proximity terms to the term store but do not merge the constants.
        // This is because we want to be able to update the constants without having to re-ground the rules.
        // If another process is creating terms then this procedure can cause terms to be created without merged constants.
        boolean originalMergeConstants = inference.getTermStore().getTermGenerator().getMergeConstants();
        inference.getTermStore().getTermGenerator().setMergeConstants(false);

        proxRules = new WeightedArithmeticRule[unFixedAtomCount];
        proxRuleObservedAtoms = new UnmanagedObservedAtom[unFixedAtomCount];
        proxRuleObservedAtomIndexes = new int[unFixedAtomCount];
        proxRuleObservedAtomValueGradient = new float[unFixedAtomCount];
        int originalAtomCount = atomStore.size();
        int proxRuleIndex = 0;
        for (int i = 0; i < originalAtomCount; i++) {
            GroundAtom atom = atomStore.getAtom(i);

            if (atom.isFixed()) {
                rvAtomIndexToProxIndex.add(-1);
                continue;
            }

            rvAtomIndexToProxIndex.add(proxRuleIndex);
            proxIndexToRVAtomIndex.add(i);

            // Create a new predicate for the proximity terms prefixed with the hashcode.
            // Users cannot create predicates prefixed with digits so this should be safe.
            Predicate proxPredicate = StandardPredicate.get(
                    String.format("augmented%s", atom.getPredicate().getName()), atom.getPredicate().getArgumentTypes());
            Predicate.registerPredicate(proxPredicate);

            proxRuleObservedAtoms[proxRuleIndex] = new UnmanagedObservedAtom(proxPredicate, atom.getArguments(), atom.getValue());
            atomStore.addAtom(proxRuleObservedAtoms[proxRuleIndex]);
            proxRuleObservedAtomIndexes[proxRuleIndex] = atomStore.getAtomIndex(proxRuleObservedAtoms[proxRuleIndex]);

            proxRules[proxRuleIndex] = new WeightedArithmeticRule(new ArithmeticRuleExpression(
                    Arrays.asList(new ConstantNumber(1.0f), (new ConstantNumber(-1.0f))),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.EQ, new ConstantNumber(0.0f)), proxRuleWeight, true);

            proxRules[proxRuleIndex].setActive(false);

            inference.getTermStore().add(new WeightedGroundArithmeticRule(
                    proxRules[proxRuleIndex], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.LTE, 0.0f));
            inference.getTermStore().add(new WeightedGroundArithmeticRule(
                    proxRules[proxRuleIndex], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.GTE, 0.0f));

            proxRuleIndex++;
        }

        inference.getTermStore().getTermGenerator().setMergeConstants(originalMergeConstants);

        super.postInitGroundModel();

        // Initialize augmented inference warm start state objects.
        augmentedInferenceTermState = inference.getTermStore().saveState();
        float[] atomValues = atomStore.getAtomValues();
        augmentedInferenceAtomValueState = Arrays.copyOf(atomValues, atomValues.length);

        augmentedRVAtomGradient = new float[atomValues.length];
        augmentedDeepAtomGradient = new float[atomValues.length];
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

        if (outerIteration == 1) {
            return false;
        }

        // Break if the objective difference is greater than the tolerance.
        return totalObjectiveDifference < objectiveDifferenceTolerance;
    }

    @Override
    protected void gradientStep(int iteration) {
        super.gradientStep(iteration);
        internalIteration++;
    }

    @Override
    protected void internalParameterGradientStep(int iteration) {
        if (internalIteration >= numInternalIterations) {
            // Update the penalty coefficients and tolerance.
            float totalObjectiveDifference = computeObjectiveDifference();

            log.trace("Outer iteration: {}, Objective Difference: {}, Squared Penalty Coefficient: {}, Linear Penalty Coefficient: {}.",
                    outerIteration, totalObjectiveDifference, squaredPenaltyCoefficient, linearPenaltyCoefficient);

            linearPenaltyCoefficient = linearPenaltyCoefficient + 2 * squaredPenaltyCoefficient * totalObjectiveDifference;
            squaredPenaltyCoefficient = squaredPenaltyCoefficient + squaredPenaltyCoefficientDelta;

            internalIteration = 0;
            outerIteration++;
        }

        // Take a step in the direction of the negative gradient of the proximity rule constants and project back onto box constraints.
        float stepSize = computeStepSize(iteration);
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
        computeFullInferenceStatistics();

        if ((internalIteration == 1) && (outerIteration == 1)) {
            // Initialize the proximity rule constants to the full inference values and their truth values if they exist.
            AtomStore atomStore = inference.getDatabase().getAtomStore();
            float[] atomValues = atomStore.getAtomValues();
            for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
                proxRuleObservedAtoms[i]._assumeValue(mpeAtomValueState[proxIndexToRVAtomIndex.get(i)]);
                atomValues[proxRuleObservedAtomIndexes[i]] = mpeAtomValueState[proxIndexToRVAtomIndex.get(i)];
                augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[i]] = mpeAtomValueState[proxIndexToRVAtomIndex.get(i)];
            }

            for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
                RandomVariableAtom randomVariableAtom = entry.getKey();
                ObservedAtom observedAtom = entry.getValue();
                int atomIndex = atomStore.getAtomIndex(randomVariableAtom);

                proxRuleObservedAtoms[rvAtomIndexToProxIndex.get(atomIndex)]._assumeValue(observedAtom.getValue());
                atomValues[proxRuleObservedAtomIndexes[rvAtomIndexToProxIndex.get(atomIndex)]] = observedAtom.getValue();
                augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[rvAtomIndexToProxIndex.get(atomIndex)]] = observedAtom.getValue();
            }
        }

        computeAugmentedInferenceStatistics();

        computeProxRuleObservedAtomValueGradient();
    }

    @Override
    protected void computeTotalAtomGradient() {
        float[] incompatibilityDifference = new float[mutableRules.size()];
        float totalEnergyDifference = computeTotalEnergyDifference(incompatibilityDifference);

        for (int i = 0; i < inference.getDatabase().getAtomStore().size(); i++) {
            float rvGradientDifference = augmentedRVAtomGradient[i] - rvAtomGradient[i];
            float deepGradientDifference = augmentedDeepAtomGradient[i] - deepAtomGradient[i];

            rvAtomGradient[i] = squaredPenaltyCoefficient * totalEnergyDifference * rvGradientDifference + linearPenaltyCoefficient * rvGradientDifference;
            deepAtomGradient[i] = squaredPenaltyCoefficient * totalEnergyDifference * deepGradientDifference + linearPenaltyCoefficient * deepGradientDifference;
        }
    }

    protected void computeProxRuleObservedAtomValueGradient() {
        Arrays.fill(proxRuleObservedAtomValueGradient, 0.0f);

        addSupervisedProxRuleObservedAtomValueGradient();
        addAugmentedLagrangianProxRuleConstantsGradient();
    }

    /**
     * Compute the incompatibility of the MAP state and the gradient of the energy function at the MAP state.
     */
    private void computeFullInferenceStatistics() {
        log.trace("Running Full Inference.");
        computeMPEStateWithWarmStart(mpeTermState, mpeAtomValueState);
        computeCurrentIncompatibility(mapIncompatibility);
        inference.getReasoner().computeOptimalValueGradient(inference.getTermStore(), rvAtomGradient, deepAtomGradient);
    }

    /**
     * Compute the augmented inference problem solution incompatibility and the gradient of the energy function at the augment inference MAP state.
     * The weights of the proximity terms augmenting the MAP inference problem are first set to a positive value and then reset after inference is completed.
     */
    protected void computeAugmentedInferenceStatistics() {
        setAugmentedInferenceProxTerms();

        log.trace("Running Augmented Inference.");
        computeMPEStateWithWarmStart(augmentedInferenceTermState, augmentedInferenceAtomValueState);
        computeCurrentIncompatibility(augmentedInferenceIncompatibility);
        // TODO(Charles): This gradient does not include the gradient of the regularization on individual random variable atoms.
        //  This is okay for now since we are only using the gradient of the deep atoms.
        inference.getReasoner().computeOptimalValueGradient(inference.getTermStore(), augmentedRVAtomGradient, augmentedDeepAtomGradient);

        resetAugmentedInferenceProxTerms();
    }

    /**
     * Set the proximity term constants for the augmented inference problem.
     */
    private void setAugmentedInferenceProxTerms() {
        for (WeightedArithmeticRule augmentedInferenceProxRule : proxRules) {
            augmentedInferenceProxRule.setActive(true);
        }

        inMPEState = false;
    }

    /**
     * Reset the proximity term constants for the augmented inference problem back to zero.
     */
    private void resetAugmentedInferenceProxTerms() {
        for (WeightedArithmeticRule augmentedInferenceProxRule : proxRules) {
            augmentedInferenceProxRule.setActive(false);
        }

        inMPEState = false;
    }

    @Override
    protected float computeLearningLoss() {
        float totalObjectiveDifference = computeObjectiveDifference();
        float supervisedLoss = computeSupervisedLoss();

        log.trace("Total objective difference: {}, Supervised Loss: {}",
                totalObjectiveDifference, supervisedLoss);

        return (squaredPenaltyCoefficient / 2.0f) * (float)Math.pow(totalObjectiveDifference, 2.0f)
                + linearPenaltyCoefficient * (totalObjectiveDifference) + supervisedLoss;
    }

    private float computeObjectiveDifference() {
        float[] incompatibilityDifference = new float[mutableRules.size()];
        float totalEnergyDifference = computeTotalEnergyDifference(incompatibilityDifference);

        float totalProxValue = computeTotalProxValue(new float[proxRuleObservedAtoms.length]);

        return totalEnergyDifference + totalProxValue;
    }

    protected abstract float computeSupervisedLoss();

    protected void addAugmentedLagrangianProxRuleConstantsGradient() {
        float[] incompatibilityDifference = new float[mutableRules.size()];
        float totalEnergyDifference = computeTotalEnergyDifference(incompatibilityDifference);

        float[] proxRuleIncompatibility = new float[proxRuleObservedAtoms.length];
        float totalProxValue = computeTotalProxValue(proxRuleIncompatibility);

        float[] proxRuleObservedAtomValueMoreauGradient = new float[proxRuleObservedAtoms.length];
        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            proxRuleObservedAtomValueMoreauGradient[i] = 2.0f * proxRuleWeight * proxRuleIncompatibility[i];
        }

        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            proxRuleObservedAtomValueGradient[i] += linearPenaltyCoefficient * proxRuleObservedAtomValueMoreauGradient[i];
            proxRuleObservedAtomValueGradient[i] += squaredPenaltyCoefficient
                    * (totalEnergyDifference + totalProxValue)
                    * proxRuleObservedAtomValueMoreauGradient[i];
        }
    }

    protected abstract void addSupervisedProxRuleObservedAtomValueGradient();

    @Override
    protected void addLearningLossWeightGradient() {
        float[] incompatibilityDifference = new float[mutableRules.size()];
        float totalEnergyDifference = computeTotalEnergyDifference(incompatibilityDifference);

        float totalProxValue = computeTotalProxValue(new float[proxRuleObservedAtoms.length]);

        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += linearPenaltyCoefficient * incompatibilityDifference[i];
            weightGradient[i] += squaredPenaltyCoefficient * (totalEnergyDifference + totalProxValue) * incompatibilityDifference[i];
        }
    }

    private float computeTotalEnergyDifference(float[] incompatibilityDifference){
        // LCQP reasoners add a regularization to the energy function to ensure strong convexity.
        float regularizationParameter = (float)((DualBCDReasoner)inference.getReasoner()).regularizationParameter;

        float totalEnergyDifference = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            incompatibilityDifference[i] = augmentedInferenceIncompatibility[i] - mapIncompatibility[i];
            if (mutableRules.get(i).isSquared()) {
                totalEnergyDifference += (mutableRules.get(i).getWeight() + regularizationParameter) * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
            } else {
                totalEnergyDifference += mutableRules.get(i).getWeight() * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
                totalEnergyDifference += regularizationParameter * (Math.pow(augmentedInferenceIncompatibility[i], 2.0f) - Math.pow(mapIncompatibility[i], 2.0f));
            }
        }

        GroundAtom[] atoms = inference.getDatabase().getAtomStore().getAtoms();
        float augmentedInferenceLCQPRegularization = 0.0f;
        float fullInferenceLCQPRegularization = 0.0f;
        for (int i = 0; i < inference.getDatabase().getAtomStore().size(); i++) {
            if (atoms[i].isFixed()) {
                continue;
            }

            augmentedInferenceLCQPRegularization += regularizationParameter * Math.pow(augmentedInferenceAtomValueState[i], 2.0f);
            fullInferenceLCQPRegularization += regularizationParameter * Math.pow(mpeAtomValueState[i], 2.0f);
        }
        totalEnergyDifference += augmentedInferenceLCQPRegularization - fullInferenceLCQPRegularization;

        return totalEnergyDifference;
    }

    private float computeTotalProxValue(float[] proxRuleIncompatibility) {
        float totalProxValue = 0.0f;
        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            proxRuleIncompatibility[i] = proxRuleObservedAtoms[i].getValue() - augmentedInferenceAtomValueState[proxIndexToRVAtomIndex.get(i)];
            totalProxValue += Math.pow(proxRuleIncompatibility[i], 2.0f);
        }
        totalProxValue = proxRuleWeight * totalProxValue;

        return totalProxValue;
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

        log.trace("Weight gradient: {}", Arrays.toString(weightGradient));
        log.trace("Weight gradient norm: {}", gradientNorm);

        float[] boxClippedProxRuleObservedAtomValueGradient = proxRuleObservedAtomValueGradient.clone();
        clipProxRuleObservedAtomValueGradient(boxClippedProxRuleObservedAtomValueGradient);
        float boxClippedProxRuleObservedAtomValueGradientNorm = MathUtils.pNorm(boxClippedProxRuleObservedAtomValueGradient, stoppingGradientNorm);

        log.trace("Proximity variable gradient norm: {}", boxClippedProxRuleObservedAtomValueGradientNorm);

        gradientNorm += boxClippedProxRuleObservedAtomValueGradientNorm;

        return gradientNorm;
    }
}
