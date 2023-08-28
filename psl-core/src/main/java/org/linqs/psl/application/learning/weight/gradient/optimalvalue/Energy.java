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
 * Learns new weights for the weighted rules in a model by minimizing the value of the energy function
 * of the optimal latent inference problem.
 * In the case there is no latent variables, this reduces to minimizing the energy the observations.
 */
public class Energy extends OptimalValue {
    public Energy(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                  Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);
    }

    @Override
    protected float computeLearningLoss() {
        float energy = 0.0f;
        for (int i = 0; i < mutableRules.size(); i++) {
            energy += mutableRules.get(i).getWeight() * latentInferenceIncompatibility[i];
        }

        return energy;
    }

    @Override
    protected void computeIterationStatistics() {
        computeLatentInferenceIncompatibility();
    }

    @Override
    protected void addLearningLossWeightGradient() {
        for (int i = 0; i < mutableRules.size(); i++) {
            weightGradient[i] += latentInferenceIncompatibility[i];
        }
    }

    @Override
    protected void computeTotalAtomGradient() {
        Arrays.fill(rvAtomGradient, 0.0f);
        Arrays.fill(deepAtomGradient, 0.0f);

        System.arraycopy(deepLatentAtomGradient, 0, deepAtomGradient, 0, deepLatentAtomGradient.length);
    }
}
