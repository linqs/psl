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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.FakeRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.RandUtils;

import java.nio.ByteBuffer;

/**
 * A term in the objective to be optimized by a SGDReasoner.
 */
public class SGDObjectiveTerm implements ReasonerTerm  {
    private boolean squared;
    private boolean hinge;

    private float deterEpsilon;

    private WeightedRule rule;
    private float constant;
    private float learningRate;

    private short size;
    private float[] coefficients;
    private int[] variableIndexes;

    public SGDObjectiveTerm(VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore,
            WeightedRule rule,
            boolean squared, boolean hinge,
            Hyperplane<RandomVariableAtom> hyperplane,
            float learningRate) {
        this(termStore, rule, squared, hinge, 0.0f, hyperplane, learningRate);
    }

    public SGDObjectiveTerm(VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore,
            WeightedRule rule,
            boolean squared, boolean hinge,
            float deterEpsilon,
            Hyperplane<RandomVariableAtom> hyperplane,
            float learningRate) {
        this.squared = squared;
        this.hinge = hinge;
        this.deterEpsilon = deterEpsilon;

        this.rule = rule;
        this.learningRate = learningRate;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        constant = hyperplane.getConstant();

        variableIndexes = new int[size];
        RandomVariableAtom[] variables = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            variableIndexes[i] = termStore.getVariableIndex(variables[i]);
        }
    }

    public static SGDObjectiveTerm createDeterTerm(
            VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore,
            Hyperplane<RandomVariableAtom> hyperplane,
            float learningRate,
            float deterWeight, float deterEpsilon) {
        return new SGDObjectiveTerm(termStore, new FakeRule(deterWeight, false), false, false, deterEpsilon, hyperplane, learningRate);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void adjustConstant(float oldValue, float newValue) {
        constant = constant - oldValue + newValue;
    }

    public boolean isConvex() {
        return MathUtils.isZero(deterEpsilon);
    }

    public float evaluate(float[] variableValues) {
        float dot = dot(variableValues);
        float weight = getWeight();

        if (!MathUtils.isZero(deterEpsilon)) {
            return evaluateDeter(weight, variableValues);
        } else if (squared && hinge) {
            // weight * [max(0.0, coeffs^T * x - constant)]^2
            return weight * (float)Math.pow(Math.max(0.0f, dot), 2);
        } else if (squared && !hinge) {
            // weight * [coeffs^T * x - constant]^2
            return weight * (float)Math.pow(dot, 2);
        } else if (!squared && hinge) {
            // weight * max(0.0, coeffs^T * x - constant)
            return weight * Math.max(0.0f, dot);
        } else {
            // weight * (coeffs^T * x - constant)
            return weight * dot;
        }
    }

    /**
     * Minimize the term by changing the random variables and return how much the random variables were moved by.
     */
    public float minimize(int iteration, float[] variableValues) {
        float movement = 0.0f;
        float weight = getWeight();

        if (!MathUtils.isZero(deterEpsilon)) {
            return minimizeDeter(weight, iteration, variableValues);
        }

        for (int i = 0 ; i < size; i++) {
            float dot = dot(variableValues);
            float gradient = computeGradient(i, dot);
            float gradientStep = weight * gradient * (learningRate / iteration);

            float newValue = Math.max(0.0f, Math.min(1.0f, variableValues[variableIndexes[i]] - gradientStep));
            movement += Math.abs(newValue - variableValues[variableIndexes[i]]);
            variableValues[variableIndexes[i]] = newValue;
        }

        return movement;
    }

    private float computeGradient(int varId, float dot) {
        if (hinge && dot <= 0.0f) {
            return 0.0f;
        }

        if (squared) {
            return 2.0f * dot * coefficients[varId];
        }

        return coefficients[varId];
    }

    private float dot(float[] variableValues) {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variableValues[variableIndexes[i]];
        }

        return value - constant;
    }

    // Functionality for deter terms.

    private float minimizeDeter(float weight, int iteration, float[] variableValues) {
        // TODO(eriq): This minimization is naive.
        float deterValue = 1.0f / size;

        // TODO(eriq): Better heuristic for checking the clustering.

        // Check the average distance to the deter point.
        float distance = 0.0f;
        for (int i = 0; i < size; i++) {
            distance += Math.abs(deterValue - variableValues[variableIndexes[i]]);
        }
        distance /= size;

        // Do nothing if the points are not clustered around the deter point.
        if (distance > deterEpsilon) {
            return 0.0f;
        }

        // Randomly choose a point to go towards 1.0, the rest go towards 0.0.
        // TODO(eriq): There is a lot that can be done to choose points more intelligently.
        //  Maybe weight be truth value, for example.
        int upPoint = RandUtils.nextInt(size);

        float movement = 0.0f;
        for (int i = 0; i < size; i++) {
            float newValue = ((i == upPoint) ? 1.0f : 0.0f);
            movement += Math.abs(newValue - variableValues[variableIndexes[i]]);
            variableValues[variableIndexes[i]] = newValue;
        }

        return movement;
    }

    /**
     * weight * 1/n * (sum_{i = 0}^{n} f(variable[i]))
     * f(x) =
     *   1.0 - x if x > 1/n
     *   x       else
     */
    private float evaluateDeter(float weight, float[] variableValues) {
        float deterValue = 1.0f / size;

        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            float variableValue = variableValues[variableIndexes[i]];
            if (variableValue > deterValue) {
                value += 1.0f - variableValue;
            } else {
                value += variableValue;
            }
        }

        return weight * (1.0f / size) * value;
    }

    /**
     * The number of bytes that writeFixedValues() will need to represent this term.
     * This is just all the member datum.
     */
    public int fixedByteSize() {
        int bitSize =
            Byte.SIZE  // squared
            + Byte.SIZE  // hinge
            + Float.SIZE  // weight
            + Float.SIZE  // constant
            + Float.SIZE  // learningRate
            + Short.SIZE  // size
            + size * (Float.SIZE + Integer.SIZE);  // coefficients + variableIndexes

        return bitSize / 8;
    }

    /**
     * Write a binary representation of the fixed values of this term to a buffer.
     * Note that the variableIndexes are written using the term store indexing.
     */
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte)(squared ? 1 : 0));
        fixedBuffer.put((byte)(hinge ? 1 : 0));
        fixedBuffer.putInt(System.identityHashCode(rule));
        fixedBuffer.putFloat(constant);
        fixedBuffer.putFloat(learningRate);
        fixedBuffer.putShort(size);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(variableIndexes[i]);
        }
    }

    /**
     * Assume the term that will be next read from the buffers.
     */
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer) {
        squared = (fixedBuffer.get() == 1);
        hinge = (fixedBuffer.get() == 1);
        rule = (WeightedRule)AbstractRule.getRule(fixedBuffer.getInt());
        constant = fixedBuffer.getFloat();
        learningRate = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();

        // Make sure that there is enough room for all these variables.
        if (coefficients.length < size) {
            coefficients = new float[size];
            variableIndexes = new int[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            variableIndexes[i] = fixedBuffer.getInt();
        }
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore) {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(getWeight());
        builder.append(" * ");

        if (hinge) {
            builder.append("max(0.0, ");
        } else {
            builder.append("(");
        }

        for (int i = 0; i < size; i++) {
            builder.append("(");
            builder.append(coefficients[i]);

            if (termStore == null) {
                builder.append(" * <index:");
                builder.append(variableIndexes[i]);
                builder.append(">)");
            } else {
                builder.append(" * ");
                builder.append(termStore.getVariableValue(variableIndexes[i]));
                builder.append(")");
            }

            if (i != size - 1) {
                builder.append(" + ");
            }
        }

        builder.append(" - ");
        builder.append(constant);

        builder.append(")");

        if (squared) {
            builder.append(" ^2");
        }

        return builder.toString();
    }

    private float getWeight() {
        if (rule != null && rule.isWeighted()) {
            return ((WeightedRule)rule).getWeight();
        }

        return Float.POSITIVE_INFINITY;
    }
}
