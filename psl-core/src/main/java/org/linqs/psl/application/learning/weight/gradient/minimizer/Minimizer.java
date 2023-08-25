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
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedGroundArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.reasoner.duallcqp.DualBCDReasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.ReasonerTerm;
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

    protected float[] latentInferenceIncompatibility;
    protected TermState[] latentInferenceTermState;
    protected float[] latentInferenceAtomValueState;

    protected float[] mapIncompatibility;
    protected float[] mapSquaredIncompatibility;

    protected float[] augmentedInferenceIncompatibility;
    protected float[] augmentedInferenceSquaredIncompatibility;
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

    protected float parameterMovementTolerance;
    protected float finalParameterMovementTolerance;
    protected float constraintTolerance;
    protected float finalConstraintTolerance;

    protected boolean initializedProxRuleConstants;
    protected int outerIteration;

    protected final float initialSquaredPenaltyCoefficient;
    protected float squaredPenaltyCoefficient;
    protected float squaredPenaltyCoefficientIncreaseRate;
    protected final float initialLinearPenaltyCoefficient;
    protected float linearPenaltyCoefficient;

    public Minimizer(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                     Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        latentInferenceIncompatibility = new float[mutableRules.size()];
        latentInferenceTermState = null;
        latentInferenceAtomValueState = null;

        mapIncompatibility = new float[mutableRules.size()];
        mapSquaredIncompatibility = new float[mutableRules.size()];

        augmentedInferenceIncompatibility = new float[mutableRules.size()];
        augmentedInferenceSquaredIncompatibility = new float[mutableRules.size()];
        augmentedInferenceTermState = null;
        augmentedInferenceAtomValueState = null;

        rvAtomIndexToProxIndex = new ArrayList<Integer>();
        proxIndexToRVAtomIndex = new ArrayList<Integer>();
        proxRules = null;
        proxRuleObservedAtoms = null;
        proxRuleObservedAtomValueGradient = null;
        proxRuleWeight = Options.MINIMIZER_PROX_RULE_WEIGHT.getFloat();

        initialSquaredPenaltyCoefficient = Options.MINIMIZER_INITIAL_SQUARED_PENALTY.getFloat();
        squaredPenaltyCoefficient = initialSquaredPenaltyCoefficient;
        squaredPenaltyCoefficientIncreaseRate = Options.MINIMIZER_SQUARED_PENALTY_INCREASE_RATE.getFloat();
        initialLinearPenaltyCoefficient = Options.MINIMIZER_INITIAL_LINEAR_PENALTY.getFloat();
        linearPenaltyCoefficient = initialLinearPenaltyCoefficient;

        parameterMovementTolerance = 1.0f / initialSquaredPenaltyCoefficient;
        finalParameterMovementTolerance = Options.MINIMIZER_FINAL_PARAMETER_MOVEMENT_CONVERGENCE_TOLERANCE.getFloat();
        constraintTolerance = (float)(1.0f / Math.pow(initialSquaredPenaltyCoefficient, 0.1f));
        finalConstraintTolerance = Options.MINIMIZER_OBJECTIVE_DIFFERENCE_TOLERANCE.getFloat();
        initializedProxRuleConstants = false;
        outerIteration = 1;
    }

    @Override
    protected void postInitGroundModel() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getDatabase().getAtomStore();

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
        boolean originalMergeConstants = trainInferenceApplication.getTermStore().getTermGenerator().getMergeConstants();
        trainInferenceApplication.getTermStore().getTermGenerator().setMergeConstants(false);

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

            // Create a new predicate for the proximity terms.
            Predicate proxPredicate = StandardPredicate.get(
                    String.format("augmented%s", atom.getPredicate().getName()), atom.getPredicate().getArgumentTypes());

            if (Predicate.get(proxPredicate.getName()) == null) {
                Predicate.registerPredicate(proxPredicate);
            } else {
                assert (Predicate.get(proxPredicate.getName()).equals(proxPredicate)) :
                        "The 'augmented' prefix on predicate names is reserved for weight learning functionality.";
            }

            proxRuleObservedAtoms[proxRuleIndex] = new UnmanagedObservedAtom(proxPredicate, atom.getArguments(), atom.getValue());
            atomStore.addAtom(proxRuleObservedAtoms[proxRuleIndex]);
            proxRuleObservedAtomIndexes[proxRuleIndex] = atomStore.getAtomIndex(proxRuleObservedAtoms[proxRuleIndex]);

            proxRules[proxRuleIndex] = new WeightedArithmeticRule(new ArithmeticRuleExpression(
                    Arrays.asList(new ConstantNumber(1.0f), (new ConstantNumber(-1.0f))),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.EQ, new ConstantNumber(0.0f)), proxRuleWeight, true);

            proxRules[proxRuleIndex].setActive(false);

            trainInferenceApplication.getTermStore().add(new WeightedGroundArithmeticRule(
                    proxRules[proxRuleIndex], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.LTE, 0.0f));
            trainInferenceApplication.getTermStore().add(new WeightedGroundArithmeticRule(
                    proxRules[proxRuleIndex], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.GTE, 0.0f));

            proxRuleIndex++;
        }

        trainInferenceApplication.getTermStore().getTermGenerator().setMergeConstants(originalMergeConstants);

        super.postInitGroundModel();

        // Initialize latent and augmented inference warm start state objects.
        float[] atomValues = trainInferenceApplication.getDatabase().getAtomStore().getAtomValues();

        latentInferenceTermState = trainInferenceApplication.getTermStore().saveState();
        latentInferenceAtomValueState = Arrays.copyOf(atomValues, atomValues.length);

        augmentedInferenceTermState = trainInferenceApplication.getTermStore().saveState();
        augmentedInferenceAtomValueState = Arrays.copyOf(atomValues, atomValues.length);

        augmentedRVAtomGradient = new float[atomValues.length];
        augmentedDeepAtomGradient = new float[atomValues.length];
    }

    @Override
    protected boolean breakOptimization(int iteration, float objective, float oldObjective) {
        if (iteration > maxNumSteps) {
            log.trace("Breaking Weight Learning. Reached maximum number of iterations: {}", maxNumSteps);
            return true;
        }

        if (runFullIterations) {
            return false;
        }

        float totalObjectiveDifference = computeObjectiveDifference();
        if (totalObjectiveDifference < finalConstraintTolerance) {
            log.trace("Breaking Weight Learning. Objective difference {} is less than final constraint tolerance {}.",
                    totalObjectiveDifference, finalConstraintTolerance);
            return true;
        }

        return false;
    }

    @Override
    protected void gradientStep(int iteration) {
        parameterMovement = 0.0f;
        parameterMovement += weightGradientStep(iteration);
        parameterMovement += internalParameterGradientStep(iteration);
        parameterMovement += atomGradientStep();

        // Update the penalty coefficients and tolerance.
        float totalObjectiveDifference = computeObjectiveDifference();

        if ((iteration > 0) && (parameterMovement < parameterMovementTolerance)) {
            outerIteration++;

            if (totalObjectiveDifference < constraintTolerance) {
                if ((totalObjectiveDifference < finalConstraintTolerance) && (parameterMovement < finalParameterMovementTolerance)) {
                    // Learning has converged.
                    return;
                }
                linearPenaltyCoefficient = linearPenaltyCoefficient + 2 * squaredPenaltyCoefficient * totalObjectiveDifference;
                constraintTolerance = (float)(constraintTolerance / Math.pow(squaredPenaltyCoefficient, 0.9));
                parameterMovementTolerance = parameterMovementTolerance / squaredPenaltyCoefficient;
            } else {
                squaredPenaltyCoefficient = squaredPenaltyCoefficientIncreaseRate * squaredPenaltyCoefficient;
                constraintTolerance = (float)(1.0f / Math.pow(squaredPenaltyCoefficient, 0.1));
                parameterMovementTolerance = (float)(1.0f / squaredPenaltyCoefficient);
            }
        }

        log.trace("Outer iteration: {}, Objective Difference: {}, Parameter Movement: {}, Squared Penalty Coefficient: {}, Linear Penalty Coefficient: {}, Constraint Tolerance: {}, parameterMovementTolerance: {}.",
                outerIteration, totalObjectiveDifference, parameterMovement, squaredPenaltyCoefficient, linearPenaltyCoefficient, constraintTolerance, parameterMovementTolerance);
    }

    @Override
    protected float internalParameterGradientStep(int iteration) {
        float proxRuleObservedAtomsValueMovement = 0.0f;
        // Take a step in the direction of the negative gradient of the proximity rule constants and project back onto box constraints.
        float stepSize = computeStepSize(iteration);
        float[] atomValues = trainInferenceApplication.getTermStore().getDatabase().getAtomStore().getAtomValues();
        for (int i = 0; i < proxRules.length; i++) {
            float newProxRuleObservedAtomsValue = Math.min(Math.max(
                    proxRuleObservedAtoms[i].getValue() - stepSize * proxRuleObservedAtomValueGradient[i], 0.0f), 1.0f);
            proxRuleObservedAtomsValueMovement += Math.abs(proxRuleObservedAtoms[i].getValue() - newProxRuleObservedAtomsValue);

            proxRuleObservedAtoms[i]._assumeValue(newProxRuleObservedAtomsValue);
            atomValues[proxRuleObservedAtomIndexes[i]] = newProxRuleObservedAtomsValue;
            augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[i]] = newProxRuleObservedAtomsValue;
        }

        return proxRuleObservedAtomsValueMovement;
    }

    protected void initializeProximityRuleConstants() {
        // Initialize the proximity rule constants to the truth if it exists or the latent MAP state.
        fixLabeledRandomVariables();

        log.trace("Running Latent Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, latentInferenceTermState, latentInferenceAtomValueState);
        inTrainingMAPState = true;

        unfixLabeledRandomVariables();

        AtomStore atomStore = trainInferenceApplication.getDatabase().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        System.arraycopy(latentInferenceAtomValueState, 0, augmentedInferenceAtomValueState, 0, latentInferenceAtomValueState.length);

        for (int i = 0; i < proxRules.length; i++) {
            proxRuleObservedAtoms[i]._assumeValue(latentInferenceAtomValueState[proxIndexToRVAtomIndex.get(i)]);
            atomValues[proxRuleObservedAtomIndexes[i]] = latentInferenceAtomValueState[proxIndexToRVAtomIndex.get(i)];
            augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[i]] = latentInferenceAtomValueState[proxIndexToRVAtomIndex.get(i)];
        }

        // Overwrite the latent MAP state value with the truth if it exists.
        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int rvAtomIndex = atomStore.getAtomIndex(randomVariableAtom);
            int proxRuleIndex = rvAtomIndexToProxIndex.get(rvAtomIndex);

            proxRuleObservedAtoms[proxRuleIndex]._assumeValue(observedAtom.getValue());
            atomValues[proxRuleObservedAtomIndexes[proxRuleIndex]] = observedAtom.getValue();
            augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[proxRuleIndex]] = observedAtom.getValue();
        }

        initializedProxRuleConstants = true;
    }

    @Override
    protected void computeIterationStatistics() {
        computeFullInferenceStatistics();

        if (!initializedProxRuleConstants) {
            initializeProximityRuleConstants();
        }

        computeAugmentedInferenceStatistics();

        computeProxRuleObservedAtomValueGradient();
    }

    @Override
    protected void computeTotalAtomGradient() {
        float totalEnergyDifference = computeObjectiveDifference();

        for (int i = 0; i < trainInferenceApplication.getDatabase().getAtomStore().size(); i++) {
            float rvGradientDifference = augmentedRVAtomGradient[i] - MAPRVAtomGradient[i];
            float deepGradientDifference = augmentedDeepAtomGradient[i] - MAPDeepAtomGradient[i];

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
     * Compute the incompatibility of the mpe state and the gradient of the energy function at the mpe state.
     */
    private void computeFullInferenceStatistics() {
        log.trace("Running Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);
        inTrainingMAPState = true;

        computeCurrentIncompatibility(mapIncompatibility);
        computeCurrentSquaredIncompatibility(mapSquaredIncompatibility);

        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), MAPRVAtomGradient, MAPDeepAtomGradient);
    }

    /**
     * Compute the augmented inference problem solution incompatibility and the gradient of the energy function at the augmented inference mpe state.
     */
    protected void computeAugmentedInferenceStatistics() {
        activateAugmentedInferenceProxTerms();

        log.trace("Running Augmented Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, augmentedInferenceTermState, augmentedInferenceAtomValueState);
        inTrainingMAPState = true;

        computeCurrentIncompatibility(augmentedInferenceIncompatibility);
        computeCurrentSquaredIncompatibility(augmentedInferenceSquaredIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), augmentedRVAtomGradient, augmentedDeepAtomGradient);

        deactivateAugmentedInferenceProxTerms();
    }

    /**
     * Set the proximity term constants for the augmented inference problem.
     */
    private void activateAugmentedInferenceProxTerms() {
        for (WeightedArithmeticRule augmentedInferenceProxRule : proxRules) {
            augmentedInferenceProxRule.setActive(true);
        }

        inTrainingMAPState = false;
    }

    /**
     * Reset the proximity term constants for the augmented inference problem back to zero.
     */
    private void deactivateAugmentedInferenceProxTerms() {
        for (WeightedArithmeticRule augmentedInferenceProxRule : proxRules) {
            augmentedInferenceProxRule.setActive(false);
        }

        inTrainingMAPState = false;
    }

    @Override
    protected float computeLearningLoss() {
        float totalObjectiveDifference = computeObjectiveDifference();
        float supervisedLoss = computeSupervisedLoss();
        float totalProxValue = computeTotalProxValue(new float[proxRuleObservedAtoms.length]);

        log.trace("Total Prox Loss: {}, Total objective difference: {}, Supervised Loss: {}",
                totalProxValue, totalObjectiveDifference, supervisedLoss);

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
        // DualBCDReasoners add a regularization to the energy function to ensure strong convexity.
        float regularizationParameter = 0.0f;
        if (trainInferenceApplication.getReasoner() instanceof DualBCDReasoner) {
            regularizationParameter = (float)((DualBCDReasoner)trainInferenceApplication.getReasoner()).regularizationParameter;
        }

        float totalEnergyDifference = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            incompatibilityDifference[i] = augmentedInferenceIncompatibility[i] - mapIncompatibility[i];
            if (mutableRules.get(i).isSquared()) {
                totalEnergyDifference += (mutableRules.get(i).getWeight() + regularizationParameter) * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
            } else {
                totalEnergyDifference += mutableRules.get(i).getWeight() * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
                totalEnergyDifference += regularizationParameter * (augmentedInferenceSquaredIncompatibility[i] - mapSquaredIncompatibility[i]);
            }
        }

        GroundAtom[] atoms = trainInferenceApplication.getDatabase().getAtomStore().getAtoms();
        float augmentedInferenceLCQPRegularization = 0.0f;
        float fullInferenceLCQPRegularization = 0.0f;
        for (int i = 0; i < trainInferenceApplication.getDatabase().getAtomStore().size(); i++) {
            if (atoms[i].isFixed()) {
                continue;
            }

            augmentedInferenceLCQPRegularization += regularizationParameter * Math.pow(augmentedInferenceAtomValueState[i], 2.0f);
            fullInferenceLCQPRegularization += regularizationParameter * Math.pow(trainMAPAtomValueState[i], 2.0f);
        }
        totalEnergyDifference += augmentedInferenceLCQPRegularization - fullInferenceLCQPRegularization;

        return totalEnergyDifference;
    }

    private float computeTotalProxValue(float[] proxRuleIncompatibility) {
        float totalProxValue = 0.0f;
        for (int i = 0; i < proxRules.length; i++) {
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

        float[] boxClippedProxRuleObservedAtomValueGradient = proxRuleObservedAtomValueGradient.clone();
        clipProxRuleObservedAtomValueGradient(boxClippedProxRuleObservedAtomValueGradient);
        float boxClippedProxRuleObservedAtomValueGradientNorm = MathUtils.pNorm(boxClippedProxRuleObservedAtomValueGradient, stoppingGradientNorm);

        gradientNorm += boxClippedProxRuleObservedAtomValueGradientNorm;

        return gradientNorm;
    }

    /**
     * A method for computing the squared incompatibilities of the rules with atoms values in their current state.
     */
    protected void computeCurrentSquaredIncompatibility(float[] incompatibilityArray) {
        // Zero out the incompatibility first.
        Arrays.fill(incompatibilityArray, 0.0f);

        float[] atomValues = trainInferenceApplication.getDatabase().getAtomStore().getAtomValues();

        // Sums up the incompatibilities.
        for (Object rawTerm : trainInferenceApplication.getTermStore()) {
            @SuppressWarnings("unchecked")
            ReasonerTerm term = (ReasonerTerm)rawTerm;

            if (!(term.getRule() instanceof WeightedRule)) {
                continue;
            }

            Integer index = ruleIndexMap.get((WeightedRule)term.getRule());

            if (index == null) {
                continue;
            }

            incompatibilityArray[index] += term.evaluateSquaredHingeLoss(atomValues);
        }
    }

    /**
     * Set RandomVariableAtoms with labels to their observed (truth) value.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void fixLabeledRandomVariables() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getDatabase().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            atomStore.getAtoms()[atomIndex] = observedAtom;
            atomStore.getAtomValues()[atomIndex] = observedAtom.getValue();
            latentInferenceAtomValueState[atomIndex] = observedAtom.getValue();
            randomVariableAtom.setValue(observedAtom.getValue());
        }

        inTrainingMAPState = false;
    }

    /**
     * Set RandomVariableAtoms with labels to their unobserved state.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void unfixLabeledRandomVariables() {
        AtomStore atomStore = trainInferenceApplication.getDatabase().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            atomStore.getAtoms()[atomIndex] = randomVariableAtom;
        }

        inTrainingMAPState = false;
    }
}
