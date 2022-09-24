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
package org.linqs.psl.database;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Parallel;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The canonical owner of all ground atoms for a Database.
 *
 * AtomStore assume that all (non closed-world) ground atoms are persisted in memory.
 * After initialization, no queries to the database will be made.
 *
 * Read operations are all thread-safe, but read and writes should not be intermixed.
 */
public class AtomStore implements Iterable<GroundAtom> {
    private Database database;

    private String threadKey;

    private int numAtoms;
    private float[] atomValues;
    private GroundAtom[] atoms;

    private Map<QueryAtom, Integer> lookup;

    public AtomStore(Database database) {
        this.database = database;

        threadKey = this.getClass().getName();

        numAtoms = 0;
        atomValues = null;
        atoms = null;

        lookup = null;

        init();
    }

    public int size() {
        return numAtoms;
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

    public GroundAtom getAtom(int index) {
        return atoms[index];
    }

    public GroundAtom getAtom(QueryAtom query) {
        Integer index = lookup.get(query);
        if (index == null) {
            return null;

            // TODO(eriq): Closed-world and PAM exceptions.
        }

        return atoms[index.intValue()];
    }

    public GroundAtom getAtom(Predicate predicate, Term... args) {
        return getAtom(getQuery(predicate, args));
    }

    /**
     * Check if there is an actual (not closed-world) atom managed by this store.
     */
    public boolean hasAtom(QueryAtom query) {
        Integer index = lookup.get(query);
        return (index != null);
    }

    public boolean hasAtom(Predicate predicate, Term... args) {
        return hasAtom(getQuery(predicate, args));
    }

    /**
     * Synchronize the atoms (getAtoms()) with their values (getAtomValues()).
     */
    public void sync() {
        for (int i = 0; i < numAtoms; i++) {
            if (!(atoms[i] instanceof RandomVariableAtom)) {
                continue;
            }

            ((RandomVariableAtom)atoms[i]).setValue(atomValues[i]);
        }
    }

    /**
     * Commit all atoms to the database.
     */
    public void commit() {
        database.commit(this);
    }

    @Override
    public Iterator<GroundAtom> iterator() {
        return Arrays.asList(atoms).sublist(0, numAtoms);
    }

    public void addAtom(GroundAtom atom) {
        if (atoms.length == numAtoms) {
            reallocate();
        }

        atoms[numAtoms] = atom;
        atomValues[numAtoms] = atom.getValue();
        lookup.put(atom.getQueryAtom(), numAtoms);

        numAtoms++;
    }

    public void close() {
        numAtoms = 0;
        atomValues = null;
        atoms = null;

        if (lookup != null) {
            lookup.clear();
            lookup = null;
        }
    }

    /**
     * Get a threadsafe query buffer.
     */
    private QueryAtom getQuery(Predicate predicate, Term... args) {
        QueryAtom query = null;

        if (!Parallel.hasThreadObject(threadKey)) {
            query = new QueryAtom(predicate, args);
            Parallel.putThreadObject(threadKey, query);
        } else {
            query = (QueryAtom)Parallel.getThreadObject(threadKey);
            query.assume(predicate, args);
        }

        return query;
    }

    private void init() {
        assert(numAtoms == 0);

        int databaseAtomCount = getDatabaseAtomCount();
        double overallocationFactor = Options.ATOM_STORE_OVERALLOCATION_FACTOR.getDouble();
        atomValues = new float[(int)(databaseAtomCount * (1.0 + overallocationFactor))];
        atoms = new GroundAtom[atomValues.length];
        lookup = new HashMap<QueryAtom, Integer>((int)(atomValues.length / 0.75));

        boolean seenMirrorAtoms = false;

        for (StandardPredicate predicate : database.getDataStore().getRegisteredPredicates()) {
            for (GroundAtom atom : database.getAllGroundAtoms(predicate)) {
                addAtom(atom);
                if (predicate.getMirror() != null) {
                    seenMirrorAtoms = true;
                }
            }
        }

        if (seenMirrorAtoms) {
            int oldNumAtoms = numAtoms;
            for (int i = 0; i < oldNumAtoms; i++) {
                if (atoms[i].getPredicate().getMirror() == null) {
                    continue;
                }

                RandomVariableAtom mirrorAtom = new RandomVariableAtom(atoms[i].getPredicate().getMirror(), atoms[i].getArguments(), atoms[i].getValue());
                addAtom(mirrorAtom);

                mirrorAtom.setMirror((RandomVariableAtom)atoms[i]);
                ((RandomVariableAtom)atoms[i]).setMirror(mirrorAtom);
            }
        }
    }

    private void reallocate() {
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
}
