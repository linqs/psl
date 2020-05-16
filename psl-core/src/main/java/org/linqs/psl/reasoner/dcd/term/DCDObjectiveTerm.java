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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.AtomTermStore;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.MathUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;

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
    private int[] indices;

    public DCDObjectiveTerm(AtomTermStore<DCDObjectiveTerm, GroundAtom> termStore,
                            boolean squared,
                            Hyperplane<GroundAtom> hyperplane,
                            float weight, float c) {
        this.squared = squared;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        constant = hyperplane.getConstant();

        indices = new int[size];
        GroundAtom[] atoms = hyperplane.getAtoms();
        for (int i = 0; i < size; i++) {
            indices[i] = termStore.getAtomIndex(atoms[i]);
        }

        adjustedWeight = weight * c;

        float tempQii = 0f;
        for (int i = 0; i < size; i++) {
            tempQii += coefficients[i] * coefficients[i];
        }
        qii = tempQii;

        lagrange = 0.0f;
    }

    public float getLagrange() {
        return lagrange;
    }

    public float evaluate(float[] variableValues) {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variableValues[indices[i]];
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

    public void minimize(boolean truncateEveryStep, ArrayList<GroundAtom> atoms, float[] values) {
        if (squared) {
            float gradient = computeGradient(atoms, values);
            gradient += lagrange / (2.0f * adjustedWeight);
            minimize(truncateEveryStep, gradient, Float.POSITIVE_INFINITY, atoms, values);
        } else {
            minimize(truncateEveryStep, computeGradient(atoms, values), adjustedWeight, atoms, values);
        }
    }

    @Override
    public int size() {
        return size;
    }

    private float computeGradient(ArrayList<GroundAtom> atoms, float[] values) {
        float val = 0.0f;

        for (int i = 0; i < size; i++) {
            if(atoms.size() <= i){
                break;
            }

            if(atoms.get(indices[i]) instanceof RandomVariableAtom){
                val += values[indices[i]] * coefficients[i];
            }
        }

        return constant - val;
    }

    private void minimize(boolean truncateEveryStep, float gradient, float lim, ArrayList<GroundAtom> atoms, float[] values) {
        float pg = gradient;
        if (MathUtils.isZero(lagrange)) {
            pg = Math.min(0.0f, gradient);
        }

        if (MathUtils.equals(lim, adjustedWeight) && MathUtils.equals(lagrange, adjustedWeight)) {
            pg = Math.max(0.0f, gradient);
        }

        if (MathUtils.isZero(pg)) {
            return;
        }

        float pa = lagrange;
        lagrange = Math.min(lim, Math.max(0.0f, lagrange - gradient / qii));
        for (int i = 0; i < size; i++) {
            if(atoms.size() <= i){
                break;
            }

            if(atoms.get(indices[i]) instanceof RandomVariableAtom){
                float val = values[indices[i]] - ((lagrange - pa) * coefficients[i]);
                if (truncateEveryStep) {
                    val = Math.max(0.0f, Math.min(1.0f, val));
                }
                values[indices[i]] = val;
            }
        }
    }

    /**
     * The number of bytes that writeFixedValues() will need to represent this term.
     * This is just all the member datum minus the lagrange value.
     */
    public int fixedByteSize() {
        int bitSize =
            Byte.SIZE  // squared
            + Float.SIZE  // adjustedWeight
            + Float.SIZE  // constant
            + Float.SIZE  // qii
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
        fixedBuffer.putFloat(adjustedWeight);
        fixedBuffer.putFloat(constant);
        fixedBuffer.putFloat(qii);
        fixedBuffer.putShort(size);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(indices[i]);
        }
    }

    /**
     * Assume the term that will be next read from the buffers.
     */
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer) {
        squared = (fixedBuffer.get() == 1);
        adjustedWeight = fixedBuffer.getFloat();
        constant = fixedBuffer.getFloat();
        qii = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();

        // Make sure that there is enough room for all these variableIndexes.
        if (coefficients.length < size) {
            coefficients = new float[size];
            indices = new int[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            indices[i] = fixedBuffer.getInt();
        }

        lagrange = volatileBuffer.getFloat();
    }

    @Override
    public String toString() {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(adjustedWeight);
        builder.append(" * max(0.0, ");

        for (int i = 0; i < size; i++) {
            builder.append("(");
            builder.append(coefficients[i]);
            builder.append(" * ");
            builder.append(indices[i]);
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
