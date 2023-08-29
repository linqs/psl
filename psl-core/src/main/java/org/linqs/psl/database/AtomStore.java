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
package org.linqs.psl.database;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The canonical owner of ground atoms.
 */
public class AtomStore implements Iterable<GroundAtom> {
    private static final Logger log = Logger.getLogger(AtomStore.class);

    public static final int MIN_ALLOCATION = 100;

    protected int numAtoms;
    protected int numRVAtoms;
    protected float[] atomValues;
    protected GroundAtom[] atoms;
    private Map<Integer, List<Integer>> connectedComponentsAtomIndexes;
    protected int maxRVAIndex;
    protected Map<Atom, Integer> lookup;

    public AtomStore() {
        log.debug("Initializing AtomStore.");

        numAtoms = 0;
        numRVAtoms = 0;
        maxRVAIndex = -1;

        double overallocationFactor = Options.ATOM_STORE_OVERALLOCATION_FACTOR.getDouble();
        int allocationSize = (int)(MIN_ALLOCATION * (1.0 + overallocationFactor));

        atomValues = new float[allocationSize];
        atoms = new GroundAtom[atomValues.length];
        connectedComponentsAtomIndexes = new HashMap<Integer, List<Integer>>();
        lookup = new HashMap<Atom, Integer>((int)(atomValues.length / 0.75));
    }

    public AtomStore copy() {
        AtomStore atomStoreCopy = new AtomStore();

        for (int i = 0; i < numAtoms; i++) {
            atomStoreCopy.addAtom(atoms[i].copy());
        }

        return atomStoreCopy;
    }

    public int size() {
        return numAtoms;
    }

    public int getNumRVAtoms() {
        return numRVAtoms;
    }

    public int getMaxRVAIndex() {
        return maxRVAIndex;
    }

    /**
     * Get the values associated with the managed atoms.
     *
     * The array may be over-allocated, use size() to know the exact number.
     * Changes to this array will not be reflected without a call to sync().
     *
     * This array is only valid as long as no changes are made to this store.
     * Specifically, additions to the store may cause re-allocations.
     */
    public float[] getAtomValues() {
        return atomValues;
    }

    /**
     * Get the atoms managed by this store.
     * See getAtomValues() for general warnings.
     */
    public GroundAtom[] getAtoms() {
        return atoms;
    }


    public Map<Integer, List<Integer>> getConnectedComponentAtomIndexes() {
        return connectedComponentsAtomIndexes;
    }

    public List<Integer> getConnectedComponentAtomIndexes(int index) {
        return connectedComponentsAtomIndexes.get(index);
    }

    public GroundAtom getAtom(int index) {
        return atoms[index];
    }

    public float getAtomValue(int index) {
        return atomValues[index];
    }

