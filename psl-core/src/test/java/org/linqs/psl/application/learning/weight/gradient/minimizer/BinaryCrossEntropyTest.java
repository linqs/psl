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
package org.linqs.psl.application.learning.weight.gradient.minimizer;

import org.junit.Test;
import org.linqs.psl.application.inference.mpe.DualBCDInference;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Options;

import org.junit.Before;

public class BinaryCrossEntropyTest extends WeightLearningTest {
    @Before
    public void setup() {
        super.setup();

        Options.WLA_INFERENCE.set(DualBCDInference.class.getName());
    }

    @Override
    protected WeightLearningApplication getBaseWLA() {
        return new BinaryCrossEntropy(info.model.getRules(), trainTargetDatabase, trainTruthDatabase,
                validationTargetDatabase, validationTruthDatabase, false);
    }

    @Test
    public void DualBCDFriendshipRankTest() {
        Options.WLA_INFERENCE.set(DualBCDInference.class.getName());

        super.friendshipRankTest();
    }

    @Test
    public void DistributedDualBCDFriendshipRankTest() {
        Options.WLA_INFERENCE.set(DualBCDInference.class.getName());

        super.friendshipRankTest();
    }
}
