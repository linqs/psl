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

import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.util.ArrayUtils;

import java.lang.reflect.Array;

/**
 * Information representing a raw hyperplane.
 */
public class Hyperplane<E extends ReasonerLocalVariable> {
    private E[] variables;
    private float[] variableCoefficients;
    private int variableIndex;
    private float constant;

    private ObservedAtom[] observations;
    private float[] observedCoefficients;
    private int observedIndex;

    @SuppressWarnings("unchecked")
    public Hyperplane(Class<E> localVariableClass, int maxVariableSize, float constant, int maxObservedSize) {
        this((E[])Array.newInstance(localVariableClass, maxVariableSize), new float[maxVariableSize],0, constant,
                new ObservedAtom [maxObservedSize], new float[maxObservedSize], 0);
    }

    public Hyperplane(E[] variables, float[] variableCoefficients, int variableIndex, float constant,
                            ObservedAtom[] observations, float[] observedCoefficients, int observedIndex) {
        this.variables = variables;
        this.variableCoefficients = variableCoefficients;
        this.constant = constant;
        this.variableIndex = variableIndex;

        this.observations = observations;
        this.observedCoefficients = observedCoefficients;
        this.observedIndex = observedIndex;
    }

    public Hyperplane(E[] variables, float[] variableCoefficients, float constant, int variableIndex) {
        this.variables = variables;
        this.variableCoefficients = variableCoefficients;
        this.constant = constant;
        this.variableIndex = variableIndex;

        this.observations = new ObservedAtom[1];
        this.observedCoefficients = new float[1];
        this.observedIndex = 0;
    }

    public void addTerm(E variable, float coefficient) {
        variables[variableIndex] = variable;
        variableCoefficients[variableIndex] = coefficient;
        variableIndex++;
    }

    public void addObservedTerm(ObservedAtom observed, float observedCoefficient) {
        observations[observedIndex] = observed;
        observedCoefficients[observedIndex] = observedCoefficient;
        observedIndex++;
    }

    public int size() {
        return variableIndex;
    }

    public int observedSize() {
        return observedIndex;
    }

    public E getVariable(int index) {
        if (index >= variableIndex) {
            throw new IndexOutOfBoundsException("Tried to access variable at index " + index + ", but only " + variableIndex + " exist.");
        }

        return variables[index];
    }

    public ObservedAtom getObserved(int index) {
        if (index >= observedIndex) {
            throw new IndexOutOfBoundsException("Tried to access observed at index " + index + ", but only " + observedIndex + " exist.");
        }

        return observations[index];
    }

    public float getCoefficient(int index) {
        if (index >= variableIndex) {
            throw new IndexOutOfBoundsException("Tried to access variable coefficient at index " + index + ", but only " + variableIndex + " exist.");
        }

        return variableCoefficients[index];
    }

    public float getObservedCoefficient(int index) {
        if (index >= observedIndex) {
            throw new IndexOutOfBoundsException("Tried to access observed coefficient at index " + index + ", but only " + observedIndex + " exist.");
        }

        return observedCoefficients[index];
    }

    public void appendCoefficient(int index, float value) {
        if (index >= variableIndex) {
            throw new IndexOutOfBoundsException("Tried to access variable coefficient at index " + index + ", but only " + variableIndex + " exist.");
        }

        variableCoefficients[index] += value;
    }

    public void appendObservedCoefficient(int index, float value) {
        if (index >= observedIndex) {
            throw new IndexOutOfBoundsException("Tried to access observed coefficient at index " + index + ", but only " + observedIndex + " exist.");
        }

        observedCoefficients[index] += value;
    }

    public float getConstant() {
        return constant;
    }

    public void setConstant(float constant) {
        this.constant = constant;
    }

    public int indexOfVariable(E needle) {
        return ArrayUtils.indexOf(variables, variableIndex, needle);
    }

    public int indexOfObserved(ObservedAtom needle) {
        return ArrayUtils.indexOf(observations, observedIndex, needle);
    }

    public E[] getVariables() {
        return variables;
    }

    public ObservedAtom[] getObservations() {
        return observations;
    }

    public float[] getCoefficients() {
        return variableCoefficients;
    }

    public float[] getObservedCoefficients() {
        return observedCoefficients;
    }
}
