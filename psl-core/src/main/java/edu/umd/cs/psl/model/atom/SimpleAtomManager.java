/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * AtomManager that does not provide any functionality beyond passing calls
 * to underlying components.
 */
public class SimpleAtomManager implements AtomManager {
	
	private final Database db;
	
	public SimpleAtomManager(Database db) {
		this.db = db;
	}

	/**
	 * Calls {@link Database#getAtom(Predicate, GroundTerm...)}.
	 */
	@Override
	public GroundAtom getAtom(Predicate p, GroundTerm... arguments) {
		return db.getAtom(p, arguments);
	}

	@Override
	public ResultList executeQuery(DatabaseQuery query) {
		return db.executeQuery(query);
	}
	
	@Override
	public boolean isClosed(StandardPredicate predicate) {
		return db.isClosed(predicate);
	}
	
}
