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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.MathUtils;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * A term in the objective to be optimized by a DCDReasoner.
 */
public class DCDObjectiveTerm implements ReasonerTerm  {
    private boolean squared;

    private float adjustedWeight;
    private float constant;
    private float lagrange;
    private float qii;

    private short size;
    private float[] coefficients;
    private RandomVariableAtom[] variables;

    public DCDObjectiveTerm(boolean squared, Hyperplane<RandomVariableAtom> hyperplane,
            float weight, float c) {
        this.squared = squared;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        variables = hyperplane.getVariables();
        constant = hyperplane.getConstant();

        adjustedWeight = weight * c;

        float tempQii = 0f;
        for (int i = 0; i < size; i++) {
            tempQii += coefficients[i] * coefficients[i];
        }
        qii = tempQii;

        lagrange = 0.0f;
    }

    public float computeGradient() {
        float val = 0.0f;

        for (int i = 0; i < size; i++) {
            val += variables[i].getValue() * coefficients[i];
        }

        return constant - val;
    }

    public float evaluate() {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        value -= constant;


        if (squared) {
            // weight * [max(coeffs^T * x - constant, 0.0)]^2
            return adjustedWeight * (float)Math.pow(Math.max(0.0f, value), 2.0f);
        } else {
            // weight * max(coeffs^T * x - constant, 0)
            return adjustedWeight * Math.max(value, 0.0f);
        }
    }

    public void minimize(boolean truncateEveryStep) {
        if (squared) {
            float gradient = computeGradient();
            gradient += lagrange / (2.0f * adjustedWeight);
            minimize(truncateEveryStep, gradient, Float.POSITIVE_INFINITY);
        } else {
            minimize(truncateEveryStep, computeGradient(), adjustedWeight);
        }
    }

    @Override
    public int size() {
        return size;
    }

    /**
     * The number of bytes that write() will need to represent this term.
     */
    public int byteSize() {
        return
            Byte.SIZE  // squared
            + Float.SIZE  // adjustedWeight
            + Float.SIZE  // constant
            + Float.SIZE  // lagrange
            + Float.SIZE  // qii
            + Short.SIZE  // size
            + size * (Float.SIZE + Integer.SIZE);  // coefficients + variables
    }

    /**
     * Write a binary representation of this term to a buffer.
     * Note that the variables are written using their Object hashcode.
     */
    public void write(ByteBuffer buffer) {
        buffer.put((byte)(squared ? 1 : 0));
        buffer.putFloat(adjustedWeight);
        buffer.putFloat(constant);
        buffer.putFloat(lagrange);
        buffer.putFloat(qii);
        buffer.putShort(size);

        for (int i = 0; i < size; i++) {
            buffer.putFloat(coefficients[i]);
            buffer.putInt(System.identityHashCode(variables[i]));
        }
    }

    /**
     * Assume the term that will be next read from the buffer.
     */
    public void read(ByteBuffer buffer, Map<Integer, RandomVariableAtom> rvaMap) {
        squared = (buffer.get() == 1);
        adjustedWeight = buffer.getFloat();
        constant = buffer.getFloat();
        lagrange = buffer.getFloat();
        qii = buffer.getFloat();
        size = buffer.getShort();

        for (int i = 0; i < size; i++) {
            coefficients[i] = buffer.getFloat();
            variables[i] = rvaMap.get(buffer.getInt());
        }
    }

    private void minimize(boolean truncateEveryStep, float grad, float lim) {
        float pg = grad;
        if (MathUtils.isZero(lagrange)) {
            pg = Math.min(grad, 0.0f);
        }

        if (MathUtils.equals(lim, adjustedWeight) && MathUtils.equals(lagrange, adjustedWeight)) {
            pg = Math.max(grad, 0.0f);
        }

        if (MathUtils.isZero(pg)) {
            return;
        }

        float pa = lagrange;
        lagrange = Math.min(Math.max(lagrange - grad / qii, 0.0f), lim);
        for (int i = 0; i < size; i++) {
            float val = variables[i].getValue() - ((lagrange - pa) * coefficients[i]);
            if (truncateEveryStep) {
                val = Math.max(Math.min(val, 1.0f), 0.0f);
            }
            variables[i].setValue(val);
        }
    }
}
