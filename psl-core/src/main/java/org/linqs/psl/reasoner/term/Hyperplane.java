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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.util.ArrayUtils;

import java.lang.reflect.Array;

/**
 * Information representing a raw hyperplane.
 */
public class Hyperplane<E extends ReasonerLocalAtom> {
    private E[] atoms;
    private float[] coefficients;
    private int index;
    private float constant;

    @SuppressWarnings("unchecked")
    public Hyperplane(Class<E> localVariableClass, int maxVariableSize, float constant) {
        this((E[])Array.newInstance(localVariableClass, maxVariableSize), new float[maxVariableSize],0, constant);
    }

    public Hyperplane(E[] atoms, float[] coefficients, int index, float constant) {
        this.atoms = atoms;
        this.coefficients = coefficients;
        this.constant = constant;
        this.index = index;
    }

    public Hyperplane(E[] atoms, float[] coefficients, float constant, int index) {
        this.atoms = atoms;
        this.coefficients = coefficients;
        this.constant = constant;
        this.index = index;
    }

    public void addAtom(E atom, float coefficient) {
        atoms[index] = atom;
        coefficients[index] = coefficient;
        index++;
    }

    public int size() {
        return index;
    }


    public E getAtom(int index) {
        if (index >= this.index) {
            throw new IndexOutOfBoundsException("Tried to access atom at index " + index + ", but only " + this.index + " exist.");
        }

        return atoms[index];
    }


    public float getCoefficient(int index) {
        if (index >= this.index) {
            throw new IndexOutOfBoundsException("Tried to access variable coefficient at index " + index + ", but only " + this.index + " exist.");
        }

        return coefficients[index];
    }

    public void appendCoefficient(int index, float value) {
        if (index >= this.index) {
            throw new IndexOutOfBoundsException("Tried to access variable coefficient at index " + index + ", but only " + this.index + " exist.");
        }

        coefficients[index] += value;
    }

    public float getConstant() {
        return constant;
    }

    public void setConstant(float constant) {
        this.constant = constant;
    }

    public int indexOfTerm(E needle) {
        return ArrayUtils.indexOf(atoms, index, needle);
    }

    public E[] getAtoms() {
        return atoms;
    }

    public float[] getCoefficients() {
        return coefficients;
    }

}
