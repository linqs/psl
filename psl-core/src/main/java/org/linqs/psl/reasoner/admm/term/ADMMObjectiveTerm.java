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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermState;
import org.linqs.psl.util.FloatMatrix;
import org.linqs.psl.util.HashCode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Objective term for an ADMMReasoner.
 *
 * This general class covers five specific types of terms:
 * 1) Linear Constraint Terms:   (0 if coefficients^T * y [comparator] constant) or (infinity otherwise)
 * 2) Linear Loss Terms:         weight * coefficients^T * y
 * 3) Hinge-Loss Terms:          weight * max(0, coefficients^T * y - constant).
 * 5) Squared Linear Loss Terms: weight * [coefficients^T * y - constant]^2
 * 5) Squared Hinge-Loss Terms:  weight * [max(0, coefficients^T * y - constant)]^2.
 * Where y can be either local or consensus values.
 *
 * Minimizing a term comes down to minimizing the weighted potential plus a squared norm:
 * weight * [max(0, coefficients^T * local - constant)]^power + (stepsize / 2) * || local - consensus + lagrange / stepsize ||_2^2.
 *
 * The reason these terms are housed in a single class instead of subclasses is for performance
 * in streaming settings where terms must be quickly serialized and deserialized.
 *
 * All coefficients must be non-zero.
 */
public class ADMMObjectiveTerm extends ReasonerTerm {
    private final float[] variableValues;
    private final float[] variableLagranges;

    // The following variables are used when solving the objective function.
    // We keep them as member data to avoid multiple allocations.
    // However, they may be null when they don't apply to the specific type of term.

    /**
     * The optimizer considering only the consensus values (and not the constraint imposed by this local hyperplane).
     * This optimizer will be projected onto this hyperplane to minimize.
     */
    private float[] consensusOptimizer;
    private float[] unitNormal;

    /**
     * Cache the matrices we will use to minimize the terms.
     * Since the matrix (which is a lower triangle) is based on the term's weights and coefficients,
     * there will typically be a lot of redundancy between rules.
     * What we are caching, specifically, is the lower triangle in the Cholesky decomposition of the symmetric matrix:
     * M[i, j] = 2 * weight * coefficients[i] * coefficients[j]
     */
    private static final Map<Integer, FloatMatrix> lowerTriangleCache = new HashMap<Integer, FloatMatrix>();

    /**
     * Construct an ADMM objective term by taking ownership of the hyperplane and all members of it.
     * Use the static creation methods.
     */
    public ADMMObjectiveTerm(Hyperplane hyperplane, Rule rule,
                             boolean squared, boolean hinge,
                             FunctionComparator comparator) {
        super(hyperplane, rule, squared, hinge, comparator);

        variableValues = new float[size];
        variableLagranges = new float[size];

        // We assume all observations have been merged.
        GroundAtom[] consensusVariables = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            variableValues[i] = consensusVariables[i].getValue();
            variableLagranges[i] = 0.0f;
        }

