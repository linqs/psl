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
package edu.umd.cs.psl.database;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.AtomCache;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * A data model for retrieving and storing {@link GroundAtom GroundAtoms}.
 * 
 * <h2>Usage</h2>
 * 
 * Databases are instantiated via {@link DataStore#getDatabase} methods.
 * <p>
 * A Database is the canonical source for a set of GroundAtoms.
 * GroundAtoms should only be retrieved via {@link #getAtom(Predicate, GroundTerm[])}
 * to ensure there exists only a single object for each GroundAtom from the Database.
 * (However, a Database might be wrapped in an {@link AtomManager}, which will pass
 * through calls to {@link AtomManager#getAtom(Predicate, GroundTerm[])}.)
 * <p>
 * A Database writes to and reads from one {@link Partition} of a DataStore
 * and can read from additional Partitions. The write Partition of a Database
 * may not be a read (or write) Partition of any other Database.
 * <p>
 * A Database contains an {@link AtomCache} which is used to store GroundAtoms
 * that have been loaded into memory and ensure these objects are unique.
 * The AtomCache is accessible via {@link #getAtomCache()}.
 * 
 * <h2>Conventions</h2>
 * 
 * Databases treat StandardPredicates as either <em>open</em> or
 * <em>closed</em>. A GroundAtom with a closed StandardPredicate is always an
 * ObservedAtom; otherwise it may a RandomVariableAtom.
 * <p>
 * RandomVariableAtoms will only be inserted and/or updated in the Database if
 * {@link #commit(RandomVariableAtom)} or {@link RandomVariableAtom#commitToDB()}
 * is called.
 * <p>
 * Any GroundAtom with a StandardPredicate that is not explicitly stored in the
 * Database has an implicit default truth value (and default confidence value
 * if the StandardPredicate is open).
 */
public interface Database {

	/**
	 * Returns the GroundAtom for the given Predicate and GroundTerms.
	 * <p>
	 * It will be a {@link RandomVariableAtom} if both of the following conditions
	 * are met:
	 * <ul>
	 *   <li>it has a {@link StandardPredicate} that is open</li>
	 *   <li>it is not stored in a read-only Partition</li>
	 * 	</ul>
	 * Otherwise, it will be an {@link ObservedAtom}.
	 * <p>
	 * If the GroundAtom is not in memory, it will be instantiated and stored
	 * in this Database's {@link AtomCache}.
	 * 
	 * @param p  the Predicate of the Atom
	 * @param arguments  the GroundTerms of the Atom
	 * @return the Atom
	 * @throws IllegalStateException  if the Atom exists in multiple read Partitions
	 */
	public GroundAtom getAtom(Predicate p, GroundTerm[] arguments);
	
	/**
	 * Persists a RandomVariableAtom in this Database's
	 * write Partition.
	 * <p>
	 * If the RandomVariableAtom is already stored in the write Partition,
	 * it will be updated.
	 * 
	 * @param atom  the Atom to persist
	 * @throws IllegalArgumentException  if atom does not belong to this Database
	 */
	public void commit(RandomVariableAtom atom);
	
	/**
	 * Returns all groundings of a Formula that match a DatabaseQuery.
	 * 
	 * @param query  the query to match
	 * @return a list of lists of substitutions of {@link GroundTerm GroundTerms}
	 *             for {@link Variable Variables}
	 * @throws IllegalArgumentException  if the query Formula is invalid
	 */
	public ResultList executeQuery(DatabaseQuery query);
	
	/**
	 * Returns whether a StandardPredicate is closed in this Database.
	 * 
	 * @param predicate  the Predicate to check
	 * @return TRUE if predicate is closed
	 */
	public boolean isClosed(StandardPredicate predicate);
	
	/**
	 * Convenience method.
	 * <p>
	 * Calls {@link DataStore#getUniqueID(Object)} on this Database's DataStore.
	 */
	public UniqueID getUniqueID(Object key);
	
	/**
	 * @return the DataStore backing this Database
	 */
	public DataStore getDataStore();
	
	/**
	 * @return the Database's AtomCache
	 */
	public AtomCache getAtomCache();
	
	/**
	 * Releases the {@link Partition Partitions} used by this Database.
	 */
	public void close();
}