    /**
     * Lookup the atom and get it's index.
     * Returns -1 if the atom does not exist.
     * Will ignore closed-world atoms.
     */
    public int getAtomIndex(Atom query) {
        Integer index = lookup.get(query);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    /**
     * Check if there is an actual (not closed-world) atom managed by this store.
     */
    public boolean hasAtom(Atom query) {
        Integer index = lookup.get(query);
        return (index != null);
    }

    /**
     * Find the root of the atom in the abstract disjoint-set data structure.
     * Additionally, update the parent of the atoms along the path to the root to point to grandparents, i.e., perform path halving.
     * This is to reduce the number of hops required to get to the root on the next call.
     */
    public synchronized int findAtomRoot(int atomIndex) {
        return findAtomRoot(getAtom(atomIndex));
    }

    public synchronized int findAtomRoot(GroundAtom atom) {
        int atomIndex = getAtomIndex(atom);
        if (atomIndex == -1) {
            // This atom is not managed by this store.
            return -1;
        }

        int parentIndex = atom.getParent();
        while (parentIndex != atomIndex) {
            GroundAtom parentAtom = getAtom(parentIndex);
            atom.setParent(parentAtom.getParent());

            atomIndex = parentIndex;
            atom = getAtom(parentIndex);
            parentIndex = atom.getParent();
        }

        return atomIndex;
    }

    /**
     * Merge the two atoms in the abstract disjoint-set data structure.
     * The root of the first atom will be the root of the merged set.
     */
    public synchronized void union(GroundAtom atom1, GroundAtom atom2) {
        // Replace nodes by their roots.
        int root1 = findAtomRoot(atom1);
        int root2 = findAtomRoot(atom2);

        if (root1 == -1 || root2 == -1) {
            // One of the atoms is not managed by this store.
            return;
        }

        if (root1 == root2) {
            // The atoms are already in the same set.
            return;
        }

        // Merge the two sets.
        GroundAtom rootAtom2 = getAtom(root2);
        rootAtom2.setParent(root1);

        connectedComponentsAtomIndexes.get(root1).addAll(connectedComponentsAtomIndexes.get(root2));
        connectedComponentsAtomIndexes.remove(root2);
    }

    /**
     * Synchronize the atoms (getAtoms()) with their values (getAtomValues()).
     * Return the RMSE between the atoms and their old values.
     */
    public double sync() {
        double movement = 0.0;

        for (int i = 0; i < numAtoms; i++) {
            if (!(atoms[i] instanceof RandomVariableAtom)) {
                continue;
            }

            movement += Math.pow(atoms[i].getValue() - atomValues[i], 2);
            ((RandomVariableAtom)atoms[i]).setValue(atomValues[i]);
        }

        return Math.sqrt(movement);

    }

    /**
     * The opposite of sync().
     * Copy the values from atoms (getAtoms()) into their values (getAtomValues()).
     */
    public void resetValues() {
        for (int i = 0; i < numAtoms; i++) {
            if (!(atoms[i] instanceof RandomVariableAtom)) {
                continue;
            }

            atomValues[i] = atoms[i].getValue();
        }
    }

    @Override
    public Iterator<GroundAtom> iterator() {
        return Arrays.asList(atoms).subList(0, numAtoms).iterator();
    }

    public Iterable<RandomVariableAtom> getRandomVariableAtoms() {
        return IteratorUtils.filterClass(this, RandomVariableAtom.class);
    }

    public Iterable<RandomVariableAtom> getRandomVariableAtoms(Predicate predicate) {
        return IteratorUtils.filter(IteratorUtils.filterClass(this, RandomVariableAtom.class), new IteratorUtils.FilterFunction<RandomVariableAtom>() {
            @Override
            public boolean keep(RandomVariableAtom atom) {
                return atom.getPredicate().equals(predicate);
            }
        });
    }

    public void addAtom(GroundAtom atom) {
        addAtomInternal(atom);
    }

    protected synchronized void addAtomInternal(GroundAtom atom) {
        if (atoms.length == numAtoms) {
            reallocate();
        }

        atom.setIndex(numAtoms);
        atom.setParent(numAtoms);

        atoms[numAtoms] = atom;
        atomValues[numAtoms] = atom.getValue();
        lookup.put(atom, numAtoms);
        connectedComponentsAtomIndexes.put(numAtoms, new ArrayList<Integer>());
        connectedComponentsAtomIndexes.get(numAtoms).add(numAtoms);

        if (atom instanceof RandomVariableAtom) {
            maxRVAIndex = numAtoms;
            numRVAtoms++;
        }

        numAtoms++;
    }

    public void close() {
        numAtoms = 0;
        numRVAtoms = 0;
        atomValues = null;
        atoms = null;
        maxRVAIndex = -1;

        if (lookup != null) {
            lookup.clear();
            lookup = null;
        }
    }

    protected synchronized void reallocate() {
        int newSize = atoms.length * 2;

        atomValues = Arrays.copyOf(atomValues, newSize);
        atoms = Arrays.copyOf(atoms, newSize);
    }
}
