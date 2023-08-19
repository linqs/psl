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
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.atom.UnmanagedObservedAtom;
import org.linqs.psl.model.atom.UnmanagedRandomVariableAtom;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Parallel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The canonical owner of all ground atoms for a Database.
 *
 * AtomStore assume that all (non closed-world) ground atoms are persisted in memory.
 * After initialization, no queries to the database will be made.
 *
 * Ground atoms that exist, but are not strictly managed by an AtomStore are unmanaged atoms
 * (UnmanagedObservedAtom and UnmanagedRandomVariableAtom).
 * These atoms are generally not stored in the AtomStore and have no true backing in the database.
 * A soft exception to this are ground atoms derived from functional predicates.
 * These are constructed as unmanaged (since they do not have a database partition),
 * but will be kept in the data store (so their values are not re-computed).
 * Options.ATOM_STORE_STORE_ALL_ATOMS can be used to force all atoms to be stored.
 * Using this option, ground atoms will still be created with their Unmanaged class,
 * but will be stored anyways.
 *
 * Read operations are all thread-safe, but read and writes should not be intermixed.
 *
 * When initializing, the AtomStore will attempt to put RVA at lower indexes and will track the highest index of an RVA.
 * This is to allow downstream processes to potentially optimize storage requirements.
 */
public class AtomStore implements Iterable<GroundAtom> {
    private static final Logger log = Logger.getLogger(AtomStore.class);

    public static final int MIN_ALLOCATION = 100;

    private Database database;

    private String threadKey;

    private int numAtoms;
    private float[] atomValues;
    private GroundAtom[] atoms;
    private Map<Integer, List<GroundAtom>> connectedComponentsAtoms;
    private int maxRVAIndex;
    private boolean storeAllAtoms;

    private Map<Atom, Integer> lookup;

    public AtomStore(Database database) {
        this.database = database;

        threadKey = this.getClass().getName();

        numAtoms = 0;
        atomValues = null;
        atoms = null;
        connectedComponentsAtoms = null;
        maxRVAIndex = -1;
        storeAllAtoms = false;

        lookup = null;

        init();
    }

    public int size() {
        return numAtoms;
    }

    public int getMaxRVAIndex() {
        return maxRVAIndex;
    }

    /**
     * Get the values associated with the managed atoms.
     *
     * The array may be over-allocated, use size() to know the exact number.
     * Changes to this array will not be reflexted without a call to sync().
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


    public Map<Integer, List<GroundAtom>> getConnectedComponentAtoms() {
        return connectedComponentsAtoms;
    }

    public List<GroundAtom> getConnectedComponentAtoms(int index) {
        return connectedComponentsAtoms.get(index);
    }

    public GroundAtom getAtom(int index) {
        return atoms[index];
    }

    public float getAtomValue(int index) {
        return atomValues[index];
    }

    /**
     * Lookup the atom and get it.
     * A GroundAtom will always be returned, but it may be unmanaged (not persisted in this store).
     */
    public GroundAtom getAtom(Atom query) {
        Integer index = lookup.get(query);
        if (index != null) {
            return atoms[index.intValue()];
        }

        // The atom does not exist.
        // This is either a functional predicate, closed-world atom, or PAM exception.

        Term[] queryArguments = query.getArguments();
        Constant[] arguments = new Constant[queryArguments.length];

        for (int i = 0; i < arguments.length; i++) {
            if (!(queryArguments[i] instanceof Constant)) {
                throw new RuntimeException("Attempted to get an atom using variables (instead of constants): " + query);
            }

            arguments[i] = (Constant)queryArguments[i];
        }

        GroundAtom atom = null;
        boolean storeAtom = false;

        if (query.getPredicate() instanceof FunctionalPredicate) {
            float value = ((FunctionalPredicate)query.getPredicate()).computeValue(database, arguments);
            atom = new UnmanagedObservedAtom(query.getPredicate(), arguments, value);
            storeAtom = true;
        } else if (database.isClosed(query.getPredicate())) {
            atom = new UnmanagedObservedAtom(query.getPredicate(), arguments, 0.0f);
        } else {
            atom = new UnmanagedRandomVariableAtom((StandardPredicate)query.getPredicate(), arguments, 0.0f);
        }

        if (storeAtom || storeAllAtoms) {
            addAtom(atom);
        }

        return atom;
    }

