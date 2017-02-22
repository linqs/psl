/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.model.atom;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

/**
 * Implements the {@link AtomManager} with a twist: this AtomManager will only return
 * {@link RandomVariableAtom RandomVariableAtoms} that were persisted in the Database
 * at instantiation.
 * <p>
 * All other types of Atoms are returned normally.
 *
 * @author Eric Norris <enorris@cs.umd.edu>
 */
public class PersistedAtomManager implements AtomManager {

	/**
	 * This AtomManager's connection to a database.
	 */
	protected final Database db;

	/**
	 * The set of all persisted RandomVariableAtoms at the time of this AtomManager's
	 * instantiation.
	 */
	protected final Set<RandomVariableAtom> persistedCache;

	/**
	 * Constructs a PersistedAtomManager with a built-in set of all the database's
	 * persisted RandomVariableAtoms.
	 *
	 * @param db  the Database to query for all getAtom() calls.
	 */
	public PersistedAtomManager(Database db) {
		this.db = db;
		this.persistedCache = new HashSet<RandomVariableAtom>();

		buildPersistedAtomCache();
	}

	protected void buildPersistedAtomCache() {
		// Iterate through all of the registered predicates in this database
		for (StandardPredicate predicate : db.getRegisteredPredicates()) {
			// Ignore any closed predicates, they will not return RandomVariableAtoms
			if (db.isClosed(predicate)) {
				continue;
			}

			List<RandomVariableAtom> atoms = db.getAllGroundRandomVariableAtoms(predicate);
			for (RandomVariableAtom atom : atoms) {
				persistedCache.add(atom);
			}
		}
	}

	@Override
	public GroundAtom getAtom(Predicate p, Constant... arguments) {
		GroundAtom atom = db.getAtom(p, arguments);
		if (!(atom instanceof RandomVariableAtom)) {
			return atom;
		}

		// Check if this is in our persisted atom cache
		if (persistedCache.contains(atom)) {
			return atom;
		}

		throw new IllegalArgumentException("Can only call getAtom() on persisted RandomVariableAtoms using a PersistedAtomManager. Cannot access " + atom);
	}

	@Override
	public ResultList executeQuery(DatabaseQuery query) {
		return db.executeQuery(query);
	}

	@Override
	public boolean isClosed(StandardPredicate predicate) {
		return db.isClosed(predicate);
	}

	public Iterable<RandomVariableAtom> getPersistedRVAtoms() {
		return Collections.unmodifiableSet(persistedCache);
	}
}
