/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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

import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.InferenceTest;
import org.linqs.psl.reasoner.gradientdescent.GradientDescentReasoner;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;

import java.util.List;

public class GradientDescentInferenceTest extends InferenceTest {
    @Before
    public void setup() {
        Options.REASONER_OBJECTIVE_BREAK.set(false);
    }

    @Override
    protected InferenceApplication getInference(List<Rule> rules, Database db) {
        return new GradientDescentInference(rules, db);
    }

    @Override
    public void initialValueTest() {
        // Skip this test in favor of specific GradientDescent variants.
    }

    @Test
    public void initialValueTestNoExtension() {
        // No extension.
        Options.GRADIENT_DESCENT_EXTENSION.set(GradientDescentReasoner.GradientDescentExtension.NONE);
        super.initialValueTest();
    }

    @Test
    public void initialValueTestNesterovAccelerationExtension() {
        // Nesterov Acceleration.
        Options.GRADIENT_DESCENT_EXTENSION.set(GradientDescentReasoner.GradientDescentExtension.NESTEROV_ACCELERATION);
        super.initialValueTest();
    }

    @Test
    public void initialValueTestMomentumExtension() {
        // Momentum.
        Options.GRADIENT_DESCENT_EXTENSION.set(GradientDescentReasoner.GradientDescentExtension.MOMENTUM);
        super.initialValueTest();
    }

    @Override
    public void testSimplexConstraints() {
        Options.GRADIENT_DESCENT_LEARNING_RATE.set(0.01f);
        super.testSimplexConstraints();
    }
}
