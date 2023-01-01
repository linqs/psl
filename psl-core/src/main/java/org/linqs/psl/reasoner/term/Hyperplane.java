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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.util.ArrayUtils;

/**
 * Information representing a raw hyperplane.
 * The hyperplane generally looks like C x V = c,
 * where C is the coefficients, V is the variables (ground atoms), and c is a constant.
 * How these values are interpreted (e.g. how the equal operator is treated) is up to downstream terms.
 * This type is more for carrying information than evaluating it.
 */
public class Hyperplane {
    private GroundAtom[] variables;
    private float[] coefficients;
    private int size;
    private float constant;

    public Hyperplane(int maxSize, float constant) {
        this(new GroundAtom[maxSize], new float[maxSize], constant, 0);
    }

    public Hyperplane(GroundAtom[] variables, float[] coefficients, float constant, int size) {
        this.variables = variables;
        this.coefficients = coefficients;
        this.constant = constant;
        this.size = size;
    }

    public void addTerm(GroundAtom variable, float coefficient) {
        variables[size] = variable;
        coefficients[size] = coefficient;
        size++;
    }

    public int size() {
        return size;
    }

    public GroundAtom getVariable(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Tried to access variable at index " + index + ", but only " + size + " exist.");
        }

        return variables[index];
    }

    public float getCoefficient(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Tried to access coefficient at index " + index + ", but only " + size + " exist.");
        }

        return coefficients[index];
    }

    public void appendCoefficient(int index, float value) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Tried to access coefficient at index " + index + ", but only " + size + " exist.");
        }

        coefficients[index] += value;
    }

    public float getConstant() {
        return constant;
    }

    public void setConstant(float constant) {
        this.constant = constant;
    }

    public int indexOfVariable(GroundAtom needle) {
        return ArrayUtils.indexOf(variables, size, needle);
    }

    public GroundAtom[] getVariables() {
        return variables;
    }

    public float[] getCoefficients() {
        return coefficients;
    }
}
