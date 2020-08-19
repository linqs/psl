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
 */
public class OnlineAtomManager extends PersistedAtomManager {
    // Atoms that have been seen, but not yet involved in grounding.
    private Set<GroundAtom> newObservedAtoms;
    private Set<GroundAtom> newRandomVariableAtoms;

    /**
     * The partition new observed atoms will be added to.
     */
    private int onlineReadPartition;

    public OnlineAtomManager(Database database) {
        super(database);

        if (!(database instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("OnlineAtomManagers require RDBMSDatabase.");
        }

        newObservedAtoms = new HashSet<GroundAtom>();
        newRandomVariableAtoms = new HashSet<GroundAtom>();

        onlineReadPartition = Options.ONLINE_READ_PARTITION.getInt();
        if (onlineReadPartition < 0) {
            onlineReadPartition = database.getReadPartitions().get(0).getID();
        }
    }

    public ObservedAtom addObservedAtom(StandardPredicate predicate, float value, Constant... arguments) {
        ObservedAtom atom = database.getCache().instantiateObservedAtom(predicate, arguments, value);
        newObservedAtoms.add(atom);
        return atom;
    }

    public RandomVariableAtom addRandomVariableAtom(StandardPredicate predicate, Constant... arguments) {
        RandomVariableAtom atom = database.getCache().instantiateRandomVariableAtom(predicate, arguments, 1.0f);
        newRandomVariableAtoms.add(atom);
        addToPersistedCache(atom);
        return atom;
    }

    public boolean hasAtom(StandardPredicate predicate, Constant... arguments) {
        return database.hasAtom(predicate, arguments);
    }

    public GroundAtom deleteAtom(StandardPredicate predicate, Constant... arguments) {
        GroundAtom atom = null;

        if (database.hasAtom(predicate, arguments)) {
            atom = database.getAtom(predicate, arguments);
            database.deleteAtom(atom);
            if (atom instanceof RandomVariableAtom) {
                persistedAtomCount--;
            }
        }

        return atom;
    }

    @Override
    public GroundAtom getAtom(Predicate predicate, Constant... arguments) {
        GroundAtom atom = super.getAtom(predicate, arguments);

        // Make sure atom was not deleted due to access exception, if so return null.
        if (atom instanceof RandomVariableAtom) {
            if (!database.hasAtom(((RandomVariableAtom)atom).getPredicate(), atom.getArguments())) {
                atom = null;
            }
        }

        return atom;
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // OnlineAtomManger does not have access exceptions.
        if (offendingAtom instanceof RandomVariableAtom) {
            deleteAtom(((RandomVariableAtom)offendingAtom).getPredicate(), offendingAtom.getArguments());
        }
    }

    public int getOnlineReadPartition() {
        return onlineReadPartition;
    }

    public synchronized Boolean hasNewAtoms() {
        return (newRandomVariableAtoms.size() > 0) || (newObservedAtoms.size() > 0);
    }

    /**
     * Return the existing new observed atoms and no longer consider them new.
     */
    public Set<GroundAtom> flushNewObservedAtoms() {
        Set<GroundAtom> atoms = new HashSet<GroundAtom>(newObservedAtoms);
        newObservedAtoms.clear();
        return atoms;
    }

    /**
     * Return the existing new random variable atoms and no longer consider them new.
     */
    public Set<GroundAtom> flushNewRandomVariableAtoms() {
        Set<GroundAtom> atoms = new HashSet<GroundAtom>(newRandomVariableAtoms);
        newRandomVariableAtoms.clear();
        return atoms;
    }
}
