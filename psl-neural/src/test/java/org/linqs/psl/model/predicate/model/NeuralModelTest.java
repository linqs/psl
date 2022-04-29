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
package org.linqs.psl.model.predicate.model;

import org.linqs.psl.test.NeuralPSLTest;

import org.junit.Test;

public class NeuralModelTest extends NeuralPSLTest {
    /**
     * Load a simple serialized model, train it, and predict.
     */
    @Test
    public void testBase() {
        NeuralModel model = new NeuralModel();

        model.load(getModelConfig(NeuralPSLTest.SIGN_MODEL_ID), null);

        // The prediciton for the entity at index 0 should swap and index 6 should stay the same.
        int swapIndex = 0;
        int sameIndex = 6;

        model.run();

        assertEquals(1.0f, model.getValue(null, swapIndex, 0));
        assertEquals(0.0f, model.getValue(null, swapIndex, 1));

        assertEquals(1.0f, model.getValue(null, sameIndex, 0));
        assertEquals(0.0f, model.getValue(null, sameIndex, 1));

        model.initialFit();

        model.run();

        assertEquals(0.0f, model.getValue(null, swapIndex, 0));
        assertEquals(1.0f, model.getValue(null, swapIndex, 1));

        assertEquals(1.0f, model.getValue(null, sameIndex, 0));
        assertEquals(0.0f, model.getValue(null, sameIndex, 1));
    }
}
