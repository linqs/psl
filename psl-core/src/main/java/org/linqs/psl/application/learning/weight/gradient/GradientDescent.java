/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.gradient.batchgenerator.BatchGenerator;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.deep.DeepModelPredicate;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.reasoner.term.TermState;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public static enum SymbolicWeightUpdate {
        MIRROR_DESCENT,
        PROJECTED_GRADIENT,
        GRADIENT_DESCENT
    }

    protected boolean symbolicWeightLearning;
    protected SymbolicWeightUpdate symbolicWeightUpdate;

    protected Map<WeightedRule, Integer> symbolicWeightedRuleIndexMap;
    protected List<WeightedRule> groundedDeepWeightedRules;
    protected Map<WeightedRule, Integer> groundedDeepWeightedRuleIndexMap;

    protected float[] symbolicWeightGradient;
    protected float[] accumulatedDeepWeightGradient;
    protected float[] deepWeightGradient;
    protected float[] expressionRVAtomGradient;
    protected float[] expressionDeepAtomGradient;
    protected float[] deepAtomGradient;
    protected float[] expressionRVAtomMAPEnergyGradient;
    protected float[] expressionDeepAtomMAPEnergyGradient;
    protected float[] epochStartWeights;
    protected float epochDeepAtomValueMovement;

    protected int trainingEvaluationComputePeriod;
    protected SimpleTermStore<? extends ReasonerTerm> trainFullTermStore;
    protected TrainingMap fullTrainingMap;
    protected List<DeepModelPredicate> trainFullDeepModelPredicates;
    protected TermState[] trainFullMAPTermState;
    protected float[] trainFullMAPAtomValueState;
    protected double currentTrainingEvaluationMetric;
    protected double bestTrainingEvaluationMetric;
    protected boolean fullMAPEvaluationBreak;
    protected int fullMAPEvaluationPatience;
    protected int lastTrainingImprovementEpoch;

    protected List<DeepModelPredicate> trainDeepModelPredicates;
    protected TermState[] trainMAPTermState;
    protected float[] trainMAPAtomValueState;

    protected BatchGenerator batchGenerator;
    protected List<TermState[]> batchMAPTermStates;
    protected List<float[]> batchMAPAtomValueStates;

    protected int validationEvaluationComputePeriod;
    protected boolean validationBreak;
    protected int validationPatience;
    protected int lastValidationImprovementEpoch;
    protected TermState[] validationMAPTermState;
    protected float[] validationMAPAtomValueState;
    protected boolean saveBestValidationWeights;
    protected Weight[] bestValidationWeights;
    double currentValidationEvaluationMetric;
    double bestValidationEvaluationMetric;

    protected float baseStepSize;
    protected boolean scaleStepSize;
    protected float maxGradientMagnitude;
    protected float maxGradientNorm;
    protected float stoppingGradientNorm;
    protected boolean clipWeightGradient;

    protected int trainingStopComputePeriod;
    protected int maxNumSteps;
    protected boolean runFullIterations;
    protected boolean movementBreak;
    protected float parameterMovement;
    protected float movementTolerance;

    protected float l2Regularization;
    protected float logRegularization;
    protected float entropyRegularization;

    public GradientDescent(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                           Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        symbolicWeightLearning = Options.WLA_GRADIENT_DESCENT_SYMBOLIC_LEARNING.getBoolean();
        symbolicWeightUpdate = SymbolicWeightUpdate.valueOf(Options.WLA_GRADIENT_DESCENT_EXTENSION.getString().toUpperCase());

        symbolicWeightedRuleIndexMap = new HashMap<WeightedRule, Integer>(mutableRules.size());
        for (int i = 0; i < mutableRules.size(); i++) {
            symbolicWeightedRuleIndexMap.put(mutableRules.get(i), i);
        }

        symbolicWeightGradient = new float[mutableRules.size()];

        deepAtomGradient = null;

        accumulatedDeepWeightGradient = null;
        deepWeightGradient = null;

        expressionRVAtomGradient = null;
        expressionDeepAtomGradient = null;

        expressionRVAtomMAPEnergyGradient = null;
        expressionDeepAtomMAPEnergyGradient = null;

        trainingEvaluationComputePeriod = Options.WLA_GRADIENT_DESCENT_TRAINING_COMPUTE_PERIOD.getInt();
        trainFullTermStore = null;
        fullTrainingMap = null;
        trainFullDeepModelPredicates = null;
        trainFullMAPTermState = null;
        trainFullMAPAtomValueState = null;
        currentTrainingEvaluationMetric = Double.NEGATIVE_INFINITY;
        bestTrainingEvaluationMetric = Double.NEGATIVE_INFINITY;
        fullMAPEvaluationBreak = Options.WLA_GRADIENT_DESCENT_FULL_MAP_EVALUATION_BREAK.getBoolean();
        fullMAPEvaluationPatience = Options.WLA_GRADIENT_DESCENT_FULL_MAP_EVALUATION_PATIENCE.getInt();
        lastTrainingImprovementEpoch = 0;

        trainMAPTermState = null;
        trainMAPAtomValueState = null;

        batchGenerator = null;
        batchMAPTermStates = null;
        batchMAPAtomValueStates = null;

        epochStartWeights = null;
        epochDeepAtomValueMovement = 0.0f;

        validationMAPTermState = null;
        validationMAPAtomValueState = null;
        validationEvaluationComputePeriod = Options.WLA_GRADIENT_DESCENT_VALIDATION_COMPUTE_PERIOD.getInt();
        saveBestValidationWeights = Options.WLA_GRADIENT_DESCENT_SAVE_BEST_VALIDATION_WEIGHTS.getBoolean();
        bestValidationWeights = null;
        currentValidationEvaluationMetric = Double.NEGATIVE_INFINITY;
        bestValidationEvaluationMetric = Double.NEGATIVE_INFINITY;
        validationBreak = Options.WLA_GRADIENT_DESCENT_VALIDATION_BREAK.getBoolean();
        validationPatience = Options.WLA_GRADIENT_DESCENT_VALIDATION_PATIENCE.getInt();
        lastValidationImprovementEpoch = 0;

        if (saveBestValidationWeights && (!this.runValidation)) {
            throw new IllegalArgumentException("If saveBestValidationWeights is true, then runValidation must also be true.");
        }

        baseStepSize = Options.WLA_GRADIENT_DESCENT_STEP_SIZE.getFloat();
        scaleStepSize = Options.WLA_GRADIENT_DESCENT_SCALE_STEP.getBoolean();
        clipWeightGradient = Options.WLA_GRADIENT_DESCENT_CLIP_GRADIENT.getBoolean();
        maxGradientMagnitude = Options.WLA_GRADIENT_DESCENT_MAX_GRADIENT.getFloat();
        maxGradientNorm = Options.WLA_GRADIENT_DESCENT_MAX_GRADIENT_NORM.getFloat();

        trainingStopComputePeriod = Options.WLA_GRADIENT_DESCENT_TRAINING_STOP_COMPUTE_PERIOD.getInt();
        maxNumSteps = Options.WLA_GRADIENT_DESCENT_NUM_STEPS.getInt();
        runFullIterations = Options.WLA_GRADIENT_DESCENT_RUN_FULL_ITERATIONS.getBoolean();
        movementBreak = Options.WLA_GRADIENT_DESCENT_MOVEMENT_BREAK.getBoolean();
        parameterMovement = Float.POSITIVE_INFINITY;
        movementTolerance = Options.WLA_GRADIENT_DESCENT_MOVEMENT_TOLERANCE.getFloat();
        stoppingGradientNorm = Options.WLA_GRADIENT_DESCENT_STOPPING_GRADIENT_NORM.getFloat();

        l2Regularization = Options.WLA_GRADIENT_DESCENT_L2_REGULARIZATION.getFloat();
        logRegularization = Options.WLA_GRADIENT_DESCENT_LOG_REGULARIZATION.getFloat();
        entropyRegularization = Options.WLA_GRADIENT_DESCENT_ENTROPY_REGULARIZATION.getFloat();
    }

    @Override
    protected void postInitGroundModel() {
        super.postInitGroundModel();

        validateState();

        initializeDeepWeightedRules();
        initializeFullModels();
        initializeBatches();
        initializeEpochStats();
        initializeInternalParameters();
        initializeFullWarmStarts();
        initializeBatchWarmStarts();
        initializeGradients();
    }

    protected void validateState() {
        if (this.runValidation && (evaluation == null)) {
            throw new IllegalArgumentException("If validation is being run, then an evaluator must be specified for predicates.");
        }

        if (runValidation && (validationInferenceApplication.getTermStore().getAtomStore().size() <= 0)) {
            throw new IllegalStateException("If validation is being run, then validation data must be provided in the runtime.json file.");
        }

        assert trainInferenceApplication.getTermStore() instanceof SimpleTermStore;
    }

    protected void initializeFullModels() {
        trainFullTermStore = (SimpleTermStore<? extends ReasonerTerm>) trainInferenceApplication.getTermStore();

        fullTrainingMap = trainingMap;

        trainFullDeepModelPredicates = deepModelPredicates;

        // Set the initial value of atoms to be the current atom value.
        // This ensures that when the inference application is reset before computing the MAP state
        // the atom values that were fixed to their warm start or true labels are preserved.
        trainInferenceApplication.setInitialValue(InitialValue.ATOM);
        validationInferenceApplication.setInitialValue(InitialValue.ATOM);
    }

    protected void initializeBatches() {
        if (batchGenerator != null) {
            batchGenerator.clear();
        }

        batchGenerator = BatchGenerator.getBatchGenerator(Options.WLA_GRADIENT_DESCENT_BATCH_GENERATOR.getString(),
                trainInferenceApplication, trainFullTermStore, deepPredicates, trainTruthDatabase.getAtomStore());
        batchGenerator.generateBatches();
    }

    protected void initializeDeepWeightedRules() {
        groundedDeepWeightedRules = new ArrayList<WeightedRule>();
        groundedDeepWeightedRuleIndexMap = new HashMap<WeightedRule, Integer>();
        int groundedDeepWeightRuleCount = 0;
        for (WeightedRule rule : deepRules) {
            Set<Integer> childHashCodes = ((AbstractRule) rule).getChildHashCodes();

            Set<Atom> coreAtomsSet = new HashSet<Atom>();
            for (Integer childHashCode : childHashCodes) {
                WeightedRule groundedWeightedRule = (WeightedRule) AbstractRule.getRule(childHashCode);

                // Verify that the atom in the deep weight is not in the term expression.
                // This pattern is not supported in the current implementation.
                GroundAtom deepWeightAtom = (GroundAtom) groundedWeightedRule.getWeight().getAtom();
                groundedWeightedRule.getCoreAtoms(coreAtomsSet);
                if (coreAtomsSet.contains(deepWeightAtom)) {
                    throw new IllegalArgumentException("Grounded Deep weight atoms: " + deepWeightAtom + " cannot be in the expression of the rule: " + rule);
                }
                coreAtomsSet.clear();

                groundedDeepWeightedRules.add((WeightedRule) AbstractRule.getRule(childHashCode));
                groundedDeepWeightedRuleIndexMap.put((WeightedRule) AbstractRule.getRule(childHashCode), groundedDeepWeightRuleCount);
                groundedDeepWeightRuleCount++;
            }
        }
    }

    protected void initializeEpochStats() {
        epochStartWeights = new float[mutableRules.size()];
        epochDeepAtomValueMovement = 0.0f;
    }

    protected void initializeInternalParameters() {
        // By default, do nothing.
        // Child classes should override this method to add internal parameters.
    }

    protected void initializeFullWarmStarts() {
        // Initialize MPE state objects for warm starts.
        trainFullMAPTermState = trainInferenceApplication.getTermStore().saveState();
        validationMAPTermState = validationInferenceApplication.getTermStore().saveState();

        float[] trainAtomValues = trainInferenceApplication.getTermStore().getAtomStore().getAtomValues();
        trainFullMAPAtomValueState = Arrays.copyOf(trainAtomValues, trainAtomValues.length);

        float[] validationAtomValues = validationInferenceApplication.getTermStore().getAtomStore().getAtomValues();
        validationMAPAtomValueState = Arrays.copyOf(validationAtomValues, validationAtomValues.length);
    }

    protected void initializeBatchWarmStarts() {
        batchMAPTermStates = new ArrayList<TermState[]>();
        batchMAPAtomValueStates = new ArrayList<float[]>();
        for (SimpleTermStore<? extends ReasonerTerm> batchTermStore : batchGenerator.getBatchTermStores()) {
            batchMAPTermStates.add(batchTermStore.saveState());

            float[] batchAtomValues = batchTermStore.getAtomStore().getAtomValues();
            batchMAPAtomValueStates.add(Arrays.copyOf(batchAtomValues, batchAtomValues.length));
        }
    }

    protected void initializeGradients() {
        deepAtomGradient = new float[trainFullMAPAtomValueState.length];

        accumulatedDeepWeightGradient = new float[trainFullMAPAtomValueState.length];
        deepWeightGradient = new float[groundedDeepWeightedRules.size()];

        expressionRVAtomGradient = new float[trainFullMAPAtomValueState.length];
        expressionDeepAtomGradient = new float[trainFullMAPAtomValueState.length];

        expressionRVAtomMAPEnergyGradient = new float[trainFullMAPAtomValueState.length];
        expressionDeepAtomMAPEnergyGradient = new float[trainFullMAPAtomValueState.length];
    }

    protected void initForLearning() {
        switch (symbolicWeightUpdate) {
            case MIRROR_DESCENT:
            case PROJECTED_GRADIENT:
                // Initialize weights to be centered on the unit simplex.
                simplexScaleWeights();

                break;
            default:
                // Do nothing.
                break;
        }

        currentTrainingEvaluationMetric = Double.NEGATIVE_INFINITY;
        bestTrainingEvaluationMetric = Double.NEGATIVE_INFINITY;
        lastTrainingImprovementEpoch = 0;

        bestValidationWeights = new Weight[mutableRules.size()];
        for (int i = 0; i < mutableRules.size(); i++) {
            bestValidationWeights[i] = mutableRules.get(i).getWeight();
        }
        currentValidationEvaluationMetric = Double.NEGATIVE_INFINITY;
        bestValidationEvaluationMetric = Double.NEGATIVE_INFINITY;
        lastValidationImprovementEpoch = 0;

        trainDeepModelPredicates = trainFullDeepModelPredicates;
        trainMAPTermState = trainFullMAPTermState;
        trainMAPAtomValueState = trainFullMAPAtomValueState;
    }

    @Override
    protected void doLearn() {
        boolean breakGD = false;
        
        log.info("Gradient Descent Weight Learning Start.");
        initForLearning();

        long totalTime = 0;
        int epoch = 0;
        while ((!breakGD) && (totalTime < timeout)) {
            log.trace("Model:");
            for (WeightedRule weightedRule: mutableRules) {
                log.trace("{}", weightedRule);
            }

            long start_eval = System.currentTimeMillis();

            if ((evaluation != null) && (epoch % trainingEvaluationComputePeriod == 0)) {
                runTrainingEvaluation(epoch);
                log.info("Epoch: {}, MAP State Training Evaluation Metric: {}", epoch, currentTrainingEvaluationMetric);
            }

            if (runValidation && (epoch % validationEvaluationComputePeriod == 0)) {
                runValidationEvaluation(epoch);
                log.info("Epoch: {}, Current MAP State Validation Evaluation Metric: {}", epoch, currentValidationEvaluationMetric);
            }

            if (epoch % trainingStopComputePeriod == 0) {
                epochStart(epoch);
            }

            long end_eval = System.currentTimeMillis();

            long start_step = System.currentTimeMillis();

            DeepPredicate.trainModeAllDeepPredicates();

            int numBatches = 0;
            float averageBatchObjective = 0.0f;
            batchGenerator.permuteBatchOrdering();
            int batchId = batchGenerator.epochStart();
            while (!batchGenerator.isEpochComplete()) {
                numBatches++;

                setBatch(batchId);
                DeepPredicate.predictAllDeepPredicates();

                resetGradients();

                computeIterationStatistics();

                addTotalDeepRuleWeightGradient();
                addTotalSymbolicWeightGradient();
                addTotalExpressionAtomGradient();
                if (clipWeightGradient) {
                    clipSymbolicWeightGradient();
                }

                averageBatchObjective += computeTotalLoss();

                gradientStep(epoch);

                if (epoch % trainingStopComputePeriod == 0) {
                    epochDeepAtomValueMovement += DeepPredicate.predictAllDeepPredicates();
                }

                batchId = batchGenerator.nextBatch();
            }
            batchGenerator.epochEnd();

            if (numBatches > 0) {
                // Average the objective across batches.
                averageBatchObjective /= numBatches;
            }

            setFullModel();

            long end_step = System.currentTimeMillis();

            long start_break_check = System.currentTimeMillis();

            if (epoch % trainingStopComputePeriod == 0) {
                measureEpochParameterMovement();
                epochEnd(epoch);

                breakGD = breakOptimization(epoch);
            }

            setFullModel();

            long end_break_check = System.currentTimeMillis();

            epoch++;
            log.info("Epoch: {}, Weight Learning Objective: {}, Iteration Time: {}", epoch, averageBatchObjective, (end_step - start_step));

            totalTime += (end_eval - start_eval) + (end_step - start_step) + (end_break_check - start_break_check);
        }
        log.info("Gradient Descent Weight Learning Finished.");

        if (saveBestValidationWeights) {
            // Reset rule weights to bestWeights.
            for (int i = 0; i < mutableRules.size(); i++) {
                mutableRules.get(i).setWeight(bestValidationWeights[i]);
            }
        }

        if ((evaluation != null) && (!saveBestValidationWeights)) {
            runTrainingEvaluation(epoch);
            double finalMAPStateEvaluation = currentTrainingEvaluationMetric;
            log.info("Final MAP State Evaluation Metric: {}", finalMAPStateEvaluation);
        }

        if (runValidation) {
            double finalMAPStateValidationEvaluation = 0.0f;
            if (saveBestValidationWeights) {
                finalMAPStateValidationEvaluation = bestValidationEvaluationMetric;
            } else {
                runValidationEvaluation(epoch);
                finalMAPStateValidationEvaluation = currentValidationEvaluationMetric;
            }
            log.info("Final MAP State Validation Evaluation Metric: {}", finalMAPStateValidationEvaluation);
        }

        log.info("Final model {} ", mutableRules);
        log.info("Total weight learning time: {}", totalTime);

        for (int i = 0; i < deepPredicates.size(); i++) {
            DeepPredicate deepPredicate = deepPredicates.get(i);

            if (!saveBestValidationWeights) {
                deepPredicate.saveDeepModel();
            }

            deepPredicate.close();
            validationDeepModelPredicates.get(i).close();
        }
    }

    protected void epochStart(int epoch) {
        epochDeepAtomValueMovement = 0.0f;

        for (int i = 0; i < mutableRules.size(); i++) {
            epochStartWeights[i] = mutableRules.get(i).getWeight().getValue();
        }
    }

    protected void epochEnd(int epoch) {
        // This method is called after the epoch parameter movement is measured.
        // Child classes can override this method to add additional functionality.
    }

    protected void measureEpochParameterMovement() {
        // Measure the movement in weights and deep atom values.
        parameterMovement = 0.0f;

        float weightMovement = 0.0f;

        for (int i = 0; i < mutableRules.size(); i++) {
            weightMovement += (float)Math.pow(epochStartWeights[i] - mutableRules.get(i).getWeight().getValue(), 2.0f);
        }
        float avgWeightMovement = weightMovement / mutableRules.size();
        log.trace("Average Epoch Weight Movement: {}", avgWeightMovement);

        float avgDeepAtomValueMovement = epochDeepAtomValueMovement / batchGenerator.numBatches();
        log.trace("Average Epoch Deep Atom Value Movement: {}", avgDeepAtomValueMovement);

        // By default, there are no internal parameters.
        // Child classes should override this method to add internal parameter movement.

        parameterMovement = avgWeightMovement + avgDeepAtomValueMovement;
    }

    protected void setFullModel() {
        trainInferenceApplication.setTermStore(trainFullTermStore);
        trainDeepModelPredicates = trainFullDeepModelPredicates;
        trainingMap = fullTrainingMap;
        trainMAPTermState = trainFullMAPTermState;
        trainMAPAtomValueState = trainFullMAPAtomValueState;

        // Set the deep predicate atom store.
        // Note predict is not called here.
        for (int i = 0; i < deepPredicates.size(); i++) {
            DeepPredicate deepPredicate = deepPredicates.get(i);
            deepPredicate.setDeepModel(trainDeepModelPredicates.get(i));
            deepPredicate.setDeepModel(trainFullDeepModelPredicates.get(i));
        }
    }

    protected void resetGradients() {
        Arrays.fill(deepAtomGradient, 0.0f);
        Arrays.fill(accumulatedDeepWeightGradient, 0.0f);
        Arrays.fill(deepWeightGradient, 0.0f);
        Arrays.fill(expressionRVAtomGradient, 0.0f);
        Arrays.fill(expressionDeepAtomGradient, 0.0f);
        Arrays.fill(expressionRVAtomMAPEnergyGradient, 0.0f);
        Arrays.fill(expressionDeepAtomMAPEnergyGradient, 0.0f);
        Arrays.fill(symbolicWeightGradient, 0.0f);
    }

    protected void setBatch(int batch) {
        SimpleTermStore<? extends ReasonerTerm> batchTermStore = batchGenerator.getBatchTermStore(batch);
        trainDeepModelPredicates = batchGenerator.getBatchDeepModelPredicates(batch);

        trainInferenceApplication.setTermStore(batchTermStore);
        trainingMap = batchGenerator.getBatchTrainingMap(batch);
        trainMAPTermState = batchMAPTermStates.get(batch);
        trainMAPAtomValueState = batchMAPAtomValueStates.get(batch);

        // Set the deep predicate atom store.
        // Note predict is not called here and should be called after the batch is set.
        for (int i = 0; i < deepPredicates.size(); i++) {
            DeepPredicate deepPredicate = deepPredicates.get(i);
            deepPredicate.setDeepModel(trainDeepModelPredicates.get(i));
        }
    }

    protected void setValidationModel() {
        // Set to validation deep model predicates.
        // Note predict is not called here and should be called after the batch is set.
        for (int i = 0; i < deepPredicates.size(); i++) {
            DeepPredicate deepPredicate = deepPredicates.get(i);
            deepPredicate.setDeepModel(validationDeepModelPredicates.get(i));
        }
    }

    protected void runTrainingEvaluation(int epoch) {
        int numEvaluatedBatches = 0;
        float totalTrainingEvaluation = 0.0f;

        DeepPredicate.evalModeAllDeepPredicates();

        int batchId = batchGenerator.epochStart();
        while (!batchGenerator.isEpochComplete()) {
            if (batchGenerator.getBatchTrainingMap(batchId).getLabelMap().isEmpty()) {
                batchId = batchGenerator.nextBatch();
                continue;
            }

            setBatch(batchId);
            DeepPredicate.predictAllDeepPredicates();
            DeepPredicate.evalAllDeepPredicates();

            // Compute the MAP state before evaluating so variables have assigned values.
            log.trace("Running MAP inference for training evaluation.");
            computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);

            evaluation.compute(trainingMap);
            totalTrainingEvaluation += (float)evaluation.getNormalizedRepMetric();

            batchId = batchGenerator.nextBatch();

            numEvaluatedBatches++;
        }
        batchGenerator.epochEnd();

        currentTrainingEvaluationMetric = totalTrainingEvaluation / numEvaluatedBatches;

        if (currentTrainingEvaluationMetric > bestTrainingEvaluationMetric) {
            lastTrainingImprovementEpoch = epoch;

            bestTrainingEvaluationMetric = currentTrainingEvaluationMetric;
        }
    }

    protected void runValidationEvaluation(int epoch) {
        setValidationModel();
        DeepPredicate.evalModeAllDeepPredicates();
        DeepPredicate.predictAllDeepPredicates();
        DeepPredicate.evalAllDeepPredicates();

        log.trace("Running Validation Inference.");
        computeMAPStateWithWarmStart(validationInferenceApplication, validationMAPTermState, validationMAPAtomValueState);

        evaluation.compute(validationMap);
        currentValidationEvaluationMetric = evaluation.getNormalizedRepMetric();

        if (MathUtils.compare(currentValidationEvaluationMetric, bestValidationEvaluationMetric) >= 0) {
            lastValidationImprovementEpoch = epoch;

            bestValidationEvaluationMetric = currentValidationEvaluationMetric;

            // Save the best rule weights.
            for (int j = 0; j < mutableRules.size(); j++) {
                bestValidationWeights[j] = mutableRules.get(j).getWeight();
            }

            // Save the best deep model weights.
            for (DeepPredicate deepPredicate : deepPredicates) {
                deepPredicate.saveDeepModel();
            }
        }
        log.debug("MAP State Best Validation Evaluation Metric: {}", bestValidationEvaluationMetric);
    }

    protected boolean breakOptimization(int epoch) {
        if (epoch >= maxNumSteps) {
            log.trace("Breaking Weight Learning. Reached maximum number of epochs: {}", maxNumSteps);
            return true;
        }

        if (runFullIterations) {
            return false;
        }

        if (fullMAPEvaluationBreak && (epoch - lastTrainingImprovementEpoch) > fullMAPEvaluationPatience) {
            log.trace("Breaking Weight Learning. No improvement in training evaluation metric for {} epochs.", (epoch - lastTrainingImprovementEpoch));
            return true;
        }

        if (validationBreak && (epoch - lastValidationImprovementEpoch) > validationPatience) {
            log.trace("Breaking Weight Learning. No improvement in validation evaluation metric for {} epochs.", (epoch - lastValidationImprovementEpoch));
            return true;
        }

        if (movementBreak && MathUtils.equals(parameterMovement, 0.0f, movementTolerance)) {
            log.trace("Breaking Weight Learning. Parameter Movement: {} is within tolerance: {}", parameterMovement, movementTolerance);
            return true;
        }

        return false;
    }

    /**
     * Clip weight gradients to avoid numerical errors.
     */
    private void clipSymbolicWeightGradient() {
        float gradientMagnitude = MathUtils.pNorm(symbolicWeightGradient, maxGradientNorm);

        if (gradientMagnitude > maxGradientMagnitude) {
            log.trace("Clipping gradient. Original gradient magnitude: {} exceeds limit: {} in L_{} space.",
                    gradientMagnitude, maxGradientMagnitude, maxGradientNorm);
            for (int i = 0; i < mutableRules.size(); i++) {
                symbolicWeightGradient[i] = maxGradientMagnitude * symbolicWeightGradient[i] / gradientMagnitude;
            }
        }
    }

    /**
     * Take a step in the direction of the negative gradient.
     * This method will call the gradient step methods for each parameter group: weights and internal parameters.
     */
    protected void gradientStep(int epoch) {
        symbolicWeightGradientStep(epoch);
        internalParameterGradientStep(epoch);
        atomGradientStep();
    }

    /**
     * Take a step in the direction of the negative gradient of the internal parameters.
     * This method does nothing by default. Children should override this method if they have internal parameters.
     */
    protected void internalParameterGradientStep(int epoch) {
        // Do nothing.
    }

    /**
     * Take a step in the direction of the negative gradient of the weights.
     * Return the total change in the weights.
     */
    protected void symbolicWeightGradientStep(int epoch) {
        if (!symbolicWeightLearning) {
            return;
        }

        float stepSize = computeStepSize(epoch);

        switch (symbolicWeightUpdate) {
            case MIRROR_DESCENT:
                float exponentiatedGradientSum = 0.0f;
                for (int j = 0; j < mutableRules.size(); j++) {
                    exponentiatedGradientSum += (float)(mutableRules.get(j).getWeight().getValue() * Math.exp(-1.0f * stepSize * symbolicWeightGradient[j]));
                }

                for (int j = 0; j < mutableRules.size(); j++) {
                    Weight weight = mutableRules.get(j).getWeight();
                    weight.setConstantValue(
                            (float)((weight.getValue() * Math.exp(-1.0f * stepSize * symbolicWeightGradient[j])) / exponentiatedGradientSum)
                    );
                }

                break;
            case PROJECTED_GRADIENT:
                for (int j = 0; j < mutableRules.size(); j++) {
                    Weight weight = mutableRules.get(j).getWeight();
                    weight.setConstantValue(
                            weight.getValue() - stepSize * symbolicWeightGradient[j]
                    );
                }

                // Project weights back onto the unit simplex.
                simplexProjectWeights();

                break;
            default:
                for (int j = 0; j < mutableRules.size(); j++) {
                    // Clip negative weights.
                    Weight weight = mutableRules.get(j).getWeight();
                    weight.setConstantValue(weight.getValue() - stepSize * symbolicWeightGradient[j]);
                }

                break;
        }

        inTrainingMAPState = false;
        inValidationMAPState = false;
    }

    /**
     * Take a step in the direction of the negative gradient of the deep atoms.
     */
    protected void atomGradientStep() {
        for (WeightedRule weightedRule : groundedDeepWeightedRules) {
            int groundedRuleIndex = groundedDeepWeightedRuleIndexMap.get(weightedRule);

            GroundAtom deepWeightAtom = (GroundAtom) weightedRule.getWeight().getAtom();

            accumulatedDeepWeightGradient[deepWeightAtom.getIndex()] += deepWeightGradient[groundedRuleIndex];
        }

        for (int i = 0; i < deepAtomGradient.length; i++) {
            deepAtomGradient[i] += accumulatedDeepWeightGradient[i];
            deepAtomGradient[i] += expressionDeepAtomGradient[i];
        }

        for (DeepPredicate deepPredicate : deepPredicates) {
            deepPredicate.fitDeepPredicate(deepAtomGradient);
        }
    }

    protected float computeStepSize(int epoch) {
        float stepSize = baseStepSize;

        if (scaleStepSize) {
            stepSize /= (epoch + 1);
        }

        return stepSize;
    }

    protected float computeGradientNorm() {
        float norm = 0.0f;

        switch (symbolicWeightUpdate) {
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

        log.trace("Weight Gradient Norm: {}", norm);
        log.trace("Deep atom Gradient Norm: {}", MathUtils.pNorm(expressionDeepAtomGradient, 2));

        norm += MathUtils.pNorm(expressionDeepAtomGradient, 2);

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
            exponentiatedGradientSum += (float)Math.exp(symbolicWeightGradient[i]);
        }

        for (int i = 0; i < mutableRules.size(); i ++) {
            float mappedWeightGradient = (float)Math.exp(symbolicWeightGradient[i]) / exponentiatedGradientSum;
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
        float[] simplexClippedGradients = symbolicWeightGradient.clone();
        for (int i = 0; i < simplexClippedGradients.length; i++) {
            if ((logRegularization == 0.0f)
                    && MathUtils.equalsStrict(mutableRules.get(i).getWeight().getValue(), 0.0f)
                    && (symbolicWeightGradient[i] > 0.0f)) {
                simplexClippedGradients[i] = 0.0f;
                continue;
            }

            if ((logRegularization == 0.0f)
                    && MathUtils.equalsStrict(mutableRules.get(i).getWeight().getValue(), 1.0f)
                    && (symbolicWeightGradient[i] < 0.0f)) {
                simplexClippedGradients[i] = 0.0f;
                continue;
            }

            simplexClippedGradients[i] = symbolicWeightGradient[i];

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

            exponentiatedGradientSum += (float)Math.exp(symbolicWeightGradient[i]);
        }

        for (int i = 0; i < mutableRules.size(); i ++) {
            if ((logRegularization == 0.0f) && MathUtils.isZero(simplexClippedGradients[i], MathUtils.STRICT_EPSILON)) {
                continue;
            }

            float mappedWeightGradient = (float)Math.exp(symbolicWeightGradient[i]) / exponentiatedGradientSum;
            norm += mappedWeightGradient * (float)Math.log(mappedWeightGradient * numNonZeroGradients);
        }

        return norm;
    }

    /**
     * The norm of non-negative weights is the norm of the lower boundary clipped weight gradient.
     */
    private float computeGradientDescentNorm() {
        float[] boundaryClippedGradients = symbolicWeightGradient.clone();
        for (int i = 0; i < boundaryClippedGradients.length; i++) {
            if (MathUtils.equals(mutableRules.get(i).getWeight().getValue(), 0.0f) && (symbolicWeightGradient[i] > 0.0f)) {
                boundaryClippedGradients[i] = 0.0f;
                continue;
            }

            boundaryClippedGradients[i] = symbolicWeightGradient[i];
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
            weights[i] = mutableRules.get(i).getWeight().getValue();
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
            Weight weight = mutableRule.getWeight();
            weight.setConstantValue(Math.max(0, weight.getValue() - tau));
        }
    }

    /**
     * Scale the weights to the unit simplex.
     */
    private void simplexScaleWeights() {
        float totalWeight = 0.0f;
        for (WeightedRule mutableRule : mutableRules) {
            totalWeight += mutableRule.getWeight().getValue();
        }

        for (WeightedRule mutableRule : mutableRules) {
            Weight weight = mutableRule.getWeight();
            weight.setConstantValue(weight.getValue() / totalWeight);
        }
    }

    /**
     * Use the provided warm start for MPE inference to save time in reasoner.
     */
    protected void computeMAPStateWithWarmStart(InferenceApplication inferenceApplication,
                                                TermState[] warmStartTermState, float[] warmStartAtomValueState) {
        // Warm start inference with previous termState.
        inferenceApplication.getTermStore().loadState(warmStartTermState);
        AtomStore atomStore = inferenceApplication.getTermStore().getAtomStore();
        float[] atomValues = atomStore.getAtomValues();
        for (int i = 0; i < atomStore.size(); i++) {
            if (atomStore.getAtom(i).isFixed()) {
                continue;
            }

            atomValues[i] = warmStartAtomValueState[i];
        }

        atomStore.sync();

        computeMAPState(inferenceApplication);

        // Save the MPE state for future warm starts.
        inferenceApplication.getTermStore().saveState(warmStartTermState);
        float[] mpeAtomValues = inferenceApplication.getTermStore().getAtomStore().getAtomValues();
        System.arraycopy(mpeAtomValues, 0, warmStartAtomValueState, 0, mpeAtomValues.length);
    }

    /**
     * A method for computing the incompatibility of rules with atoms values in their current state.
     */
    protected void computeCurrentIncompatibility(float[] symbolicWeightRuleIncompatibility, float[] deepWeightRuleIncompatibility) {
        // Zero out the incompatibility first.
        Arrays.fill(symbolicWeightRuleIncompatibility, 0.0f);
        Arrays.fill(deepWeightRuleIncompatibility, 0.0f);

        float[] atomValues = trainInferenceApplication.getTermStore().getAtomStore().getAtomValues();

        // Sums up the incompatibilities.
        for (Object rawTerm : trainInferenceApplication.getTermStore()) {
            ReasonerTerm term = (ReasonerTerm)rawTerm;

            if (!(term.getRule() instanceof WeightedRule)) {
                continue;
            }

            Weight weight = ((WeightedRule)term.getRule()).getWeight();

            Integer index = null;
            if (weight.isDeep()) {
                index = groundedDeepWeightedRuleIndexMap.get((WeightedRule) term.getRule());
            } else {
                index = symbolicWeightedRuleIndexMap.get((WeightedRule) term.getRule());
            }

            if (index == null) {
                // Relaxed constraints are weighted rules that are not part of the optimization.
                continue;
            }

            float incompatibility = term.evaluateIncompatibility(atomValues);

            if (weight.isDeep()) {
                deepWeightRuleIncompatibility[index] += incompatibility;
            } else {
                symbolicWeightRuleIncompatibility[index] += incompatibility;
            }
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

        if (!symbolicWeightLearning) {
            return regularization;
        }

        for (WeightedRule mutableRule : mutableRules) {
            float logWeight = (float)Math.max(Math.log(mutableRule.getWeight().getValue()), Math.log(MathUtils.STRICT_EPSILON));
            regularization += l2Regularization * (float)Math.pow(mutableRule.getWeight().getValue(), 2.0f)
                    - logRegularization * logWeight
                    + entropyRegularization * mutableRule.getWeight().getValue() * logWeight;
        }

        return regularization;
    }

    /**
     * Compute the gradient of the total loss with respect to the deep rule weights.
     */
    protected abstract void addTotalDeepRuleWeightGradient();

    /**
     * Compute the gradient of the regularized learning loss with respect to the weights.
     */
    protected void addTotalSymbolicWeightGradient() {
        if (!symbolicWeightLearning) {
            return;
        }

        addLearningLossSymbolicWeightGradient();
        addRegularizationSymbolicWeightGradient();
    }

    /**
     * Add the gradient of the learning loss with respect to the symbolic weights.
     */
    protected abstract void addLearningLossSymbolicWeightGradient();

    /**
     * Add the gradient of the regularization with respect to the weights.
     */
    protected void addRegularizationSymbolicWeightGradient() {
        for (int i = 0; i < mutableRules.size(); i++) {
            float logWeight = (float)Math.log(Math.max(mutableRules.get(i).getWeight().getValue(), MathUtils.STRICT_EPSILON));
            symbolicWeightGradient[i] += (float)(2.0f * l2Regularization * mutableRules.get(i).getWeight().getValue()
                    - logRegularization / Math.max(mutableRules.get(i).getWeight().getValue(), MathUtils.STRICT_EPSILON)
                    + entropyRegularization * (logWeight + 1));
        }
    }

    /**
     * Add the gradient of the total loss with respect to the expression atoms.
     */
    protected abstract void addTotalExpressionAtomGradient();
}
