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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A place to store terms that are to be optimized.
 */
public abstract class TermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements Iterable<T> {
    // Matching arrays for variables values and atoms.
    // If the atom is null, then it has been deleted by a child.
    // A -1 will be stored if we need to go to the atom for the value.
    protected float[] variableValues;
    protected GroundAtom[] variableAtoms;

    // Keep track of variable indexes.
    protected Map<GroundAtom, Integer> variables;

    // The count of all seen variables (dead and alive).
    // Since children may delete variables, we need a variable specifically for the next index.
    // (Otherwise, we could just use the size of the map as the next index.)
    protected int totalVariableCount;
    protected int numRandomVariableAtoms;
    protected int numObservedAtoms;

    public TermStore() {
        ensureVariableCapacity(Options.MEMORY_VTS_DEFAULT_SIZE.getInt());
    }

    /**
     * Ensure that the underlying structures can have the required term capacity.
     * This is more of a hint to the store about how much memory will be used.
     * This is best called on an empty store so it can prepare.
     */
    public abstract void ensureTermCapacity(long capacity);

    /**
     * Add a term to the store that was generated from the given ground rule.
     * The hyperplane used to create the term is provided for reference.
     */
    public abstract void addTerm(GroundRule rule, T term, Hyperplane<? extends ReasonerLocalVariable> hyperplane);

    /**
     * Get a list of all the terms.
     */
    public abstract List<T> getTerms();

    /**
     * Get the term at the specified index.
     */
    public abstract T getTerm(long index);

    /**
     * Ensure that the underlying structures can have the required variable capacity.
     * This is more of a hint to the store about how much memory will be used.
     * This is best called on an empty store so it can prepare.
     * Not all term stores will even manage variables.
     */
    public synchronized void ensureVariableCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Variable capacity must be non-negative. Got: " + capacity);
        }

        if (variables == null || totalVariableCount == 0) {
            // If there are no variables, then (re-)allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            totalVariableCount = 0;
            numRandomVariableAtoms = 0;
            numObservedAtoms = 0;

            variableValues = new float[capacity];
            variableAtoms = new GroundAtom[capacity];
        } else if (totalVariableCount < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < totalVariableCount * 2) {
                capacity = totalVariableCount * 2;
            }

            // Reallocate and copy over variables.
            Map<GroundAtom, Integer> newVariables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            newVariables.putAll(variables);
            variables = newVariables;

            variableValues = Arrays.copyOf(variableValues, capacity);
            variableAtoms = Arrays.copyOf(variableAtoms, capacity);
        }
    }

    /**
     * Get the total number of atoms (dead or alive) tracked by this term store.
     * The number here must coincide with the size (not length) of the array returned by getAtomValues().
     */
    public synchronized int getNumAtoms() {
        return totalVariableCount;
    }

    /**
     * Get the total number of random variables tracked by this term store.
     */
    public synchronized int getNumRandomVariables() {
        return numRandomVariableAtoms;
    }

    /**
     * Get the total number of observed variables tracked by this term store.
     */
    public synchronized int getNumObservedVariables() {
        return numObservedAtoms;
    }

    /**
     * Create a variable local to a specific reasoner term.
     */
    public abstract V createLocalVariable(GroundAtom atom);

    /**
     * Get all the atoms tracked by this term store.
     * Note that atoms are allowed to be null if they have been deleted.
     */
    public synchronized GroundAtom[] getAtoms() {
        return variableAtoms;
    }

    /**
     * Get the atom at the specified index.
     */
    public synchronized GroundAtom getAtom(int index) {
        return variableAtoms[index];
    }

    /**
     * Get the index that matches up to getAtomValues().
     */
    public synchronized int getAtomIndex(GroundAtom atom) {
        Integer index = variables.get(atom);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    /**
     * Get the values for the variable atoms.
     * Changing a value in this array and calling syncAtoms() will change the actual atom's value.
     */
    public synchronized float[] getAtomValues() {
        return variableValues;
    }

    /**
     * Get the atom value for the given index.
     */
    public synchronized float getAtomValue(int index) {
        return variableValues[index];
    }

    /**
     * Notify the term store that the variables have been updated through a process external to standard optimization.
     */
    public abstract void variablesExternallyUpdated();

    /**
     * Ensure that atoms tracked by this term store match the internal representation of those atoms.
     * Note observed atom and atoms not tracked by this term store are not updated.
     * @return The RMSE between the tracked atoms and their internal representation.
     */
    public synchronized double syncAtoms() {
        double movement = 0.0;

        for (int i = 0; i < totalVariableCount; i++) {
            if (variableAtoms[i] == null) {
                continue;
            }

            if (variableAtoms[i] instanceof RandomVariableAtom) {
                movement += Math.pow(variableAtoms[i].getValue() - variableValues[i], 2);
                ((RandomVariableAtom)variableAtoms[i]).setValue(variableValues[i]);
            }
        }

        return Math.sqrt(movement);
    }

    /**
     * Get an iterator over the terms in the store that does not write to disk.
     */
    public abstract Iterator<T> noWriteIterator();

    /**
     * A notification by the Reasoner that a single iteration is complete.
     * TermStores may use this as a chance to update and data structures.
     */
    public abstract void iterationComplete();

    /**
     * Is the term store loaded, and can it give an accurate term and variable count.
     */
    public abstract boolean isLoaded();

    /**
     * A notification by the Reasoner that optimization is about to begin.
     * TermStores may use this as a chance to finalize data structures.
     */
    public abstract void initForOptimization();

    public abstract long size();

    /**
     * Remove any existing terms and prepare for a new set.
     */
    public synchronized void clear() {
        totalVariableCount = 0;
        numRandomVariableAtoms = 0;
        numObservedAtoms = 0;

        if (variables != null) {
            variables.clear();
        }

        variableValues = null;
        variableAtoms = null;
    }

    /**
     * Reset the existing terms for another round of inference.
     * Atom values are used to reset variables.
     * Does NOT clear().
     */
    public synchronized void reset() {
        for (int i = 0; i < totalVariableCount; i++) {
            if (variableAtoms[i] == null) {
                continue;
            }

            variableValues[i] = variableAtoms[i].getValue();
        }
    }

    /**
     * Close down the term store, it will not be used any more.
     */
    public synchronized void close() {
        clear();

        if (variables != null) {
            variables = null;
        }
    }
}
