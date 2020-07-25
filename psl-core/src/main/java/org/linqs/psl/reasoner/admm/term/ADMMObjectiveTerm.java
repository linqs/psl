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

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.model.rule.WeightedGroundRule;
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
 * Where y can be either local or concensus values.
 *
 * Minimizing a term comes down to minizing the weighted potential plus a squared norm:
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

    protected float weight;
    protected int size;

    protected float[] coefficients;
    protected LocalVariable[] variables;

    protected boolean squared;
    protected boolean hinge;

    protected float constant;

    /**
     * When non-null, this term must be a hard constraint.
     */
    protected FunctionComparator comparator;

    // The following variables are used when solving the objective function.
    // We keep them as member data to avoid multiple allocations.
    // However, they may be null when they don't apply to the specific type of term.

    /**
     * The optimizer considering only the consensus values (and not the constraint imposed by this local hyperplane).
     * This optimizer will be projected onto this hyperplane to minimize.
     */
    protected float[] consensusOptimizer;
    protected float[] unitNormal;

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
    private ADMMObjectiveTerm(Hyperplane<LocalVariable> hyperplane, GroundRule groundRule,
            boolean squared, boolean hinge,
            FunctionComparator comparator) {
        this.squared = squared;
        this.hinge = hinge;
        this.comparator = comparator;

        this.size = hyperplane.size();
        this.variables = hyperplane.getVariables();
        this.coefficients = hyperplane.getCoefficients();
        this.constant = hyperplane.getConstant();

        if (groundRule instanceof WeightedGroundRule) {
            this.weight = (float)((WeightedGroundRule)groundRule).getWeight();
        } else {
            this.weight = Float.POSITIVE_INFINITY;
        }

        TermType termType = getTermType();
        if (termType == TermType.HingeLossTerm || termType == TermType.LinearConstraintTerm) {
            initUnitNormal();
        }
    }

    public static ADMMObjectiveTerm createLinearConstraintTerm(Hyperplane<LocalVariable> hyperplane, GroundRule groundRule, FunctionComparator comparator) {
        return new ADMMObjectiveTerm(hyperplane, groundRule, false, false, comparator);
    }

    public static ADMMObjectiveTerm createLinearLossTerm(Hyperplane<LocalVariable> hyperplane, GroundRule groundRule) {
        return new ADMMObjectiveTerm(hyperplane, groundRule, false, false, null);
    }

    public static ADMMObjectiveTerm createHingeLossTerm(Hyperplane<LocalVariable> hyperplane, GroundRule groundRule) {
        return new ADMMObjectiveTerm(hyperplane,groundRule, false, true, null);
    }

    public static ADMMObjectiveTerm createSquaredLinearLossTerm(Hyperplane<LocalVariable> hyperplane, GroundRule groundRule) {
        return new ADMMObjectiveTerm(hyperplane, groundRule, true, false, null);
    }

    public static ADMMObjectiveTerm createSquaredHingeLossTerm(Hyperplane<LocalVariable> hyperplane, GroundRule groundRule) {
        return new ADMMObjectiveTerm(hyperplane, groundRule, true, true, null);
    }

    public void updateLagrange(float stepSize, float[] consensusValues) {
        // Use index instead of iterator here so we can see clear results in the profiler.
        // http://psy-lob-saw.blogspot.co.uk/2014/12/the-escape-of-arraylistiterator.html
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setLagrange(variable.getLagrange() + stepSize * (variable.getValue() - consensusValues[variable.getGlobalId()]));
        }
    }

    /**
     * Get the variables used in this term.
     * The caller should not modify the returned array, and should check size() for a reliable length.
     */
    public LocalVariable[] getVariables() {
        return variables;
    }

    /**
     * Get the number of variables in this term.
     */
    @Override
    public int size() {
        return size;
    }

    public boolean isConstraint() {
        return getTermType() == TermType.LinearConstraintTerm;
    }

    /**
     * Get the specific type of term this instance represents.
     */
    public TermType getTermType() {
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
        switch (getTermType()) {
            case LinearConstraintTerm:
                minimizeConstraint(stepSize, consensusValues);
                break;
            case LinearLossTerm:
                minimizeLinearLoss(stepSize, consensusValues);
                break;
            case HingeLossTerm:
                minimizeHingeLoss(stepSize, consensusValues);
                break;
            case SquaredLinearLossTerm:
                minimizeSquaredLinearLoss(stepSize, consensusValues);
                break;
            case SquaredHingeLossTerm:
                minimizeSquaredHingeLoss(stepSize, consensusValues);
                break;
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    /**
     * Evaluate this potential using the local variables.
     */
    public float evaluate() {
        switch (getTermType()) {
            case LinearConstraintTerm:
                return evaluateConstraint();
            case LinearLossTerm:
                return evaluateLinearLoss();
            case HingeLossTerm:
                return evaluateHingeLoss();
            case SquaredLinearLossTerm:
                return evaluateSquaredLinearLoss();
            case SquaredHingeLossTerm:
                return evaluateSquaredHingeLoss();
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    /**
     * Evaluate this potential using the given consensus values.
     */
    public float evaluate(float[] consensusValues) {
        switch (getTermType()) {
            case LinearConstraintTerm:
                return evaluateConstraint(consensusValues);
            case LinearLossTerm:
                return evaluateLinearLoss(consensusValues);
            case HingeLossTerm:
                return evaluateHingeLoss(consensusValues);
            case SquaredLinearLossTerm:
                return evaluateSquaredLinearLoss(consensusValues);
            case SquaredHingeLossTerm:
                return evaluateSquaredHingeLoss(consensusValues);
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
                LocalVariable variable = variables[i];
                variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
                total += coefficients[i] * variable.getValue();
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

    private void minimizeLinearLoss(float stepSize, float[] consensusValues) {
        // Linear losses can be directly minimized.

        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];

            float value =
                    consensusValues[variable.getGlobalId()]
                    - variable.getLagrange() / stepSize
                    - (weight * coefficients[i] / stepSize);

            variable.setValue(value);
        }
    }

    /**
     * weight * coefficients^T * local
     */
    private float evaluateLinearLoss() {
        return weight * computeInnerPotential();
    }

    /**
     * weight * coefficients^T * consensus
     */
    private float evaluateLinearLoss(float[] consensusValues) {
        return weight * computeInnerPotential(consensusValues);
    }

    // Functionality for hinge-loss terms.

    private void minimizeHingeLoss(float stepSize, float[] consensusValues) {
        // Look to see if the solution is in one of three sections (in increasing order of difficulty):
        // 1) The flat region.
        // 2) The linear region.
        // 3) The hinge point.

        // Take a gradient step and see if we are in the flat region.
        float total = 0.0f;
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
            total += (coefficients[i] * variable.getValue());
        }

        // If we are on the flat region, then we are at a solution.
        if (total <= constant) {
            return;
        }

        // Take a gradient step and see if we are in the linear region.
        total = 0.0f;
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setValue((consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize) - (weight * coefficients[i] / stepSize));
            total += coefficients[i] * variable.getValue();
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
    private float evaluateHingeLoss() {
        return weight * Math.max(0.0f, computeInnerPotential());
    }

    /**
     * weight * max(0.0, coefficients^T * consensus - constant)
     */
    private float evaluateHingeLoss(float[] consensusValues) {
        return weight * Math.max(0.0f, computeInnerPotential(consensusValues));
    }

    // Functionality for squared linear loss terms.

    public void minimizeSquaredLinearLoss(float stepSize, float[] consensusValues) {
        minWeightedSquaredHyperplane(stepSize, consensusValues);
    }

    /**
     * weight * (coefficients^T * local - constant)^2
     */
    public float evaluateSquaredLinearLoss() {
        return weight * (float)Math.pow(computeInnerPotential(), 2.0);
    }

    /**
     * weight * (coefficients^T * consensus - constant)^2
     */
    public float evaluateSquaredLinearLoss(float[] consensusValues) {
        return weight * (float)Math.pow(computeInnerPotential(consensusValues), 2.0);
    }

    // Functionality for squared hinge-loss terms.

    public void minimizeSquaredHingeLoss(float stepSize, float[] consensusValues) {
        // Take a gradient step and see if we are in the flat region.
        float total = 0.0f;
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
            total += coefficients[i] * variable.getValue();
        }

        // If we are on the flat region, then we are at a solution.
        if (total <= constant) {
            return;
        }

        // We are in the quadratic region, so solve that to find a solution.
        minWeightedSquaredHyperplane(stepSize, consensusValues);
    }

    /**
     * weight * [max(0, coefficients^T * local - constant)]^2
     */
    public float evaluateSquaredHingeLoss() {
        return weight * (float)Math.pow(Math.max(0.0f, computeInnerPotential()), 2.0);
    }

    /**
     * weight * [max(0, coefficients^T * consensus - constant)]^2
     */
    public float evaluateSquaredHingeLoss(float[] consensusValues) {
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
    protected float computeInnerPotential() {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        return value - constant;
    }

    /**
     * coefficients^T * consensus - constant
     */
    protected float computeInnerPotential(float[] consensusValues) {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * consensusValues[variables[i].getGlobalId()];
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
    protected void project(float stepSize, float[] consensusValues) {
        // When there is only one variable, there is only one answer.
        // This answer must satisfy the constraint.
        if (size == 1) {
            variables[0].setValue(constant / coefficients[0]);
            return;
        }

        // ConsensusOptimizer = Projection + (multiplier)(unitNormal).
        // Note that the projection is in this hyperplane and therefore orthogonal to the unitNormal.
        // So, these two orthogonal components can makeup the consensusOptimizer.

        // Get the min w.r.t. to the consensus values.
        // This is done by taking a step according to the lagrange.
        for (int i = 0; i < size; i++) {
            consensusOptimizer[i] = consensusValues[variables[i].getGlobalId()] - variables[i].getLagrange() / stepSize;
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
            variables[i].setValue(consensusOptimizer[i] - multiplier * unitNormal[i]);
        }
    }

    /**
     * Minimizes the term as a weighted, squared hyperplane.
     * This function to minimize takes the form:
     * weight * [coefficients^T * local - constant]^2 + (stepsize / 2) * || local - consensus + lagrange / stepsize ||_2^2.
     *
     * The result of the minimization will be stored in the local variables.
     */
    protected void minWeightedSquaredHyperplane(float stepSize, float[] consensusValues) {
        // Different solving methods will be used depending on the size of the hyperplane.

        // Pre-load the local variable with a term that is common in all the solutions:
        // stepsize * consensus - lagrange + (2 * weight * coefficients * constant).
        for (int i = 0; i < size; i++) {
            float value =
                    stepSize * consensusValues[variables[i].getGlobalId()] - variables[i].getLagrange()
                    + 2.0f * weight * coefficients[i] * constant;

            variables[i].setValue(value);
        }

        // Hyperplanes with only one variable can be solved trivially.
        if (size == 1) {
            LocalVariable variable = variables[0];
            float coefficient = coefficients[0];

            variable.setValue(variable.getValue() / (2.0f * weight * coefficient * coefficient + stepSize));

            return;
        }

        // Hyperplanes with only two variables can be solved fairly easily.
        if (size == 2) {
            LocalVariable variable0 = variables[0];
            LocalVariable variable1 = variables[1];
            float coefficient0 = coefficients[0];
            float coefficient1 = coefficients[1];

            float a0 = 2.0f * weight * coefficient0 * coefficient0 + stepSize;
            float b1 = 2.0f * weight * coefficient1 * coefficient1 + stepSize;
            float a1b0 = 2.0f * weight * coefficient0 * coefficient1;

            variable1.setValue(variable1.getValue() - a1b0 * variable0.getValue() / a0);
            variable1.setValue(variable1.getValue() / (b1 - a1b0 * a1b0 / a0));

            variable0.setValue((variable0.getValue() - a1b0 * variable1.getValue()) / a0);

            return;
        }

        // In the case of larger hyperplanes, we can use a Cholesky decomposition to minimize.

        FloatMatrix lowerTriangle = fetchLowerTriangle(stepSize);

        for (int i = 0; i < size; i++) {
            float newValue = variables[i].getValue();

            for (int j = 0; j < i; j++) {
                newValue -= lowerTriangle.get(i, j) * variables[j].getValue();
            }

            variables[i].setValue(newValue / lowerTriangle.get(i, i));
        }

        for (int i = size - 1; i >= 0; i--) {
            float newValue = variables[i].getValue();

            for (int j = size - 1; j > i; j--) {
                newValue -= lowerTriangle.get(j, i) * variables[j].getValue();
            }

            variables[i].setValue(newValue / lowerTriangle.get(i, i));
        }
    }

    /**
     * Get the lower triangle if it already exists, compute and cache it otherwise.
     */
    private FloatMatrix fetchLowerTriangle(float stepSize) {
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
        return computeLowerTriangle(stepSize, hash);
    }

    /**
     * Actually copute the lower triangle and store it in the cache.
     * There is one triangle per rule, so most ground rules will just pull off the same cache.
     */
    private synchronized FloatMatrix computeLowerTriangle(float stepSize, int hash) {
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
}
