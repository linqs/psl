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

/**
 * Information representing a raw online hyperplane.
 */
public class OnlineHyperplane <E extends ReasonerLocalVariable> extends Hyperplane{
    private ObservedAtom[] observeds;
    private float[] observedCoefficients;
    private int observedIndex;

    @SuppressWarnings("unchecked")
    public OnlineHyperplane(Class<E> localVariableClass, int maxVariableSize, float constant, int maxObservedSize) {
        this(localVariableClass, maxVariableSize, constant, new ObservedAtom [maxObservedSize], new float[maxObservedSize], 0);
    }

    public OnlineHyperplane(Class<E> localVariableClass, int maxVariableSize, float constant, ObservedAtom[] observeds, float[] observedCoefficients, int observedIndex) {
        super(localVariableClass, maxVariableSize, constant);
        this.observeds = observeds;
        this.observedCoefficients = observedCoefficients;
        this.observedIndex = observedIndex;
    }

    public void addObservedTerm(ObservedAtom observed, float observedCoefficient) {
        observeds[observedIndex] = observed;
        observedCoefficients[observedIndex] = observedCoefficient;
        observedIndex++;
    }

    public int getObservedIndex() {
        return observedIndex;
    }

    public ObservedAtom getObserved(int index) {
        if (index >= observedIndex) {
            throw new IndexOutOfBoundsException("Tried to access observed at index " + index + ", but only " + observedIndex + " exist.");
        }

        return observeds[index];
    }

    public float getObservedCoefficient(int index) {
        if (index >= observedIndex) {
            throw new IndexOutOfBoundsException("Tried to access observed coefficient at index " + index + ", but only " + observedIndex + " exist.");
        }

        return observedCoefficients[index];
    }

    public void appendObservedCoefficient(int index, float value) {
        if (index >= observedIndex) {
            throw new IndexOutOfBoundsException("Tried to access observed coefficient at index " + index + ", but only " + observedIndex + " exist.");
        }

        observedCoefficients[index] += value;
    }

    public int indexOfObserved(ObservedAtom needle) {
        return ArrayUtils.indexOf(observeds, observedIndex, needle);
    }

    public ObservedAtom[] getObserveds() {
        return observeds;
    }

    public float[] getObservedCoefficients() {
        return observedCoefficients;
    }
}
