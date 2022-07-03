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
package org.linqs.psl.application.learning.weight.search.grid;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.Options;

public class InitialWeightRankSearchTest extends WeightLearningTest {
    @Override
    protected WeightLearningApplication getWLA() {
        // Turn down the number of iterations of both ADMM and VotedPerceptron.
        Options.WLA_VP_NUM_STEPS.set(5);
        Options.ADMM_MAX_ITER.set(Integer.valueOf(25));

        // Use the classic MLE.
        WeightLearningApplication internalWLA = new MaxLikelihoodMPE(info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);

        return new InitialWeightRankSearch(info.model.getRules(), internalWLA, weightLearningTrainDB, weightLearningTruthDB);
    }
}