    public GroundAtom getAtom(Predicate predicate, Constant... args) {
        QueryAtom query = getQuery(predicate, args);
        GroundAtom atom = getAtom(query);
        releaseQuery(query);
        return atom;
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

    public int getAtomIndex(Predicate predicate, Constant... args) {
        QueryAtom query = getQuery(predicate, args);
        int index = getAtomIndex(query);
        releaseQuery(query);
        return index;
    }

    /**
     * Check if there is an actual (not closed-world) atom managed by this store.
     */
    public boolean hasAtom(Atom query) {
        Integer index = lookup.get(query);
        return (index != null);
    }

    public boolean hasAtom(Predicate predicate, Constant... args) {
        QueryAtom query = getQuery(predicate, args);
        boolean result = hasAtom(query);
        releaseQuery(query);
        return result;
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

        connectedComponentsAtoms.get(root1).addAll(connectedComponentsAtoms.get(root2));
        connectedComponentsAtoms.remove(root2);
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

    /**
     * Commit all unobs atoms to the database.
     */
    public void commit() {
        commit(false);
    }

    /**
     * Commit atoms to the database.
     */
    public void commit(boolean includeObs) {
        if (includeObs) {
            database.commit(this);
        } else {
            database.commit(getRandomVariableAtoms());
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
        if (hasAtom(atom)) {
            GroundAtom otherAtom = getAtom(atom);

            if (atom.getPartition() == otherAtom.getPartition()) {
                // These are the same atom, a multi-thead access may got past an earlier check.
                return;
            }

            throw new IllegalStateException(String.format(
                    "Two identical atoms found in the same database." +
                    " First Instance: (Atom: %s, Type: %s, Partition: %d)," +
                    " Second Instance: (Atom: %s, Type: %s, Partition: %d).",
                    otherAtom, otherAtom.getClass(), otherAtom.getPartition(),
                    atom, atom.getClass(), atom.getPartition()));
        }

        addAtomInternal(atom);
    }

    public synchronized void addAtomInternal(GroundAtom atom) {
        if (atoms.length == numAtoms) {
            reallocate();
        }

        atom.setIndex(numAtoms);
        atom.setParent(numAtoms);

        atoms[numAtoms] = atom;
        atomValues[numAtoms] = atom.getValue();
        lookup.put(atom, numAtoms);
        connectedComponentsAtoms.put(numAtoms, new ArrayList<GroundAtom>());
        connectedComponentsAtoms.get(numAtoms).add(atom);

        if (atom instanceof RandomVariableAtom) {
            maxRVAIndex = numAtoms;
        }

        numAtoms++;
    }

    public void close() {
        numAtoms = 0;
        atomValues = null;
        atoms = null;
        maxRVAIndex = -1;

        if (lookup != null) {
            lookup.clear();
            lookup = null;
        }
    }

    /**
     * Get a threadsafe query buffer.
     * The returned QueryAtom should be released for reuse through releaseQuery().
     */
    private QueryAtom getQuery(Predicate predicate, Constant... args) {
        if (!Parallel.hasThreadObject(threadKey)) {
            Parallel.putThreadObject(threadKey, new ThreadResources(new QueryAtom(predicate, args)));
        }

        @SuppressWarnings("unchecked")
        ThreadResources resources = (ThreadResources)Parallel.getThreadObject(threadKey);

        if (resources.queryInUse) {
            // There are rare situations (with functional predicates) where a thread can already be using it's query
            // and request a new one.
            // In these rare situations, just allocate a new query.
            return new QueryAtom(predicate, args);
        }

        resources.query.assume(predicate, args);
        resources.queryInUse = true;

        return resources.query;
    }

    private void releaseQuery(QueryAtom query) {
        @SuppressWarnings("unchecked")
        ThreadResources resources = (ThreadResources)Parallel.getThreadObject(threadKey);

        if (resources == null) {
            throw new RuntimeException("Attempt to release a query that has not been allocated (by getQuery()).");
        }

        resources.queryInUse = false;
    }

    private void init() {
        assert(numAtoms == 0);

        log.debug("Initializing AtomStore.");

        int databaseAtomCount = getDatabaseAtomCount();
        double overallocationFactor = Options.ATOM_STORE_OVERALLOCATION_FACTOR.getDouble();
        int allocationSize = (int)(Math.max(MIN_ALLOCATION, databaseAtomCount) * (1.0 + overallocationFactor));
        storeAllAtoms = Options.ATOM_STORE_STORE_ALL_ATOMS.getBoolean();

        atomValues = new float[allocationSize];
        atoms = new GroundAtom[atomValues.length];
        connectedComponentsAtoms = new HashMap<Integer, List<GroundAtom>>();
        lookup = new HashMap<Atom, Integer>((int)(atomValues.length / 0.75));

        // Load open predicates first (to get RVAs at a lower index).
        for (StandardPredicate predicate : database.getDataStore().getRegisteredPredicates()) {
            if (database.isClosed(predicate)) {
                continue;
            }

            for (GroundAtom atom : database.getAllGroundAtoms(predicate)) {
                addAtom(atom);
            }
        }

        // Now load closed predicates.
        for (StandardPredicate predicate : database.getDataStore().getRegisteredPredicates()) {
            if (!database.isClosed(predicate)) {
                continue;
            }

            for (GroundAtom atom : database.getAllGroundAtoms(predicate)) {
                addAtom(atom);
            }
        }

        log.debug("AtomStore Initialized.");
    }

    private synchronized void reallocate() {
        int newSize = atoms.length * 2;

        atomValues = Arrays.copyOf(atomValues, newSize);
        atoms = Arrays.copyOf(atoms, newSize);
    }

    private int getDatabaseAtomCount() {
        int count = 0;

        for (StandardPredicate predicate : database.getDataStore().getRegisteredPredicates()) {
            count += database.countAllGroundAtoms(predicate);
        }

        return count;
    }

    private static class ThreadResources {
        public QueryAtom query;
        public boolean queryInUse;

        public ThreadResources(QueryAtom query) {
            this.query = query;
            queryInUse = false;
        }
    }
}
