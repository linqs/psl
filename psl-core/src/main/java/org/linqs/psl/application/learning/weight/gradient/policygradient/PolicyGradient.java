package org.linqs.psl.application.learning.weight.gradient.policygradient;

import org.linqs.psl.application.learning.weight.gradient.GradientDescent;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.deep.DeepModelPredicate;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.reasoner.term.TermState;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.RandUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A gradient descent-based learner that uses a policy gradient to update atoms and weights.
 */
public abstract class PolicyGradient extends GradientDescent {
    private static final Logger log = Logger.getLogger(PolicyGradient.class);

    public enum DeepAtomPolicyDistribution {
        CATEGORICAL
    }

    public enum PolicyUpdate {
        INDEPENDENT_CATEGORICAL_REINFORCE,
    }

    public enum RewardFunction {
        EVALUATION
    }

    private final PolicyUpdate policyUpdate;
    protected final RewardFunction rewardFunction;
    private float[] actionValueFunction;

    private int numSamples;
    protected int[] actionSampleCounts;

    protected float[] initialDeepAtomValues;

    protected float latentInferenceEnergy;
    protected float[] latentInferenceIncompatibility;
    protected TermState[] latentInferenceTermState;
    protected float[] latentInferenceAtomValueState;
    protected List<TermState[]> batchLatentInferenceTermStates;
    protected List<float[]> batchLatentInferenceAtomValueStates;
    protected float[] rvLatentEnergyGradient;
    protected float[] deepLatentEnergyGradient;
    protected float[] deepPolicyGradient;

    protected float energyLossCoefficient;

    protected float MAPStateEvaluation;

    protected float mapEnergy;
    protected float[] mapIncompatibility;

    public PolicyGradient(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                        Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        policyUpdate = PolicyUpdate.valueOf(Options.POLICY_GRADIENT_POLICY_UPDATE.getString().toUpperCase());
        rewardFunction = RewardFunction.valueOf(Options.POLICY_GRADIENT_REWARD_FUNCTION.getString().toUpperCase());
        actionValueFunction = null;

        numSamples = Options.POLICY_GRADIENT_NUM_SAMPLES.getInt();
        actionSampleCounts = null;
        initialDeepAtomValues = null;

        latentInferenceEnergy = Float.POSITIVE_INFINITY;
        latentInferenceIncompatibility = new float[mutableRules.size()];
        latentInferenceTermState = null;
        latentInferenceAtomValueState = null;
        batchLatentInferenceTermStates = new ArrayList<TermState[]>();
        batchLatentInferenceAtomValueStates = new ArrayList<float[]>();
        rvLatentEnergyGradient = null;
        deepLatentEnergyGradient = null;
        deepPolicyGradient = null;

        energyLossCoefficient = Options.POLICY_GRADIENT_ENERGY_LOSS_COEFFICIENT.getFloat();

        MAPStateEvaluation = Float.NEGATIVE_INFINITY;

        mapEnergy = Float.POSITIVE_INFINITY;
        mapIncompatibility = new float[mutableRules.size()];
    }

    @Override
    protected void initForLearning() {
        super.initForLearning();

        if (symbolicWeightLearning){
            throw new IllegalArgumentException("Policy Gradient does not currently support symbolic weight learning.");
        }
    }

    protected abstract float computeReward();

    protected abstract float computeReward(Set<GroundAtom> truthSubset);

    @Override
    protected float computeLearningLoss() {
        return  (float) ((evaluation.getNormalizedMaxRepMetric() - MAPStateEvaluation) + energyLossCoefficient * latentInferenceEnergy);
    }

    @Override
    protected void initializeBatchWarmStarts() {
        super.initializeBatchWarmStarts();

        for (int i = 0; i < batchGenerator.numBatchTermStores(); i++) {
            SimpleTermStore<? extends ReasonerTerm> batchTermStore = batchGenerator.getBatchTermStore(i);
            batchLatentInferenceTermStates.add(batchTermStore.saveState());
            batchLatentInferenceAtomValueStates.add(Arrays.copyOf(batchTermStore.getAtomStore().getAtomValues(), batchTermStore.getAtomStore().getAtomValues().length));
        }
    }

    @Override
    protected void initializeGradients() {
        super.initializeGradients();

        rvLatentEnergyGradient = new float[trainFullMAPAtomValueState.length];
        deepLatentEnergyGradient = new float[trainFullMAPAtomValueState.length];
        deepPolicyGradient = new float[trainFullMAPAtomValueState.length];
        actionValueFunction = new float[trainFullMAPAtomValueState.length];
        actionSampleCounts = new int[trainFullMAPAtomValueState.length];
    }

    @Override
    protected void resetGradients() {
        super.resetGradients();

        Arrays.fill(rvLatentEnergyGradient, 0.0f);
        Arrays.fill(deepLatentEnergyGradient, 0.0f);
        Arrays.fill(deepPolicyGradient, 0.0f);
        Arrays.fill(actionValueFunction, 0.0f);
        Arrays.fill(actionSampleCounts, 0);
    }

