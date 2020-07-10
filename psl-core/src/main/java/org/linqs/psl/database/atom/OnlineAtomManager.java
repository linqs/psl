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
package org.linqs.psl.database.atom;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

import java.util.HashSet;
import java.util.Set;

/**
 * A persisted atom manager that will add new atoms in an online setting.
 * If activateAtoms() is called, then all online atoms above the activation threshold
 * (Options.LAM_ACTIVATION_THRESHOLD) will be instantiated as real atoms.
 */
public class OnlineAtomManager extends PersistedAtomManager {
    private static final float DEFAULT_UNOBSERVED_VALUE = 1.0f;

    /**
     * All the ground atoms that have been seen, but not instantiated.
     */
    private Set<GroundAtom> obAtoms;
    private Set<GroundAtom> rvAtoms;
    private Set<GroundAtom> newAtoms;

    public OnlineAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("OnlineAtomManagers require RDBMSDatabase.");
        }

        obAtoms = new HashSet<GroundAtom>();
        rvAtoms = new HashSet<GroundAtom>();
        newAtoms = new HashSet<GroundAtom>();
    }

    //TODO(connor) Check to see if atom exists in the database, throw error if it does
    public synchronized void addObservedAtom(Predicate predicate, Float value, Constant... arguments) {
        AtomCache cache = db.getCache();
        ObservedAtom atom = cache.instantiateObservedAtom(predicate, arguments, value);

        obAtoms.add(atom);
        newAtoms.add(atom);
    }

    public synchronized void addRandomVariableAtom(StandardPredicate predicate, Constant... arguments) {
        AtomCache cache = db.getCache();
        RandomVariableAtom atom = cache.instantiateRandomVariableAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
        addToPersistedCache(atom);

        rvAtoms.add(atom);
        newAtoms.add(atom);
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // OnlineAtomManger does not have access exceptions.
    }

    public synchronized Set<GroundAtom> activateNewAtoms() {
        db.commit(obAtoms, Partition.SPECIAL_READ_ID);
        db.commit(rvAtoms, Partition.SPECIAL_WRITE_ID);

        Set<GroundAtom> newAtoms = new HashSet<>(this.newAtoms);

        obAtoms.clear();
        rvAtoms.clear();
        this.newAtoms.clear();

        return newAtoms;
    }

    public synchronized Set<GroundAtom> getNewAtoms() {
        return newAtoms;
    }

    public synchronized Set<GroundAtom> getNewObservedAtoms() {
        return obAtoms;
    }

    public synchronized Set<GroundAtom> getNewRandomVariableAtoms() {
        return rvAtoms;
    }
}
