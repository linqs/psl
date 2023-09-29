/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.streaming.StreamingTerm;

import java.nio.ByteBuffer;

/**
 * A term in the objective to be optimized by a SGDReasoner.
 */
public class SGDObjectiveTerm extends StreamingTerm {
    /**
     * Construct a SGD objective term by taking ownership of the hyperplane and all members of it.
     */
    public SGDObjectiveTerm(WeightedRule rule, boolean squared, boolean hinge, Hyperplane hyperplane) {
        super(hyperplane, rule, squared, hinge, null);
    }

    public SGDObjectiveTerm(short size, float[] coefficients, float constant, int[] atomIndexes,
                            Rule rule, boolean squared, boolean hinge, FunctionComparator comparator) {
        super(size, coefficients, constant, atomIndexes, rule, squared, hinge, comparator);
    }

    @Override
    public SGDObjectiveTerm copy() {
        return new SGDObjectiveTerm(size, coefficients, constant, atomIndexes, rule, squared, hinge, comparator);
    }

    /**
     * The number of bytes that writeFixedValues() will need to represent this term.
     * This is just all the member datum.
     */
    @Override
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

    @Override
    public void writeFixedValues(ByteBuffer fixedBuffer) {
        fixedBuffer.put((byte)(squared ? 1 : 0));
        fixedBuffer.put((byte)(hinge ? 1 : 0));
        fixedBuffer.putInt(rule.hashCode());
        fixedBuffer.putFloat(constant);
        fixedBuffer.putShort(size);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
            fixedBuffer.putInt(atomIndexes[i]);
        }
    }

    @Override
    public void read(ByteBuffer fixedBuffer) {
        squared = (fixedBuffer.get() == 1);
        hinge = (fixedBuffer.get() == 1);
        rule = (WeightedRule)AbstractRule.getRule(fixedBuffer.getInt());
        constant = fixedBuffer.getFloat();
        size = fixedBuffer.getShort();

        // Make sure that there is enough room for all these variables.
        if (coefficients.length < size) {
            coefficients = new float[size];
            atomIndexes = new int[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
            atomIndexes[i] = fixedBuffer.getInt();
        }
    }
}
