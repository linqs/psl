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

import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.IteratorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
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
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "persistedatommanager";

    /**
     * Whether or not to throw an exception on illegal access.
     * Note that in most cases, this indicates incorrectly formed data.
     * This should only be set to false when the user understands why these
     * exceptions are thrown in the first place and the grounding implications of
     * not having the atom initially in the database.
     */
    public static final String THROW_ACCESS_EXCEPTION_KEY = CONFIG_PREFIX + ".throwaccessexception";
    public static final boolean THROW_ACCESS_EXCEPTION_DEFAULT = true;

    /**
     * If false, ignore any atoms that would otherwise throw a PersistedAccessException.
     * Instead, just give a single warning and return the RVA as-is.
     */
    private final boolean throwOnIllegalAccess;
    private boolean warnOnIllegalAccess;

    private int persistedAtomCount;

    public PersistedAtomManager(Database db) {
        this(db, false);
    }

    /**
     * Constructs a PersistedAtomManager with a built-in set of all the database's persisted RandomVariableAtoms.
     * @param prebuiltCache the database already has a populated atom cache, no need to build it again.
     */
    public PersistedAtomManager(Database db, boolean prebuiltCache) {
        super(db);

        throwOnIllegalAccess = Config.getBoolean(THROW_ACCESS_EXCEPTION_KEY, THROW_ACCESS_EXCEPTION_DEFAULT);
        warnOnIllegalAccess = !throwOnIllegalAccess;

        if (prebuiltCache) {
            persistedAtomCount = db.getCachedRVACount();
        } else {
            buildPersistedAtomCache();
        }
    }

    private void buildPersistedAtomCache() {
        persistedAtomCount = 0;

        // Iterate through all of the registered predicates in this database
        for (StandardPredicate predicate : db.getDataStore().getRegisteredPredicates()) {
            // Ignore any closed predicates, they will not return RandomVariableAtoms
            if (db.isClosed(predicate)) {
                // Make the database cache all the atoms from the closed predicates,
                // but don't do anything with them now.
                db.getAllGroundAtoms(predicate);

                continue;
            }

            // First pull all the random variable atoms and mark them as persisted.
            for (RandomVariableAtom atom : db.getAllGroundRandomVariableAtoms(predicate)) {
                atom.setPersisted(true);
                persistedAtomCount++;
            }

            // Now pull all the observed atoms so they will get cached.
            // This will throw if any observed atoms were previously seen as RVAs.
            db.getAllGroundObservedAtoms(predicate);
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
            if (!atom.getPersisted()) {
                atom.setPersisted(true);
                persistedAtomCount++;
            }
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
                    offendingAtom, THROW_ACCESS_EXCEPTION_KEY));
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
