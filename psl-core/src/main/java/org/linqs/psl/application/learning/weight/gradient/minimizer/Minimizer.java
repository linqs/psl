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
import org.linqs.psl.reasoner.term.SimpleTermStore;
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

    protected float latentInferenceEnergy;
    protected float[] latentInferenceIncompatibility;
    protected TermState[] latentInferenceTermState;
    protected float[] latentInferenceAtomValueState;
    protected List<TermState[]> batchLatentInferenceTermStates;
    protected List<float[]> batchLatentInferenceAtomValueStates;
    protected float[] rvLatentAtomGradient;
    protected float[] deepLatentAtomGradient;
    protected float energyLossCoefficient;

    protected float mapEnergy;
    protected float[] mapIncompatibility;
    protected float[] mapSquaredIncompatibility;

    protected float augmentedInferenceEnergy;
    protected float[] augmentedInferenceIncompatibility;
    protected float[] augmentedInferenceSquaredIncompatibility;

    protected TermState[] augmentedInferenceTermState;
    protected float[] augmentedInferenceAtomValueState;
    protected List<TermState[]> batchAugmentedInferenceTermStates;
    protected List<float[]> batchAugmentedInferenceAtomValueStates;


    protected float[] augmentedRVAtomEnergyGradient;
    protected float[] augmentedDeepAtomEnergyGradient;

    protected final float proxRuleWeight;
    protected WeightedArithmeticRule[] proxRules;
    protected List<WeightedArithmeticRule[]> batchProxRules;

    protected List<Integer> rvAtomIndexToProxRuleIndex;
    protected List<Integer> proxRuleIndexToRVAtomIndex;
    protected List<List<Integer>> batchRVAtomIndexToProxRuleIndexes;
    protected List<List<Integer>> batchProxRuleIndexToRVAtomIndexes;

    protected UnmanagedObservedAtom[] proxRuleObservedAtoms;
    protected int[] proxRuleObservedAtomIndexes;
    protected List<UnmanagedObservedAtom[]> batchProxRuleObservedAtoms;
    protected List<int[]> batchProxRuleObservedAtomIndexes;

    protected final float proxRuleObservedAtomValueStepSize;
    protected float proxRuleObservedAtomsValueEpochMovement;
    protected float[] proxRuleObservedAtomValueGradient;
    protected List<float[]> batchProxRuleObservedAtomValueGradients;

    protected float constraintRelaxationConstant;
    protected float parameterMovementTolerance;
    protected float finalParameterMovementTolerance;
    protected float constraintTolerance;
    protected float finalConstraintTolerance;

    protected int outerIteration;
    protected int augmentedLagrangianIteration;

    protected final float initialSquaredPenaltyCoefficient;
    protected float squaredPenaltyCoefficient;
    protected float squaredPenaltyCoefficientIncreaseRate;
    protected final float initialLinearPenaltyCoefficient;
    protected float linearPenaltyCoefficient;
    protected List<Float> batchLinearPenaltyCoefficients;

    public Minimizer(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                     Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        latentInferenceEnergy = Float.POSITIVE_INFINITY;
        latentInferenceIncompatibility = new float[mutableRules.size()];
        latentInferenceTermState = null;
        latentInferenceAtomValueState = null;
        batchLatentInferenceTermStates = new ArrayList<TermState[]>();
        batchLatentInferenceAtomValueStates = new ArrayList<float[]>();
        rvLatentAtomGradient = null;
        deepLatentAtomGradient = null;
        energyLossCoefficient = Options.MINIMIZER_ENERGY_LOSS_COEFFICIENT.getFloat();

        mapEnergy = Float.POSITIVE_INFINITY;
        mapIncompatibility = new float[mutableRules.size()];
        mapSquaredIncompatibility = new float[mutableRules.size()];

        augmentedInferenceEnergy = Float.POSITIVE_INFINITY;
        augmentedInferenceIncompatibility = new float[mutableRules.size()];
        augmentedInferenceSquaredIncompatibility = new float[mutableRules.size()];
        augmentedInferenceTermState = null;
        augmentedInferenceAtomValueState = null;
        batchAugmentedInferenceTermStates = new ArrayList<TermState[]>();
        batchAugmentedInferenceAtomValueStates = new ArrayList<float[]>();
        augmentedRVAtomEnergyGradient = null;
        augmentedDeepAtomEnergyGradient = null;

        proxRuleWeight = Options.MINIMIZER_PROX_RULE_WEIGHT.getFloat();
        proxRules = null;
        batchProxRules = new ArrayList<WeightedArithmeticRule[]>();

        rvAtomIndexToProxRuleIndex = new ArrayList<Integer>();
        proxRuleIndexToRVAtomIndex = new ArrayList<Integer>();
        batchRVAtomIndexToProxRuleIndexes = new ArrayList<List<Integer>>();
        batchProxRuleIndexToRVAtomIndexes = new ArrayList<List<Integer>>();

        proxRuleObservedAtoms = null;
        proxRuleObservedAtomIndexes = null;
        batchProxRuleObservedAtoms = new ArrayList<UnmanagedObservedAtom[]>();
        batchProxRuleObservedAtomIndexes = new ArrayList<int[]>();

        proxRuleObservedAtomValueStepSize = Options.MINIMIZER_PROX_VALUE_STEP_SIZE.getFloat();
        proxRuleObservedAtomsValueEpochMovement = Float.POSITIVE_INFINITY;
        proxRuleObservedAtomValueGradient = null;
        batchProxRuleObservedAtomValueGradients = new ArrayList<float[]>();

        initialSquaredPenaltyCoefficient = Options.MINIMIZER_INITIAL_SQUARED_PENALTY.getFloat();
        squaredPenaltyCoefficient = initialSquaredPenaltyCoefficient;
        squaredPenaltyCoefficientIncreaseRate = Options.MINIMIZER_SQUARED_PENALTY_INCREASE_RATE.getFloat();
        initialLinearPenaltyCoefficient = Options.MINIMIZER_INITIAL_LINEAR_PENALTY.getFloat();
        linearPenaltyCoefficient = initialLinearPenaltyCoefficient;
        batchLinearPenaltyCoefficients = new ArrayList<Float>();

        outerIteration = 1;
        augmentedLagrangianIteration = 1;
        constraintRelaxationConstant = Float.NEGATIVE_INFINITY;
        parameterMovementTolerance = 1.0f / initialSquaredPenaltyCoefficient;
        finalParameterMovementTolerance = Options.MINIMIZER_FINAL_PARAMETER_MOVEMENT_CONVERGENCE_TOLERANCE.getFloat();
        constraintTolerance = (float)(1.0f / Math.pow(initialSquaredPenaltyCoefficient, 0.1f));
        finalConstraintTolerance = Options.MINIMIZER_OBJECTIVE_DIFFERENCE_TOLERANCE.getFloat();
    }

    @Override
    protected void initializeInternalParameters() {
        super.initializeInternalParameters();

        for (int batch = 0; batch < batchGenerator.getNumBatches(); batch++) {
            batchLinearPenaltyCoefficients.add(initialLinearPenaltyCoefficient);

            batchRVAtomIndexToProxRuleIndexes.add(new ArrayList<Integer>());
            batchProxRuleIndexToRVAtomIndexes.add(new ArrayList<Integer>());

            SimpleTermStore<? extends ReasonerTerm> batchTermStore = batchGenerator.getBatchTermStore(batch);
            AtomStore atomStore = batchTermStore.getAtomStore();

            // Create and add the augmented inference proximity terms.
            int unFixedAtomCount = 0;
            for (GroundAtom atom : atomStore) {
                if (atom.isFixed()) {
                    continue;
                }

                unFixedAtomCount++;
            }

            batchProxRules.add(new WeightedArithmeticRule[unFixedAtomCount]);
            batchProxRuleObservedAtoms.add(new UnmanagedObservedAtom[unFixedAtomCount]);
            batchProxRuleObservedAtomIndexes.add(new int[unFixedAtomCount]);
            batchProxRuleObservedAtomValueGradients.add(new float[unFixedAtomCount]);

            initializeProxRules(batchGenerator.getBatchTermStore(batch), batchRVAtomIndexToProxRuleIndexes.get(batch),
                    batchProxRuleIndexToRVAtomIndexes.get(batch), batchProxRules.get(batch),
                    batchProxRuleObservedAtoms.get(batch), batchProxRuleObservedAtomIndexes.get(batch));
        }
    }

    private void initializeProxRules(SimpleTermStore<? extends ReasonerTerm> termStore,
                                     List<Integer> rvAtomIndexToProxIndex, List<Integer> proxIndexToRVAtomIndex,
                                     WeightedArithmeticRule[] proxRules, UnmanagedObservedAtom[] proxRuleObservedAtoms,
                                     int[] proxRuleObservedAtomIndexes) {
        AtomStore atomStore = termStore.getAtomStore();

        // Create and add the proximity terms to the term store but do not merge the constants.
        // This is because we want to be able to update the constants without having to re-ground the rules.
        // If another process is creating terms then this procedure can cause terms to be created without merged constants.
        boolean originalMergeConstants = termStore.getTermGenerator().getMergeConstants();
        termStore.getTermGenerator().setMergeConstants(false);

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

            termStore.add(new WeightedGroundArithmeticRule(
                    proxRules[proxRuleIndex], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.LTE, 0.0f));
            termStore.add(new WeightedGroundArithmeticRule(
                    proxRules[proxRuleIndex], Arrays.asList(1.0f, -1.0f),
                    Arrays.asList(atom, proxRuleObservedAtoms[proxRuleIndex]),
                    FunctionComparator.GTE, 0.0f));

            proxRuleIndex++;
        }

        termStore.getTermGenerator().setMergeConstants(originalMergeConstants);
    }

    @Override
    protected void initializeBatchWarmStarts() {
        super.initializeBatchWarmStarts();

        for (int i = 0; i < batchGenerator.getNumBatches(); i++) {
            SimpleTermStore<? extends ReasonerTerm> batchTermStore = batchGenerator.getBatchTermStore(i);
            batchLatentInferenceTermStates.add(batchTermStore.saveState());
            batchLatentInferenceAtomValueStates.add(Arrays.copyOf(batchTermStore.getAtomStore().getAtomValues(), batchTermStore.getAtomStore().getAtomValues().length));

            batchAugmentedInferenceTermStates.add(batchTermStore.saveState());
            batchAugmentedInferenceAtomValueStates.add(Arrays.copyOf(batchTermStore.getAtomStore().getAtomValues(), batchTermStore.getAtomStore().getAtomValues().length));
        }
    }

    @Override
    protected void initializeGradients() {
        super.initializeGradients();

        rvLatentAtomGradient = new float[trainFullMAPAtomValueState.length];
        deepLatentAtomGradient = new float[trainFullMAPAtomValueState.length];

        augmentedRVAtomEnergyGradient = new float[trainFullMAPAtomValueState.length];
        augmentedDeepAtomEnergyGradient = new float[trainFullMAPAtomValueState.length];
    }

    @Override
    protected void setBatch(int batch) {
        super.setBatch(batch);

        latentInferenceTermState = batchLatentInferenceTermStates.get(batch);
        latentInferenceAtomValueState = batchLatentInferenceAtomValueStates.get(batch);

        augmentedInferenceTermState = batchAugmentedInferenceTermStates.get(batch);
        augmentedInferenceAtomValueState = batchAugmentedInferenceAtomValueStates.get(batch);

        proxRules = batchProxRules.get(batch);
        rvAtomIndexToProxRuleIndex = batchRVAtomIndexToProxRuleIndexes.get(batch);
        proxRuleIndexToRVAtomIndex = batchProxRuleIndexToRVAtomIndexes.get(batch);
        proxRuleObservedAtoms = batchProxRuleObservedAtoms.get(batch);
        proxRuleObservedAtomIndexes = batchProxRuleObservedAtomIndexes.get(batch);
        proxRuleObservedAtomValueGradient = batchProxRuleObservedAtomValueGradients.get(batch);

        linearPenaltyCoefficient = batchLinearPenaltyCoefficients.get(batch);
    }

    @Override
    protected boolean breakOptimization(int epoch) {
        if (epoch >= maxNumSteps) {
            log.trace("Breaking Weight Learning. Reached maximum number of epochs: {}", maxNumSteps);
            return true;
        }

        if (runFullIterations) {
            return false;
        }

        if (fullMAPEvaluationBreak && (epoch - lastFullMAPImprovementEpoch) > fullMAPEvaluationPatience) {
            log.trace("Breaking Weight Learning. No improvement in training evaluation metric for {} epochs.", (epoch - lastFullMAPImprovementEpoch));
            return true;
        }

        if (validationBreak && (epoch - lastValidationImprovementEpoch) > validationPatience) {
            log.trace("Breaking Weight Learning. No improvement in validation evaluation metric for {} epochs.", (epoch - lastValidationImprovementEpoch));
            return true;
        }

        float totalEnergyDifference = computeTotalEnergyDifference();
        if (totalEnergyDifference < finalConstraintTolerance) {
            log.trace("Breaking Weight Learning. Objective difference {} is less than final constraint tolerance {}.",
                    totalEnergyDifference, finalConstraintTolerance);
            return true;
        }

        return false;
    }

    @Override
    protected void epochStart(int epoch) {
        super.epochStart(epoch);

        if (epoch == 0) {
            constraintRelaxationConstant = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < batchGenerator.getNumBatches(); i++) {
                setBatch(i);

                initializeProximityRuleConstants();

                computeFullInferenceStatistics();
                computeAugmentedInferenceStatistics();

                if (constraintRelaxationConstant < augmentedInferenceEnergy - mapEnergy) {
                    constraintRelaxationConstant = augmentedInferenceEnergy - mapEnergy;
                }
            }
        }

        proxRuleObservedAtomsValueEpochMovement = 0.0f;
    }

    @Override
    protected void measureEpochParameterMovement() {
        super.measureEpochParameterMovement();

        parameterMovement += proxRuleObservedAtomsValueEpochMovement;

        log.trace("Epoch Internal Parameter Movement: {}", proxRuleObservedAtomsValueEpochMovement);
    }

    @Override
    protected void epochEnd(int epoch) {
        super.epochEnd(epoch);

        if (parameterMovement < parameterMovementTolerance) {
            augmentedLagrangianIteration++;

            // Compute the total objective difference summed over all the batches.
            float totalConstraintViolation = computeTotalEnergyDifferenceConstraintViolation();

            if (totalConstraintViolation < constraintTolerance) {
                if ((totalConstraintViolation < finalConstraintTolerance)
                        && (parameterMovement < finalParameterMovementTolerance)) {
                    log.trace("Augmented Lagrangian Learning has converged. Outer Iteration: {}. Constraint Relaxation Constant: {}, Total Constraint Violation: {}, Parameter Movement: {}, Squared Penalty Coefficient: {}, Constraint Tolerance: {}, parameterMovementTolerance: {}",
                            outerIteration, constraintRelaxationConstant, totalConstraintViolation, parameterMovement, squaredPenaltyCoefficient, constraintTolerance, parameterMovementTolerance);

                    // Augmented Lagrangian Learning has converged.
                    constraintRelaxationConstant = 0.5f * constraintRelaxationConstant;
                    constraintTolerance = (float) (1.0f / Math.pow(squaredPenaltyCoefficient, 0.1f));
                    parameterMovementTolerance = 1.0f / squaredPenaltyCoefficient;
                    augmentedLagrangianIteration = 1;

                    outerIteration++;

                    return;
                }

                for (int batch = 0; batch < batchGenerator.getNumBatches(); batch++) {
                    setBatch(batch);

                    // We need to recompute the iteration statistics for each batch because the parameters may have changed.
                    computeIterationStatistics();

                    batchLinearPenaltyCoefficients.set(batch,
                            batchLinearPenaltyCoefficients.get(batch)
                                    + 2 * squaredPenaltyCoefficient * Math.max(0.0f, augmentedInferenceEnergy - mapEnergy - constraintRelaxationConstant));
                }
                constraintTolerance = (float) (constraintTolerance / Math.pow(squaredPenaltyCoefficient, 0.9f));
                parameterMovementTolerance = parameterMovementTolerance / squaredPenaltyCoefficient;
            } else {
                squaredPenaltyCoefficient = squaredPenaltyCoefficientIncreaseRate * squaredPenaltyCoefficient;
                constraintTolerance = (float) (1.0f / Math.pow(squaredPenaltyCoefficient, 0.1f));
                parameterMovementTolerance = 1.0f / squaredPenaltyCoefficient;
            }

            log.trace("Augmented Lagrangian iteration: {}, Total Constraint Violation: {}, Parameter Movement: {}, Squared Penalty Coefficient: {}, Constraint Tolerance: {}, parameterMovementTolerance: {}.",
                    augmentedLagrangianIteration, totalConstraintViolation, parameterMovement, squaredPenaltyCoefficient, constraintTolerance, parameterMovementTolerance);
        }
    }

    @Override
    protected void internalParameterGradientStep(int epoch) {
        // Take a step in the direction of the negative gradient of the proximity rule constants and project back onto box constraints.
        float[] atomValues = trainInferenceApplication.getTermStore().getAtomStore().getAtomValues();
        for (int i = 0; i < proxRules.length; i++) {
            float newProxRuleObservedAtomsValue = Math.min(Math.max(
                    proxRuleObservedAtoms[i].getValue() - proxRuleObservedAtomValueStepSize * proxRuleObservedAtomValueGradient[i], 0.0f), 1.0f);
            proxRuleObservedAtomsValueEpochMovement += Math.pow(proxRuleObservedAtoms[i].getValue() - newProxRuleObservedAtomsValue, 2.0f);

            proxRuleObservedAtoms[i]._assumeValue(newProxRuleObservedAtomsValue);
            atomValues[proxRuleObservedAtomIndexes[i]] = newProxRuleObservedAtomsValue;
            augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[i]] = newProxRuleObservedAtomsValue;
        }
    }

    protected void initializeProximityRuleConstants() {
        // Initialize the proximity rule constants to the truth if it exists or the latent MAP state.
        fixLabeledRandomVariables();

        log.trace("Running Latent Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, latentInferenceTermState, latentInferenceAtomValueState);
        inTrainingMAPState = true;

        unfixLabeledRandomVariables();

        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        System.arraycopy(latentInferenceAtomValueState, 0, augmentedInferenceAtomValueState, 0, latentInferenceAtomValueState.length);

        for (int i = 0; i < proxRules.length; i++) {
            proxRuleObservedAtoms[i]._assumeValue(latentInferenceAtomValueState[proxRuleIndexToRVAtomIndex.get(i)]);
            atomValues[proxRuleObservedAtomIndexes[i]] = latentInferenceAtomValueState[proxRuleIndexToRVAtomIndex.get(i)];
            augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[i]] = latentInferenceAtomValueState[proxRuleIndexToRVAtomIndex.get(i)];
        }

        // Overwrite the latent MAP state value with the truth if it exists.
        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int rvAtomIndex = atomStore.getAtomIndex(randomVariableAtom);
            if (rvAtomIndex == -1) {
                // This atom is not in the current batch.
                continue;
            }

            int proxRuleIndex = rvAtomIndexToProxRuleIndex.get(rvAtomIndex);

            proxRuleObservedAtoms[proxRuleIndex]._assumeValue(observedAtom.getValue());
            atomValues[proxRuleObservedAtomIndexes[proxRuleIndex]] = observedAtom.getValue();
            augmentedInferenceAtomValueState[proxRuleObservedAtomIndexes[proxRuleIndex]] = observedAtom.getValue();
        }
    }

    @Override
    protected void computeIterationStatistics() {
        computeFullInferenceStatistics();

        computeLatentInferenceStatistics();

        computeAugmentedInferenceStatistics();

        computeProxRuleObservedAtomValueGradient();
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

        mapEnergy = trainInferenceApplication.getReasoner().parallelComputeObjective(trainInferenceApplication.getTermStore()).objective;
        computeCurrentIncompatibility(mapIncompatibility);
        computeCurrentSquaredIncompatibility(mapSquaredIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), MAPRVAtomEnergyGradient, MAPDeepAtomEnergyGradient);
    }

    /**
     * Compute the augmented inference problem solution incompatibility and the gradient of the energy function at the augmented inference mpe state.
     */
    protected void computeAugmentedInferenceStatistics() {
        activateAugmentedInferenceProxTerms();

        log.trace("Running Augmented Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, augmentedInferenceTermState, augmentedInferenceAtomValueState);
        inTrainingMAPState = true;

        augmentedInferenceEnergy = trainInferenceApplication.getReasoner().parallelComputeObjective(trainInferenceApplication.getTermStore()).objective;
        computeCurrentIncompatibility(augmentedInferenceIncompatibility);
        computeCurrentSquaredIncompatibility(augmentedInferenceSquaredIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), augmentedRVAtomEnergyGradient, augmentedDeepAtomEnergyGradient);

        deactivateAugmentedInferenceProxTerms();
    }

    /**
     * Compute the latent inference problem solution incompatibility.
     * RandomVariableAtoms with labels are fixed to their observed (truth) value.
     */
    protected void computeLatentInferenceStatistics() {
        fixLabeledRandomVariables();

        log.trace("Running Latent Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, latentInferenceTermState, latentInferenceAtomValueState);
        inTrainingMAPState = true;

        latentInferenceEnergy = trainInferenceApplication.getReasoner().parallelComputeObjective(trainInferenceApplication.getTermStore()).objective;
        computeCurrentIncompatibility(latentInferenceIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), rvLatentAtomGradient, deepLatentAtomGradient);

        unfixLabeledRandomVariables();
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
        float objectiveDifference = augmentedInferenceEnergy - mapEnergy;
        float constraintViolation = Math.max(0.0f, objectiveDifference - constraintRelaxationConstant);
        float supervisedLoss = computeSupervisedLoss();
        float totalProxValue = computeTotalProxValue(new float[proxRuleObservedAtoms.length]);

        log.trace("Prox Loss: {}, Objective difference: {}, Constraint Violation: {}, Supervised Loss: {}, Energy Loss: {}.",
                totalProxValue, objectiveDifference, constraintViolation, supervisedLoss, latentInferenceEnergy);

        return (squaredPenaltyCoefficient / 2.0f) * (float)Math.pow(constraintViolation, 2.0f)
                + linearPenaltyCoefficient * (constraintViolation)
                + supervisedLoss
                + energyLossCoefficient * latentInferenceEnergy;
    }

    /**
     * Compute the total objective difference measured across each batch.
     */
    private float computeTotalEnergyDifference() {
        float totalObjectiveDifference = 0.0f;
        for (int batch = 0; batch < batchGenerator.getNumBatches(); batch++) {
            setBatch(batch);

            // We need to recompute the iteration statistics for each batch because the parameters may have changed.
            computeIterationStatistics();

            totalObjectiveDifference += augmentedInferenceEnergy - mapEnergy;
        }

        return totalObjectiveDifference;
    }

    /**
     * Compute the total violation of the energy difference constraint measured across each batch.
     */
    private float computeTotalEnergyDifferenceConstraintViolation() {
        float totalObjectiveDifference = 0.0f;
        for (int batch = 0; batch < batchGenerator.getNumBatches(); batch++) {
            setBatch(batch);

            // We need to recompute the iteration statistics for each batch because the parameters may have changed.
            computeIterationStatistics();

            totalObjectiveDifference += Math.max(0.0f, augmentedInferenceEnergy - mapEnergy - constraintRelaxationConstant);
        }

        return totalObjectiveDifference;
    }

    protected abstract float computeSupervisedLoss();

    protected void addAugmentedLagrangianProxRuleConstantsGradient() {
        if (augmentedInferenceEnergy - mapEnergy <= constraintRelaxationConstant) {
            // Energy difference constraint is not violated.
            return;
        }

        float[] proxRuleIncompatibility = new float[proxRuleObservedAtoms.length];
        computeTotalProxValue(proxRuleIncompatibility);

        float[] proxRuleObservedAtomValueMoreauGradient = new float[proxRuleObservedAtoms.length];
        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            proxRuleObservedAtomValueMoreauGradient[i] = 2.0f * proxRuleWeight * proxRuleIncompatibility[i];
        }

        for (int i = 0; i < proxRuleObservedAtoms.length; i++) {
            proxRuleObservedAtomValueGradient[i] += linearPenaltyCoefficient * proxRuleObservedAtomValueMoreauGradient[i];
            proxRuleObservedAtomValueGradient[i] += squaredPenaltyCoefficient
                    * (augmentedInferenceEnergy - mapEnergy - constraintRelaxationConstant)
                    * proxRuleObservedAtomValueMoreauGradient[i];
        }
    }

    protected abstract void addSupervisedProxRuleObservedAtomValueGradient();

    @Override
    protected void addLearningLossWeightGradient() {
        // Energy loss gradient.
        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += energyLossCoefficient * latentInferenceIncompatibility[i];
        }

        // Energy difference constraint gradient.
        if (augmentedInferenceEnergy - mapEnergy <= constraintRelaxationConstant) {
            // Constraint is not violated.
            return;
        }

        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += linearPenaltyCoefficient * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
            weightGradient[i] += squaredPenaltyCoefficient * (augmentedInferenceEnergy - mapEnergy - constraintRelaxationConstant)
                    * (augmentedInferenceIncompatibility[i] - mapIncompatibility[i]);
        }
    }

    @Override
    protected void computeTotalAtomGradient() {
        Arrays.fill(rvAtomGradient, 0.0f);
        Arrays.fill(deepAtomGradient, 0.0f);

        // Energy Loss Gradient.
        for (int i = 0; i < trainInferenceApplication.getTermStore().getAtomStore().size(); i++) {
            GroundAtom atom = trainInferenceApplication.getTermStore().getAtomStore().getAtom(i);

            if (atom instanceof ObservedAtom) {
                continue;
            }

            deepAtomGradient[i] += energyLossCoefficient * deepLatentAtomGradient[i];
        }

        // Energy difference constraint gradient.
        if (augmentedInferenceEnergy - mapEnergy <= constraintRelaxationConstant) {
            // Constraint is not violated.
            return;
        }

        float constraintViolation = augmentedInferenceEnergy - mapEnergy - constraintRelaxationConstant;

        for (int i = 0; i < trainInferenceApplication.getTermStore().getAtomStore().size(); i++) {
            GroundAtom atom = trainInferenceApplication.getTermStore().getAtomStore().getAtom(i);

            if (atom instanceof ObservedAtom) {
                continue;
            }

            float rvEnergyGradientDifference = augmentedRVAtomEnergyGradient[i] - MAPRVAtomEnergyGradient[i];
            float deepAtomEnergyGradientDifference = augmentedDeepAtomEnergyGradient[i] - MAPDeepAtomEnergyGradient[i];

            rvAtomGradient[i] += squaredPenaltyCoefficient * constraintViolation * rvEnergyGradientDifference
                    + linearPenaltyCoefficient * rvEnergyGradientDifference;
            deepAtomGradient[i] += squaredPenaltyCoefficient * constraintViolation * deepAtomEnergyGradientDifference
                    + linearPenaltyCoefficient * deepAtomEnergyGradientDifference;
        }
    }

    private float computeTotalProxValue(float[] proxRuleIncompatibility) {
        float regularizationParameter = 0.0f;
        if (trainInferenceApplication.getReasoner() instanceof DualBCDReasoner) {
            regularizationParameter = (float) DualBCDReasoner.regularizationParameter;
        }

        float totalProxValue = 0.0f;
        for (int i = 0; i < proxRules.length; i++) {
            proxRuleIncompatibility[i] = proxRuleObservedAtoms[i].getValue() - augmentedInferenceAtomValueState[proxRuleIndexToRVAtomIndex.get(i)];
            totalProxValue += Math.pow(proxRuleIncompatibility[i], 2.0f);
        }
        totalProxValue = (proxRuleWeight + regularizationParameter) * totalProxValue;

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

        float[] atomValues = trainInferenceApplication.getTermStore().getAtomStore().getAtomValues();

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
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            if (atomIndex == -1) {
                // This atom is not in the current batch.
                continue;
            }

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
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            if (atomIndex == -1) {
                // This atom is not in the current batch.
                continue;
            }

            atomStore.getAtoms()[atomIndex] = randomVariableAtom;
        }

        inTrainingMAPState = false;
    }
}
