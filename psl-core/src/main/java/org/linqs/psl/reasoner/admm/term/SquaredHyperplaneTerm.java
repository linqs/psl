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
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.util.FloatMatrix;
import org.linqs.psl.util.HashCode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Objective term for an ADMMReasoner that is based on a squared
 * hyperplane in some way.
 *
 * Stores the characterization of the hyperplane as coefficients^T * x = constant
 * and minimizes with the weighted, squared hyperplane in the objective.
 */
public abstract class SquaredHyperplaneTerm extends ADMMObjectiveTerm {
    protected final float[] coefficients;
    protected final float constant;

    /**
     * The lower triangle in the Cholesky decomposition of the symmetric matrix:
     * M[i, j] = 2 * weight * coefficients[i] * coefficients[j]
     */
    private FloatMatrix lowerTriangle;

    private static Map<Integer, FloatMatrix> lowerTriangleCache = new HashMap<Integer, FloatMatrix>();

    private static final Semaphore matrixSemaphore = new Semaphore(1);

    // TODO(eriq): All the matrix work is suspect.
    // The old code was using some cache that didn't seem too useful. Could it have been?

    public SquaredHyperplaneTerm(GroundRule groundRule, Hyperplane<LocalVariable> hyperplane) {
        super(hyperplane, groundRule);

        this.coefficients = hyperplane.getCoefficients();
        this.constant = hyperplane.getConstant();

        lowerTriangle = null;
    }

    private void initLowerTriangle(float stepSize) {
        // Note that this method will only be called once (and not for every term),
        // so we will compute the hash here and not save it.
        int hash = HashCode.build(((WeightedGroundRule)groundRule).getWeight());
        hash = HashCode.build(hash, stepSize);
        for (int i = 0; i < size; i++) {
            hash = HashCode.build(hash, coefficients[i]);
        }

        // First check the cache.
        // Each rule (not ground rule) will have its own lowerTriangle.
        lowerTriangle = lowerTriangleCache.get(hash);
        if (lowerTriangle != null) {
            return;
        }

        // If we didn't find it, then synchronize and compute on this thread.
        lowerTriangle = computeLowerTriangle(stepSize, hash);
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

        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        float coeff = 0.0f;

        FloatMatrix matrix = FloatMatrix.zeroes(size, size);

        for (int i = 0; i < size; i++) {
            // Note that the matrix is symmetric.
            for (int j = i; j < size; j++) {
                if (i == j) {
                    coeff = 2 * weight * coefficients[i] * coefficients[i] + stepSize;
                    matrix.set(i, i, coeff);
                } else {
                    coeff = 2 * weight * coefficients[i] * coefficients[j];
                    matrix.set(i, j, coeff);
                    matrix.set(j, i, coeff);
                }
            }
        }

        matrix.choleskyDecomposition(true);
        lowerTriangleCache.put(hash, matrix);

        return matrix;
    }

    /**
     * coefficients^T * x - constant
     */
    @Override
    public float evaluate() {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        return value - constant;
    }

    @Override
    public float evaluate(float[] consensusValues) {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * consensusValues[variables[i].getGlobalId()];
        }

        return value - constant;
    }

    /**
     * Minimizes the weighted, squared hyperplane <br />
     * argmin weight * (coefficients^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
     * <p>
     * Stores the result in x.
     */
    protected void minWeightedSquaredHyperplane(float stepSize, float[] consensusValues) {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();

        // Construct constant term in the gradient (moved to right-hand side).
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];

            float value = stepSize * (consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
            value += 2 * weight * coefficients[i] * constant;

            variable.setValue(value);
        }

        // Solve for x

        // Handle very small hyperplanes specially.
        if (size == 1) {
            LocalVariable variable = variables[0];
            float coeff = coefficients[0];

            variable.setValue(variable.getValue() / (2 * weight * coeff * coeff + stepSize));
            return;
        }

        // Handle small hyperplanes specially.
        if (size == 2) {
            LocalVariable variable0 = variables[0];
            LocalVariable variable1 = variables[1];
            float coeff0 = coefficients[0];
            float coeff1 = coefficients[1];

            float a0 = 2 * weight * coeff0 * coeff0 + stepSize;
            float b1 = 2 * weight * coeff1 * coeff1 + stepSize;
            float a1b0 = 2 * weight * coeff0 * coeff1;

            variable1.setValue(variable1.getValue() - a1b0 * variable0.getValue() / a0);
            variable1.setValue(variable1.getValue() / (b1 - a1b0 * a1b0 / a0));

            variable0.setValue((variable0.getValue() - a1b0 * variable1.getValue()) / a0);

            return;
        }

        // Fast system solve.
        if (lowerTriangle == null) {
            initLowerTriangle(stepSize);
        }

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
}
