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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingTerm;

import java.nio.ByteBuffer;

/**
 * A term in the objective to be optimized by a DCDReasoner.
 */
public class DCDObjectiveTerm implements StreamingTerm {
    private boolean squared;

    private WeightedRule rule;
    private float constant;
    private float lagrange;
    private float qii;
    private float c;

    private short size;
    private float[] coefficients;
    private int[] variableIndexes;

    public DCDObjectiveTerm(VariableTermStore<DCDObjectiveTerm, GroundAtom> termStore,
            WeightedRule rule,
            boolean squared,
            Hyperplane<GroundAtom> hyperplane,
            float c) {
        this.squared = squared;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        constant = hyperplane.getConstant();

        variableIndexes = new int[size];
        GroundAtom[] variables = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            variableIndexes[i] = termStore.getVariableIndex(variables[i]);
        }

        this.rule = rule;
        this.c = c;

        float tempQii = 0f;
        for (int i = 0; i < size; i++) {
            tempQii += coefficients[i] * coefficients[i];
        }
        qii = tempQii;

        lagrange = 0.0f;
    }

    public float evaluate(float[] variableValues) {
        float value = 0.0f;
        float adjustedWeight = rule.getWeight() * c;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variableValues[variableIndexes[i]];
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

    @Override
    public int size() {
        return size;
    }

    public boolean isSquared() {
        return squared;
    }

    @Override
    public void adjustConstant(float oldValue, float newValue) {
        constant = constant - oldValue + newValue;
    }

    public boolean isConvex() {
        return true;
    }

    public float computeGradient(float[] variableValues) {
        float val = 0.0f;

        for (int i = 0; i < size; i++) {
            val += variableValues[variableIndexes[i]] * coefficients[i];
        }

        return constant - val;
    }

    public float[] getCoefficients() {
        return coefficients;
    }

    public float getLagrange() {
        return lagrange;
    }

    public void setLagrange(float lagrange) {
        this.lagrange = lagrange;
    }

    public WeightedRule getRule() {
        return rule;
    }

    public int[] getVariableIndexes() {
        return variableIndexes;
    }

    public float getQii() {
        return qii;
    }

    @Override
    public int fixedByteSize() {
        int bitSize =
            Byte.SIZE  // squared
            + Integer.SIZE  // rule hash
            + Float.SIZE  // constant
            + Float.SIZE  // qii
            + Float.SIZE  // c
            + Short.SIZE  // size
            + size * (Float.SIZE + Integer.SIZE);  // coefficients + variableIndexes

        return bitSize / 8;
    }

    @Override
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte)(squared ? 1 : 0));
        fixedBuffer.putInt(rule.hashCode());
        fixedBuffer.putFloat(constant);
        fixedBuffer.putFloat(qii);
        fixedBuffer.putFloat(c);
        fixedBuffer.putShort(size);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(variableIndexes[i]);
        }
    }

    @Override
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer) {
        squared = (fixedBuffer.get() == 1);
        rule = (WeightedRule)AbstractRule.getRule(fixedBuffer.getInt());
        constant = fixedBuffer.getFloat();
        qii = fixedBuffer.getFloat();
        c = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();

        // Make sure that there is enough room for all these variableIndexes.
        if (coefficients.length < size) {
            coefficients = new float[size];
            variableIndexes = new int[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            variableIndexes[i] = fixedBuffer.getInt();
        }

        lagrange = volatileBuffer.getFloat();
    }

    @Override
    public String toString() {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(rule.getWeight() * c);
        builder.append(" * max(0.0, ");

        for (int i = 0; i < size; i++) {
            builder.append("(");
            builder.append(coefficients[i]);
            builder.append(" * ");
            builder.append(variableIndexes[i]);
            builder.append(")");

            if (i != size - 1) {
                builder.append(" + ");
            }
        }

        builder.append(" - ");
        builder.append(constant);

        builder.append(")");

        if (squared) {
            builder.append("^2");
        }

        return builder.toString();
    }
}
