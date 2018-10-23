/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.reasoner.term.WeightedTerm;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.util.HashCode;

import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.algo.decomposition.DenseFloatCholeskyDecomposition;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;

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
public abstract class SquaredHyperplaneTerm extends ADMMObjectiveTerm implements WeightedTerm {
    protected final float[] coefficients;
    protected final float constant;

    private FloatMatrix2D L;

    private static Map<DenseFloatMatrix2DWithHashcode, FloatMatrix2D> lCache = new HashMap<DenseFloatMatrix2DWithHashcode, FloatMatrix2D>();

    private static final Semaphore matrixSemaphore = new Semaphore(1);

    // TODO(eriq): All the matrix work is suspect.
    // The old code was using some cache that didn't seem too useful. Could it have been?

    public SquaredHyperplaneTerm(GroundRule groundRule, Hyperplane hyperplane) {
        super(hyperplane, groundRule);

        this.coefficients = hyperplane.getCoefficients();
        this.constant = hyperplane.getConstant();

        L = null;
    }

    private void computeL(float stepSize) {
        // Since the method is synchronized, check to see if we have already computed L.
        if (L != null) {
            return;
        }

        float weight = (float)((WeightedGroundRule)groundRule).getWeight();

        float coeff;
        DenseFloatMatrix2DWithHashcode matrix = new DenseFloatMatrix2DWithHashcode(size, size);
        for (int i = 0; i < size; i++) {
            // Note that the matrix is symmetric.
            for (int j = i; j < size; j++) {
                if (i == j) {
                    coeff = 2 * weight * coefficients[i] * coefficients[i] + stepSize;
                    matrix.setQuick(i, i, coeff);
                } else {
                    coeff = 2 * weight * coefficients[i] * coefficients[j];
                    matrix.setQuick(i, j, coeff);
                    matrix.setQuick(j, i, coeff);
                }
            }
        }

        L = lCache.get(matrix);
        if (L == null) {
            // The matrix library itself cannot be called concurrently.
            try {
                matrixSemaphore.acquire();
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted constructing matrix", ex);
            }

            L = new DenseFloatCholeskyDecomposition(matrix).getL();
            lCache.put(matrix, L);

            matrixSemaphore.release();
        }
    }

    @Override
    public void weightChanged() {
        // Recompute L.
        L = null;
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

    /**
     * Minimizes the weighted, squared hyperplane <br />
     * argmin weight * (coefficients^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
     * <p>
     * Stores the result in x.
     */
    protected void minWeightedSquaredHyperplane(float stepSize, float[] consensusValues) {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();

        // Constructs constant term in the gradient (moved to right-hand side).
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];

            float value = stepSize * (consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
            value += 2 * weight * coefficients[i] * constant;

            variable.setValue(value);
        }

        // Solve for x

        // Handle small hyperplanes specially.
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
        if (L == null) {
            computeL(stepSize);
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < i; j++) {
                variables[i].setValue(variables[i].getValue() - L.getQuick(i, j) * variables[j].getValue());
            }
            variables[i].setValue(variables[i].getValue() / L.getQuick(i, i));
        }

        for (int i = size - 1; i >= 0; i--) {
            for (int j = size - 1; j > i; j--) {
                variables[i].setValue(variables[i].getValue() - L.getQuick(j, i) * variables[j].getValue());
            }
            variables[i].setValue(variables[i].getValue() / L.getQuick(i, i));
        }
    }

    private class DenseFloatMatrix2DWithHashcode extends DenseFloatMatrix2D {
        private static final long serialVersionUID = -8102931034927566306L;
        private boolean needsNewHashcode;
        private int hashcode = 0;

        public DenseFloatMatrix2DWithHashcode(int rows, int columns) {
            super(rows, columns);
            needsNewHashcode = true;
        }

        @Override
        public void setQuick(int row, int column, float value) {
            needsNewHashcode = true;
            super.setQuick(row, column, value);
        }

        @Override
        public int hashCode() {
            if (needsNewHashcode) {
                hashcode = HashCode.DEFAULT_INITIAL_NUMBER;
                for (int i = 0; i < rows(); i++) {
                    for (int j = 0; j < columns(); j++) {
                        hashcode = HashCode.build(hashcode, getQuick(i, j));
                    }
                }

                needsNewHashcode = false;
            }

            return hashcode;
        }
    }
}
