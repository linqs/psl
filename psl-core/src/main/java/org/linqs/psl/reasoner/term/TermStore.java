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
public abstract class TermStore<T extends ReasonerTerm, V extends ReasonerAtom> implements Iterable<T> {
    // Matching arrays for atom values and atoms.
    // If the atom is null, then it has been deleted by a child.
    // A -1 will be stored if we need to go to the atom for the value.
    protected float[] atomValues;
    protected GroundAtom[] atoms;

    // Keep track of atom indexes.
    protected Map<GroundAtom, Integer> atomIndexMap;

    // The count of all seen atoms (dead and alive).
    // Since children may delete atoms, we need a variable specifically for the next index.
    // (Otherwise, we could just use the size of the map as the next index.)
    protected int totalAtomCount;
    protected int numRandomVariableAtoms;
    protected int numObservedAtoms;

    public TermStore() {
        ensureAtomCapacity(Options.MEMORY_VTS_DEFAULT_SIZE.getInt());
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
    public abstract void addTerm(GroundRule rule, T term, Hyperplane<? extends ReasonerAtom> hyperplane);

    /**
     * Get a list of all the terms.
     */
    public abstract List<T> getTerms();

    /**
     * Get the term at the specified index.
     */
    public abstract T getTerm(long index);

    /**
     * Ensure that the underlying structures can have the required atom capacity.
     * This is more of a hint to the store about how much memory will be used.
     * This is best called on an empty store so it can prepare.
     */
    public synchronized void ensureAtomCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Atom capacity must be non-negative. Got: " + capacity);
        }

        if (atomIndexMap == null || totalAtomCount == 0) {
            // If there are no atoms, then (re-)allocate the atom storage.
            // The default load factor for Java HashSets is 0.75.
            atomIndexMap = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            totalAtomCount = 0;
            numRandomVariableAtoms = 0;
            numObservedAtoms = 0;

            atomValues = new float[capacity];
            atoms = new GroundAtom[capacity];
        } else if (totalAtomCount < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < totalAtomCount * 2) {
                capacity = totalAtomCount * 2;
            }

            // Reallocate and copy over atoms.
            Map<GroundAtom, Integer> newAtoms = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            newAtoms.putAll(atomIndexMap);
            atomIndexMap = newAtoms;

            atomValues = Arrays.copyOf(atomValues, capacity);
            atoms = Arrays.copyOf(atoms, capacity);
        }
    }

    /**
     * Get the total number of atoms (dead or alive) tracked by this term store.
     * The number here must coincide with the size (not length) of the array returned by getAtomValues().
     */
    public synchronized int getNumAtoms() {
        return totalAtomCount;
    }

    /**
     * Get the total number of RandomVariableAtoms tracked by this term store.
     */
    public synchronized int getNumRandomVariableAtoms() {
        return numRandomVariableAtoms;
    }

    /**
     * Get the total number of ObservedAtoms tracked by this term store.
     */
    public synchronized int getNumObservedAtoms() {
        return numObservedAtoms;
    }

    /**
     * Create a ReasonerAtom for a reasoner term.
     */
    public abstract V createReasonerAtom(GroundAtom atom);

    /**
     * Get all the atoms tracked by this term store.
     * Note that atoms are allowed to be null if they have been deleted.
     */
    public synchronized GroundAtom[] getAtoms() {
        return atoms;
    }

    /**
     * Get the atom at the specified index.
     */
    public synchronized GroundAtom getAtom(int index) {
        return atoms[index];
    }

    /**
     * Get the index that matches up to getAtomValues().
     */
    public synchronized int getAtomIndex(GroundAtom atom) {
        Integer index = atomIndexMap.get(atom);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    /**
     * Get the values for the atoms.
     * Changing a value in this array and calling syncAtoms() will change the actual atom's value.
     */
    public synchronized float[] getAtomValues() {
        return atomValues;
    }

    /**
     * Get the atom value for the given index.
     */
    public synchronized float getAtomValue(int index) {
        return atomValues[index];
    }

    /**
     * Notify the term store that the atoms have been updated through a process external to standard optimization.
     */
    public abstract void atomsExternallyUpdated();

    /**
     * Ensure that atoms tracked by this term store match the internal representation of those atoms.
     * Note observed atom and atoms not tracked by this term store are not updated.
     * @return The RMSE between the tracked atoms and their internal representation.
     */
    public synchronized double syncAtoms() {
        double movement = 0.0;

        for (int i = 0; i < totalAtomCount; i++) {
            if (atoms[i] == null) {
                continue;
            }

            if (atoms[i] instanceof RandomVariableAtom) {
                movement += Math.pow(atoms[i].getValue() - atomValues[i], 2);
                ((RandomVariableAtom) atoms[i]).setValue(atomValues[i]);
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
     * Is the term store loaded, and can it give an accurate term and atom count.
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
        totalAtomCount = 0;
        numRandomVariableAtoms = 0;
        numObservedAtoms = 0;

        if (atomIndexMap != null) {
            atomIndexMap.clear();
        }

        atomValues = null;
        atoms = null;
    }

    /**
     * Reset the existing terms for another round of inference.
     * The atom instance values are used to reset reasoner atom values.
     * Does NOT clear().
     */
    public synchronized void reset() {
        for (int i = 0; i < totalAtomCount; i++) {
            if (atoms[i] == null) {
                continue;
            }

            atomValues[i] = atoms[i].getValue();
        }
    }

    /**
     * Close down the term store, it will not be used any more.
     */
    public synchronized void close() {
        clear();

        if (atomIndexMap != null) {
            atomIndexMap = null;
        }
    }
}
