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
package org.linqs.psl.application.learning.weight.gradient;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermState;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Learns weights for weighted rules in a model by optimizing an objective via Gradient Descent.
 * Child classes define methods for computing the value and gradient of a loss.
 */
public abstract class GradientDescent extends WeightLearningApplication {
    private static final Logger log = Logger.getLogger(GradientDescent.class);

    /**
     * The Gradient Descent Extension to use.
     * MIRROR_DESCENT(Default): Perform mirror descent (normalized exponeniated gradient descent)
     *     on the chosen loss with unit simplex constrained weights.
     * NONE: Perform standard gradient descent with only lower bound (>=0) constraints on the weights.
     */
    public static enum GDExtension {
        MIRROR_DESCENT,
        PROJECTED_GRADIENT,
        NONE
    }

    protected GDExtension gdExtension;

    protected Map<WeightedRule, Integer> ruleIndexMap;

    protected float[] weightGradient;
    protected TermState[] trainMAPTermState;
    protected float[] trainMAPAtomValueState;

    protected TermState[] validationMAPTermState;
    protected float[] validationMAPAtomValueState;
    protected boolean runValidation;
    protected boolean saveBestValidationWeights;


    protected float baseStepSize;
    protected boolean scaleStepSize;
    protected float maxGradientMagnitude;
    protected float maxGradientNorm;
    protected float stoppingGradientNorm;
    protected boolean clipWeightGradient;

    protected int maxNumSteps;
    protected boolean runFullIterations;
    protected boolean objectiveBreak;
    protected boolean normBreak;
    protected float objectiveTolerance;
    protected float normTolerance;

    protected float l2Regularization;
    protected float logRegularization;
    protected float entropyRegularization;

