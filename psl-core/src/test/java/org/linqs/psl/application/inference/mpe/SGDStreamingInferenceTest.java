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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.InferenceTest;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.sgd.SGDReasoner;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class SGDStreamingInferenceTest extends InferenceTest {
    @Before
    public void setup() {
        Options.REASONER_OBJECTIVE_BREAK.set(false);
    }

    @Override
    protected InferenceApplication getInference(List<Rule> rules, Database db) {
        return new SGDStreamingInference(rules, db);
    }

    @Override
    public void testLogicalTautologyTrivial() {
        // Streaming methods cannot use this test since it requires
        // ground rules and terms to be kept in memory.
    }

    @Override
    public void initialValueTest() {
        // Skip this test in favor of specific SGD variants.
    }

    @Test
    public void initialValueTestNoExtension() {
        // No extension.
        Options.SGD_EXTENSION.set(SGDReasoner.SGDExtension.NONE);

        // SGD Non-coordinate step.
        Options.SGD_LEARNING_RATE.set(1.0);
        Options.SGD_INVERSE_TIME_EXP.set(0.5);
        Options.SGD_COORDINATE_STEP.set(false);
        super.initialValueTest();

        // SGD Coordinate step.
        Options.SGD_COORDINATE_STEP.set(true);
        super.initialValueTest();
    }

    @Test
    public void initialValueTestAdamExtension() {
        // Adam.
        Options.SGD_EXTENSION.set(SGDReasoner.SGDExtension.ADAM);

        // Non-coordinate step.
        Options.SGD_LEARNING_RATE.set(1.0);
        Options.SGD_INVERSE_TIME_EXP.set(0.5);
        Options.SGD_COORDINATE_STEP.set(false);
        super.initialValueTest();

        // Coordinate step.
        Options.SGD_COORDINATE_STEP.set(true);
        super.initialValueTest();
    }

    @Test
    public void initialValueTestAdaGradExtension() {
        // AdaGrad.
        Options.SGD_EXTENSION.set(SGDReasoner.SGDExtension.ADAGRAD);

        // Non-coordinate step.
        Options.SGD_LEARNING_RATE.set(1.0);
        Options.SGD_INVERSE_TIME_EXP.set(0.5);
        Options.SGD_COORDINATE_STEP.set(false);
        super.initialValueTest();

        // Coordinate step.
        Options.SGD_COORDINATE_STEP.set(true);
        super.initialValueTest();
    }

    @Override
    public void testSimplexConstraints() {
        Options.SGD_LEARNING_RATE.set(1.0);
        Options.SGD_INVERSE_TIME_EXP.set(2.0);
        Options.SGD_COORDINATE_STEP.set(false);
        super.testSimplexConstraints();
    }
}
