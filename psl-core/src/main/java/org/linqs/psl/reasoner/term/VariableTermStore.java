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

import org.linqs.psl.model.atom.GroundAtom;

/**
 * An interface for term stores that can handle some variable operations.
 */
public interface VariableTermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> extends TermStore<T, V> {
    /**
     * Get the total number of variables (dead or alive) tracked by this term store.
     * The number here must coincide with the size (not length) of the array returned by getVariableValues().
     */
    public int getNumVariables();

    /**
     * Get the total number of random variables tracked by this term store.
     */
    public int getNumRandomVariables();

    /**
     * Get the total number of observed variables tracked by this term store.
     */
    public int getNumObservedVariables();

    public Iterable<V> getVariables();

    /**
     * Is the term store loaded, and can it give an accurate term and variable count.
     */
    public boolean isLoaded();

    /**
     * Get the values for the variable atoms.
     * Changing a value in this array and calling syncAtoms() will change the actual atom's value.
     */
    public float[] getVariableValues();

    /**
     * Get the index that matches up to getVariableValues().
     */
    public int getVariableIndex(V variable);

    /**
     * Get the variable for the given index.
     */
    public float getVariableValue(int index);

    /**
     * Get all the variables tracked by this term store.
     * Note that variables are allowed to be null if they have been deleted.
     */
    public GroundAtom[] getVariableAtoms();
}
