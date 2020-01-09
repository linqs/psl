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

public class HingeLossTermTest {
    @Test
    public void testMinimize() {
        // Problem 1: Solution on the hinge
        float[] z = {0.2f, 0.5f};
        float[] y = {0.0f, 0.0f};
        float[] coeffs = {1.0f, -1.0f};
        float constant = -0.95f;
        float weight = 1.0f;
        float stepSize = 1.0f;
        float[] expected = {-0.125f, 0.825f};
        testProblem(z, y, coeffs, constant, weight, stepSize, expected);

        // Problem 2: Solution on the hinge
        z = new float[] {0.3f, 0.5f, 0.1f};
        y = new float[] {0.1f, 0.0f, -0.05f};
        coeffs = new float[] {1.0f, -0.5f, 0.4f};
        constant = -0.15f;
        weight = 1.0f;
        stepSize = 0.5f;
        expected = new float[] {0.043257f, 0.528361f, 0.177309f};
        testProblem(z, y, coeffs, constant, weight, stepSize, expected);

        // Problem 3: Solution on the zero side
        z = new float[] {0.3f, 0.5f, 0.1f};
        y = new float[] {0.1f, 0.0f, -0.05f};
        coeffs = new float[] {1.0f, -0.5f, 0.4f};
        constant = 0.0f;
        weight = 2.0f;
        stepSize = 0.5f;
        expected = new float[] {0.1f, 0.5f, 0.2f};
        testProblem(z, y, coeffs, constant, weight, stepSize, expected);

        // Problem 4: Solution on the zero side
        z = new float[] {0.1f};
        y = new float[] {0.15f};
        coeffs = new float[] {1.0f};
        constant = 0.0f;
        weight = 2.0f;
        stepSize = 1.0f;
        expected = new float[] {-0.05f};
        testProblem(z, y, coeffs, constant, weight, stepSize, expected);

        // Problem 5: Solution on the linear side
        z = new float[] {0.7f, 0.5f};
        y = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = 0.0f;
        weight = 1.0f;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.6f};
        testProblem(z, y, coeffs, constant, weight, stepSize, expected);

        // Problem 6: Solution on the hinge, two variables, non-1 stepsize and non-0 dual variables
        z = new float[] {0.7f, 0.5f};
        y = new float[] {0.05f, 1.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = -0.5f;
        weight = 2.0f;
        stepSize = 2.0f;
        expected = new float[] {0.0875f, 0.5875f};
        testProblem(z, y, coeffs, constant, weight, stepSize, expected);
    }

    private void testProblem(float[] z, float[] y, float[] coeffs, float constant,
            float weight, final float stepSize, float[] expected) {
        LocalVariable[] variables = new LocalVariable[z.length];

        for (int i = 0; i < z.length; i++) {
            variables[i] = new LocalVariable(i, z[i]);
            variables[i].setLagrange(y[i]);
        }

        HingeLossTerm term = new HingeLossTerm(new FakeGroundRule(weight), new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length));
        term.minimize(stepSize, z);

        for (int i = 0; i < z.length; i++) {
            assertEquals(expected[i], variables[i].getValue(), 5e-5);
        }
    }
}
