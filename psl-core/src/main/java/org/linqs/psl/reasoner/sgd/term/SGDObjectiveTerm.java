/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;

import org.apache.commons.lang.mutable.MutableInt;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * A term in the objective to be optimized by a SGDReasoner.
 */
public class SGDObjectiveTerm implements ReasonerTerm  {
    private boolean squared;
    private boolean hinge;

    private float weight;
    private float constant;
    private float learningRate;

    private short size;
    private float[] coefficients;
    private RandomVariableAtom[] variables;

    public SGDObjectiveTerm(boolean squared, boolean hinge,
            Hyperplane<RandomVariableAtom> hyperplane,
            float weight, float learningRate) {
        this.squared = squared;
        this.hinge = hinge;

        this.weight = weight;
        this.learningRate = learningRate;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        variables = hyperplane.getVariables();
        constant = hyperplane.getConstant();
    }

    @Override
    public int size() {
        return size;
    }

    public float evaluate() {
        float dot = dot();

        if (squared && hinge) {
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

    public void minimize(int iteration) {
        for (int i = 0 ; i < size; i++) {
            float dot = dot();
            float gradient = computeGradient(iteration, i, dot);

            gradient *= (learningRate / iteration);

            variables[i].setValue(Math.max(0.0f, Math.min(1.0f, variables[i].getValue() - gradient)));
        }
    }

    private float computeGradient(int iteration, int varId, float dot) {
        if (hinge && dot <= 0.0f) {
            return 0.0f;
        }

        if (squared && hinge) {
            return weight * 2.0f * dot * coefficients[varId];
        } else if (squared && !hinge) {
            return weight * 2.0f * dot * coefficients[varId];
        } else if (!squared && hinge) {
            return weight * coefficients[varId];
        } else {
            return weight * coefficients[varId];
        }
    }

    private float dot() {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        return value - constant;
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
            + size * (Float.SIZE + Integer.SIZE);  // coefficients + variables

        return bitSize / 8;
    }

    /**
     * Write a binary representation of the fixed values of this term to a buffer.
     * Note that the variables are written using their Object hashcode.
     */
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte)(squared ? 1 : 0));
        fixedBuffer.put((byte)(hinge ? 1 : 0));
        fixedBuffer.putFloat(weight);
        fixedBuffer.putFloat(constant);
        fixedBuffer.putFloat(learningRate);
        fixedBuffer.putShort(size);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(System.identityHashCode(variables[i]));
        }
    }

    /**
     * Assume the term that will be next read from the buffers.
     */
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer,
            Map<MutableInt, RandomVariableAtom> rvaMap, MutableInt intBuffer) {
        squared = (fixedBuffer.get() == 1);
        hinge = (fixedBuffer.get() == 1);
        weight = fixedBuffer.getFloat();
        constant = fixedBuffer.getFloat();
        learningRate = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();

        // Make sure that there is enough room for all these variables.
        if (coefficients.length < size) {
            coefficients = new float[size];
            variables = new RandomVariableAtom[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            intBuffer.setValue(fixedBuffer.getInt());
            variables[i] = rvaMap.get(intBuffer);
        }
    }

    @Override
    public String toString() {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(weight);
        builder.append(" * ");

        if (hinge) {
            builder.append(" * max(0.0, ");
        }

        for (int i = 0; i < size; i++) {
            builder.append("(");
            builder.append(coefficients[i]);
            builder.append(" * ");
            builder.append(variables[i]);
            builder.append(")");

            if (i != size - 1) {
                builder.append(" + ");
            }
        }

        builder.append(" - ");
        builder.append(constant);

        if (hinge) {
            builder.append(")");
        }

        if (squared) {
            builder.append("^2");
        }

        return builder.toString();
    }
}
