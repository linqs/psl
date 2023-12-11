package org.linqs.psl.application.learning.weight.gradient.policygradient;

import org.linqs.psl.application.learning.weight.gradient.GradientDescent;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.deep.DeepModelPredicate;
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
        REINFORCE,
        REINFORCE_BASELINE,
    }

    private final DeepAtomPolicyDistribution deepAtomPolicyDistribution;
    private final PolicyUpdate policyUpdate;

    private float[] scores;
    private float scoreMovingAverage;
    private float[] sampleProbabilities;

    protected float[] initialDeepAtomValues;
    protected float[] policySampledDeepAtomValues;

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
    protected float supervisedLoss;
    protected float[] mapIncompatibility;

    public PolicyGradient(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                        Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        deepAtomPolicyDistribution = DeepAtomPolicyDistribution.valueOf(Options.POLICY_GRADIENT_POLICY_DISTRIBUTION.getString().toUpperCase());
        policyUpdate = PolicyUpdate.valueOf(Options.POLICY_GRADIENT_POLICY_UPDATE.getString().toUpperCase());

        scores = null;
        scoreMovingAverage = 0.0f;

        sampleProbabilities = null;

        initialDeepAtomValues = null;
        policySampledDeepAtomValues = null;

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
        supervisedLoss = Float.POSITIVE_INFINITY;
        mapIncompatibility = new float[mutableRules.size()];
    }

    @Override
    protected void initForLearning() {
        super.initForLearning();

        if (symbolicWeightLearning){
            throw new IllegalArgumentException("Policy Gradient does not support symbolic weight learning.");
        }

        scoreMovingAverage = Float.POSITIVE_INFINITY;
    }

    protected abstract void computeSupervisedLoss();

    @Override
    protected float computeLearningLoss() {
        return  supervisedLoss + energyLossCoefficient * latentInferenceEnergy;
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

        rvLatentAtomGradient = new float[trainFullMAPAtomValueState.length];
        deepLatentAtomGradient = new float[trainFullMAPAtomValueState.length];
    }

    @Override
    protected void setBatch(int batch) {
        super.setBatch(batch);

        latentInferenceTermState = batchLatentInferenceTermStates.get(batch);
        latentInferenceAtomValueState = batchLatentInferenceAtomValueStates.get(batch);
        initialDeepAtomValues = new float[trainInferenceApplication.getTermStore().getAtomStore().size()];
        policySampledDeepAtomValues = new float[trainInferenceApplication.getTermStore().getAtomStore().size()];
        scores = new float[trainInferenceApplication.getTermStore().getAtomStore().size()];
    }


    @Override
    protected void computeIterationStatistics() {
        Arrays.fill(scores, 0.0f);

        for (DeepModelPredicate deepModelPredicate : trainDeepModelPredicates) {
            Map<String, ArrayList<RandomVariableAtom>> atomIdentiferToCategories = deepModelPredicate.getAtomIdentiferToCategories();
            for (Map.Entry<String, ArrayList<RandomVariableAtom>> entry : atomIdentiferToCategories.entrySet()) {
                ArrayList<RandomVariableAtom> categories = entry.getValue();

                sampleDeepAtomValues(categories);

                computeMAPInferenceStatistics();
                computeSupervisedLoss();
                computeLatentInferenceStatistics();

                float score = computeScore();
                for (RandomVariableAtom category : categories) {
                    int atomIndex = trainInferenceApplication.getTermStore().getAtomStore().getAtomIndex(category);
                    scores[atomIndex] = score;
                }

                resetDeepAtomValues(categories);
            }
        }
        updateScoreMovingAverage();

        computeMAPInferenceStatistics();
        computeSupervisedLoss();
        computeLatentInferenceStatistics();
    }

    /**
     * Sample the deep atom values according to a policy parameterized by the deep model predictions.
     */
    protected void sampleDeepAtomValues(ArrayList<RandomVariableAtom> categories) {
        // Save the initial deep model predictions to reset the deep atom values after computing iteration statistics.
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        Arrays.fill(initialDeepAtomValues, 0.0f);
        Arrays.fill(policySampledDeepAtomValues, 0.0f);

        for (RandomVariableAtom category : categories) {
            int atomIndex = atomStore.getAtomIndex(category);
            initialDeepAtomValues[atomIndex] = atomStore.getAtomValues()[atomIndex];
        }

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

        sampleProbabilities = new float[atomStore.size()];

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
                sampleProbabilities[atomIndex] = categoryProbabilities[i];
                categories.get(i).setValue(1.0f);
            }

            policySampledDeepAtomValues[atomIndex] = categories.get(i).getValue();
            atomValues[atomIndex] = categories.get(i).getValue();
        }
    }

    private void resetDeepAtomValues(ArrayList<RandomVariableAtom> categories) {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        for (RandomVariableAtom category : categories) {
            int atomIndex = atomStore.getAtomIndex(category);
            category.setValue(initialDeepAtomValues[atomIndex]);
            atomValues[atomIndex] = initialDeepAtomValues[atomIndex];
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
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), MAPRVAtomEnergyGradient, MAPDeepAtomEnergyGradient);
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

    @Override
    protected void addLearningLossWeightGradient() {
        throw new UnsupportedOperationException("Policy Gradient does not support learning symbolic weights.");
    }

    @Override
    protected void computeTotalAtomGradient() {
        Arrays.fill(rvAtomGradient, 0.0f);
        Arrays.fill(deepAtomGradient, 0.0f);

        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        for (int i = 0; i < atomStore.size(); i++) {
            GroundAtom atom = atomStore.getAtom(i);

            if (atom instanceof ObservedAtom) {
                continue;
            }

            switch (deepAtomPolicyDistribution) {
                case CATEGORICAL:
                    computeCategoricalAtomGradient(i);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown policy distribution: " + deepAtomPolicyDistribution);
            }
        }
    }

    private void computeCategoricalAtomGradient(int atomIndex) {
        if (policySampledDeepAtomValues[atomIndex] == 0.0f) {
            deepAtomGradient[atomIndex] = 0.0f;
            return;
        }

        switch (policyUpdate) {
            case REINFORCE:
                deepAtomGradient[atomIndex] += scores[atomIndex] / sampleProbabilities[atomIndex];
                break;
            case REINFORCE_BASELINE:
                deepAtomGradient[atomIndex] += (scores[atomIndex] - scoreMovingAverage) / sampleProbabilities[atomIndex];
                break;
            default:
                throw new IllegalArgumentException("Unknown policy update: " + policyUpdate);
        }
    }

    private float computeScore() {
        return energyLossCoefficient * latentInferenceEnergy + supervisedLoss;
    }

    private void updateScoreMovingAverage() {
        float scoreAverage = 0.0f;
        int numScores = 0;
        for (DeepModelPredicate deepModelPredicate : trainDeepModelPredicates) {
            Map<String, ArrayList<RandomVariableAtom>> atomIdentiferToCategories = deepModelPredicate.getAtomIdentiferToCategories();
            for (Map.Entry<String, ArrayList<RandomVariableAtom>> entry : atomIdentiferToCategories.entrySet()) {
                ArrayList<RandomVariableAtom> categories = entry.getValue();

                for (RandomVariableAtom category : categories) {
                    int atomIndex = trainInferenceApplication.getTermStore().getAtomStore().getAtomIndex(category);
                    scoreAverage += scores[atomIndex];
                    numScores += 1;
                }
            }
        }
        scoreAverage /= numScores;

        if (!Float.isInfinite(scoreMovingAverage)) {
            scoreMovingAverage = 0.9f * scoreMovingAverage + 0.1f * scoreAverage;
        } else {
            scoreMovingAverage = scoreAverage;
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
