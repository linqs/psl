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
import org.linqs.psl.grounding.LazyGrounding;
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
    private Set<GroundAtom> newAtomBuffer;

     // Ground atoms that are currently being used for grounding.
    private Set<GroundAtom> newAtomCache;

    // Predicates that are currently being used for grounding.
    private Set<Predicate> onlinePredicates;

    // The partition we will add new observed atoms to.
    protected int onlineReadPartition;

    public OnlineAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("OnlineAtomManagers require RDBMSDatabase.");
        }

        obsAtomBuffer = new HashSet<GroundAtom>();
        rvAtomBuffer = new HashSet<GroundAtom>();
        newAtomBuffer = new HashSet<GroundAtom>();

        newAtomCache = new HashSet<GroundAtom>();
        onlinePredicates = new HashSet<Predicate>();

        if (Options.ONLINE_READ_PARTITION.getString() == null) {
            onlineReadPartition = db.getReadPartitions().get(0).getID();
        } else {
            onlineReadPartition = Options.ONLINE_READ_PARTITION.getInt();
        }
    }

    //TODO(connor) Check to see if atom exists in the database, throw error if it does
    public synchronized void addObservedAtom(Predicate predicate, Float value, Constant... arguments) {
        AtomCache cache = db.getCache();
        ObservedAtom atom = cache.instantiateObservedAtom(predicate, arguments, value);

        obsAtomBuffer.add(atom);
        newAtomBuffer.add(atom);
    }

    public synchronized void addRandomVariableAtom(StandardPredicate predicate, Constant... arguments) {
        AtomCache cache = db.getCache();
        RandomVariableAtom atom = cache.instantiateRandomVariableAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
        addToPersistedCache(atom);

        rvAtomBuffer.add(atom);
        newAtomBuffer.add(atom);
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // OnlineAtomManger does not have access exceptions.
    }

    public synchronized void activateNewAtoms() {
        db.commit(obsAtomBuffer, Partition.SPECIAL_READ_ID);
        db.commit(rvAtomBuffer, Partition.SPECIAL_WRITE_ID);

        newAtomCache.addAll(this.newAtomBuffer);
        onlinePredicates.addAll(LazyGrounding.getLazyPredicates(this.newAtomBuffer));

        obsAtomBuffer.clear();
        rvAtomBuffer.clear();
        newAtomBuffer.clear();
    }

    public synchronized void flushNewAtomCache() {
        for (Predicate onlinePredicate : onlinePredicates) {
            db.moveToPartition(onlinePredicate, Partition.SPECIAL_WRITE_ID, db.getWritePartition().getID());
            db.moveToPartition(onlinePredicate, Partition.SPECIAL_READ_ID, onlineReadPartition);
        }

        onlinePredicates.clear();
        newAtomCache.clear();
    }

    public synchronized Set<Predicate> getOnlinePredicates() {
        return onlinePredicates;
    }

    public synchronized Set<GroundAtom> getNewAtoms() {
        return newAtomBuffer;
    }

    public synchronized Set<GroundAtom> getNewObservedAtoms() {
        return obsAtomBuffer;
    }

    public synchronized Set<GroundAtom> getNewRandomVariableAtoms() {
        return rvAtomBuffer;
    }
}
