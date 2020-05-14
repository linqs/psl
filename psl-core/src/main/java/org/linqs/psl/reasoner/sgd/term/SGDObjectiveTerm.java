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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;

import java.nio.ByteBuffer;

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
    public int[] termIndexes;

    private short observedSize;
    private float[] observedCoefficients;
    private int[] observedIndexes;

    public SGDObjectiveTerm(TermStore<SGDObjectiveTerm, GroundAtom> termStore,
                            boolean squared, boolean hinge,
                            Hyperplane<GroundAtom> hyperplane,
                            float weight, float learningRate) {
        this.squared = squared;
        this.hinge = hinge;

        this.weight = weight;
        this.learningRate = learningRate;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        constant = hyperplane.getConstant();

        termIndexes = new int[size];
        GroundAtom[] terms = hyperplane.getTerms();
        for (int i = 0; i < size; i++) {
            termIndexes[i] = termStore.getAtomIndex(terms[i]);
        }
    }

    /**
     * @param observedValues: index into the termstore observedAtoms array
     * */
    public void updateConstant(float[] observedValues){
        float recomputedConstant = 0;

        for(int i = 0; i < observedIndexes.length; i ++){
            recomputedConstant = recomputedConstant + observedCoefficients[i] * observedValues[i];
        }

        constant = recomputedConstant;
    }

    public int[] getObservedIndices(){
        int[] obsIndices = new int[observedSize];

        for(int i = 0; i < observedSize; i++){
            obsIndices[i] = observedIndexes[i];
        }

        return obsIndices;
    }

    @Override
    public int size() {
        return size;
    }

    public float evaluate(float[] variableValues) {
        float dot = dot(variableValues);

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

    /**
     * Minimize the term by changing the random variables and return how much the random variables were moved by.
     */
    public float minimize(int iteration, float[] variableValues) {
        float movement = 0.0f;

        for (int i = 0 ; i < size; i++) {
            float dot = dot(variableValues);
            float gradient = computeGradient(iteration, i, dot);
            float gradientStep = gradient * (learningRate / iteration);

            float newValue = Math.max(0.0f, Math.min(1.0f, variableValues[i] - gradientStep));
            movement += Math.abs(newValue - variableValues[i]);
            variableValues[i] = newValue;
        }

        return movement;
    }

    private float computeGradient(int iteration, int varId, float dot) {
        if (hinge && dot <= 0.0f) {
            return 0.0f;
        }

        if (squared) {
            return weight * 2.0f * dot * coefficients[varId];
        }

        return weight * coefficients[varId];
    }

    private float dot(float[] variableValues) {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variableValues[termIndexes[i]];
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
            + Short.SIZE  // observed size
            + size * (Float.SIZE + Integer.SIZE)  // coefficients + variableIndexes
            + observedSize * (Float.SIZE + Integer.SIZE);  // observed coefficients + observedIndexes

        return bitSize / 8;
    }

    /**
     * Write a binary representation of the fixed values of this term to a buffer.
     * Note that the variableIndexes are written using the term store indexing.
     */
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte)(squared ? 1 : 0));
        fixedBuffer.put((byte)(hinge ? 1 : 0));
        fixedBuffer.putFloat(weight);
        fixedBuffer.putFloat(constant);
        fixedBuffer.putFloat(learningRate);
        fixedBuffer.putShort(size);
        fixedBuffer.putShort(observedSize);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(termIndexes[i]);
        }

        for (int i = 0; i < observedSize; i++) {
            fixedBuffer.putFloat(observedCoefficients[i]);
            fixedBuffer.putInt(observedIndexes[i]);
        }
    }

    /**
     * Assume the term that will be next read from the buffers.
     */
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer) {
        squared = (fixedBuffer.get() == 1);
        hinge = (fixedBuffer.get() == 1);
        weight = fixedBuffer.getFloat();
        constant = fixedBuffer.getFloat();
        learningRate = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();
        observedSize = fixedBuffer.getShort();

        // Make sure that there is enough room for all these variables.
        if (coefficients.length < size) {
            coefficients = new float[size];
            termIndexes = new int[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            termIndexes[i] = fixedBuffer.getInt();
        }

        if (observedCoefficients.length < observedSize) {
            observedCoefficients = new float[observedSize];
            observedIndexes = new int[observedSize];
        }

        for (int i = 0; i < observedSize; i++) {
            observedCoefficients[i] = fixedBuffer.getFloat();
            observedIndexes[i] = fixedBuffer.getInt();
        }
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore) {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(weight);
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
                builder.append(termIndexes[i]);
                builder.append(">)");
            } else {
                builder.append(" * ");
                builder.append(termStore.getVariableValue(termIndexes[i]));
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
}
