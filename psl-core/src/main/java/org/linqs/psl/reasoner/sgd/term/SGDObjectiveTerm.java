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

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.FakeRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingTerm;
import org.linqs.psl.util.MathUtils;

import java.nio.ByteBuffer;

/**
 * A term in the objective to be optimized by a SGDReasoner.
 */
public class SGDObjectiveTerm implements StreamingTerm {
    private boolean squared;
    private boolean hinge;

    private float deterEpsilon;

    private WeightedRule rule;
    private float constant;

    private short size;
    private float[] coefficients;
    private int[] atomIndexes;

    public SGDObjectiveTerm(TermStore<SGDObjectiveTerm, GroundAtom> termStore,
            WeightedRule rule,
            boolean squared, boolean hinge,
            Hyperplane<GroundAtom> hyperplane) {
        this(termStore, rule, squared, hinge, 0.0f, hyperplane);
    }

    public SGDObjectiveTerm(TermStore<SGDObjectiveTerm, GroundAtom> termStore,
            WeightedRule rule,
            boolean squared, boolean hinge,
            float deterEpsilon,
            Hyperplane<GroundAtom> hyperplane) {
        this.squared = squared;
        this.hinge = hinge;
        this.deterEpsilon = deterEpsilon;

        this.rule = rule;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        constant = hyperplane.getConstant();

        atomIndexes = new int[size];
        GroundAtom[] atoms = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            atomIndexes[i] = termStore.getAtomIndex(atoms[i]);
        }
    }

    public static SGDObjectiveTerm createDeterTerm(
            TermStore<SGDObjectiveTerm, GroundAtom> termStore,
            Hyperplane<GroundAtom> hyperplane,
            float deterWeight, float deterEpsilon) {
        return new SGDObjectiveTerm(termStore, new FakeRule(deterWeight, false), false, false, deterEpsilon, hyperplane);
    }

    public float getDeterEpsilon() {
        return deterEpsilon;
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

    public float evaluate(float[] atomValues) {
        float dot = dot(atomValues);
        float weight = getWeight();

        if (!MathUtils.isZero(deterEpsilon)) {
            return evaluateDeter(weight, atomValues);
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

    public float computePartial(int varId, float dot, float weight) {
        if (hinge && dot <= 0.0f) {
            return 0.0f;
        }

        if (squared) {
            return weight * 2.0f * dot * coefficients[varId];
        }

        return weight * coefficients[varId];
    }

    public float dot(float[] atomValues) {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * atomValues[atomIndexes[i]];
        }

        return value - constant;
    }

    /**
     * weight * 1/n * (sum_{i = 0}^{n} f(atom[i]))
     * f(x) =
     *   1.0 - x if x > 1/n
     *   x       else
     */
    private float evaluateDeter(float weight, float[] atomValues) {
        float deterValue = 1.0f / size;

        float evaluation = 0.0f;
        for (int i = 0; i < size; i++) {
            float value = atomValues[atomIndexes[i]];
            if (value > deterValue) {
                evaluation += 1.0f - value;
            } else {
                evaluation += value;
            }
        }

        return weight * (1.0f / size) * evaluation;
    }

    /**
     * The number of bytes that writeFixedValues() will need to represent this term.
     * This is just all the member datum.
     */
    public WeightedRule getRule() {
        return rule;
    }

    public int[] getAtomIndexes() {
        return atomIndexes;
    }

    @Override
    public int fixedByteSize() {
        int bitSize =
            Byte.SIZE  // squared
            + Byte.SIZE  // hinge
            + Integer.SIZE  // rule hash
            + Float.SIZE  // constant
            + Short.SIZE  // size
            + Float.SIZE // deter epsilon
            + size * (Float.SIZE + Integer.SIZE);  // coefficients + indexes

        return bitSize / 8;
    }

    @Override
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte)(squared ? 1 : 0));
        fixedBuffer.put((byte)(hinge ? 1 : 0));
        fixedBuffer.putInt(System.identityHashCode(rule));
        fixedBuffer.putFloat(constant);
        fixedBuffer.putShort(size);
        fixedBuffer.putFloat(deterEpsilon);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(atomIndexes[i]);
        }
    }

    @Override
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer) {
        squared = (fixedBuffer.get() == 1);
        hinge = (fixedBuffer.get() == 1);
        rule = (WeightedRule)AbstractRule.getRule(fixedBuffer.getInt());
        constant = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();
        deterEpsilon = fixedBuffer.getFloat();

        // Make sure that there is enough room for all the coefficients and indexes.
        if (coefficients.length < size) {
            coefficients = new float[size];
            atomIndexes = new int[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            atomIndexes[i] = fixedBuffer.getInt();
        }
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(TermStore<SGDObjectiveTerm, GroundAtom> termStore) {
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
                builder.append(atomIndexes[i]);
                builder.append(">)");
            } else {
                builder.append(" * ");
                builder.append(termStore.getAtomValue(atomIndexes[i]));
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
