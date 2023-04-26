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
package org.linqs.psl.application.learning.weight.search.grid;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Options;

public class GuidedRandomGridSearchTest extends WeightLearningTest {
    public GuidedRandomGridSearchTest() {
        super();
        assertBaseTest = false;
        assertFriendshipRankTest = false;
    }

    @Override
    protected WeightLearningApplication getBaseWLA() {
        // Narrow the search space for tests.
        Options.WLA_RGS_MAX_LOCATIONS.set(100);

        return new GuidedRandomGridSearch(info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB, weightLearningValidationDB);
    }
}
