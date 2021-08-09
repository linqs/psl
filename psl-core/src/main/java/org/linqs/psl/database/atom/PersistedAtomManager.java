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
package org.linqs.psl.database.atom;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.predicate.model.ModelPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.util.IteratorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Implements the {@link AtomManager} with a twist: this AtomManager will only return
 * {@link RandomVariableAtom RandomVariableAtoms} that were persisted in the Database
 * at instantiation.
 *
 * All other types of Atoms are returned normally.
 *
 * getAtom() is thread-safe.
 */
public class PersistedAtomManager extends AtomManager {
    private static final Logger log = LoggerFactory.getLogger(PersistedAtomManager.class);

    /**
     * If false, ignore any atoms that would otherwise throw a PersistedAccessException.
     * Instead, just give a single warning and return the RVA as-is.
     */
    private final boolean throwOnIllegalAccess;
    private boolean warnOnIllegalAccess;

    /**
     * The initial value to give atoms accessed illegally.
     */
    private InitialValue initialValueOnIllegalAccess;

    protected int persistedAtomCount;

    public PersistedAtomManager(Database db) {
        this(db, false);
    }

    public PersistedAtomManager(Database db, boolean prebuiltCache) {
        this(db, prebuiltCache, InitialValue.ATOM);
    }

    /**
     * Constructs a PersistedAtomManager with a built-in set of all the database's persisted RandomVariableAtoms.
     * @param prebuiltCache the database already has a populated atom cache, no need to build it again.
     * @param initialValueOnIllegalAccess the initial value to give an atom accessed illegally.
     */
    public PersistedAtomManager(Database db, boolean prebuiltCache, InitialValue initialValueOnIllegalAccess) {
        super(db);

        throwOnIllegalAccess = Options.PAM_THROW_ACCESS_EXCEPTION.getBoolean();
        warnOnIllegalAccess = !throwOnIllegalAccess;

        this.initialValueOnIllegalAccess = initialValueOnIllegalAccess;

        if (prebuiltCache) {
            persistedAtomCount = db.getCachedRVACount();
        } else {
            buildPersistedAtomCache();
        }
    }

    private void buildPersistedAtomCache() {
        persistedAtomCount = 0;

        // Keep track of mirror variables to commit them to the database.
        List<RandomVariableAtom> mirrorAtoms = new LinkedList<RandomVariableAtom>();

        // Iterate through all of the registered predicates in this database
        for (StandardPredicate predicate : db.getDataStore().getRegisteredPredicates()) {
            // Ignore any closed predicates, they will not return RandomVariableAtoms
            if (db.isClosed(predicate)) {
                // Make the database cache all the atoms from the closed predicates,
                // but don't do anything with them now.
                db.getAllGroundAtoms(predicate);

                continue;
            }

            // Fixed mirrors will be instantiated when the other part of the mirror is instantiated.
            if (predicate instanceof ModelPredicate) {
                continue;
            }

            // First pull all the random variable atoms and mark them as persisted.
            for (RandomVariableAtom atom : db.getAllGroundRandomVariableAtoms(predicate)) {
                atom.setPersisted(true);
                persistedAtomCount++;

                // If this predicate has a mirror, ensure that the other half of the mirror pair is created.
                if (predicate.getMirror() != null) {
                    RandomVariableAtom mirrorAtom = (RandomVariableAtom)db.getAtom(predicate.getMirror(), true, atom.getArguments());
                    mirrorAtoms.add(mirrorAtom);

                    atom.setMirror(mirrorAtom);
                    mirrorAtom.setMirror(atom);

                    mirrorAtom.setPersisted(true);
                    persistedAtomCount++;
                }
            }

            // Now pull all the observed atoms so they will get cached.
            // This will throw if any observed atoms were previously seen as RVAs.
            db.getAllGroundObservedAtoms(predicate);

            if (mirrorAtoms.size() > 0) {
                db.commit(mirrorAtoms);
                mirrorAtoms.clear();
            }
        }
    }

    // This method is currently threadsafe, but if child classes edit the persisted cache,
    // then they will be responsible for synchronization.
    @Override
    public GroundAtom getAtom(Predicate predicate, Constant... arguments) {
        GroundAtom atom = db.getAtom(predicate, arguments);
        if (!(atom instanceof RandomVariableAtom)) {
            return atom;
        }
        RandomVariableAtom rvAtom = (RandomVariableAtom)atom;

        if (!rvAtom.getPersisted()) {
            if (!rvAtom.getAccessException()) {
                // This is the first time we have seen this atom.
                rvAtom.setValue(initialValueOnIllegalAccess.getVariableValue(rvAtom));
            }

            rvAtom.setAccessException(true);
        }

        if (enableAccessExceptions && (throwOnIllegalAccess || warnOnIllegalAccess) && rvAtom.getAccessException()) {
            reportAccessException(null, rvAtom);
        }

        return rvAtom;
    }

    /**
     * Commit all the atoms in this manager's persisted cache.
     */
    public void commitPersistedAtoms() {
        db.commitCachedAtoms(true);
    }

    public int getPersistedCount() {
        return persistedAtomCount;
    }

    public Iterable<RandomVariableAtom> getPersistedRVAtoms() {
        return IteratorUtils.filter(db.getAllCachedRandomVariableAtoms(), new IteratorUtils.FilterFunction<RandomVariableAtom>() {
            @Override
            public boolean keep(RandomVariableAtom atom) {
                return atom.getPersisted();
            }
        });
    }

    protected void addToPersistedCache(Set<RandomVariableAtom> atoms) {
        for (RandomVariableAtom atom : atoms) {
            addToPersistedCache(atom);
        }
    }

    protected void addToPersistedCache(RandomVariableAtom atom) {
        if (!atom.getPersisted()) {
            atom.setPersisted(true);
            persistedAtomCount++;
        }
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        if (throwOnIllegalAccess) {
            if (ex == null) {
                ex = new PersistedAccessException((RandomVariableAtom)offendingAtom);
            }

            throw ex;
        }

        if (warnOnIllegalAccess) {
            warnOnIllegalAccess = false;
            log.warn(String.format("Found a non-persisted RVA (%s)." +
                    " If you do not understand the implications of this warning," +
                    " check your configuration and set '%s' to true." +
                    " This warning will only be logged once.",
                    offendingAtom, Options.PAM_THROW_ACCESS_EXCEPTION.name()));
        }
    }

    public static class PersistedAccessException extends IllegalArgumentException {
        public RandomVariableAtom atom;

        public PersistedAccessException(RandomVariableAtom atom) {
            super("Can only call getAtom() on persisted RandomVariableAtoms (RVAs)" +
                    " using a PersistedAtomManager." +
                    " Cannot access " + atom + "." +
                    " This typically means that provided data is insufficient." +
                    " An RVA (atom to be inferred (target)) was constructed during" +
                    " grounding that does not exist in the provided data.");
            this.atom = atom;
        }
    }
}
