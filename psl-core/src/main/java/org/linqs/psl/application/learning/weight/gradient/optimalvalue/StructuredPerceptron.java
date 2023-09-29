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
package org.linqs.psl.application.learning.weight.gradient.optimalvalue;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;

import java.util.Arrays;
import java.util.List;

/**
 * Learns new weights for the weighted rules in a model using the structured perceptron algorithm.
 * The structured perceptron learning loss is the difference in the energy of the latent and full inference problems.
 */
public class StructuredPerceptron extends OptimalValue {
    protected float[] MAPIncompatibility;

    public StructuredPerceptron(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                                Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        MAPIncompatibility = new float[mutableRules.size()];
    }

    @Override
    protected void computeIterationStatistics() {
        computeLatentInferenceIncompatibility();
        computeFullInferenceIncompatibility();
    }

    /**
     * Compute the incompatibility of the MAP state.
     */
    private void computeFullInferenceIncompatibility() {
        computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);
        inTrainingMAPState = true;

        computeCurrentIncompatibility(MAPIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), MAPRVAtomEnergyGradient, MAPDeepAtomEnergyGradient);
    }

    @Override
    protected float computeLearningLoss() {
        float energyDifference = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            energyDifference += mutableRules.get(i).getWeight() * (latentInferenceIncompatibility[i] - MAPIncompatibility[i]);
        }

        return energyDifference;
    }

    @Override
    protected void addLearningLossWeightGradient() {
        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += latentInferenceIncompatibility[i] - MAPIncompatibility[i];
        }
    }

    @Override
    protected void computeTotalAtomGradient() {
        Arrays.fill(rvAtomGradient, 0.0f);
        Arrays.fill(deepAtomGradient, 0.0f);

        for (int i = 0; i < rvAtomGradient.length; i++) {
            rvAtomGradient[i] = rvLatentAtomGradient[i] - MAPRVAtomEnergyGradient[i];
            deepAtomGradient[i] = deepLatentAtomGradient[i] - MAPDeepAtomEnergyGradient[i];
        }
    }
}
