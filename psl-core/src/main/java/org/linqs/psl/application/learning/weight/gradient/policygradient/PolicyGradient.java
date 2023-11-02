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
import java.util.List;
import java.util.Map;

/**
 * A gradient descent-based learner that uses a policy gradient to update atoms and weights.
 */
public abstract class PolicyGradient extends GradientDescent {
    private static final Logger log = Logger.getLogger(PolicyGradient.class);

    public enum PolicyDistribution {
        CATEGORICAL,
        GUMBEL_SOFTMAX
    }

    private final PolicyDistribution policyDistribution;

    private float lossMovingAverage;
    private float[] sampleProbabilities;

    private final float gumbelSoftmaxTemperature;

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

        policyDistribution = PolicyDistribution.valueOf(Options.POLICY_GRADIENT_POLICY_DISTRIBUTION.getString().toUpperCase());
        lossMovingAverage = 0.0f;

        sampleProbabilities = null;

        gumbelSoftmaxTemperature = Options.POLICY_GRADIENT_GUMBEL_SOFTMAX_TEMPERATURE.getFloat();

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

        lossMovingAverage = 0.0f;
    }

    protected abstract void computeSupervisedLoss();

    @Override
    protected float computeLearningLoss() {
        log.trace("Supervised Loss: {}, Energy Loss: {}.", supervisedLoss, latentInferenceEnergy);

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
    }


    @Override
    protected void computeIterationStatistics() {
        sampleDeepAtomValues();
        // TODO(Charles): Sample symbolic weights.

        computeMAPInferenceStatistics();
        computeSupervisedLoss();
        computeLatentInferenceStatistics();

        // TODO(Charles): Reset symbolic weights.
        resetDeepAtomValues();
    }

    /**
     * Sample the deep atom values according to a policy parameterized by the deep model predictions.
     */
    protected void sampleDeepAtomValues() {
        // Save the initial deep model predictions to reset the deep atom values after computing iteration statistics.
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        for (int i = 0; i < atomStore.size(); i++) {
            GroundAtom atom = atomStore.getAtoms()[i];
            if (atom.getPredicate() instanceof DeepPredicate) {
                initialDeepAtomValues[i] = atom.getValue();
            } else {
                initialDeepAtomValues[i] = 0.0f;
                policySampledDeepAtomValues[i] = 0.0f;
            }
        }

        switch (policyDistribution) {
            case CATEGORICAL:
                sampleCategorical();
                break;
            case GUMBEL_SOFTMAX:
                sampleGumbelSoftmax();
                break;
            default:
                throw new IllegalArgumentException("Unknown policy distribution: " + policyDistribution);
        }
    }

    private void sampleCategorical() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        sampleProbabilities = new float[atomStore.size()];

        // Sample the deep model predictions according to the stochastic categorical policy.
        for (DeepModelPredicate deepModelPredicate : trainDeepModelPredicates) {
            Map <String, ArrayList<RandomVariableAtom>> atomIdentiferToCategories = deepModelPredicate.getAtomIdentiferToCategories();
            for (Map.Entry<String, ArrayList<RandomVariableAtom>> entry : atomIdentiferToCategories.entrySet()) {
                ArrayList<RandomVariableAtom> categories = entry.getValue();

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
//                        log.trace("Sampled category: {}.", categories.get(i).toStringWithValue());
                    }

                    policySampledDeepAtomValues[atomIndex] = categories.get(i).getValue();
                    atomValues[atomIndex] = categories.get(i).getValue();
                }
            }
        }
    }

    private void sampleGumbelSoftmax() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        // Sample the deep model predictions according to the stochastic gumbel softmax policy.
        for (DeepModelPredicate deepModelPredicate : trainDeepModelPredicates) {

            Map <String, ArrayList<RandomVariableAtom>> atomIdentiferToCategories = deepModelPredicate.getAtomIdentiferToCategories();
            for (Map.Entry<String, ArrayList<RandomVariableAtom>> entry : atomIdentiferToCategories.entrySet()) {
                String atomIdentifier = entry.getKey();
                ArrayList<RandomVariableAtom> categories = entry.getValue();

                float[] gumbelSoftmaxSample = new float[categories.size()];
                float categoryProbabilitySum = 0.0f;
                for (int i = 0; i < categories.size(); i++) {
                    float gumbelSample = RandUtils.nextGumbel();

                    gumbelSoftmaxSample[i] = (float) Math.exp((Math.log(Math.max(categories.get(i).getValue(), MathUtils.STRICT_EPSILON)) + gumbelSample)
                            / gumbelSoftmaxTemperature);
                    categoryProbabilitySum += gumbelSoftmaxSample[i];
                }

                // Renormalize the probabilities and set the deep atom values.
                for (int i = 0; i < categories.size(); i++) {
                    gumbelSoftmaxSample[i] = gumbelSoftmaxSample[i] / categoryProbabilitySum;

                    RandomVariableAtom category = categories.get(i);
                    int atomIndex = atomStore.getAtomIndex(category);

                    category.setValue(gumbelSoftmaxSample[i]);
                    policySampledDeepAtomValues[atomIndex] = gumbelSoftmaxSample[i];
                    atomValues[atomIndex] = gumbelSoftmaxSample[i];
                }
            }
        }
    }

    private void resetDeepAtomValues() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();

        for (int i = 0; i < atomStore.size(); i++) {
            GroundAtom atom = atomStore.getAtoms()[i];
            if (atom.getPredicate() instanceof DeepPredicate) {
                ((RandomVariableAtom) atom).setValue(initialDeepAtomValues[i]);
                atomValues[i] = initialDeepAtomValues[i];
            }
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
        // TODO(Charles): Energy Loss Policy Gradient.
        // Energy loss gradient.
        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += energyLossCoefficient * latentInferenceIncompatibility[i];
        }

        // TODO(Charles): Supervised loss gradient.
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

            switch (policyDistribution) {
                case CATEGORICAL:
                    computeCategoricalAtomGradient(atom, i);
                    break;
                case GUMBEL_SOFTMAX:
                    computeGumbelSoftmaxAtomGradient(atom, i);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown policy distribution: " + policyDistribution);
            }
        }
    }

    private void computeCategoricalAtomGradient(GroundAtom atom, int atomIndex) {
        if (policySampledDeepAtomValues[atomIndex] == 0.0f) {
            deepAtomGradient[atomIndex] = 0.0f;
            return;
        }

        float score = energyLossCoefficient * latentInferenceEnergy + supervisedLoss;
        lossMovingAverage = 0.9f * lossMovingAverage + 0.1f * score;

        deepAtomGradient[atomIndex] += (score - lossMovingAverage) / sampleProbabilities[atomIndex];

//        log.trace("Atom: {}, Score: {}, Gradient: {}.", atom.toStringWithValue(), score, deepAtomGradient[atomIndex]);
    }

    private void computeGumbelSoftmaxAtomGradient(GroundAtom atom, int atomIndex) {
        // TODO(Charles): Compute the energy and supervised policy gradient for the gumbel softmax policy.
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
