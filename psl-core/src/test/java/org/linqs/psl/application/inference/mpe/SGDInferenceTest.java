/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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

import org.junit.After;
import org.linqs.psl.reasoner.sgd.SGDReasoner;

import java.util.List;

public class SGDInferenceTest extends InferenceTest {
    @After
    public void cleanup() {
        Options.SGD_LEARNING_RATE.clear();
        Options.SGD_COORDINATE_STEP.clear();
        Options.SGD_EXTENSION.clear();
    }

    @Override
    protected InferenceApplication getInference(List<Rule> rules, Database db) {
        return new SGDInference(rules, db);
    }

    @Override
    public void initialValueTest() {
        // SGD Non-coordinate step.
        Options.SGD_LEARNING_RATE.set(1.0);
        Options.SGD_INVERSE_TIME_EXP.set(0.5);
        Options.SGD_COORDINATE_STEP.set(false);
        super.initialValueTest();

        // SGD Coordinate step.
        Options.SGD_COORDINATE_STEP.set(true);
        super.initialValueTest();

        cleanup();

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

        cleanup();

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
