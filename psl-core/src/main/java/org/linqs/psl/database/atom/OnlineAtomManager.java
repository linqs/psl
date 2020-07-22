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

    //Ground atoms that have been seen, but not instantiated.
    private Set<GroundAtom> obsAtomBuffer;
    private Set<GroundAtom> rvAtomBuffer;

    // The partition we will add new observed atoms to.
    protected int onlineReadPartition;

    public OnlineAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("OnlineAtomManagers require RDBMSDatabase.");
        }

        obsAtomBuffer = new HashSet<GroundAtom>();
        rvAtomBuffer = new HashSet<GroundAtom>();

        if (Options.ONLINE_READ_PARTITION.getString() == null) {
            onlineReadPartition = db.getReadPartitions().get(0).getID();
        } else {
            onlineReadPartition = Options.ONLINE_READ_PARTITION.getInt();
        }
    }

    //TODO(connor) Check to see if atom exists in the database, throw error if it does
    public synchronized ObservedAtom addObservedAtom(Predicate predicate, Float value, Constant... arguments) {
        AtomCache cache = db.getCache();
        ObservedAtom atom = cache.instantiateObservedAtom(predicate, arguments, value);

        obsAtomBuffer.add(atom);

        return atom;
    }

    public synchronized RandomVariableAtom addRandomVariableAtom(StandardPredicate predicate, Constant... arguments) {
        AtomCache cache = db.getCache();
        RandomVariableAtom atom = cache.instantiateRandomVariableAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
        addToPersistedCache(atom);

        rvAtomBuffer.add(atom);

        return atom;
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // OnlineAtomManger does not have access exceptions.
    }

    public int getOnlineReadPartition() {
        return onlineReadPartition;
    }

    public synchronized Boolean hasNewAtoms() {
        return (rvAtomBuffer.size() > 0) || (obsAtomBuffer.size() > 0);
    }

    public synchronized Set<GroundAtom> flushNewObservedAtoms() {
        Set<GroundAtom> obsAtomCache = new HashSet<GroundAtom>(obsAtomBuffer);
        obsAtomBuffer.clear();
        return obsAtomCache;
    }

    public synchronized Set<GroundAtom> flushNewRandomVariableAtoms() {
        Set<GroundAtom> rvAtomCache = new HashSet<GroundAtom>(rvAtomBuffer);
        rvAtomBuffer.clear();
        return rvAtomCache;
    }
}
