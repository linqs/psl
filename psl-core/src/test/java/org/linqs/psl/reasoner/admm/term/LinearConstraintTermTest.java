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

import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LinearConstraintTermTest {
    @Test
    public void testMinimize() {
        // Problem 1: Constraint inactive at solution
        float[] z = {0.2f, 0.5f};
        float[] y = {0.0f, 0.0f};
        float[] coeffs = {1.0f, 1.0f};
        float constant = 1.0f;
        FunctionComparator comparator = FunctionComparator.LTE;
        float stepSize = 1.0f;
        float[] expected = {0.2f, 0.5f};
        testProblem(z, y, coeffs, constant, comparator, stepSize, expected);

        // Problem 2: Constraint active at solution
        z = new float[] {0.7f, 0.5f};
        y = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, 1.0f};
        constant = 1.0f;
        comparator = FunctionComparator.LTE;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.4f};
        testProblem(z, y, coeffs, constant, comparator, stepSize, expected);

        // Problem 3: Equality constraint
        z = new float[] {0.7f, 0.5f};
        y = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = 0.0f;
        comparator = FunctionComparator.EQ;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.6f};
        testProblem(z, y, coeffs, constant, comparator, stepSize, expected);
    }

    private void testProblem(float[] z, float[] y, float[] coeffs, float constant,
            FunctionComparator comparator, final float stepSize, float[] expected) {
        LocalVariable[] variables = new LocalVariable[z.length];

        for (int i = 0; i < z.length; i++) {
            variables[i] = new LocalVariable(i, z[i]);
            variables[i].setLagrange(y[i]);
        }

        LinearConstraintTerm term = new LinearConstraintTerm(null, new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length), comparator);
        term.minimize(stepSize, z);

        for (int i = 0; i < z.length; i++) {
            assertEquals(expected[i], variables[i].getValue(), 5e-5);
        }
    }
}
