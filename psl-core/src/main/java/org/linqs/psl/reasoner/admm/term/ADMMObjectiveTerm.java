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
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.FloatMatrix;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.MathUtils;

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
public class ADMMObjectiveTerm implements ReasonerTerm {
    /**
     * The specific type of term represented by this instance.
     */
    public static enum TermType {
        LinearConstraintTerm,
        LinearLossTerm,
        HingeLossTerm,
        SquaredLinearLossTerm,
        SquaredHingeLossTerm,
    }

    private final TermType termType;

    private final Rule rule;

    private int size;

    private float[] coefficients;

    private float[] variableValues;
    private float[] variableLagranges;
    private int[] consensusIndexes;

    private boolean squared;
    private boolean hinge;

    private float constant;

    /**
     * When non-null, this term must be a hard constraint.
     */
    private FunctionComparator comparator;

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
    private static Map<Integer, FloatMatrix> lowerTriangleCache = new HashMap<Integer, FloatMatrix>();

    /**
     * Construct an ADMM objective term by taking ownership of the hyperplane and all members of it.
     * Use the static creation methods.
     */
    private ADMMObjectiveTerm(Hyperplane hyperplane, Rule rule,
            boolean squared, boolean hinge,
            FunctionComparator comparator) {
        this.rule = rule;

        this.squared = squared;
        this.hinge = hinge;
        this.comparator = comparator;

        this.size = hyperplane.size();
        this.coefficients = hyperplane.getCoefficients();
        this.constant = hyperplane.getConstant();

        variableValues = new float[size];
        variableLagranges = new float[size];
        consensusIndexes = new int[size];

        // We assume all observations have been merged.
        GroundAtom[] consensusVariables = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            variableValues[i] = consensusVariables[i].getValue();
            variableLagranges[i] = 0.0f;
            consensusIndexes[i] = consensusVariables[i].getIndex();
        }

