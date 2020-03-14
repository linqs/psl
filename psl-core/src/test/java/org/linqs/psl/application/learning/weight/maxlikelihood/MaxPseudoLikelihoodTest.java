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
package org.linqs.psl.application.learning.weight.maxlikelihood;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Options;

import org.junit.After;

public class MaxPseudoLikelihoodTest extends WeightLearningTest {
    public MaxPseudoLikelihoodTest() {
        super();

        // TODO(eriq): MPLE is broken, fix it.
        assertBaseTest = false;
    }

    @Override
    protected WeightLearningApplication getWLA() {
        // Do less steps for tests.
        Options.WLA_VP_NUM_STEPS.set(5);

        return new MaxPseudoLikelihood(info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }
}