    @Override
    protected void setBatch(int batch) {
        super.setBatch(batch);

        latentInferenceTermState = batchLatentInferenceTermStates.get(batch);
        latentInferenceAtomValueState = batchLatentInferenceAtomValueStates.get(batch);
        initialDeepAtomValues = new float[trainInferenceApplication.getTermStore().getAtomStore().size()];
    }


    @Override
    protected void computeIterationStatistics() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        computeMAPInferenceStatistics();

        MAPStateEvaluation = computeReward();

        computeLatentInferenceStatistics();

        // Save the initial deep model predictions to reset the deep atom values after computing iteration statistics
        // and to compute action probabilities.
        System.arraycopy(atomStore.getAtomValues(), 0, initialDeepAtomValues, 0, atomStore.size());

        computeValueFunctionEstimates();
        computePolicyGradient();
    }

    private void computePolicyGradient() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        for (int atomIndex = 0; atomIndex < atomStore.size(); atomIndex++) {
            GroundAtom atom = atomStore.getAtom(atomIndex);

            // Skip atoms that are not DeepAtoms.
            if (!((atom instanceof RandomVariableAtom) && (atom.getPredicate() instanceof DeepPredicate))) {
                continue;
            }

            switch (policyUpdate) {
                case INDEPENDENT_CATEGORICAL_REINFORCE:
                    deepPolicyGradient[atomIndex] = -1.0f * actionValueFunction[atomIndex];
                    break;
                default:
                    throw new IllegalArgumentException("Unknown policy update: " + policyUpdate);
            }
        }

        clipPolicyGradient();
    }

    /**
     * Clip policy gradient to stabilize learning.
     */
    private void clipPolicyGradient() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        float gradientMagnitude = MathUtils.pNorm(deepPolicyGradient, maxGradientNorm);

        if (gradientMagnitude > maxGradientMagnitude) {
            for (int atomIndex = 0; atomIndex < atomStore.size(); atomIndex++) {
                deepPolicyGradient[atomIndex] = maxGradientMagnitude * deepPolicyGradient[atomIndex] / gradientMagnitude;
            }
        }
    }

    private void computeValueFunctionEstimates() {
        switch (policyUpdate) {
            case INDEPENDENT_CATEGORICAL_REINFORCE:
                computeINDEPENDENTCATEGORICALREINFORCEActionValueEstimates();
                break;
            default:
                throw new IllegalArgumentException("Unknown policy update: " + policyUpdate);
        }
    }

    /**
     * Compute the action value estimates using the independent categorical REINFORCE algorithm.
     * This algorithm computes the action value estimates for each categorical action independently.
     */
    private void computeINDEPENDENTCATEGORICALREINFORCEActionValueEstimates() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        for (DeepModelPredicate deepModelPredicate : trainDeepModelPredicates) {
            Map<String, ArrayList<RandomVariableAtom>> atomIdentiferToCategories = deepModelPredicate.getAtomIdentiferToCategories();
            for (Map.Entry<String, ArrayList<RandomVariableAtom>> entry : atomIdentiferToCategories.entrySet()) {
                for (RandomVariableAtom fixedCategory : entry.getValue()) {
                    int fixedCategoryIndex = atomStore.getAtomIndex(fixedCategory);

                    fixConnectedComponent(fixedCategory);

                    for (int i = 0; i < numSamples; i++) {
                        sampleAllDeepAtomValues();

                        // Override sample with fixed category assignment.
                        for (RandomVariableAtom category : entry.getValue()) {
                            int atomIndex = atomStore.getAtomIndex(category);

                            if (atomIndex != fixedCategoryIndex) {
                                category.setValue(0.0f);
                            } else {
                                category.setValue(1.0f);
                            }

                            atomValues[atomIndex] = category.getValue();
                        }

                        computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);

                        float reward = computeReward(buildComponentTruthSubset(fixedCategory));
                        addCategoryActionValue(fixedCategoryIndex, fixedCategory, reward);

                        resetAllDeepAtomValues();
                    }

                    reactivateConnectedComponents();
                }
            }
        }

        for (int atomIndex = 0; atomIndex < atomStore.size(); atomIndex++) {
            actionValueFunction[atomIndex] /= numSamples;
        }
    }

    private Set<GroundAtom> buildComponentTruthSubset(RandomVariableAtom atom) {
        Set<GroundAtom> componentTruthSubset = new HashSet<GroundAtom>();

        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        List<Integer> componentAtomIndexes = atomStore.getConnectedComponentAtomIndexes(atomStore.findAtomRoot(atom));
        for (int atomIndex : componentAtomIndexes) {
            componentTruthSubset.add(atomStore.getAtom(atomIndex));
        }

        return componentTruthSubset;
    }

    /**
     * Fix the connected component of the given atom, i.e., deactivate all terms outside the connected component.
     */
    private void fixConnectedComponent(RandomVariableAtom atom) {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        int componentID = atomStore.findAtomRoot(atom);

        // Deactivate all terms outside the fixed categories component.
        List<Integer> allComponentIDs = ((SimpleTermStore<? extends ReasonerTerm>) trainInferenceApplication.getTermStore()).getConnectedComponentKeys();
        for (Integer componentID2 : allComponentIDs) {
            if (componentID2 == componentID) {
                continue;
            }

            List<? extends ReasonerTerm> component = ((SimpleTermStore<? extends ReasonerTerm>) trainInferenceApplication.getTermStore()).getConnectedComponent(componentID2);
            for (ReasonerTerm term : component) {
                term.setActive(false);
            }
        }
    }

    /**
     * Reactivate all connected components.
     */
    private void reactivateConnectedComponents() {
        List<Integer> allComponentIDs = ((SimpleTermStore<? extends ReasonerTerm>) trainInferenceApplication.getTermStore()).getConnectedComponentKeys();
        for (Integer componentID : allComponentIDs) {
            List<? extends ReasonerTerm> component = ((SimpleTermStore<? extends ReasonerTerm>) trainInferenceApplication.getTermStore()).getConnectedComponent(componentID);
            for (ReasonerTerm term : component) {
                term.setActive(true);
            }
        }
    }

    /**
     * Sample all deep atom values according to a policy parameterized by the deep model predictions.
     */
    protected void sampleAllDeepAtomValues() {
        for (DeepModelPredicate deepModelPredicate : trainDeepModelPredicates) {
            Map<String, ArrayList<RandomVariableAtom>> atomIdentiferToCategories = deepModelPredicate.getAtomIdentiferToCategories();
            for (Map.Entry<String, ArrayList<RandomVariableAtom>> entry : atomIdentiferToCategories.entrySet()) {
                sampleDeepAtomValues(entry.getValue());
            }
        }
        inTrainingMAPState = false;
    }

    /**
     * Sample the deep atom values according to a policy parameterized by the deep model predictions.
     */
    protected void sampleDeepAtomValues(ArrayList<RandomVariableAtom> categories) {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        // Sample the deep model predictions according to the stochastic categorical policy.
        float[] categoryProbabilities = new float[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            categoryProbabilities[i] = categories.get(i).getValue();
        }

        int sampledCategoryIndex = RandUtils.sampleCategorical(categoryProbabilities);

        for (int i = 0; i < categories.size(); i++) {
            int atomIndex = atomStore.getAtomIndex(categories.get(i));

            if (i != sampledCategoryIndex) {
                categories.get(i).setValue(0.0f);
            } else {
                categories.get(i).setValue(1.0f);
                actionSampleCounts[atomIndex]++;
            }

            atomValues[atomIndex] = categories.get(i).getValue();
        }
    }

    /**
     * Reset all deep atom values to their initial values.
     */
    private void resetAllDeepAtomValues() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        for (int i = 0; i < atomStore.size(); i++) {
            GroundAtom atom = atomStore.getAtom(i);

            // Skip atoms that are not DeepAtoms.
            if (!((atom instanceof RandomVariableAtom) && (atom.getPredicate() instanceof DeepPredicate))) {
                continue;
            }

            ((RandomVariableAtom) atom).setValue(initialDeepAtomValues[i]);
            atomValues[i] = initialDeepAtomValues[i];
        }
    }

    /**
     * Compute the incompatibility of the mpe state and the gradient of the energy function at the mpe state.
     */
    private void computeMAPInferenceStatistics() {
        log.trace("Running MAP Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);
        inTrainingMAPState = true;

        mapEnergy = trainInferenceApplication.getReasoner().parallelComputeObjective(trainInferenceApplication.getTermStore()).objective;
        computeCurrentIncompatibility(mapIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), MAPRVEnergyGradient, MAPDeepEnergyGradient);
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
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), rvLatentEnergyGradient, deepLatentEnergyGradient);

        unfixLabeledRandomVariables();
    }

    @Override
    protected void addLearningLossWeightGradient() {
        throw new UnsupportedOperationException("Policy Gradient does not support learning symbolic weights.");
    }

    @Override
    protected void addTotalAtomGradient() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        for (int atomIndex = 0; atomIndex < atomStore.size(); atomIndex++) {
            rvGradient[atomIndex] = energyLossCoefficient * rvLatentEnergyGradient[atomIndex];
            deepGradient[atomIndex] = energyLossCoefficient * deepLatentEnergyGradient[atomIndex] + deepPolicyGradient[atomIndex];
        }
    }

    private void addCategoryActionValue(int atomIndex, RandomVariableAtom atom, float reward) {
        // Skip atoms not selected by the policy.
        if (MathUtils.isZero(atom.getValue())) {
            return;
        }

        actionValueFunction[atomIndex] += reward;
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
