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
import org.linqs.psl.util.RandUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A gradient descent-based learner that uses a policy gradient to update atoms and weights.
 */
public abstract class PolicyGradient extends GradientDescent {
    private static final Logger log = Logger.getLogger(PolicyGradient.class);

    public enum DeepAtomPolicyDistribution {
        CATEGORICAL
    }

    public enum PolicyUpdate {
        REINFORCE
    }

    private final DeepAtomPolicyDistribution deepAtomPolicyDistribution;
    private final PolicyUpdate policyUpdate;

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
    protected float[] deepSupervisedLossGradient;

    protected float energyLossCoefficient;

    protected float MAPStateSupervisedLoss;

    protected float mapEnergy;
    protected float[] mapIncompatibility;

    public PolicyGradient(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                        Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        deepAtomPolicyDistribution = DeepAtomPolicyDistribution.valueOf(Options.POLICY_GRADIENT_POLICY_DISTRIBUTION.getString().toUpperCase());
        policyUpdate = PolicyUpdate.valueOf(Options.POLICY_GRADIENT_POLICY_UPDATE.getString().toUpperCase());

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
        deepSupervisedLossGradient = null;

        energyLossCoefficient = Options.MINIMIZER_ENERGY_LOSS_COEFFICIENT.getFloat();

        MAPStateSupervisedLoss = Float.POSITIVE_INFINITY;

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

    protected abstract float computeSupervisedLoss();

    @Override
    protected float computeLearningLoss() {
        return  MAPStateSupervisedLoss + energyLossCoefficient * latentInferenceEnergy;
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
        deepSupervisedLossGradient = new float[trainFullMAPAtomValueState.length];

        actionSampleCounts = new int[trainFullMAPAtomValueState.length];
    }

    @Override
    protected void resetGradients() {
        super.resetGradients();

        Arrays.fill(rvLatentEnergyGradient, 0.0f);
        Arrays.fill(deepLatentEnergyGradient, 0.0f);
        Arrays.fill(deepSupervisedLossGradient, 0.0f);

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

        MAPStateSupervisedLoss = computeSupervisedLoss();

        computeLatentInferenceStatistics();

        // Save the initial deep model predictions to reset the deep atom values after computing iteration statistics
        // and to compute action probabilities.
        System.arraycopy(atomStore.getAtomValues(), 0, initialDeepAtomValues, 0, atomStore.size());

        switch (policyUpdate) {
            case REINFORCE:
                addREINFORCESupervisedLossGradient();
                break;
            default:
                throw new IllegalArgumentException("Unknown policy update: " + policyUpdate);
        }
    }

    private void addREINFORCESupervisedLossGradient() {
        for (int i = 0; i < numSamples; i++) {
            sampleAllDeepAtomValues();

            computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);

            addSupervisedLossDeepGradient(computeSupervisedLoss());

            resetAllDeepAtomValues();
        }

        for (int i = 0; i < deepSupervisedLossGradient.length; i++) {
            if (actionSampleCounts[i] == 0) {
                deepSupervisedLossGradient[i] = 0.0f;
                continue;
            }

            log.trace("Atom: {} Deep Supervised Loss Gradient: {}",
                    trainInferenceApplication.getTermStore().getAtomStore().getAtom(i).toStringWithValue(), deepSupervisedLossGradient[i]);
            deepSupervisedLossGradient[i] /= actionSampleCounts[i];
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
        switch (deepAtomPolicyDistribution) {
            case CATEGORICAL:
                sampleCategorical(categories);
                break;
            default:
                throw new IllegalArgumentException("Unknown policy distribution: " + deepAtomPolicyDistribution);
        }
    }

    private void sampleCategorical(ArrayList<RandomVariableAtom> categories) {
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
        for (int i = 0; i < rvGradient.length; i++) {
            rvGradient[i] = energyLossCoefficient * rvLatentEnergyGradient[i];
            deepGradient[i] = energyLossCoefficient * deepLatentEnergyGradient[i] + deepSupervisedLossGradient[i];
        }
    }

    private void addSupervisedLossDeepGradient(float supervisedLoss) {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        for (int atomIndex = 0; atomIndex < atomStore.size(); atomIndex++) {
            GroundAtom atom = atomStore.getAtom(atomIndex);

            // Skip atoms that are not DeepAtoms.
            if (!((atom instanceof RandomVariableAtom) && (atom.getPredicate() instanceof DeepPredicate))) {
                continue;
            }

            switch (deepAtomPolicyDistribution) {
                case CATEGORICAL:
                    addCategoricalPolicySupervisedLossGradient(atomIndex, (RandomVariableAtom) atom, supervisedLoss);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown policy distribution: " + deepAtomPolicyDistribution);
            }
        }
    }

    private void addCategoricalPolicySupervisedLossGradient(int atomIndex, RandomVariableAtom atom, float score) {
        // Skip atoms not selected by the policy.
        if (atom.getValue() == 0.0f) {
            return;
        }

        switch (policyUpdate) {
            case REINFORCE:
                // The initialDeepAtomValues are the action probabilities.
                deepSupervisedLossGradient[atomIndex] += score / initialDeepAtomValues[atomIndex];
                break;
            default:
                throw new IllegalArgumentException("Unknown policy update: " + policyUpdate);
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
