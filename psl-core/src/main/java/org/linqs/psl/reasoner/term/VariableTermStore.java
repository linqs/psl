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

import org.linqs.psl.model.atom.GroundAtom;

import java.util.ArrayList;

/**
 * An interface for term stores that can handle some atom operations.
 */
public interface VariableTermStore<T extends ReasonerTerm, V extends ReasonerLocalAtom> extends TermStore<T, V> {
    int getNumVariables();

    public Iterable<V> getVariables();

    /**
     * Is the term store loaded, and can it give an accurate term and variable count.
     */
    public boolean isLoaded();

    /**
     * Get the values for variable atoms.
     * Changing a value in this array and calling syncAtoms() will change the actual atom's value.
     */
    public float[] getVariableValues();

    /**
     * Get the index that matches up to getAtomValues().
     */
    public int getVariableIndex(V atom);

    /**
     * Get the variable for the given index.
     */
    public float getVariableValue(int index);

    /**
     * Ensure that all the variable atoms have the same value as the array returned by getVariableValues().
     */
    public void syncAtoms();

    public GroundAtom[] getVariableAtoms();

}
