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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.rule.FakeRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.FloatMatrix;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.RandUtils;

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
        DeterCollectiveTerm,
        DeterIndependentTerm,
    }

    protected final TermType termType;

    protected final Rule rule;

    protected int size;

    private final float[] coefficients;
    private final LocalAtom[] localAtoms;

    private final boolean squared;
    private final boolean hinge;

    /**
     * Used as either the deter epsilon (when DeterCollectiveTerm)
     * or as the deter value (when DeterIntependentTerm).
     */
    private final float deterConstant;

    private float constant;

    /**
     * When non-null, this term must be a hard constraint.
     */
    private final FunctionComparator comparator;

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
    private ADMMObjectiveTerm(Hyperplane<LocalAtom> hyperplane, Rule rule,
            boolean squared, boolean hinge,
            boolean collectiveDeter, float deterConstant,
            FunctionComparator comparator) {
        this.rule = rule;

        this.squared = squared;
        this.hinge = hinge;
        this.deterConstant = deterConstant;
        this.comparator = comparator;

        this.size = hyperplane.size();
        this.localAtoms = hyperplane.getVariables();
        this.coefficients = hyperplane.getCoefficients();
        this.constant = hyperplane.getConstant();

        termType = getTermType(collectiveDeter);
        if (termType == TermType.HingeLossTerm || termType == TermType.LinearConstraintTerm) {
            initUnitNormal();
        }
    }

    public static ADMMObjectiveTerm createLinearConstraintTerm(Hyperplane<LocalAtom> hyperplane, Rule rule, FunctionComparator comparator) {
        return new ADMMObjectiveTerm(hyperplane, rule, false, false, false, 0.0f, comparator);
    }

    public static ADMMObjectiveTerm createLinearLossTerm(Hyperplane<LocalAtom> hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane, rule, false, false, false, 0.0f, null);
    }

    public static ADMMObjectiveTerm createHingeLossTerm(Hyperplane<LocalAtom> hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane,rule, false, true, false, 0.0f, null);
    }

    public static ADMMObjectiveTerm createSquaredLinearLossTerm(Hyperplane<LocalAtom> hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane, rule, true, false, false, 0.0f, null);
    }

    public static ADMMObjectiveTerm createSquaredHingeLossTerm(Hyperplane<LocalAtom> hyperplane, Rule rule) {
        return new ADMMObjectiveTerm(hyperplane, rule, true, true, false, 0.0f, null);
    }

    public static ADMMObjectiveTerm createCollectiveDeterTerm(Hyperplane<LocalAtom> hyperplane, float deterWeight, float deterConstant) {
        return new ADMMObjectiveTerm(hyperplane, new FakeRule(deterWeight, false), false, false, true, deterConstant, null);
    }

    public static ADMMObjectiveTerm createIndependentDeterTerm(Hyperplane<LocalAtom> hyperplane, float deterWeight, float deterConstant) {
        return new ADMMObjectiveTerm(hyperplane, new FakeRule(deterWeight, false), false, false, false, deterConstant, null);
    }

    public void updateLagrange(float stepSize, float[] consensusValues) {
        for (int i = 0; i < size; i++) {
            LocalAtom localAtom = localAtoms[i];
            localAtom.setLagrange(localAtom.getLagrange() + stepSize * (localAtom.getValue() - consensusValues[localAtom.getGlobalId()]));
        }
    }

    /**
     * Get the atoms used in this term.
     * The caller should not modify the returned array, and should check size() for a reliable length.
     */
    public LocalAtom[] getLocalAtoms() {
        return localAtoms;
    }

    /**
     * Get the number of atoms in this term.
     */
    @Override
    public int size() {
        return size;
    }

    @Override
    public void adjustConstant(float oldValue, float newValue) {
        constant = constant - oldValue + newValue;
    }

    public boolean isConstraint() {
        return termType == TermType.LinearConstraintTerm;
    }

    @Override
    public boolean isConvex() {
        return termType != TermType.DeterCollectiveTerm && termType != TermType.DeterIndependentTerm;
    }

    /**
     * Get the specific type of term this instance represents.
     */
    private TermType getTermType(boolean collectiveDeter) {
        if (comparator != null) {
            return TermType.LinearConstraintTerm;
        } else if (!MathUtils.isZero(deterConstant)) {
            if (collectiveDeter) {
                return TermType.DeterCollectiveTerm;
            } else {
                return TermType.DeterIndependentTerm;
            }
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
     * Modify the LocalAtoms to minimize this term (within the bounds of the step size).
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
            case DeterCollectiveTerm:
                minimizeCollectiveDeter(stepSize, weight, consensusValues);
                break;
            case DeterIndependentTerm:
                minimizeIndependentDeter(stepSize, weight, consensusValues);
                break;
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    /**
     * Evaluate this potential using the LocalAtoms.
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
            case DeterCollectiveTerm:
                return evaluateCollectiveDeter(weight);
            case DeterIndependentTerm:
                return evaluateIndependentDeter(weight);
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    /**
     * Evaluate this potential using the given consensus values.
     */
    public float evaluate(float[] consensusValues) {
        float weight = getWeight();

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
            case DeterCollectiveTerm:
                return evaluateCollectiveDeter(weight, consensusValues);
            case DeterIndependentTerm:
                return evaluateIndependentDeter(weight, consensusValues);
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
                LocalAtom localAtom = localAtoms[i];
                localAtom.setValue(consensusValues[localAtom.getGlobalId()] - localAtom.getLagrange() / stepSize);
                total += coefficients[i] * localAtom.getValue();
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
            LocalAtom localAtom = localAtoms[i];

            float value =
                    consensusValues[localAtom.getGlobalId()]
                    - localAtom.getLagrange() / stepSize
                    - (weight * coefficients[i] / stepSize);

            localAtom.setValue(value);
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
            LocalAtom localAtom = localAtoms[i];
            localAtom.setValue(consensusValues[localAtom.getGlobalId()] - localAtom.getLagrange() / stepSize);
            total += (coefficients[i] * localAtom.getValue());
        }

        // If we are on the flat region, then we are at a solution.
        if (total <= constant) {
            return;
        }

        // Take a gradient step and see if we are in the linear region.
        total = 0.0f;
        for (int i = 0; i < size; i++) {
            LocalAtom localAtom = localAtoms[i];
            localAtom.setValue((consensusValues[localAtom.getGlobalId()] - localAtom.getLagrange() / stepSize) - (weight * coefficients[i] / stepSize));
            total += coefficients[i] * localAtom.getValue();
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
            LocalAtom localAtom = localAtoms[i];
            localAtom.setValue(consensusValues[localAtom.getGlobalId()] - localAtom.getLagrange() / stepSize);
            total += coefficients[i] * localAtom.getValue();
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

    // Functionality for collective deter terms.

    private void minimizeCollectiveDeter(float stepSize, float weight, float[] consensusValues) {
        // TODO(eriq): This minimization is naive.
        float deterValue = 1.0f / size;

        // TODO(eriq): Better heuristic for checking the clustering.

        // Check the average distance to the deter point.
        float distance = 0.0f;
        for (int i = 0; i < size; i++) {
            distance += Math.abs(deterValue - consensusValues[localAtoms[i].getGlobalId()]);
        }
        distance /= size;

        // Do nothing if the points are not clustered around the deter point.
        if (distance > deterConstant) {
            return;
        }

        // Randomly choose a point to go towards 1.0, the rest go towards 0.0.
        // TODO(eriq): There is a lot that can be done to choose points more intelligently.
        //  Maybe weight be truth value, for example.
        int upPoint = RandUtils.nextInt(size);

        for (int i = 0; i < size; i++) {
            float value = ((i == upPoint) ? 1.0f : 0.0f);
            localAtoms[i].setValue(value);
        }
    }

    /**
     * weight * 1/n * (sum_{i = 0}^{n} f(local[i]))
     * f(x) =
     *   1.0 - x if x > 1/n
     *   x       else
     */
    private float evaluateCollectiveDeter(float weight) {
        float deterValue = 1.0f / size;

        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            float localAtomValue = localAtoms[i].getValue();
            if (localAtomValue > deterValue) {
                value += 1.0f - localAtomValue;
            } else {
                value += localAtomValue;
            }
        }

        return weight * (1.0f / size) * value;
    }

    /**
     * weight * 1/n * (sum_{i = 0}^{n} f(consensus[i]))
     * f(x) =
     *   1.0 - x if x > 1/n
     *   x       else
     */
    private float evaluateCollectiveDeter(float weight, float[] consensusValues) {
        float deterValue = 1.0f / size;

        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            float localAtomValue = consensusValues[localAtoms[i].getGlobalId()];
            if (localAtomValue > deterValue) {
                value += 1.0f - localAtomValue;
            } else {
                value += localAtomValue;
            }
        }

        return weight * (1.0f / size) * value;
    }

    // Functionality for independent deter terms.
    // Treat these similarly to linear loss terms.
    // The closer values are to the deter constant, the higher the penalty.

    private void minimizeIndependentDeter(float stepSize, float weight, float[] consensusValues) {
        // Linear losses can be directly minimized.
        for (int i = 0; i < size; i++) {
            LocalAtom localAtom = localAtoms[i];

            float value = 0.0f;

            if (localAtom.getValue() > deterConstant) {
                // If we are past the deter point, keep moving up.
                value = consensusValues[localAtom.getGlobalId()]
                        - localAtom.getLagrange() / stepSize
                        + (weight * coefficients[i] / stepSize);
            } else {
                // If we are lower than the deter point, then move down.
                value = consensusValues[localAtom.getGlobalId()]
                        - localAtom.getLagrange() / stepSize
                        - (weight * coefficients[i] / stepSize);
            }

            localAtom.setValue(value);
        }
    }

    private float evaluateIndependentDeter(float weight) {
        float rawDissatisfaction = computeInnerPotential();
        float dissatisfaction = 1.0f - Math.abs(rawDissatisfaction - deterConstant);
        return weight * dissatisfaction;
    }

    private float evaluateIndependentDeter(float weight, float[] consensusValues) {
        float rawDissatisfaction = computeInnerPotential(consensusValues);
        float dissatisfaction = 1.0f - Math.abs(rawDissatisfaction - deterConstant);
        return weight * dissatisfaction;
    }

    // General Utilities

    private void initUnitNormal() {
        // If the hyperplane only has one atom, we can take shortcuts solving it.
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
            value += coefficients[i] * localAtoms[i].getValue();
        }

        return value - constant;
    }

    /**
     * coefficients^T * consensus - constant
     */
    private float computeInnerPotential(float[] consensusValues) {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * consensusValues[localAtoms[i].getGlobalId()];
        }

        return value - constant;
    }

    /**
     * Project the solution to the consensus problem onto this hyperplane,
     * thereby finding the min solution.
     * The consensus problem is:
     * [argmin_local stepSize / 2 * ||local - consensus + lagrange / stepSize ||_2^2],
     * while this hyperplane is: [coefficients^T * local = constant].
     * The result of the projection is stored in the LocalAtom.
     */
    private void project(float stepSize, float[] consensusValues) {
        // When there is only one atom, there is only one answer.
        // This answer must satisfy the constraint.
        if (size == 1) {
            localAtoms[0].setValue(constant / coefficients[0]);
            return;
        }

        // ConsensusOptimizer = Projection + (multiplier)(unitNormal).
        // Note that the projection is in this hyperplane and therefore orthogonal to the unitNormal.
        // So, these two orthogonal components can makeup the consensusOptimizer.

        // Get the min w.r.t. to the consensus values.
        // This is done by taking a step according to the lagrange.
        for (int i = 0; i < size; i++) {
            consensusOptimizer[i] = consensusValues[localAtoms[i].getGlobalId()] - localAtoms[i].getLagrange() / stepSize;
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
            localAtoms[i].setValue(consensusOptimizer[i] - multiplier * unitNormal[i]);
        }
    }

    /**
     * Minimizes the term as a weighted, squared hyperplane.
     * The function to minimize takes the form:
     * weight * [coefficients^T * local - constant]^2 + (stepsize / 2) * || local - consensus + lagrange / stepsize ||_2^2.
     *
     * The result of the minimization will be stored in the LocalAtom.
     */
    private void minWeightedSquaredHyperplane(float stepSize, float weight, float[] consensusValues) {
        // Different solving methods will be used depending on the size of the hyperplane.

        // Pre-load the LocalAtom with a term that is common in all the solutions:
        // stepsize * consensus - lagrange + (2 * weight * coefficients * constant).
        for (int i = 0; i < size; i++) {
            float value =
                    stepSize * consensusValues[localAtoms[i].getGlobalId()] - localAtoms[i].getLagrange()
                    + 2.0f * weight * coefficients[i] * constant;

            localAtoms[i].setValue(value);
        }

        // Hyperplanes with only one atom can be solved trivially.
        if (size == 1) {
            LocalAtom localAtom = localAtoms[0];
            float coefficient = coefficients[0];

            localAtom.setValue(localAtom.getValue() / (2.0f * weight * coefficient * coefficient + stepSize));

            return;
        }

        // Hyperplanes with only two atoms can be solved fairly easily.
        if (size == 2) {
            LocalAtom localAtom0 = localAtoms[0];
            LocalAtom localAtom1 = localAtoms[1];
            float coefficient0 = coefficients[0];
            float coefficient1 = coefficients[1];

            float a0 = 2.0f * weight * coefficient0 * coefficient0 + stepSize;
            float b1 = 2.0f * weight * coefficient1 * coefficient1 + stepSize;
            float a1b0 = 2.0f * weight * coefficient0 * coefficient1;

            localAtom1.setValue(localAtom1.getValue() - a1b0 * localAtom0.getValue() / a0);
            localAtom1.setValue(localAtom1.getValue() / (b1 - a1b0 * a1b0 / a0));

            localAtom0.setValue((localAtom0.getValue() - a1b0 * localAtom1.getValue()) / a0);

            return;
        }

        // In the case of larger hyperplanes, we can use a Cholesky decomposition to minimize.

        FloatMatrix lowerTriangle = fetchLowerTriangle(stepSize, weight);

        for (int i = 0; i < size; i++) {
            float newValue = localAtoms[i].getValue();

            for (int j = 0; j < i; j++) {
                newValue -= lowerTriangle.get(i, j) * localAtoms[j].getValue();
            }

            localAtoms[i].setValue(newValue / lowerTriangle.get(i, i));
        }

        for (int i = size - 1; i >= 0; i--) {
            float newValue = localAtoms[i].getValue();

            for (int j = size - 1; j > i; j--) {
                newValue -= lowerTriangle.get(j, i) * localAtoms[j].getValue();
            }

            localAtoms[i].setValue(newValue / lowerTriangle.get(i, i));
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
