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
package edu.umd.cs.psl.model.atom;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

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
	private final Database db;
	
	/**
	 * The set of all persisted RandomVariableAtoms at the time of this AtomManager's
	 * instantiation.
	 */
	private final Set<RandomVariableAtom> persistedCache;
	
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
	
	private void buildPersistedAtomCache() {
		// Iterate through all of the registered predicates in this database
		for (StandardPredicate predicate : db.getRegisteredPredicates()) {
			// Ignore any closed predicates, they will not return RandomVariableAtoms
			if (db.isClosed(predicate))
				continue;
			
			// Construct the query for this predicate
			Variable vars[] = new Variable[predicate.getArity()];
			for (int i = 0; i < vars.length; i++)
				vars[i] = new Variable("V" + String.valueOf(i));
			Formula queryFormula = new QueryAtom(predicate, vars);
			
			// Execute the query and interpret the results
			ResultList list = db.executeQuery(new DatabaseQuery(queryFormula));
			for (int i = 0; i < list.size(); i ++) {
				// Query the database for this specific atom
				GroundAtom atom = db.getAtom(predicate, list.get(i));
				
				// If this is a RandomVariableAtom, store it in our cache
				if (atom instanceof RandomVariableAtom)
					persistedCache.add((RandomVariableAtom)atom);
			}
		}
	}
	
	@Override
	public GroundAtom getAtom(Predicate p, GroundTerm... arguments) {
		GroundAtom atom = db.getAtom(p, arguments);
		if (atom instanceof RandomVariableAtom) {
			// Check if this is in our persisted atom cache
			if (persistedCache.contains(atom))
				return atom;
			else
				throw new IllegalArgumentException("Can only call getAtom() on persisted RandomVariableAtoms using a PersistedAtomManager. Cannot access " + atom);
		} else
			return atom;
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
