/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
import org.linqs.psl.config.Config;
import org.linqs.psl.reasoner.admm.ADMMReasoner;

public class InitialWeightRandomGridSearchTest extends WeightLearningTest {
    public InitialWeightRandomGridSearchTest() {
        super();
        assertBaseTest = false;
        assertFriendshipRankTest = false;
    }

    @Override
    protected WeightLearningApplication getWLA() {
        // Narrow the search space for tests.
        Config.setProperty(GridSearch.POSSIBLE_WEIGHTS_KEY, "1:10:100");
        Config.setProperty(RandomGridSearch.MAX_LOCATIONS_KEY, 50);

        // Turn down the number of iterations of both ADMM and VotedPerceptron.
        Config.setProperty(VotedPerceptron.NUM_STEPS_KEY, 3);
        Config.setProperty(ADMMReasoner.MAX_ITER_KEY, 20);

        // Use the classic MLE.
        WeightLearningApplication internalWLA = new MaxLikelihoodMPE(info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);

        return new InitialWeightRandomGridSearch(info.model.getRules(), internalWLA, weightLearningTrainDB, weightLearningTruthDB);
    }
}
