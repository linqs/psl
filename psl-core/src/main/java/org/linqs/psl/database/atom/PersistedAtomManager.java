/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

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
		super(db);
		this.persistedCache = new HashSet<RandomVariableAtom>();

		buildPersistedAtomCache();
	}

	private void buildPersistedAtomCache() {
		// Iterate through all of the registered predicates in this database
		for (StandardPredicate predicate : db.getRegisteredPredicates()) {
			// Ignore any closed predicates, they will not return RandomVariableAtoms
			if (db.isClosed(predicate)) {
				// Make the database cache all the atoms from the closed predicates,
				// but don't do anything with them now.
				db.getAllGroundAtoms(predicate);

				continue;
			}

			for (RandomVariableAtom atom : db.getAllGroundRandomVariableAtoms(predicate)) {
				persistedCache.add(atom);
			}
		}
	}

	@Override
	public GroundAtom getAtom(Predicate predicate, Constant... arguments) {
		GroundAtom atom = db.getAtom(predicate, arguments);
		if (!(atom instanceof RandomVariableAtom)) {
			return atom;
		}
		RandomVariableAtom rvAtom = (RandomVariableAtom)atom;

		// Check if this is in our persisted atom cache
		if (persistedCache.contains(rvAtom)) {
			return atom;
		}

		throw new PersistedAccessException(rvAtom);
	}

	/**
	 * Commit all the atoms in this manager's persisted cache.
	 */
	public void commitPersistedAtoms() {
		db.commit(persistedCache);
	}

	public Set<RandomVariableAtom> getPersistedRVAtoms() {
		return Collections.unmodifiableSet(persistedCache);
	}

	protected void addToPersistedCache(Set<RandomVariableAtom> atoms) {
		persistedCache.addAll(atoms);
	}

	public static class PersistedAccessException extends IllegalArgumentException {
		public RandomVariableAtom atom;
		public PersistedAccessException(RandomVariableAtom atom) {
			super("Can only call getAtom() on persisted RandomVariableAtoms" +
					" using a PersistedAtomManager." +
					" Cannot access " + atom);
			this.atom = atom;
		}
	}
}