        if (termType == TermType.HingeLossTerm || termType == TermType.LinearConstraintTerm) {
            initUnitNormal();
        }
    }

    public ADMMObjectiveTerm(short size, float[] coefficients, float constant, int[] atomIndexes,
                             Rule rule, boolean squared, boolean hinge, FunctionComparator comparator,
                             float[] variableValues, float[] variableLagranges) {
        super(size, coefficients, constant, atomIndexes, rule, squared, hinge, comparator);

        this.variableValues = Arrays.copyOf(variableValues, size);
        this.variableLagranges = Arrays.copyOf(variableLagranges, size);

        if (termType == TermType.HingeLossTerm || termType == TermType.LinearConstraintTerm) {
            initUnitNormal();
        }
    }

    @Override
    public ADMMObjectiveTerm copy() {
        return new ADMMObjectiveTerm(size, coefficients, constant, atomIndexes,
                rule, squared, hinge, comparator, variableValues, variableLagranges);
    }

    public static ADMMObjectiveTerm createLinearConstraintTerm(Hyperplane hyperplane, Rule rule, FunctionComparator comparator) {
        return new ADMMObjectiveTerm(hyperplane, rule, false, false, comparator);
    }

    public static ADMMObjectiveTerm createLinearLossTerm(Hyperplane hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane, rule, false, false, null);
    }

    public static ADMMObjectiveTerm createHingeLossTerm(Hyperplane hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane,rule, false, true, null);
    }

    public static ADMMObjectiveTerm createSquaredLinearLossTerm(Hyperplane hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane, rule, true, false, null);
    }

    public static ADMMObjectiveTerm createSquaredHingeLossTerm(Hyperplane hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane, rule, true, true, null);
    }

    public void updateLagrange(float stepSize, float[] consensusValues) {
        for (int i = 0; i < size; i++) {
            variableLagranges[i] += stepSize * (variableValues[i] - consensusValues[atomIndexes[i]]);
        }
    }

    public void setLocalValue(short index, float value, float lagrange) {
        variableValues[index] = value;
        variableLagranges[index] = lagrange;
    }

    public float getVariableValue(short index) {
        return variableValues[index];
    }

    public float getVariableLagrange(short index) {
        return variableLagranges[index];
    }

    /**
     * Modify the local variables to minimize this term (within the bounds of the step size).
     */
    public void minimize(float stepSize, float[] consensusValues) {
        float weight = getWeight();

        switch (termType) {
            case LinearConstraintTerm:
                minimizeConstraint(stepSize, consensusValues);
                break;
            case LinearLossTerm:
                minimizeLinearLoss(stepSize, weight, consensusValues);
                break;
            case HingeLossTerm:
                minimizeHingeLoss(stepSize, weight, consensusValues);
                break;
            case SquaredLinearLossTerm:
                minimizeSquaredLinearLoss(stepSize, weight, consensusValues);
                break;
            case SquaredHingeLossTerm:
                minimizeSquaredHingeLoss(stepSize, weight, consensusValues);
                break;
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    // Functionality for constraint terms.

    private void minimizeConstraint(float stepSize, float[] consensusValues) {
        // If the constraint is an inequality, then we may be able to solve without projection.
        if (!comparator.equals(FunctionComparator.EQ)) {
            float total = 0.0f;

            // Take the lagrange step and see if that is the solution.
            for (int i = 0; i < size; i++) {
                float newValue = consensusValues[atomIndexes[i]] - variableLagranges[i] / stepSize;
                variableValues[i] = newValue;
                total += coefficients[i] * newValue;
            }

            // If the constraint is satisfied, them we are done.
            if ((comparator.equals(FunctionComparator.LTE) && total <= constant)
                    || (comparator.equals(FunctionComparator.GTE) && total >= constant)) {
                return;
            }
        }

        // If the naive minimization didn't work, or if it's an equality constraint,
        // then project onto the hyperplane.
        project(stepSize, consensusValues);
    }

    // Functionality for linear loss terms.

    private void minimizeLinearLoss(float stepSize, float weight, float[] consensusValues) {
        // Linear losses can be directly minimized.
        for (int i = 0; i < size; i++) {
            float value =
                    consensusValues[atomIndexes[i]]
                    - variableLagranges[i] / stepSize
                    - (weight * coefficients[i] / stepSize);

            variableValues[i] = value;
        }
    }

    // Functionality for hinge-loss terms.

    private void minimizeHingeLoss(float stepSize, float weight, float[] consensusValues) {
        // Look to see if the solution is in one of three sections (in increasing order of difficulty):
        // 1) The flat region.
        // 2) The linear region.
        // 3) The hinge point.

        // Take a gradient step and see if we are in the flat region.
        float total = 0.0f;
        for (int i = 0; i < size; i++) {
            float newValue = consensusValues[atomIndexes[i]] - variableLagranges[i] / stepSize;
            variableValues[i] = newValue;
            total += (coefficients[i] * newValue);
        }

        // If we are on the flat region, then we are at a solution.
        if (total <= constant) {
            return;
        }

        // Take a gradient step and see if we are in the linear region.
        total = 0.0f;
        for (int i = 0; i < size; i++) {
            float newValue = (consensusValues[atomIndexes[i]] - variableLagranges[i] / stepSize) - (weight * coefficients[i] / stepSize);
            variableValues[i] = newValue;
            total += (coefficients[i] * newValue);
        }

        // If we are in the linear region, then we are at a solution.
        if (total >= constant) {
            return;
        }

        // We are on the hinge, project to find the solution.
        project(stepSize, consensusValues);
    }

    // Functionality for squared linear loss terms.

    private void minimizeSquaredLinearLoss(float stepSize, float weight, float[] consensusValues) {
        minWeightedSquaredHyperplane(stepSize, weight, consensusValues);
    }

    // Functionality for squared hinge-loss terms.

    private void minimizeSquaredHingeLoss(float stepSize, float weight, float[] consensusValues) {
        // Take a gradient step and see if we are in the flat region.
        float total = 0.0f;
        for (int i = 0; i < size; i++) {
            float newValue = consensusValues[atomIndexes[i]] - variableLagranges[i] / stepSize;
            variableValues[i] = newValue;
            total += (coefficients[i] * newValue);
        }

        // If we are on the flat region, then we are at a solution.
        if (total <= constant) {
            return;
        }

        // We are in the quadratic region, so solve that to find a solution.
        minWeightedSquaredHyperplane(stepSize, weight, consensusValues);
    }

    // General Utilities

    private void initUnitNormal() {
        // If the hyperplane only has one random variable, we can take shortcuts solving it.
        if (size == 1) {
            consensusOptimizer = null;
            unitNormal = null;
            return;
        }

        consensusOptimizer = new float[size];
        unitNormal = new float[size];

        float length = 0.0f;
        for (int i = 0; i < size; i++) {
            length += coefficients[i] * coefficients[i];
        }
        length = (float)Math.sqrt(length);

        for (int i = 0; i < size; i++) {
            unitNormal[i] = coefficients[i] / length;
        }
    }

    /**
     * Project the solution to the consensus problem onto this hyperplane,
     * thereby finding the min solution.
     * The consensus problem is:
     * [argmin_local stepSize / 2 * ||local - consensus + lagrange / stepSize ||_2^2],
     * while this hyperplane is: [coefficients^T * local = constant].
     * The result of the projection is stored in the local variables.
     */
    private void project(float stepSize, float[] consensusValues) {
        // When there is only one variable, there is only one answer.
        // This answer must satisfy the constraint.
        if (size == 1) {
            variableValues[0] = constant / coefficients[0];
            return;
        }

        // ConsensusOptimizer = Projection + (multiplier)(unitNormal).
        // Note that the projection is in this hyperplane and therefore orthogonal to the unitNormal.
        // So, these two orthogonal components can makeup the consensusOptimizer.

        // Get the min w.r.t. to the consensus values.
        // This is done by taking a step according to the lagrange.
        for (int i = 0; i < size; i++) {
            consensusOptimizer[i] = consensusValues[atomIndexes[i]] - variableLagranges[i] / stepSize;
        }

        // Get the length of the normal.
        // Any matching index can be used to compute the length.
        float length = coefficients[0] / unitNormal[0];

        // Get the multiplier to the unit normal that properly scales it to match the consensus optimizer.
        // We start with the constant, because it is actually part of our vector,
        // but since it always has a 1 cofficient we treat it differently.
        float multiplier = -1.0f * constant / length;
        for (int i = 0; i < size; i++) {
            multiplier += consensusOptimizer[i] * unitNormal[i];
        }

        // Projection = ConsensusOptimizer - (multiplier)(unitNormal).
        for (int i = 0; i < size; i++) {
            variableValues[i] = consensusOptimizer[i] - multiplier * unitNormal[i];
        }
    }

    /**
     * Minimizes the term as a weighted, squared hyperplane.
     * The function to minimize takes the form:
     * weight * [coefficients^T * local - constant]^2 + (stepsize / 2) * || local - consensus + lagrange / stepsize ||_2^2.
     *
     * The result of the minimization will be stored in the local variables.
     */
    private void minWeightedSquaredHyperplane(float stepSize, float weight, float[] consensusValues) {
        // Different solving methods will be used depending on the size of the hyperplane.

        // Pre-load the local variable with a term that is common in all the solutions:
        // stepsize * consensus - lagrange + (2 * weight * coefficients * constant).
        for (int i = 0; i < size; i++) {
            variableValues[i] =
                    stepSize * consensusValues[atomIndexes[i]] - variableLagranges[i]
                    + 2.0f * weight * coefficients[i] * constant;
        }

        // Hyperplanes with only one variable can be solved trivially.
        if (size == 1) {
            variableValues[0] /= 2.0f * weight * coefficients[0] * coefficients[0] + stepSize;
            return;
        }

        // Hyperplanes with only two variables can be solved fairly easily.
        if (size == 2) {
            float variableValue0 = variableValues[0];
            float variableValue1 = variableValues[1];

            float coefficient0 = coefficients[0];
            float coefficient1 = coefficients[1];

            float a0 = 2.0f * weight * coefficient0 * coefficient0 + stepSize;
            float b1 = 2.0f * weight * coefficient1 * coefficient1 + stepSize;
            float a1b0 = 2.0f * weight * coefficient0 * coefficient1;

            variableValue1 = variableValue1 - a1b0 * variableValue0 / a0;
            variableValue1 = variableValue1 / (b1 - a1b0 * a1b0 / a0);

            variableValue0 = (variableValue0 - a1b0 * variableValue1) / a0;

            variableValues[0] = variableValue0;
            variableValues[1] = variableValue1;

            return;
        }

        // In the case of larger hyperplanes, we can use a Cholesky decomposition to minimize.

        FloatMatrix lowerTriangle = fetchLowerTriangle(stepSize, weight);

        for (int i = 0; i < size; i++) {
            float newValue = variableValues[i];

            for (int j = 0; j < i; j++) {
                newValue -= lowerTriangle.get(i, j) * variableValues[j];
            }

            variableValues[i] = newValue / lowerTriangle.get(i, i);
        }

        for (int i = size - 1; i >= 0; i--) {
            float newValue = variableValues[i];

            for (int j = size - 1; j > i; j--) {
                newValue -= lowerTriangle.get(j, i) * variableValues[j];
            }

            variableValues[i] = newValue / lowerTriangle.get(i, i);
        }
    }

    /**
     * Get the lower triangle if it already exists, compute and cache it otherwise.
     */
    private FloatMatrix fetchLowerTriangle(float stepSize, float weight) {
        int hash = HashCode.build(weight);
        hash = HashCode.build(hash, stepSize);
        for (int i = 0; i < size; i++) {
            hash = HashCode.build(hash, coefficients[i]);
        }

        // First check the cache.
        // Typically, each rule (not ground rule) will have its own lowerTriangle.
        FloatMatrix lowerTriangle = lowerTriangleCache.get(hash);
        if (lowerTriangle != null) {
            return lowerTriangle;
        }

        // If we didn't find it, then synchronize and compute it on this thread.
        return computeLowerTriangle(stepSize, weight, hash);
    }

    /**
     * Actually copute the lower triangle and store it in the cache.
     * There is one triangle per rule, so most ground rules will just pull off the same cache.
     */
    private synchronized FloatMatrix computeLowerTriangle(float stepSize, float weight, int hash) {
        // There is still a race condition in the map fetch before getting here,
        // so we will check one more time while synchronized.
        if (lowerTriangleCache.containsKey(hash)) {
            return lowerTriangleCache.get(hash);
        }

        float coefficient = 0.0f;

        FloatMatrix matrix = FloatMatrix.zeroes(size, size);

        for (int i = 0; i < size; i++) {
            // Note that the matrix is symmetric.
            for (int j = i; j < size; j++) {
                if (i == j) {
                    coefficient = 2.0f * weight * coefficients[i] * coefficients[i] + stepSize;
                    matrix.set(i, i, coefficient);
                } else {
                    coefficient = 2.0f * weight * coefficients[i] * coefficients[j];
                    matrix.set(i, j, coefficient);
                    matrix.set(j, i, coefficient);
                }
            }
        }

        matrix.choleskyDecomposition(true);
        lowerTriangleCache.put(hash, matrix);

        return matrix;
    }

    @Override
    public void loadState(TermState termState) {
        assert termState instanceof ADMMObjectiveTermState;
        ADMMObjectiveTermState objectiveTermState = (ADMMObjectiveTermState)termState;

        System.arraycopy(objectiveTermState.variableValues, 0, variableValues, 0, variableValues.length);
        System.arraycopy(objectiveTermState.variableLagranges, 0, variableLagranges, 0, variableLagranges.length);
    }

    @Override
    public TermState saveState() {
        return new ADMMObjectiveTermState(variableValues, variableLagranges);
    }

    @Override
    public void saveState(TermState termState) {
        assert termState instanceof ADMMObjectiveTermState;
        ADMMObjectiveTermState objectiveTermState = (ADMMObjectiveTermState)termState;

        System.arraycopy(variableValues, 0, objectiveTermState.variableValues, 0, variableValues.length);
        System.arraycopy(variableLagranges, 0, objectiveTermState.variableLagranges, 0, variableLagranges.length);
    }

    public static final class ADMMObjectiveTermState extends TermState {
        public float[] variableValues;
        public float[] variableLagranges;

        public ADMMObjectiveTermState(float[] variableValues, float[] variableLagranges) {
            this.variableValues = Arrays.copyOf(variableValues, variableValues.length);
            this.variableLagranges = Arrays.copyOf(variableLagranges, variableLagranges.length);
        }
    }
}
