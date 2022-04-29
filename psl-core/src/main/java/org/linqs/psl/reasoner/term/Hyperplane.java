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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.util.ArrayUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Information representing a raw hyperplane.
 */
public class Hyperplane<E extends ReasonerLocalVariable> {
    private E[] variables;
    private float[] coefficients;
    private int size;
    private float constant;

    // Information for RVAs that have been integrated into the constant.
    // This will only be allocated if used.
    private List<IntegratedRVA> integratedRVAs;

    @SuppressWarnings("unchecked")
    public Hyperplane(Class<E> localVariableClass, int maxSize, float constant) {
        this((E[])Array.newInstance(localVariableClass, maxSize), new float[maxSize], constant, 0);
    }

    public Hyperplane(E[] variables, float[] coefficients, float constant, int size) {
        this.variables = variables;
        this.coefficients = coefficients;
        this.constant = constant;
        this.size = size;

        integratedRVAs = null;
    }

    public void addTerm(E variable, float coefficient) {
        variables[size] = variable;
        coefficients[size] = coefficient;
        size++;
    }

    /**
     * Indicate that an RVA has been integrated into the constant.
     *
     * This does NOT change the constant, that should be done via setConstant.
     * This merely informs the hyperplane that an RVA has already been integrated.
     *
     * The coefficient should be set such that
     * "incorporating" this term would mean adding it to the constant,
     * and "removing" this term would mean subtracting it from the constant.
     */
    public void addIntegratedRVA(RandomVariableAtom atom, float coefficient) {
        if (integratedRVAs == null) {
            integratedRVAs = new ArrayList<IntegratedRVA>(variables.length);
        }

        integratedRVAs.add(new IntegratedRVA(atom, coefficient));
    }

    public int size() {
        return size;
    }

    public E getVariable(int index) {
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

    public int indexOfVariable(E needle) {
        return ArrayUtils.indexOf(variables, size, needle);
    }

    public E[] getVariables() {
        return variables;
    }

    public float[] getCoefficients() {
        return coefficients;
    }

    /**
     * Get any RVAs that are integrated into the constant.
     * These are not treated as normal variables.
     */
    public List<IntegratedRVA> getIntegratedRVAs() {
        return integratedRVAs;
    }

    public static class IntegratedRVA {
        public RandomVariableAtom atom;
        public float coefficient;

        public IntegratedRVA(RandomVariableAtom atom, float coefficient) {
            this.atom = atom;
            this.coefficient = coefficient;
        }
    }
}
