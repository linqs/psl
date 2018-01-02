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
package org.linqs.psl.database;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

import java.util.HashSet;
import java.util.Set;

public class ReadOnlyDatabase {

	// The database this class wraps around
	private final Database db;

	public ReadOnlyDatabase(Database db) {
		this.db = db;
	}

	public GroundAtom getAtom(Predicate predicate, Constant... arguments) {
		if (predicate instanceof ExternalFunctionalPredicate) {
			return db.getAtom(predicate, arguments);
		} else if (predicate instanceof StandardPredicate) {
			if (db.isClosed((StandardPredicate)predicate)) {
				return db.getAtom(predicate, arguments);
			} else {
				throw new IllegalArgumentException("Can only call getAtom() on a closed or functional predicate.");
			}
		} else if (predicate instanceof SpecialPredicate) {
         throw new IllegalArgumentException("SpecialPredicates do not have tangible atoms.");
		} else {
			throw new IllegalArgumentException("Unknown predicate type: " + predicate.getClass().getName());
		}
	}

	public ResultList executeQuery(DatabaseQuery query) {
		Set<Atom> atoms = new HashSet<Atom>();

		for (Atom atom : query.getFormula().getAtoms(atoms)) {
         Predicate predicate = atom.getPredicate();

			if (predicate instanceof FunctionalPredicate) {
				continue;
			} else if (predicate instanceof StandardPredicate) {
				if (db.isClosed((StandardPredicate)predicate)) {
               continue;
            }
            throw new IllegalArgumentException("Can only perform queries over closed or functional predicates.");
         } else if (predicate instanceof SpecialPredicate) {
            throw new IllegalArgumentException("SpecialPredicates do not have tangible atoms.");
			} else {
				throw new IllegalArgumentException("Unknown predicate type: " + predicate.getClass().getName());
			}
		}

		return db.executeQuery(query);
	}

	@Override
	public boolean equals(Object other) {
		if (other != null && other instanceof ReadOnlyDatabase) {
			return db.equals(((ReadOnlyDatabase)other).db);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return db.hashCode();
	}
}
