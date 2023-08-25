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
import org.linqs.psl.model.atom.UnmanagedObservedAtom;
import org.linqs.psl.model.atom.UnmanagedRandomVariableAtom;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Parallel;

import java.util.HashMap;

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
public class PersistedAtomStore extends AtomStore {
    private static final Logger log = Logger.getLogger(PersistedAtomStore.class);

    private Database database;

    private String threadKey;
    private boolean storeAllAtoms;

    public PersistedAtomStore(Database database) {
        super();

        this.database = database;

        threadKey = this.getClass().getName();

        storeAllAtoms = false;

        int databaseAtomCount = getDatabaseAtomCount();
        double overallocationFactor = Options.ATOM_STORE_OVERALLOCATION_FACTOR.getDouble();
        int allocationSize = (int)(Math.max(MIN_ALLOCATION, databaseAtomCount) * (1.0 + overallocationFactor));
        storeAllAtoms = Options.ATOM_STORE_STORE_ALL_ATOMS.getBoolean();

        atomValues = new float[allocationSize];
        atoms = new GroundAtom[atomValues.length];
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

    public int getAtomIndex(Predicate predicate, Constant... args) {
        QueryAtom query = getQuery(predicate, args);
        int index = getAtomIndex(query);
        releaseQuery(query);
        return index;
    }

    public boolean hasAtom(Predicate predicate, Constant... args) {
        QueryAtom query = getQuery(predicate, args);
        boolean result = hasAtom(query);
        releaseQuery(query);
        return result;
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

    protected void releaseQuery(QueryAtom query) {
        @SuppressWarnings("unchecked")
        ThreadResources resources = (ThreadResources)Parallel.getThreadObject(threadKey);

        if (resources == null) {
            throw new RuntimeException("Attempt to release a query that has not been allocated (by getQuery()).");
        }

        resources.queryInUse = false;
    }

    protected int getDatabaseAtomCount() {
        int count = 0;

        for (StandardPredicate predicate : database.getDataStore().getRegisteredPredicates()) {
            count += database.countAllGroundAtoms(predicate);
        }

        return count;
    }

    protected static class ThreadResources {
        public QueryAtom query;
        public boolean queryInUse;

        public ThreadResources(QueryAtom query) {
            this.query = query;
            queryInUse = false;
        }
    }
}
