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
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.AggregateAtom;
import edu.umd.cs.psl.model.atom.AtomCache;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.FunctionalAtom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.atom.StandardAtom;
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
 * <h2>GroundAtom Types</h2>
 * 
 * Of the three basic types of GroundAtoms, {@link StandardAtom}, {@link AggregateAtom},
 * and {@link FunctionalAtom}, only StandardAtoms are stored in the Database.
 * Any AggregateAtom or FunctionalAtom is implicitly in the Database.
 * <p>
 * All StandardAtoms that exist in one of the Database's read Partitions are
 * {@link ObservedAtom ObservedAtoms}. If a StandardAtom does not, then it's a
 * {@link RandomVariableAtom}.
 * <p>
 * If a RandomVariableAtom is retrieved from the Database for the first time,
 * whether it is initially fixed depends on its {@link Predicate}.
 * <p>
 * Databases treat Predicates as either <em>open</em> or
 * <em>closed</em>. A RandomVariableAtom with a closed Predicate is initially
 * fixed. A RandomVariableAtom with an open Predicate is initially not.
 * Upon initialization of the Database, all Predicates are open.
 * <p>
 * RandomVariableAtoms will only be inserted and/or updated in the Database if
 * {@link #commit(RandomVariableAtom)} or {@link RandomVariableAtom#commitToDB()}
 * is called.
 * 
 * <h2>Conventions</h2>
 * 
 * Any RandomVariableAtom that is not stored in the Database has an implicit
 * truth value of 0.0.
 */
public interface Database {

	/**
	 * Returns the GroundAtom for the given Predicate and GroundTerms.
	 * <p>
	 * If the GroundAtom has not been loaded into memory before, it will be stored
	 * in this Database's {@link AtomCache}.
	 * 
	 * @param p  the Predicate of the Atom
	 * @param arguments  the GroundTerms of the Atom
	 * @return the Atom
	 */
	public GroundAtom getAtom(Predicate p, GroundTerm[] arguments);
	
	/**
	 * Converts a ground Atom to an AtomRecord and stores it in this Database's
	 * write {@link Partition}.
	 * <p>
	 * If an AtomRecord for the given Atom already exists in the write Partition,
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
	 * Opens a StandardPredicate.
	 * <p>
	 * If a StandardPredicate is open, then any {@link RandomVariableAtom}
	 * of that Predicate will be initially unfixed when instantiated in memory.
	 * 
	 * @param predicate  Predicate to open
	 */
	public void open(StandardPredicate predicate);
	
	/**
	 * Closes a StandardPredicate.
	 * <p>
	 * If a StandardPredicate is closed, then any {@link RandomVariableAtom}
	 * of that Predicate will be initially fixed when instantiated in memory.
	 * 
	 * @param predicate  Predicate to close
	 */
	public void close(StandardPredicate predicate);
	
	/**
	 * Returns whether a StandardPredicate is closed in this Database.
	 * 
	 * @param predicate  the Predicate to check
	 * @return TRUE if predicate is closed
	 */
	public boolean isClosed(StandardPredicate predicate);
	
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
