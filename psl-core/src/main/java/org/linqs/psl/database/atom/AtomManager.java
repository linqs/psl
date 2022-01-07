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
package org.linqs.psl.database.atom;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

/**
 * Provides centralization and hooks for managing the {@link GroundAtom GroundAtoms}
 * that are instantiated from a {@link Database}.
 *
 * By wrapping {@link Database#getAtom(Predicate, Constant...)},
 * an AtomManager gives additional control over the GroundAtoms that come from
 * that Database.
 *
 * Additionally, AtomManagers can support other functionality that might require
 * coordination by providing a single component to call to carry out tasks.
 *
 * An AtomManager should be initialized with the Database for which it is managing
 * Atoms.
 */
public abstract class AtomManager {
    protected final Database db;

    /**
     * If the specific AtomManager supports access exceptions,
     * then this will control if they are actually thrown.
     */
    protected boolean enableAccessExceptions;

    public AtomManager(Database db) {
        this.db = db;
        this.enableAccessExceptions = true;
    }

    /**
     * Returns the GroundAtom for the given Predicate and GroundTerms.
     *
     * This method must call {@link Database#getAtom(Predicate, Constant...)}
     * to actually retrieve the GroundAtom.
     * Atom managers reserve the right to reject any atom by returning null here.
     *
     * @param predicate the Predicate of the Atom
     * @param arguments the GroundTerms of the Atom
     * @return the Atom
     */
    public abstract GroundAtom getAtom(Predicate predicate, Constant... arguments);

    /**
     * Calls {@link Database#executeQuery(DatabaseQuery)} on the
     * encapsulated Database.
     *
     * @param query the query to execute
     * @return the query results exactly as returned by the Database
     */
    public ResultList executeQuery(DatabaseQuery query) {
        return db.executeQuery(query);
    }

    /**
     * Calls {@link Database#executeGroundingQuery(Formula)} on the
     * encapsulated Database.
     */
    public QueryResultIterable executeGroundingQuery(Formula formula) {
        return db.executeGroundingQuery(formula);
    }

    /**
     * Calls {@link Database#isClosed(StandardPredicate)} on the
     * encapsulated Database.
     *
     * @param predicate the predicate to check
     * @return TRUE if predicate is closed in the Database
     */
    public boolean isClosed(StandardPredicate predicate) {
        return db.isClosed(predicate);
    }

    public Database getDatabase() {
        return db;
    }

    /**
     * Set whether or not to throw access exceptions.
     * @return the old setting for this value.
     */
    public boolean enableAccessExceptions(boolean newValue) {
        boolean oldValue = enableAccessExceptions;
        enableAccessExceptions = newValue;
        return oldValue;
    }

    /**
     * Get the number of RandomVariableAtoms cached by the database.
     */
    public int getCachedRVACount() {
        return db.getCachedRVACount();
    }

    /**
     * Get the number of ObservedAtoms cached by the database.
     */
    public int getCachedObsCount() {
        return db.getCachedObsCount();
    }

    /**
     * Decide whether or not to throw an access exception.
     * This will bypass |enableAccessExceptions|.
     */
    public abstract void reportAccessException(RuntimeException ex, GroundAtom offendingAtom);
}
