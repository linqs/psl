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
package org.linqs.psl.application.learning.weight.search;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Config;

public class InitialWeightHyperbandTest extends WeightLearningTest {
    public InitialWeightHyperbandTest() {
        super();

        // TODO(eriq)
        assertBaseTest = false;
        assertFriendshipRankTest = false;
    }

    @Override
    protected WeightLearningApplication getWLA() {
        Config.setProperty(Hyperband.NUM_BRACKETS_KEY, 1);
        Config.setProperty(Hyperband.BASE_BRACKET_SIZE_KEY, 2);
        Config.setProperty(VotedPerceptron.NUM_STEPS_KEY, 5);

        return new InitialWeightHyperband(info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }
}
