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
package org.linqs.psl.reasoner.admm.term;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.reasoner.term.Hyperplane;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LinearLossTermTest {
    @Test
    public void testMinimize() {
        // Problem 1
        float[] z = {0.4f, 0.5f};
        float[] y = {0.0f, 0.0f};
        float[] coeffs = {0.3f, -1.0f};
        float weight = 1.0f;
        float stepSize = 1.0f;
        float[] expected = {0.1f, 1.5f};
        testProblem(z, y, coeffs, weight, stepSize, expected);
    }

    private void testProblem(float[] z, float[] y, float[] coeffs, float weight,
            final float stepSize, float[] expected) {
        LocalVariable[] variables = new LocalVariable[z.length];

        for (int i = 0; i < z.length; i++) {
            variables[i] = new LocalVariable(i, z[i]);
            variables[i].setLagrange(y[i]);
        }

        LinearLossTerm term = new LinearLossTerm(new FakeGroundRule(weight), new Hyperplane<LocalVariable>(variables, coeffs, 0.0f, z.length));
        term.minimize(stepSize, z);

        for (int i = 0; i < z.length; i++) {
            assertEquals(expected[i], variables[i].getValue(), 5e-5);
        }
    }
}
