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
package org.linqs.psl.application.learning.weight.gradient.optimalvalue;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.Logger;

import java.util.List;

/**
 * Learns new weights for the weighted rules in a model using the structured perceptron algorithm.
 * The structured perceptron learning loss is the difference in the energy of the latent and full inference problems.
 */
public class StructuredPerceptron extends OptimalValue {
    private static final Logger log = Logger.getLogger(StructuredPerceptron.class);

    protected float[] symbolicWeightRuleMAPIncompatibility;
    protected float[] deepWeightRuleMAPIncompatibility;

    public StructuredPerceptron(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                                Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        symbolicWeightRuleMAPIncompatibility = new float[mutableRules.size()];
        deepWeightRuleMAPIncompatibility = null;
    }

    @Override
    protected void postInitGroundModel() {
        super.postInitGroundModel();

        deepWeightRuleMAPIncompatibility = new float[groundedDeepWeightedRules.size()];
    }

    @Override
    protected void computeIterationStatistics() {
        computeLatentInferenceIncompatibility();
        computeMAPInferenceIncompatibility();
    }

    /**
     * Compute the incompatibility of the MAP state.
     */
    private void computeMAPInferenceIncompatibility() {
        log.trace("Running MAP Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, trainMAPTermState, trainMAPAtomValueState);
        inTrainingMAPState = true;

        computeCurrentIncompatibility(symbolicWeightRuleMAPIncompatibility, deepWeightRuleMAPIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), expressionRVAtomMAPEnergyGradient, expressionDeepAtomMAPEnergyGradient);
    }

    @Override
    protected float computeLearningLoss() {
        float energyDifference = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            energyDifference += mutableRules.get(i).getWeight().getValue() * (latentSymbolicWeightRuleIncompatibility[i] - symbolicWeightRuleMAPIncompatibility[i]);
        }

        return energyDifference;
    }

    @Override
    protected void addLearningLossSymbolicWeightGradient() {
        for (int i = 0; i < mutableRules.size(); i++) {
            symbolicWeightGradient[i] += latentSymbolicWeightRuleIncompatibility[i] - symbolicWeightRuleMAPIncompatibility[i];
        }
    }

    @Override
    protected void addTotalDeepRuleWeightGradient() {
        for (int i = 0; i < deepWeightGradient.length; i++) {
            deepWeightGradient[i] += latentDeepWeightRuleIncompatibility[i] - deepWeightRuleMAPIncompatibility[i];
        }
    }

    @Override
    protected void addTotalExpressionAtomGradient() {
        for (int i = 0; i < expressionRVAtomGradient.length; i++) {
            expressionRVAtomGradient[i] += rvLatentAtomGradient[i] - expressionRVAtomMAPEnergyGradient[i];
            expressionDeepAtomGradient[i] += deepLatentAtomGradient[i] - expressionDeepAtomMAPEnergyGradient[i];
        }
    }
}
