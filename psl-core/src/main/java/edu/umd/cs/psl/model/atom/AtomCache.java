/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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

/**
 * Storage for {@link GroundAtom GroundAtoms} so that a {@link Database}
 * always returns the same object for a GroundAtom.
 * <p>
 * Also serves as the factory for GroundAtoms for a Database.
 */
public class AtomCache {
	
	private Database db;
	
	/**
	 * Constructs a new AtomCache for a Database.
	 * 
	 * @param db  the Database for which GroundAtoms will be cached
	 */
	public AtomCache(Database db) {
		this.db = db;
	}
	
	public GroundAtom getCachedAtom(FormulaAtom atom) {
		return null;
	}
}