        termType = getTermType();
        if (termType == TermType.HingeLossTerm || termType == TermType.LinearConstraintTerm) {
            initUnitNormal();
        }
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
            variableLagranges[i] += stepSize * (variableValues[i] - consensusValues[consensusIndexes[i]]);
        }
    }

    /**
     * Get the number of variables in this term.
     */
    @Override
    public int size() {
        return size;
    }

    @Override
    public Rule getRule() {
        return rule;
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

    public boolean isConstraint() {
        return termType == TermType.LinearConstraintTerm;
    }

    /**
     * Get the specific type of term this instance represents.
     */
    private TermType getTermType() {
        if (comparator != null) {
            return TermType.LinearConstraintTerm;
        } else if (!squared && !hinge) {
            return TermType.LinearLossTerm;
        } else if (!squared && hinge) {
            return TermType.HingeLossTerm;
        } else if (squared && !hinge) {
            return TermType.SquaredLinearLossTerm;
        } else if (squared && hinge) {
            return TermType.SquaredHingeLossTerm;
        }

        throw new IllegalStateException("Unknown term type.");
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

    /**
     * Evaluate this potential using the local variables.
     */
    public float evaluate() {
        float weight = getWeight();

        switch (termType) {
            case LinearConstraintTerm:
                return evaluateConstraint();
            case LinearLossTerm:
                return evaluateLinearLoss(weight);
            case HingeLossTerm:
                return evaluateHingeLoss(weight);
            case SquaredLinearLossTerm:
                return evaluateSquaredLinearLoss(weight);
            case SquaredHingeLossTerm:
                return evaluateSquaredHingeLoss(weight);
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    /**
     * Evaluate this potential using the given consensus values.
     */
    @Override
    public float evaluate(float[] consensusValues) {
        return getWeight() * evaluateIncompatibility(consensusValues);
    }

    /**
     * Evaluate this potential's incompatibility using the given consensus values.
     */
    @Override
    public float evaluateIncompatibility(float[] consensusValues) {
        float weight = 1.0f;

        switch (termType) {
            case LinearConstraintTerm:
                return evaluateConstraint(consensusValues);
            case LinearLossTerm:
                return evaluateLinearLoss(weight, consensusValues);
            case HingeLossTerm:
                return evaluateHingeLoss(weight, consensusValues);
            case SquaredLinearLossTerm:
                return evaluateSquaredLinearLoss(weight, consensusValues);
            case SquaredHingeLossTerm:
                return evaluateSquaredHingeLoss(weight, consensusValues);
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
                float newValue = consensusValues[consensusIndexes[i]] - variableLagranges[i] / stepSize;
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

    private float evaluateConstraint() {
        return evaluateConstraint(null);
    }

    /**
     * Evalauate to zero if the constraint is satisfied, infinity otherwise.
     * if (coefficients^T * y [comparator] constant) { return 0.0 }
     * else { return infinity }
     */
    private float evaluateConstraint(float[] consensusValues) {
        float value = 0.0f;
        if (consensusValues == null) {
            value = computeInnerPotential();
        } else {
            value = computeInnerPotential(consensusValues);
        }

        if (comparator.equals(FunctionComparator.EQ)) {
            if (MathUtils.isZero(value, MathUtils.RELAXED_EPSILON)) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else if (comparator.equals(FunctionComparator.LTE)) {
            if (value <= 0.0f) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else if (comparator.equals(FunctionComparator.GTE)) {
            if (value >= 0.0f) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else {
            throw new IllegalStateException("Unknown comparison function.");
        }
    }

    // Functionality for linear loss terms.

    private void minimizeLinearLoss(float stepSize, float weight, float[] consensusValues) {
        // Linear losses can be directly minimized.
        for (int i = 0; i < size; i++) {
            float value =
                    consensusValues[consensusIndexes[i]]
                    - variableLagranges[i] / stepSize
                    - (weight * coefficients[i] / stepSize);

            variableValues[i] = value;
        }
    }

    /**
     * weight * coefficients^T * local
     */
    private float evaluateLinearLoss(float weight) {
        return weight * computeInnerPotential();
    }

    /**
     * weight * coefficients^T * consensus
     */
    private float evaluateLinearLoss(float weight, float[] consensusValues) {
        return weight * computeInnerPotential(consensusValues);
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
            float newValue = consensusValues[consensusIndexes[i]] - variableLagranges[i] / stepSize;
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
            float newValue = (consensusValues[consensusIndexes[i]] - variableLagranges[i] / stepSize) - (weight * coefficients[i] / stepSize);
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

    /**
     * weight * max(0.0, coefficients^T * local - constant)
     */
    private float evaluateHingeLoss(float weight) {
        return weight * Math.max(0.0f, computeInnerPotential());
    }

    /**
     * weight * max(0.0, coefficients^T * consensus - constant)
     */
    private float evaluateHingeLoss(float weight, float[] consensusValues) {
        return weight * Math.max(0.0f, computeInnerPotential(consensusValues));
    }

    // Functionality for squared linear loss terms.

    private void minimizeSquaredLinearLoss(float stepSize, float weight, float[] consensusValues) {
        minWeightedSquaredHyperplane(stepSize, weight, consensusValues);
    }

    /**
     * weight * (coefficients^T * local - constant)^2
     */
    private float evaluateSquaredLinearLoss(float weight) {
        return weight * (float)Math.pow(computeInnerPotential(), 2.0);
    }

    /**
     * weight * (coefficients^T * consensus - constant)^2
     */
    private float evaluateSquaredLinearLoss(float weight, float[] consensusValues) {
        return weight * (float)Math.pow(computeInnerPotential(consensusValues), 2.0);
    }

    // Functionality for squared hinge-loss terms.

    private void minimizeSquaredHingeLoss(float stepSize, float weight, float[] consensusValues) {
        // Take a gradient step and see if we are in the flat region.
        float total = 0.0f;
        for (int i = 0; i < size; i++) {
            float newValue = consensusValues[consensusIndexes[i]] - variableLagranges[i] / stepSize;
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

    /**
     * weight * [max(0, coefficients^T * local - constant)]^2
     */
    private float evaluateSquaredHingeLoss(float weight) {
        return weight * (float)Math.pow(Math.max(0.0f, computeInnerPotential()), 2.0);
    }

    /**
     * weight * [max(0, coefficients^T * consensus - constant)]^2
     */
    private float evaluateSquaredHingeLoss(float weight, float[] consensusValues) {
        return weight * (float)Math.pow(Math.max(0.0f, computeInnerPotential(consensusValues)), 2.0);
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
     * coefficients^T * local - constant
     */
    private float computeInnerPotential() {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variableValues[i];
        }

        return value - constant;
    }

    /**
     * coefficients^T * consensus - constant
     */
    private float computeInnerPotential(float[] consensusValues) {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * consensusValues[consensusIndexes[i]];
        }

        return value - constant;
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
            consensusOptimizer[i] = consensusValues[consensusIndexes[i]] - variableLagranges[i] / stepSize;
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
                    stepSize * consensusValues[consensusIndexes[i]] - variableLagranges[i]
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

    private float getWeight() {
        if (rule != null && rule.isWeighted()) {
            return ((WeightedRule)rule).getWeight();
        }

        return Float.POSITIVE_INFINITY;
    }
}
