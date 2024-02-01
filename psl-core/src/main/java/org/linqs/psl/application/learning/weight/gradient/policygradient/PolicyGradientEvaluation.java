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
package org.linqs.psl.application.learning.weight.gradient.policygradient;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.Logger;

import java.util.List;
import java.util.Set;

/**
 * Learns parameters for a model by minimizing the specified evaluation metric
 * using the policy gradient learning framework.
 */
public class PolicyGradientEvaluation extends PolicyGradient {
    private static final Logger log = Logger.getLogger(PolicyGradientEvaluation.class);

    public PolicyGradientEvaluation(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                                    Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);
    }

    @Override
    protected float computeReward() {
        evaluation.compute(trainingMap);

        return internalComputeReward();
    }

    @Override
    protected float computeReward(Set<GroundAtom> truthSubset) {
        evaluation.compute(trainingMap, truthSubset);

        return internalComputeReward();
    }

    private float internalComputeReward() {
        float reward = 0.0f;

        float normalizedRepMetric = (float) evaluation.getNormalizedRepMetric();

        switch (rewardFunction) {
            case EVALUATION:
                reward = normalizedRepMetric;
                break;
            default:
                throw new IllegalArgumentException("Unknown reward function: " + rewardFunction);
        }

        return reward;
    }
}
