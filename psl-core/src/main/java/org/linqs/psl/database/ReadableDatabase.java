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

import org.linqs.psl.database.atom.AtomCache;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A database for retrieving {@link GroundAtom GroundAtoms}.
 *
 * To persist {@link GroundAtom GroundAtoms} use a WritableDatabase.
 */
public interface ReadableDatabase {
    /**
     * Returns the GroundAtom for the given Predicate and GroundTerms.
     *
     * Any GroundAtom with a {@link StandardPredicate} can be retrieved if and only
     * if its Predicate was registered with the DataStore at the time of the Database's
     * instantiation. Any GroundAtom with an ExternalFunctionalPredicate} can also be retrieved.
     * This method first checks the {@link AtomCache} to see if the GroundAtom already
     * exists in memory. If it does, then that object is returned.
     *
     * If the GroundAtom does not exist in memory, then it will be instantiated and
     * stored in the AtomCache before being returned. The subtype and state of the
     * instantiated GroundAtom depends on several factors:
     * <ul>
     *     <li>
     *         If the GroundAtom is persisted in a read Partition, then it will be
     *         instantiated as an {@link ObservedAtom} with the persisted state.
     *     </li>
     *     <li>
     *         If the GroundAtom is persisted in the write Partition, then it will be
     *         instantiated with the persisted state. It will be instantiated as an
     *         ObservedAtom if its Predicate is closed and as a {@link RandomVariableAtom}
     *         if it is open.
     *     </li>
     *     <li>
     *         If the GroundAtom has a StandardPredicate but is not persisted
     *         in any of the Database's partitions, it will be instantiated with a truth
     *         value of 0.0. It will be instantiated as an ObservedAtom if its Predicate
     *         is closed and as a RandomVariableAtom if it is open.
     *     </li>
     *     <li>
     *         If the GroundAtom has an ExternalFunctionalPredicate, then it will be
     *         instantiated as an ObservedAtom with the functionally defined
     *         truth value.
     *     </li>
     * </ul>
     *
     * @throws IllegalStateException if the Atom is persisted in multiple read Partitions
     */
    public GroundAtom getAtom(Predicate predicate, Constant... arguments);

    /**
     * Check to see if a ground atom exists in the database.
     * This looks for a real ground atom and ignores the closed-world assumption.
     * If found, the atom will be cached for subsequent requests to this or getAtom().
     */
    public boolean hasAtom(StandardPredicate predicate, Constant... arguments);

    /**
     * Get a count of all the ground atoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public int countAllGroundAtoms(StandardPredicate predicate);

    /**
     * Get a count of all the ground RandomVariableAtoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public int countAllGroundRandomVariableAtoms(StandardPredicate predicate);

    /**
     * Fetch all the ground atoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate);

    /**
     * Fetch all the GroundAtoms in this database's cache.
     */
    public Iterable<GroundAtom> getAllCachedAtoms();

    /**
     * Fetch all the RandomVariableAtoms in this database's cache.
     */
    public Iterable<RandomVariableAtom> getAllCachedRandomVariableAtoms();

    /**
     * Fetch all the ground RandomVariableAtoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public List<RandomVariableAtom> getAllGroundRandomVariableAtoms(StandardPredicate predicate);

    /**
     * Fetch all the ground ObservedAtoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public List<ObservedAtom> getAllGroundObservedAtoms(StandardPredicate predicate);

    /**
     * Returns all groundings of a Formula that match a DatabaseQuery.
     */
    public ResultList executeQuery(DatabaseQuery query);

    /**
     * Like executeQuery(), but specifically for grounding queries.
     * This will use extra optimizations.
     */
    public QueryResultIterable executeGroundingQuery(Formula formula);

    /**
     * Returns whether a StandardPredicate is closed in this Database.
     */
    public boolean isClosed(StandardPredicate predicate);

    /**
     * Get the number of RandomVariableAtoms in the database's cache.
     */
    public int getCachedRVACount();
}
