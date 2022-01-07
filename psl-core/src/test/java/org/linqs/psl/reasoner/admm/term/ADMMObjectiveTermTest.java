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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.rule.FakeRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.Test;

public class ADMMObjectiveTermTest extends PSLBaseTest {
    @Test
    public void testLinearConstraintTermMinimize() {
        // Problem 1: Constraint inactive at solution
        float[] consensus = {0.2f, 0.5f};
        float[] lagrange = {0.0f, 0.0f};
        float[] coeffs = {1.0f, 1.0f};
        float constant = 1.0f;
        FunctionComparator comparator = FunctionComparator.LTE;
        float stepSize = 1.0f;
        float[] expected = {0.2f, 0.5f};
        testProblem(false, false, comparator, consensus, lagrange, coeffs, constant, 0.0f, stepSize, expected);

        // Problem 2: Constraint active at solution
        consensus = new float[] {0.7f, 0.5f};
        lagrange = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, 1.0f};
        constant = 1.0f;
        comparator = FunctionComparator.LTE;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.4f};
        testProblem(false, false, comparator, consensus, lagrange, coeffs, constant, 0.0f, stepSize, expected);

        // Problem 3: Equality constraint
        consensus = new float[] {0.7f, 0.5f};
        lagrange = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = 0.0f;
        comparator = FunctionComparator.EQ;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.6f};
        testProblem(false, false, comparator, consensus, lagrange, coeffs, constant, 0.0f, stepSize, expected);
    }

    @Test
    public void testLinearLossTermMinimize() {
        // Problem 1
        float[] consensus = {0.4f, 0.5f};
        float[] lagrange = {0.0f, 0.0f};
        float[] coeffs = {0.3f, -1.0f};
        float weight = 1.0f;
        float stepSize = 1.0f;
        float[] expected = {0.1f, 1.5f};
        testProblem(false, false, null, consensus, lagrange, coeffs, 0.0f, weight, stepSize, expected);
    }

    @Test
    public void testHingeLossTermMinimize() {
        // Problem 1: Solution on the hinge
        float[] consensus = {0.2f, 0.5f};
        float[] lagrange = {0.0f, 0.0f};
        float[] coeffs = {1.0f, -1.0f};
        float constant = -0.95f;
        float weight = 1.0f;
        float stepSize = 1.0f;
        float[] expected = {-0.125f, 0.825f};
        testProblem(false, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 2: Solution on the hinge
        consensus = new float[] {0.3f, 0.5f, 0.1f};
        lagrange = new float[] {0.1f, 0.0f, -0.05f};
        coeffs = new float[] {1.0f, -0.5f, 0.4f};
        constant = -0.15f;
        weight = 1.0f;
        stepSize = 0.5f;
        expected = new float[] {0.043257f, 0.528361f, 0.177309f};
        testProblem(false, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 3: Solution on the zero side
        consensus = new float[] {0.3f, 0.5f, 0.1f};
        lagrange = new float[] {0.1f, 0.0f, -0.05f};
        coeffs = new float[] {1.0f, -0.5f, 0.4f};
        constant = 0.0f;
        weight = 2.0f;
        stepSize = 0.5f;
        expected = new float[] {0.1f, 0.5f, 0.2f};
        testProblem(false, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 4: Solution on the zero side
        consensus = new float[] {0.1f};
        lagrange = new float[] {0.15f};
        coeffs = new float[] {1.0f};
        constant = 0.0f;
        weight = 2.0f;
        stepSize = 1.0f;
        expected = new float[] {-0.05f};
        testProblem(false, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 5: Solution on the linear side
        consensus = new float[] {0.7f, 0.5f};
        lagrange = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = 0.0f;
        weight = 1.0f;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.6f};
        testProblem(false, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 6: Solution on the hinge, two variables, non-1 stepsize and non-0 dual variables
        consensus = new float[] {0.7f, 0.5f};
        lagrange = new float[] {0.05f, 1.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = -0.5f;
        weight = 2.0f;
        stepSize = 2.0f;
        expected = new float[] {0.0875f, 0.5875f};
        testProblem(false, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);
    }

    @Test
    public void testSquaredLinearLossTermMinimize() {
        // Problem 1
        float[] consensus = {0.4f, 0.5f, 0.1f};
        float[] lagrange = {0.0f, 0.0f, -0.05f};
        float[] coeffs = {0.3f, -1.0f, 0.4f};
        float constant = -20.0f;
        float weight = 0.5f;
        float stepSize = 2.0f;
        float[] expected = {-1.41569f, 6.55231f, -2.29593f};
        testProblem(true, false, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);
    }

    @Test
    public void testSquaredHingeLossTermMinimize() {
        // Problem 1: Solution on the quadratic side
        float[] consensus = {0.2f, 0.5f};
        float[] lagrange = {0.0f, 0.0f};
        float[] coeffs = {1.0f, -1.0f};
        float constant = -0.95f;
        float weight = 1.0f;
        float stepSize = 1.0f;
        float[] expected = {-0.06f, 0.76f};
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 2: Solution on the quadratic side
        consensus = new float[] {0.3f, 0.5f, 0.1f};
        lagrange = new float[] {0.1f, 0.0f, -0.05f};
        coeffs = new float[] {1.0f, -0.5f, 0.4f};
        constant = -0.15f;
        weight = 1.0f;
        stepSize = 0.5f;
        expected = new float[] {0.051798f, 0.524096f, 0.180720f};
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 3: Solution on the zero side
        consensus = new float[] {0.3f, 0.5f, 0.1f};
        lagrange = new float[] {0.1f, 0.0f, -0.05f};
        coeffs = new float[] {1.0f, -0.5f, 0.4f};
        constant = 0.0f;
        weight = 2.0f;
        stepSize = 0.5f;
        expected = new float[] {0.1f, 0.5f, 0.2f};
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 4: Solution on the quadratic side
        consensus = new float[] {0.1f};
        lagrange = new float[] {-0.15f};
        coeffs = new float[] {1.0f};
        constant = 0.0f;
        weight = 2.0f;
        stepSize = 1.0f;
        expected = new float[] {0.05f};
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 5: Solution on the quadratic side
        consensus = new float[] {0.7f, 0.5f};
        lagrange = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = 0.0f;
        weight = 1.0f;
        stepSize = 1.0f;
        expected = new float[] {0.62f, 0.58f};
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);

        // Problem 6: Solution on the quadratic side
        // Tests factorization caching by repeating the test three times
        consensus = new float[] {3.7f, -0.5f, 0.5f};
        lagrange = new float[] {0.0f, 0.0f, 0.0f};
        coeffs = new float[] {1.0f, -1.0f, 0.5f};
        constant = -0.5f;
        weight = 2.0f;
        stepSize = 2.0f;
        expected = new float[] {1.9f, 1.3f, -0.4f};
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);
        testProblem(true, true, null, consensus, lagrange, coeffs, constant, weight, stepSize, expected);
    }

    private void testProblem(
            boolean squared, boolean hinge, FunctionComparator comparator,
            float[] consensus, float[] lagrange,
            float[] coeffs, float constant,
            float weight, float stepSize, float[] expected) {
        LocalVariable[] variables = new LocalVariable[consensus.length];

        for (int i = 0; i < consensus.length; i++) {
            variables[i] = new LocalVariable(i, consensus[i]);
            variables[i].setLagrange(lagrange[i]);
        }

        ADMMObjectiveTerm term = null;
        if (comparator != null) {
            term = ADMMObjectiveTerm.createLinearConstraintTerm(
                    new Hyperplane<LocalVariable>(variables, coeffs, constant, consensus.length),
                    null,
                    comparator);
        } else if (!squared && !hinge) {
            term = ADMMObjectiveTerm.createLinearLossTerm(
                    new Hyperplane<LocalVariable>(variables, coeffs, 0.0f, consensus.length),
                    new FakeRule(weight, squared));
        } else if (!squared && hinge) {
            term = ADMMObjectiveTerm.createHingeLossTerm(
                    new Hyperplane<LocalVariable>(variables, coeffs, constant, consensus.length),
                    new FakeRule(weight, squared));
        } else if (squared && !hinge) {
            term = ADMMObjectiveTerm.createSquaredLinearLossTerm(
                    new Hyperplane<LocalVariable>(variables, coeffs, constant, consensus.length),
                    new FakeRule(weight, squared));
        } else if (squared && hinge) {
            term = ADMMObjectiveTerm.createSquaredHingeLossTerm(
                    new Hyperplane<LocalVariable>(variables, coeffs, constant, consensus.length),
                    new FakeRule(weight, squared));
        }

        term.minimize(stepSize, consensus);

        for (int i = 0; i < consensus.length; i++) {
            assertEquals(expected[i], variables[i].getValue(), 5e-5);
        }
    }
}
