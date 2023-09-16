/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.reasoner.gradientdescent;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.gradientdescent.term.GradientDescentObjectiveTerm;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;

import java.util.List;

/**
 * Uses a Gradient optimization method to optimize its GroundRules.
 */
public class GradientDescentReasoner extends Reasoner<GradientDescentObjectiveTerm> {
    private static final Logger log = Logger.getLogger(GradientDescentReasoner.class);

    /**
     * The Gradient Descent Extension to use.
     */
    public enum GradientDescentExtension {
        NONE,
        MOMENTUM,
        NESTEROV_ACCELERATION
    }

    /**
     * The Gradient Descent learning schedule to use.
     */
    public static enum GradientDescentLearningSchedule {
        CONSTANT,
        STEPDECAY
    }

    private final boolean firstOrderBreak;
    private final float firstOrderTolerance;
    private final float firstOrderNorm;

    private float[] gradient;

    private final float initialLearningRate;
    private final float learningRateInverseScaleExp;
    private final GradientDescentReasoner.GradientDescentLearningSchedule learningSchedule;
    private final GradientDescentExtension gdExtension;

    public GradientDescentReasoner() {
        maxIterations = Options.GRADIENT_DESCENT_MAX_ITER.getInt();
        firstOrderBreak = Options.GRADIENT_DESCENT_FIRST_ORDER_BREAK.getBoolean();
        firstOrderTolerance = Options.GRADIENT_DESCENT_FIRST_ORDER_THRESHOLD.getFloat();
        firstOrderNorm = Options.GRADIENT_DESCENT_FIRST_ORDER_NORM.getFloat();

        gradient = null;

        gdExtension = GradientDescentExtension.valueOf(Options.GRADIENT_DESCENT_EXTENSION.getString().toUpperCase());

        initialLearningRate = Options.GRADIENT_DESCENT_LEARNING_RATE.getFloat();
        learningRateInverseScaleExp = Options.GRADIENT_DESCENT_INVERSE_TIME_EXP.getFloat();
        learningSchedule = GradientDescentReasoner.GradientDescentLearningSchedule.valueOf(Options.GRADIENT_DESCENT_LEARNING_SCHEDULE.getString().toUpperCase());
    }

    @Override
    public double optimize(TermStore<GradientDescentObjectiveTerm> termStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        termStore.initForOptimization();
        initForOptimization(termStore);

        // Return if there are no decision variables.
        boolean hasDecisionVariables = false;
        for (GroundAtom atom : termStore.getAtomStore()) {
            if (!(atom.isFixed())) {
                hasDecisionVariables = true;
                break;
            }
        }

        if (!hasDecisionVariables){
            log.trace("No random variable atoms to optimize.");
            return parallelComputeObjective(termStore).objective;
        }

        // Return if there are no terms.
        if (termStore.size() == 0) {
            log.trace("No terms to optimize.");
            return parallelComputeObjective(termStore).objective;
        }

        float learningRate = 0.0f;

        GroundAtom[] atoms = termStore.getAtomStore().getAtoms();
        float[] atomValues = termStore.getAtomStore().getAtomValues();
        float[] update = new float[termStore.getAtomStore().size()];
        gradient = new float[termStore.getAtomStore().size()];
        float[] deepAtomGradients = new float[termStore.getAtomStore().size()];
        ObjectiveResult objectiveResult = parallelComputeObjective(termStore);
        ObjectiveResult oldObjectiveResult = null;

        long totalTime = 0;
        boolean breakGradientDescent = false;
        int iteration = 1;
        while (!breakGradientDescent) {
            long startTime = System.currentTimeMillis();

            learningRate = calculateAnnealedLearningRate(iteration);

            if (gdExtension == GradientDescentExtension.NESTEROV_ACCELERATION) {
                for (int i = 0; i < gradient.length; i++) {
                    if (atoms[i].isFixed()) {
                        continue;
                    }

                    atomValues[i] = Math.min(Math.max(atomValues[i] - 0.9f * update[i], 0.0f), 1.0f);
                }
            }

            parallelComputeGradient(termStore, gradient, deepAtomGradients);
            clipGradientMagnitude(gradient, 1.0f);

            for (int i = 0; i < gradient.length; i++) {
                if (atoms[i].isFixed()) {
                    continue;
                }

                switch (gdExtension) {
                    case MOMENTUM:
                        update[i] = 0.9f * update[i] + learningRate * gradient[i];
                        atomValues[i] = Math.min(Math.max(atomValues[i] - update[i], 0.0f), 1.0f);
                        break;
                    case NESTEROV_ACCELERATION:
                        update[i] = 0.9f * update[i] + learningRate * gradient[i];
                        atomValues[i] = Math.min(Math.max(atomValues[i] - learningRate * gradient[i], 0.0f), 1.0f);
                        break;
                    case NONE:
                        atomValues[i] = Math.min(Math.max(atomValues[i] - learningRate * gradient[i], 0.0f), 1.0f);
                        break;
                }
            }

            oldObjectiveResult = objectiveResult;
            objectiveResult = parallelComputeObjective(termStore);

            long endTime = System.currentTimeMillis();
            totalTime += System.currentTimeMillis() - startTime;

            breakGradientDescent = breakOptimization(iteration, termStore, objectiveResult, oldObjectiveResult);

            log.trace("Iteration {} -- Objective: {}, Iteration Time: {}, Total Optimization Time: {}.",
                    iteration, objectiveResult.objective, (endTime - startTime), totalTime);

            evaluate(termStore, iteration, evaluations, trainingMap);

            iteration++;
        }

        optimizationComplete(termStore, parallelComputeObjective(termStore), totalTime);
        return objectiveResult.objective;
    }

    @Override
    protected boolean breakOptimization(int iteration, TermStore<GradientDescentObjectiveTerm> termStore,
                                        ObjectiveResult objective, ObjectiveResult oldObjective) {
        if (super.breakOptimization(iteration, termStore, objective, oldObjective)) {
            return true;
        }

        // Run through the maximum number of iterations.
        if (runFullIterations) {
            return false;
        }

        // Don't break if there are violated constraints.
        if (objective != null && objective.violatedConstraints > 0) {
            return false;
        }

        // Break if the norm of the gradient is zero.
        if (firstOrderBreak
                && MathUtils.equals(MathUtils.pNorm(gradient, firstOrderNorm), 0.0f, firstOrderTolerance)) {
            log.trace("Breaking optimization. Gradient magnitude: {} below tolerance: {}.",
                    MathUtils.pNorm(gradient, firstOrderNorm), firstOrderTolerance);
            return true;
        }

        return false;
    }

    private float calculateAnnealedLearningRate(int iteration) {
        switch (learningSchedule) {
            case CONSTANT:
                return initialLearningRate;
            case STEPDECAY:
                return initialLearningRate / ((float)Math.pow(iteration, learningRateInverseScaleExp));
            default:
                throw new IllegalArgumentException(String.format("Illegal value found for gradient descent learning schedule: '%s'", learningSchedule));
        }
    }
}
