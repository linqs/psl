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
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.sgd.SGDExtension;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * A term in the objective to be optimized by a SGDReasoner.
 */
public class SGDObjectiveTerm implements ReasonerTerm  {
    private boolean squared;
    private boolean hinge;

    private WeightedRule rule;
    private float constant;

    private short size;
    private float[] coefficients;
    private int[] variableIndexes;

    public SGDObjectiveTerm(VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore,
            WeightedRule rule,
            boolean squared, boolean hinge,
            Hyperplane<GroundAtom> hyperplane) {
        this.squared = squared;
        this.hinge = hinge;

        this.rule = rule;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        constant = hyperplane.getConstant();

        variableIndexes = new int[size];
        GroundAtom[] variables = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            variableIndexes[i] = termStore.getVariableIndex(variables[i]);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void adjustConstant(float oldValue, float newValue) {
        constant = constant - oldValue + newValue;
    }

    public float evaluate(float[] variableValues) {
        float dot = dot(variableValues);
        float weight = rule.getWeight();

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
    public float minimize(int iteration, VariableTermStore termStore, float learningRate,
            Map<Integer, Float> accumulatedGradientSquares,
            Map<Integer, Float> accumulatedGradientMean,
            Map<Integer, Float> accumulatedGradientVariance,
            SGDExtension sgdExtension, boolean coordinateStep) {
        float movement = 0.0f;
        float variableStep = 0.0f;
        float newValue = 0.0f;
        float partial = 0.0f;

        GroundAtom[] variableAtoms = termStore.getVariableAtoms();
        float[] variableValues = termStore.getVariableValues();
        float dot = dot(variableValues);

        for (int i = 0 ; i < size; i++) {
            if (variableAtoms[variableIndexes[i]] instanceof ObservedAtom) {
                continue;
            }

            partial = computePartial(i, dot, rule.getWeight());
            variableStep = computeVariableStep(variableIndexes[i], iteration, learningRate, partial,
                    accumulatedGradientSquares, accumulatedGradientMean, accumulatedGradientVariance,
                    sgdExtension);

            newValue = Math.max(0.0f, Math.min(1.0f, variableValues[variableIndexes[i]] - variableStep));
            movement += Math.abs(newValue - variableValues[variableIndexes[i]]);
            variableValues[variableIndexes[i]] = newValue;

            if (coordinateStep) {
                dot = dot(variableValues);
            }
        }

        return movement;
    }

    private float computeVariableStep(
            int variableIndex, int iteration, float learningRate, float partial,
            Map<Integer, Float> accumulatedGradientSquares,
            Map<Integer, Float> accumulatedGradientMean,
            Map<Integer, Float> accumulatedGradientVariance,
            SGDExtension sgdExtension) {
        float step = 0.0f;
        float adaptedLearningRate = 0.0f;

        switch (sgdExtension) {
            case NONE:
                step = partial * learningRate;
                break;
            case ADAGRAD:
                if (accumulatedGradientSquares.get(variableIndex) == null) {
                    accumulatedGradientSquares.put(variableIndex, (float)Math.pow(partial, 2.0f));
                } else {
                    accumulatedGradientSquares.put(variableIndex, accumulatedGradientSquares.get(variableIndex)
                            + (float)Math.pow(partial, 2.0f));
                }

                adaptedLearningRate = learningRate / (float)Math.sqrt(accumulatedGradientSquares.get(variableIndex) + 1e-8f);

                step = partial * adaptedLearningRate;
                break;
            case ADAM:
                float beta1 = 0.9f * (float)Math.pow(1.0f - 1.0e-8f, iteration - 1.0f);
                float beta2 = 0.999f;
                float meanHat = 0.0f;
                float varianceHat = 0.0f;

                if (accumulatedGradientMean.get(variableIndex) == null) {
                    accumulatedGradientMean.put(variableIndex, (1.0f - beta1) * partial);
                } else {
                    accumulatedGradientMean.put(variableIndex, beta1 * accumulatedGradientMean.get(variableIndex) + (1.0f - beta1) * partial);
                }

                if (accumulatedGradientVariance.get(variableIndex) == null) {
                    accumulatedGradientVariance.put(variableIndex, (1.0f - beta2) * (float)Math.pow(partial, 2.0f));
                } else {
                    accumulatedGradientVariance.put(variableIndex, beta2 * accumulatedGradientVariance.get(variableIndex)
                            + (1.0f - beta2) * (float)Math.pow(partial, 2.0f));
                }

                meanHat = accumulatedGradientMean.get(variableIndex) / (1.0f - beta1);
                varianceHat = accumulatedGradientVariance.get(variableIndex) / (1.0f - beta2);

                adaptedLearningRate = learningRate / ((float)Math.sqrt(varianceHat) + 1e-8f);
                step = meanHat * adaptedLearningRate;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported SGD extension: %s", sgdExtension.getName()));
        }

        return step;
    }

    private float computePartial(int varId, float dot, float weight) {
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
            value += coefficients[i] * variableValues[variableIndexes[i]];
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
            + Integer.SIZE  // rule hash
            + Float.SIZE  // constant
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

    public String toString(VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore) {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(rule.getWeight());
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
}
