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

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;

public class GridSearchDiscreteLossTest extends WeightLearningTest {
    public GridSearchDiscreteLossTest() {
        super();
        assertBaseTest = false;
        assertFriendshipRankTest = false;
    }

    @Override
    protected WeightLearningApplication getWLA() {
        // Narrow the search space for tests.
        Options.WLA_GS_POSSIBLE_WEIGHTS.set("0.01:1:10");

        // Use MAE as an objective.
        Options.WLA_EVAL.set(DiscreteEvaluator.class.getName());

        return new GridSearch(info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }
}
