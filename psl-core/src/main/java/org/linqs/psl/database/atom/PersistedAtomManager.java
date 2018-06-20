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

import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

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
	 * This should only be set to true when the user understands why these
	 * exceptions are thrown in the first place and the grounding implications of
	 * not having the atom initially in the database.
	 */
	public static final String THROW_ACCESS_EXCEPTION_KEY = CONFIG_PREFIX + ".throwaccessexception";
	public static final boolean THROW_ACCESS_EXCEPTION_DEFAULT = true;
	/**
	 * The set of all persisted RandomVariableAtoms at the time of this AtomManager's
	 * instantiation.
	 */
	protected final Set<RandomVariableAtom> persistedCache;

	/**
	 * If false, ignore any atoms that would otherwise throw a PersistedAccessException.
	 * Instead, just give a single warning and return the RVA as-is.
	 */
	private final boolean throwOnIllegalAccess;
	private boolean warnOnIllegalAccess;

	/**
	 * Constructs a PersistedAtomManager with a built-in set of all the database's
	 * persisted RandomVariableAtoms.
	 *
	 * @param db  the Database to query for all getAtom() calls.
	 */
	public PersistedAtomManager(Database db) {
		super(db);
		this.persistedCache = new HashSet<RandomVariableAtom>();

		throwOnIllegalAccess = Config.getBoolean(THROW_ACCESS_EXCEPTION_KEY, THROW_ACCESS_EXCEPTION_DEFAULT);
		warnOnIllegalAccess = !throwOnIllegalAccess;

		buildPersistedAtomCache();
	}

	private void buildPersistedAtomCache() {
		// Iterate through all of the registered predicates in this database
		for (StandardPredicate predicate : db.getDataStore().getRegisteredPredicates()) {
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

	// This method is currently threadsafe, but if child classes edit the persisted cache,
	// then they will be responsible for synchronization.
	@Override
	public GroundAtom getAtom(Predicate predicate, Constant... arguments) {
		GroundAtom atom = db.getAtom(predicate, arguments);
		if (!(atom instanceof RandomVariableAtom)) {
			return atom;
		}
		RandomVariableAtom rvAtom = (RandomVariableAtom)atom;


		// Only check against the persisted cache if we need to warn or throw.
		if ((throwOnIllegalAccess || warnOnIllegalAccess) && !persistedCache.contains(rvAtom)) {
			if (throwOnIllegalAccess) {
				throw new PersistedAccessException(rvAtom);
			}

			warnOnIllegalAccess = false;
			log.warn(String.format("Found a non-persisted RVA (%s)." +
					" If you do not understand the implications of this warning," +
					" check your configuration and set '%s' to false." +
					" This warning will only be logged once.",
					rvAtom, THROW_ACCESS_EXCEPTION_KEY));
		}

		return rvAtom;
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