    public GradientDescent(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                           Database validationTargetDatabase, Database validationTruthDatabase) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase);

        gdExtension = GDExtension.valueOf(Options.WLA_GRADIENT_DESCENT_EXTENSION.getString().toUpperCase());

        ruleIndexMap = new HashMap<WeightedRule, Integer>(mutableRules.size());
        for (int i = 0; i < mutableRules.size(); i++) {
            ruleIndexMap.put(mutableRules.get(i), i);
        }

        weightGradient = new float[mutableRules.size()];

        trainMAPTermState = null;
        trainMAPAtomValueState = null;

        validationMAPTermState = null;
        validationMAPAtomValueState = null;
        runValidation = Options.WLA_GRADIENT_DESCENT_RUN_VALIDATION.getBoolean();
        saveBestValidationWeights = Options.WLA_GRADIENT_DESCENT_SAVE_BEST_VALIDATION_WEIGHTS.getBoolean();

        if (!((!runValidation) || (evaluation != null))) {
            throw new IllegalArgumentException("If validation is being run, then an evaluator must be specified for predicates.");
        }

        if (!((!saveBestValidationWeights) || runValidation)) {
            throw new IllegalArgumentException("If saveBestValidationWeights is true, then runValidation must also be true.");
        }

        baseStepSize = Options.WLA_GRADIENT_DESCENT_STEP_SIZE.getFloat();
        scaleStepSize = Options.WLA_GRADIENT_DESCENT_SCALE_STEP.getBoolean();
        clipWeightGradient = Options.WLA_GRADIENT_DESCENT_CLIP_GRADIENT.getBoolean();
        maxGradientMagnitude = Options.WLA_GRADIENT_DESCENT_MAX_GRADIENT.getFloat();
        maxGradientNorm = Options.WLA_GRADIENT_DESCENT_MAX_GRADIENT_NORM.getFloat();

        maxNumSteps = Options.WLA_GRADIENT_DESCENT_NUM_STEPS.getInt();
        runFullIterations = Options.WLA_GRADIENT_DESCENT_RUN_FULL_ITERATIONS.getBoolean();
        objectiveBreak = Options.WLA_GRADIENT_DESCENT_OBJECTIVE_BREAK.getBoolean();
        normBreak = Options.WLA_GRADIENT_DESCENT_NORM_BREAK.getBoolean();
        objectiveTolerance = Options.WLA_GRADIENT_DESCENT_OBJECTIVE_TOLERANCE.getFloat();
        normTolerance = Options.WLA_GRADIENT_DESCENT_NORM_TOLERANCE.getFloat();
        stoppingGradientNorm = Options.WLA_GRADIENT_DESCENT_STOPPING_GRADIENT_NORM.getFloat();

        l2Regularization = Options.WLA_GRADIENT_DESCENT_L2_REGULARIZATION.getFloat();
        logRegularization = Options.WLA_GRADIENT_DESCENT_LOG_REGULARIZATION.getFloat();
        entropyRegularization = Options.WLA_GRADIENT_DESCENT_ENTROPY_REGULARIZATION.getFloat();
    }

    @Override
    protected void postInitGroundModel() {
        super.postInitGroundModel();

        if (!((!runValidation) || (validationInferenceApplication.getDatabase().getAtomStore().size() > 0))) {
            throw new IllegalStateException("If validation is being run, then validation data must be provided in the runtime.json file.");
        }

        // Set the initial value of atoms to be the current atom value.
        // This ensures that when the inference application is reset before computing the MAP state
        // the atom values that were fixed to their warm start or true labels are preserved.
        trainInferenceApplication.setInitialValue(InitialValue.ATOM);
        validationInferenceApplication.setInitialValue(InitialValue.ATOM);

        // Initialize MPE state objects for warm starts.
        trainMAPTermState = trainInferenceApplication.getTermStore().saveState();
        validationMAPTermState = validationInferenceApplication.getTermStore().saveState();

        float[] trainAtomValues = trainInferenceApplication.getDatabase().getAtomStore().getAtomValues();
        trainMAPAtomValueState = Arrays.copyOf(trainAtomValues, trainAtomValues.length);

        float[] validationAtomValues = validationInferenceApplication.getDatabase().getAtomStore().getAtomValues();
        validationMAPAtomValueState = Arrays.copyOf(validationAtomValues, validationAtomValues.length);
    }

    protected void initForLearning() {
        switch (gdExtension) {
            case MIRROR_DESCENT:
            case PROJECTED_GRADIENT:
                // Initialize weights to be centered on the unit simplex.
                simplexScaleWeights();
                inTrainingMAPState = false;
                inValidationMAPState = false;

                break;
            default:
                // Do nothing.
                break;
        }
    }

    @Override
    protected void doLearn() {
        boolean breakGD = false;
        float objective = 0.0f;
        float oldObjective = Float.POSITIVE_INFINITY;

        double currentTrainingEvaluationMetric = Double.NEGATIVE_INFINITY;
        double bestTrainingEvaluationMetric = Double.NEGATIVE_INFINITY;

        double currentValidationEvaluationMetric = Double.NEGATIVE_INFINITY;
        double bestValidationEvaluationMetric = Double.NEGATIVE_INFINITY;

        float[] bestWeights = new float[mutableRules.size()];

        log.info("Gradient Descent Weight Learning Start.");
        initForLearning();

        for (int i = 0; i < bestWeights.length; i++) {
            bestWeights[i] = mutableRules.get(i).getWeight();
        }

        long totalTime = 0;
        int iteration = 0;
        while (!breakGD) {
            long start = System.currentTimeMillis();

            log.trace("Model: {}", mutableRules);

            if (log.isTraceEnabled() && (evaluation != null)) {
                // Compute the MAP state before evaluating so variables have assigned values.
                computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);
                inTrainingMAPState = true;

                evaluation.compute(trainingMap);
                currentTrainingEvaluationMetric = evaluation.getNormalizedRepMetric();
                log.trace("MAP State Training Evaluation Metric: {}", currentTrainingEvaluationMetric);
            }

            if (runValidation) {
                computeMAPStateWithWarmStart(validationInferenceApplication, validationMAPTermState, validationMAPAtomValueState);
                inValidationMAPState = true;

                evaluation.compute(validationMap);
                currentValidationEvaluationMetric = evaluation.getNormalizedRepMetric();
                log.debug("MAP State Validation Evaluation Metric: {}", currentValidationEvaluationMetric);

                if (currentValidationEvaluationMetric > bestValidationEvaluationMetric) {
                    bestValidationEvaluationMetric = currentValidationEvaluationMetric;
                    bestTrainingEvaluationMetric = currentTrainingEvaluationMetric;

                    for (int i = 0; i < mutableRules.size(); i++) {
                        bestWeights[i] = mutableRules.get(i).getWeight();
                    }

                    log.debug("New Best Validation Model: {}", mutableRules);
                }

                log.debug("MAP State Best Validation Evaluation Metric: {}", bestValidationEvaluationMetric);
            }

            gradientStep(iteration);

            computeIterationStatistics();

            objective = computeTotalLoss();
            computeTotalWeightGradient();
            if (clipWeightGradient) {
                clipWeightGradient();
            }

            long end = System.currentTimeMillis();

            totalTime += end - start;
            oldObjective = objective;

            breakGD = breakOptimization(iteration, objective, oldObjective);

            log.trace("Iteration {} -- Weight Learning Objective: {}, Gradient Magnitude: {}, Iteration Time: {}",
                    iteration, objective, computeGradientNorm(), (end - start));

            iteration++;
        }

        log.info("Gradient Descent Weight Learning Finished.");

        if (saveBestValidationWeights) {
            // Reset rule weights to bestWeights.
            for (int i = 0; i < mutableRules.size(); i++) {
                mutableRules.get(i).setWeight(bestWeights[i]);
            }

            inTrainingMAPState = false;
            inValidationMAPState = false;
        }

        if (evaluation != null) {
            computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);
            inTrainingMAPState = true;

            evaluation.compute(trainingMap);
            log.info("Final MAP State Evaluation Metric: {}", evaluation.getNormalizedRepMetric());
        }

        if (runValidation) {
            computeMAPStateWithWarmStart(validationInferenceApplication, validationMAPTermState, validationMAPAtomValueState);
            inValidationMAPState = true;

            evaluation.compute(validationMap);
            log.info("Final MAP State Validation Evaluation Metric: {}", evaluation.getNormalizedRepMetric());
        }

        log.info("Final Model {} ", mutableRules);
        log.info("Final Weight Learning Loss: {}, Final Gradient Magnitude: {}, Total optimization time: {}",
                computeTotalLoss(), computeGradientNorm(), totalTime);
    }

    protected boolean breakOptimization(int iteration, float objective, float oldObjective) {
        if (iteration > maxNumSteps) {
            return true;
        }

        if (runFullIterations) {
            return false;
        }

        if (objectiveBreak && MathUtils.equals(objective, oldObjective, objectiveTolerance)) {
            return true;
        }

        if (normBreak) {
            return MathUtils.equals(computeGradientNorm(), 0.0f, normTolerance);
        }

        return false;
    }

    /**
     * Clip weight gradients to avoid numerical errors.
     */
    private void clipWeightGradient() {
        float gradientMagnitude = MathUtils.pNorm(weightGradient, maxGradientNorm);

        if (gradientMagnitude > maxGradientMagnitude) {
            log.trace("Clipping gradient. Original gradient magnitude: {} exceeds limit: {} in L_{} space.",
                    gradientMagnitude, maxGradientMagnitude, maxGradientNorm);
            for (int i = 0; i < mutableRules.size(); i++) {
                weightGradient[i] = maxGradientMagnitude * weightGradient[i] / gradientMagnitude;
            }
        }
    }

    /**
     * Take a step in the direction of the negative gradient.
     * This method will call the gradient step methods for each parameter group: weights and internal parameters.
     */
    protected void gradientStep(int iteration) {
        weightGradientStep(iteration);
        internalParameterGradientStep(iteration);
    }

    /**
     * Take a step in the direction of the negative gradient of the internal parameters.
     * This method is a no-op for the abstract class. Children should override this method if they have internal parameters.
     */
    protected void internalParameterGradientStep(int iteration) {
        // Do nothing.
    }

    /**
     * Take a step in the direction of the negative gradient of the weights.
     */
    protected void weightGradientStep(int iteration) {
        float stepSize = computeStepSize(iteration);

        switch (gdExtension) {
            case MIRROR_DESCENT:
                float exponentiatedGradientSum = 0.0f;
                for (int j = 0; j < mutableRules.size(); j++) {
                    exponentiatedGradientSum += mutableRules.get(j).getWeight() * Math.exp(-1.0f * stepSize * weightGradient[j]);
                }

                for (int j = 0; j < mutableRules.size(); j++) {
                    mutableRules.get(j).setWeight(
                            (float)((mutableRules.get(j).getWeight()
                                    * Math.exp(-1.0f * stepSize * weightGradient[j]))
                                    / exponentiatedGradientSum));
                }

                break;
            case PROJECTED_GRADIENT:
                for (int j = 0; j < mutableRules.size(); j++) {
                    mutableRules.get(j).setWeight(mutableRules.get(j).getWeight() - weightGradient[j] * stepSize);
                }

                // Project weights back onto the unit simplex.
                simplexProjectWeights();

                break;
            default:
                for (int j = 0; j < mutableRules.size(); j++) {
                    // Clip negative weights.
                    mutableRules.get(j).setWeight(mutableRules.get(j).getWeight() - weightGradient[j] * stepSize);
                }

                break;
        }

        inTrainingMAPState = false;
        inValidationMAPState = false;
    }

    protected float computeStepSize(int iteration) {
        float stepSize = baseStepSize;

        if (scaleStepSize) {
            stepSize /= (iteration + 1);
        }

        return stepSize;
    }

    protected float computeGradientNorm() {
        float norm = 0.0f;

        switch (gdExtension) {
            case MIRROR_DESCENT:
                norm = computeMirrorDescentNorm();
                break;
            case PROJECTED_GRADIENT:
                norm = computeProjectedGradientDescentNorm();
                break;
            default:
                norm = computeGradientDescentNorm();
                break;
        }

        return norm;
    }

    /**
     * The norm of strictly positive and simplex constrained weights is the KL-divergence between
     * the distribution of the weight gradient in the dual space and the discrete uniform distribution.
     */
    private float computeMirrorDescentNorm() {
        float norm = 0.0f;

        float exponentiatedGradientSum = 0.0f;
        for (int i = 0; i < mutableRules.size(); i ++) {
            exponentiatedGradientSum += Math.exp(weightGradient[i]);
        }

        for (int i = 0; i < mutableRules.size(); i ++) {
            float mappedWeightGradient = (float)Math.exp(weightGradient[i]) / exponentiatedGradientSum;
            norm += mappedWeightGradient * (float)Math.log(mappedWeightGradient * mutableRules.size());
        }

        return norm;
    }

    /**
     * The norm of simplex constrained weights is the KL-divergence between the distribution of the
     * non-zero and boundary clipped weight gradient in the dual space and the discrete uniform distribution.
     */
    private float computeProjectedGradientDescentNorm() {
        float norm = 0.0f;

        int numNonZeroGradients = 0;
        float[] simplexClippedGradients = weightGradient.clone();
        for (int i = 0; i < simplexClippedGradients.length; i++) {
            if ((logRegularization == 0.0f) && MathUtils.equalsStrict(mutableRules.get(i).getWeight(), 0.0f) && (weightGradient[i] > 0.0f)) {
                simplexClippedGradients[i] = 0.0f;
                continue;
            }

            if ((logRegularization == 0.0f) && MathUtils.equalsStrict(mutableRules.get(i).getWeight(), 1.0f) && (weightGradient[i] < 0.0f)) {
                simplexClippedGradients[i] = 0.0f;
                continue;
            }

            simplexClippedGradients[i] = weightGradient[i];

            if ((logRegularization == 0.0f) && MathUtils.isZero(simplexClippedGradients[i], MathUtils.STRICT_EPSILON)) {
                continue;
            }

            numNonZeroGradients++;
        }

        float exponentiatedGradientSum = 0.0f;
        for (int i = 0; i < mutableRules.size(); i ++) {
            if ((logRegularization == 0.0f) && MathUtils.isZero(simplexClippedGradients[i], MathUtils.STRICT_EPSILON)) {
                continue;
            }

            exponentiatedGradientSum += Math.exp(weightGradient[i]);
        }

        for (int i = 0; i < mutableRules.size(); i ++) {
            if ((logRegularization == 0.0f) && MathUtils.isZero(simplexClippedGradients[i], MathUtils.STRICT_EPSILON)) {
                continue;
            }

            float mappedWeightGradient = (float)Math.exp(weightGradient[i]) / exponentiatedGradientSum;
            norm += mappedWeightGradient * (float)Math.log(mappedWeightGradient * numNonZeroGradients);
        }

        return norm;
    }

    /**
     * The norm of non-negative weights is the norm of the lower boundary clipped weight gradient.
     */
    private float computeGradientDescentNorm() {
        float[] boundaryClippedGradients = weightGradient.clone();
        for (int i = 0; i < boundaryClippedGradients.length; i++) {
            if (MathUtils.equals(mutableRules.get(i).getWeight(), 0.0f) && (weightGradient[i] > 0.0f)) {
                boundaryClippedGradients[i] = 0.0f;
                continue;
            }

            boundaryClippedGradients[i] = weightGradient[i];
        }

        return MathUtils.pNorm(boundaryClippedGradients, stoppingGradientNorm);
    }

    /**
     * Project the current weights onto the unit simplex.
     * This function follows the algorithm presented in: https://optimization-online.org/2014/08/4498/
     */
    public void simplexProjectWeights() {
        int numWeights = mutableRules.size();
        float[] weights = new float[numWeights];
        for (int i = 0; i < numWeights; i++) {
            weights[i] = mutableRules.get(i).getWeight();
        }

        Arrays.sort(weights);

        float cumulativeWeightSum = 0;
        float tau = 0;
        for (int i = 1; i <= numWeights; i++) {
            float nextCumulativeWeightSum = cumulativeWeightSum + weights[numWeights - i];
            float nextTau = (nextCumulativeWeightSum - 1.0f) / i;
            if (nextTau >= weights[numWeights - i]) {
                break;
            }
            cumulativeWeightSum = nextCumulativeWeightSum;
            tau = nextTau;
        }

        for (WeightedRule mutableRule: mutableRules) {
            mutableRule.setWeight(Math.max(0, mutableRule.getWeight() - tau));
        }
    }

    /**
     * Scale the weights to the unit simplex.
     */
    private void simplexScaleWeights() {
        float totalWeight = 0.0f;
        for (WeightedRule mutableRule : mutableRules) {
            totalWeight += mutableRule.getWeight();
        }

        for (WeightedRule mutableRule : mutableRules) {
            mutableRule.setWeight(mutableRule.getWeight() / totalWeight);
        }
    }

    /**
     * Use the provided warm start for MPE inference to save time in reasoner.
     */
    protected void computeMAPStateWithWarmStart(InferenceApplication inferenceApplication, TermState[] termState, float[] atomValueState) {
        // Warm start inference with previous termState.
        inferenceApplication.getTermStore().loadState(termState);
        AtomStore atomStore = inferenceApplication.getDatabase().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();
        for (int i = 0; i < atomStore.size(); i++) {
            if (atomStore.getAtom(i) instanceof ObservedAtom) {
                continue;
            }

            atomValues[i] = atomValueState[i];
        }

        atomStore.sync();

        computeMAPState(inferenceApplication);

        // Save the MPE state for future warm starts.
        inferenceApplication.getTermStore().saveState(termState);
        float[] mpeAtomValues = inferenceApplication.getDatabase().getAtomStore().getAtomValues();
        System.arraycopy(mpeAtomValues, 0, atomValueState, 0, mpeAtomValues.length);
    }

    /**
     * A method for computing the incompatibility of rules with atoms values in their current state.
     */
    protected void computeCurrentIncompatibility(float[] incompatibilityArray) {
        // Zero out the incompatibility first.
        Arrays.fill(incompatibilityArray, 0.0f);

        float[] atomValues = trainInferenceApplication.getDatabase().getAtomStore().getAtomValues();

        // Sums up the incompatibilities.
        for (Object rawTerm : trainInferenceApplication.getTermStore()) {
            ReasonerTerm term = (ReasonerTerm)rawTerm;

            if (!(term.getRule() instanceof WeightedRule)) {
                continue;
            }

            Integer index = ruleIndexMap.get((WeightedRule)term.getRule());

            if (index == null) {
                continue;
            }

            incompatibilityArray[index] += term.evaluateIncompatibility(atomValues);
        }
    }

    /**
     * Method called at the start of every gradient descent iteration to
     * compute statistics needed for loss and gradient computations.
     */
    protected abstract void computeIterationStatistics();

    /**
     * Method for computing the total regularized loss.
     */
    protected float computeTotalLoss() {
        float learningLoss = computeLearningLoss();
        float regularization = computeRegularization();

        log.trace("Learning Loss: {}, Regularization: {}", learningLoss, regularization);

        return learningLoss + regularization;
    }

    /**
     * Compute the learning loss.
     */
    protected abstract float computeLearningLoss();

    /**
     * Compute the regularization.
     */
    protected float computeRegularization() {
        float regularization = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            WeightedRule mutableRule = mutableRules.get(i);
            float logWeight = (float)Math.max(Math.log(mutableRule.getWeight()), Math.log(MathUtils.STRICT_EPSILON));
            regularization += l2Regularization * (float)Math.pow(mutableRule.getWeight(), 2.0f)
                    - logRegularization * logWeight
                    + entropyRegularization * mutableRule.getWeight() * logWeight;
        }

        return regularization;
    }

    /**
     * Compute the gradient of the regularized learning loss with respect to the weights.
     */
    protected void computeTotalWeightGradient() {
        Arrays.fill(weightGradient, 0.0f);

        addLearningLossWeightGradient();
        addRegularizationWeightGradient();
    }

    /**
     * Add the gradient of the learning loss with respect to the weights.
     */
    protected abstract void addLearningLossWeightGradient();

    /**
     * Add the gradient of the regularization with respect to the weights.
     */
    protected void addRegularizationWeightGradient() {
        for (int i = 0; i < mutableRules.size(); i++) {
            float logWeight = (float)Math.log(Math.max(mutableRules.get(i).getWeight(), MathUtils.STRICT_EPSILON));
            weightGradient[i] += 2.0f * l2Regularization * mutableRules.get(i).getWeight()
                    - logRegularization / Math.max(mutableRules.get(i).getWeight(), MathUtils.STRICT_EPSILON)
                    + entropyRegularization * (logWeight + 1);
        }
    }
}
