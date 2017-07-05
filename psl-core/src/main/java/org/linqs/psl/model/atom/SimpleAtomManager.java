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
package org.linqs.psl.model.atom;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

/**
 * AtomManager that does not provide any functionality beyond passing calls
 * to underlying components.
 */
public class SimpleAtomManager implements AtomManager {
	
	protected final Database db;
	
	public SimpleAtomManager(Database db) {
		this.db = db;
	}

	/**
	 * Calls {@link Database#getAtom(Predicate, Constant...)}.
	 */
	@Override
	public GroundAtom getAtom(Predicate p, Constant... arguments) {
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
