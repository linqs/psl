/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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

import java.util.HashSet;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueID;

public class ReadOnlyDatabase {

	// The database this class wraps around
	private final Database db;
	
	public ReadOnlyDatabase(Database db) {
		this.db = db;
		
	}
	
	public GroundAtom getAtom(Predicate p, Constant... arguments) {
		if (p instanceof FunctionalPredicate) {
			return db.getAtom(p, arguments);
		} else if (p instanceof StandardPredicate) {
			if (db.isClosed((StandardPredicate)p))
				return db.getAtom(p, arguments);
			else
				throw new IllegalArgumentException("Can only call getAtom() on a closed or functional predicate.");
		} else
			throw new IllegalArgumentException("Unknown predicate type: " + p.getClass().getName());
	}

	public ResultList executeQuery(DatabaseQuery query) {
		HashSet<Atom> atoms = new HashSet<Atom>();
		
		for (Atom atom : query.getFormula().getAtoms(atoms)) {
			if (atom.getPredicate() instanceof FunctionalPredicate)
				continue;
			else if (atom.getPredicate() instanceof StandardPredicate) {
				if (!db.isClosed((StandardPredicate)atom.getPredicate()))
					throw new IllegalArgumentException("Can only perform queries over closed or functional predicates.");
				else
					continue;
			} else
				throw new IllegalArgumentException("Unknown predicate type: " + atom.getPredicate().getClass().getName());
		}
		
		return db.executeQuery(query);
	}
	
	public UniqueID getUniqueID(Object key) {
		return db.getUniqueID(key);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof ReadOnlyDatabase)
			return db.equals(((ReadOnlyDatabase) o).db);
		else
			return false;
	}
	
	@Override
	public int hashCode() {
		return db.hashCode();
	}
}
