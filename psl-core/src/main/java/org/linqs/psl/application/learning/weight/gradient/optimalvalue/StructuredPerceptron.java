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

import java.util.List;

/**
 * Learns new weights for the weighted rules in a model using the structured perceptron algorithm.
 * The structured perceptron learning loss is the difference in the energy of the latent and full inference problems.
 */
public class StructuredPerceptron extends OptimalValue {
    protected float[] MAPIncompatibility;

    public StructuredPerceptron(List<Rule> rules, Database rvDB, Database observedDB, Database validationDB) {
        super(rules, rvDB, observedDB, validationDB);

        MAPIncompatibility = new float[mutableRules.size()];
    }

    @Override
    protected void computeIterationStatistics() {
        computeLatentStatistics();
        computeFullStatistics();
    }

    /**
     * Compute the incompatibility of the MAP state.
     */
    private void computeFullStatistics() {
        computeMPEStateWithWarmStart(mpeTermState, mpeAtomValueState);
        computeCurrentIncompatibility(MAPIncompatibility);
        inference.getReasoner().computeOptimalValueGradient(inference.getTermStore(), rvAtomGradient, deepAtomGradient);
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
        for (int i = 0; i < rvAtomGradient.length; i++) {
            rvAtomGradient[i] = rvLatentAtomGradient[i] - rvAtomGradient[i];
            deepAtomGradient[i] = deepLatentAtomGradient[i] - deepAtomGradient[i];
        }
    }
}
