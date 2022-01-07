/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.util.MathUtils;

import org.linqs.psl.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Learns new weights for the weighted rules in a model using the voted perceptron algorithm.
 *
 * The weight-learning objective is to maximize the likelihood according to the distribution:
 *
 * p(X) = 1 / Z(w) * exp{-sum[w * f(X)]}
 *
 * Where:
 *  - X is the set of RandomVariableAtoms,
 *  - f(X) the incompatibility of each GroundRule,
 *  - w is the weight of that GroundRule,
 *  - and Z(w) is a normalization factor.
 *
 * The voted perceptron algorithm starts at the current weights and at each step
 * computes the gradient of the objective, takes that step multiplied by a step size
 * (possibly truncated to stay in the region of feasible weights), and
 * saves the new weights. The components of the gradient are each divided by the
 * number of weighted ground rules from that Rule. The learned weights
 * are the averages of the saved weights.
 *
 * For the gradient of the objective, the expected total incompatibility is
 * computed by subclasses in {@link #computeExpectedIncompatibility()}.
 *
 * Reasonable initial implementations are provided for all methods.
 * Child classes should be able to pick and chose which to override.
 *
 * Voted perceptron methods must use an InferenceApplication with a ground rules store,
 * i.e. VotedPerceptron cannot stream.
 */
public abstract class VotedPerceptron extends WeightLearningApplication {
    private static final Logger log = LoggerFactory.getLogger(VotedPerceptron.class);

    // Corresponds 1-1 with mutableRules.
    protected double[] observedIncompatibility;
    protected double[] expectedIncompatibility;

    protected final double l2Regularization;
    protected final double l1Regularization;
    protected final boolean scaleGradient;

    protected double baseStepSize;
    protected boolean scaleStepSize;
    protected boolean averageSteps;
    protected boolean zeroInitialWeights;
    protected boolean clipNegativeWeights;
    protected boolean cutObjective;
    protected double inertia;
    protected final int maxNumSteps;
    protected int numSteps;

    /**
     * Learning loss at the current point
     */
    private double currentLoss;

    public VotedPerceptron(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        observedIncompatibility = new double[mutableRules.size()];
        expectedIncompatibility = new double[mutableRules.size()];

        numSteps = Options.WLA_VP_NUM_STEPS.getInt();
        maxNumSteps = numSteps;

        baseStepSize = Options.WLA_VP_STEP.getDouble();
        inertia = Options.WLA_VP_INERTIA.getDouble();
        l1Regularization = Options.WLA_VP_L1.getDouble();
        l2Regularization = Options.WLA_VP_L2.getDouble();

        scaleGradient = Options.WLA_VP_SCALE_GRADIENT.getBoolean();
        averageSteps = Options.WLA_VP_AVERAGE_STEPS.getBoolean();
        scaleStepSize = Options.WLA_VP_SCALE_STEP.getBoolean();
        zeroInitialWeights = Options.WLA_VP_ZERO_INITIAL_WEIGHTS.getBoolean();
        clipNegativeWeights = Options.WLA_VP_CLIP_NEGATIVE_WEIGHTS.getBoolean();
        cutObjective = Options.WLA_VP_CUT_OBJECTIVE.getBoolean();

        currentLoss = Double.NaN;
    }

    protected void postInitGroundModel() {
        if (trainingMap.getLatentVariables().size() > 0) {
            log.warn("Latent variable(s) found when using a VotedPerceptron-based weight learning method ({})."
                    + " VotedPerceptron uses gradients to update weights, but latent variables may make the gradients less accurate."
                    + " Weight learning may still perform sufficiently."
                    + " Found {} latent variables."
                    + " Example latent variable: [{}].",
                    this.getClass().getName(),
                    trainingMap.getLatentVariables().size(),
                    trainingMap.getLatentVariables().get(0));
        }
    }

    @Override
    protected void doLearn() {
        float[] avgWeights = new float[mutableRules.size()];

        String currentLocation = null;

        // Computes the observed incompatibilities.
        computeObservedIncompatibility();

        // Reset the RVAs to default values.
        setDefaultRandomVariables();

        if (zeroInitialWeights) {
            for (WeightedRule rule : mutableRules) {
                rule.setWeight(0.0f);
            }
        }

        // Compute the initial objective.
        if (log.isDebugEnabled() && evaluator != null) {
            // Compute the MPE state before evaluating so variables have assigned values.
            computeMPEState();

            evaluator.compute(trainingMap);
            double objective = -1.0 * evaluator.getNormalizedRepMetric();

            log.debug("Initial Training Objective: {}", objective);
        }

        double[] scalingFactor = computeScalingFactor();

        // Keep track of the last steps for each weight so we can apply momentum.
        double[] lastSteps = new double[mutableRules.size()];
        double lastObjective = -1.0;

        float[] currentWeights = new float[mutableRules.size()];
        float[] lastWeights = new float[mutableRules.size()];
        for (int i = 0; i < mutableRules.size(); i++) {
            lastWeights[i] = mutableRules.get(i).getWeight();
        }

        // Computes the gradient steps.
        for (int step = 0; step < numSteps; step++) {
            log.debug("Starting iteration {}", step);
            currentLoss = Double.NaN;

            // Computes the expected incompatibility.
            computeExpectedIncompatibility();

            double norm = 0.0;

            // Updates weights.
            for (int i = 0; i < mutableRules.size(); i++) {
                float newWeight = mutableRules.get(i).getWeight();
                double currentStep = (expectedIncompatibility[i] - observedIncompatibility[i]
                        - l2Regularization * newWeight
                        - l1Regularization) / scalingFactor[i];

                currentStep *= baseStepSize;

                if (scaleStepSize) {
                    currentStep /= (step + 1);
                }

                // Apply momentum.
                currentStep += inertia * lastSteps[i];

                if (clipNegativeWeights) {
                    newWeight = (float)Math.max(0.0, newWeight + currentStep);
                } else {
                    newWeight = (float)(newWeight + currentStep);
                }

                log.trace("Gradient: {} (without momentun: {}), Expected Incomp.: {}, Observed Incomp.: {} -- ({}) {}",
                        currentStep, currentStep - (inertia * lastSteps[i]),
                        expectedIncompatibility[i], observedIncompatibility[i],
                        i, mutableRules.get(i));

                mutableRules.get(i).setWeight(newWeight);
                lastSteps[i] = currentStep;
                avgWeights[i] += newWeight;
                currentWeights[i] = newWeight;
                norm += Math.pow(expectedIncompatibility[i] - observedIncompatibility[i], 2);
            }

            // Set the current location.
            currentLocation = StringUtils.join(DELIM, currentWeights);

            log.trace("Weights: {}", currentWeights);

            // The weights have changed, so we are no longer in an MPE state.
            inMPEState = false;

            norm = Math.sqrt(norm);

            if (log.isDebugEnabled()) {
                getLoss();
            }

            double objective = -1.0;
            if ((cutObjective || log.isDebugEnabled()) && evaluator != null) {
                // Compute the MPE state before evaluating so variables have assigned values.
                computeMPEState();

                evaluator.compute(trainingMap);
                objective = -1.0 * evaluator.getNormalizedRepMetric();

                log.debug("Weights: {} -- objective: {}", currentLocation, objective);

                if (cutObjective && step > 0 && objective > lastObjective) {
                    log.trace("Objective increased: {} -> {}, cutting step size: {} -> {}.",
                            lastObjective, objective, baseStepSize, baseStepSize / 2.0);
                    baseStepSize /= 2.0;
                    objective = lastObjective;

                    // Set the weights back to the previous ones.
                    for (int i = 0; i < mutableRules.size(); i++) {
                        lastSteps[i] = 0.0;
                        avgWeights[i] -= mutableRules.get(i).getWeight();

                        mutableRules.get(i).setWeight(lastWeights[i]);
                    }
                } else {
                    lastObjective = objective;
                }
            }

            for (int i = 0; i < mutableRules.size(); i++) {
                lastWeights[i] = mutableRules.get(i).getWeight();
            }

            log.debug("Iteration {} complete. Likelihood: {}. Training Objective: {}, Icomp. L2-norm: {}", step, currentLoss, objective, norm);
            log.trace("Model {} ", mutableRules);
        }

        // Sets the weights to their averages.
        if (averageSteps) {
            for (int i = 0; i < mutableRules.size(); i++) {
                mutableRules.get(i).setWeight(avgWeights[i] / numSteps);
            }
        }
    }

    /**
     * Internal method for computing the loss at the current point before taking a step.
     * Child methods may override.
     *
     * The default implementation just sums the product of the difference between the expected and observed incompatibility.
     *
     * @return current learning loss
     */
    protected double computeLoss() {
        double loss = 0.0;
        for (int i = 0; i < mutableRules.size(); i++) {
            loss += mutableRules.get(i).getWeight() * (observedIncompatibility[i] - expectedIncompatibility[i]);
        }

        return loss;
    }

    protected double computeRegularizer() {
        if (l1Regularization == 0.0 && l2Regularization == 0.0) {
            return 0.0;
        }

        double l2 = 0.0;
        double l1 = 0.0;

        for (WeightedRule rule : mutableRules) {
            l2 += Math.pow(rule.getWeight(), 2);
            l1 += Math.abs(rule.getWeight());
        }

        return 0.5 * l2Regularization * l2 + l1Regularization * l1;
    }

    public double getLoss() {
        if (Double.isNaN(currentLoss)) {
            currentLoss = computeLoss();
        }

        return currentLoss;
    }

    /**
     * Computes the amount to scale gradient for each rule.
     * Scales by the number of groundings of each rule
     * unless the rule is not grounded in the training set, in which case
     * scales by 1.0.
     */
    protected double[] computeScalingFactor() {
        double [] factor = new double[mutableRules.size()];
        for (int i = 0; i < factor.length; i++) {
            factor[i] = Math.max(1.0, inference.getGroundRuleStore().count(mutableRules.get(i)));
        }

        return factor;
    }

    /**
     * Compute the incompatibility in the model using the labels (truth values) from the observed (truth) database.
     * This method is responsible for filling the observedIncompatibility member variable.
     * This may call setLabeledRandomVariables() and not reset any ground atoms to their original value.
     *
     * The default implementation just calls setLabeledRandomVariables() and sums the incompatibility for each rule.
     */
    protected void computeObservedIncompatibility() {
        setLabeledRandomVariables();

        // Zero out the observed incompatibility first.
        for (int i = 0; i < observedIncompatibility.length; i++) {
            observedIncompatibility[i] = 0.0;
        }

        // Sums up the incompatibilities.
        for (int i = 0; i < mutableRules.size(); i++) {
            for (GroundRule groundRule : inference.getGroundRuleStore().getGroundRules(mutableRules.get(i))) {
                observedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
            }
        }
    }

    /**
     * Compute the incompatibility in the model.
     * This method is responsible for filling the expectedIncompatibility member variable.
     *
     * The default implementation is the total incompatibility in the MPE state.
     * IE, just calls computeMPEState() and then sums the incompatibility for each rule.
     */
    protected void computeExpectedIncompatibility() {
        computeMPEState();

        // Zero out the expected incompatibility first.
        for (int i = 0; i < expectedIncompatibility.length; i++) {
            expectedIncompatibility[i] = 0.0;
        }

        // Sums up the incompatibilities.
        for (int i = 0; i < mutableRules.size(); i++) {
            for (GroundRule groundRule : inference.getGroundRuleStore().getGroundRules(mutableRules.get(i))) {
                expectedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
            }
        }
    }

    /**
     * Set RandomVariableAtoms with training labels to their observed values.
     */
    protected void setLabeledRandomVariables() {
        inMPEState = false;

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getLabelMap().entrySet()) {
            entry.getKey().setValue(entry.getValue().getValue());
        }
    }

    /**
     * Set all RandomVariableAtoms we know of to their default values.
     */
    protected void setDefaultRandomVariables() {
        inMPEState = false;

        for (RandomVariableAtom atom : trainingMap.getLabelMap().keySet()) {
            atom.setValue(0.0f);
        }

        for (RandomVariableAtom atom : trainingMap.getLatentVariables()) {
            atom.setValue(0.0f);
        }
    }

    @Override
    public void setBudget(double budget) {
        super.setBudget(budget);

        numSteps = (int)Math.ceil(budget * maxNumSteps);
    }
}
