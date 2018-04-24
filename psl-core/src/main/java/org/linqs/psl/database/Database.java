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

import org.linqs.psl.model.atom.AtomCache;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DateAttribute;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.LongAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;

/**
 * A data model for retrieving and persisting {@link GroundAtom GroundAtoms}.
 *
 * Every GroundAtom retrieved from a Database is either a {@link RandomVariableAtom}
 * or an {@link ObservedAtom}. The method {@link #getAtom(Predicate, Constant...)}
 * determines which type a GroundAtom is. In addition, a GroundAtom with a
 * {@link StandardPredicate} can be persisted in a Database. If a
 * GroundAtom is persisted, it is persisted in one of the Partitions the
 * Database can read and is available for querying via {@link #executeQuery(DatabaseQuery)}.
 *
 * <h2>Setup</h2>
 *
 * Databases are instantiated via {@link DataStore#getDatabase} methods.
 *
 * A Database writes to and reads from one {@link Partition} of a DataStore
 * and can read from additional Partitions. The write Partition of a Database
 * may not be a read (or write) Partition of any other Database managed by the datastore.
 *
 * A Database can be instantiated with a set of StandardPredicates
 * to close. (Any StandardPredicate not closed initially remains open.) Whether
 * a StandardPredicate is open or closed affects the behavior of
 * {@link #getAtom(Predicate, Constant...)}.
 *
 * <h2>Retrieving GroundAtoms</h2>
 *
 * A Database is the canonical source for a set of GroundAtoms.
 * GroundAtoms should only be retrieved via {@link #getAtom(Predicate, Constant...)}
 * to ensure there exists only a single object for each GroundAtom from the Database.
 *
 * A Database contains an {@link AtomCache} which is used to store GroundAtoms
 * that have been instantiated in memory and ensure these objects are unique.
 * The AtomCache is accessible via {@link #getAtomCache()}.
 *
 * <h2>Persisting RandomVariableAtoms</h2>
 *
 * A RandomVariableAtom can be persisted (including updated) in the write
 * Partition via {@link #commit(RandomVariableAtom)} or
 * {@link RandomVariableAtom#commitToDB()}.
 *
 * <h2>Querying for Groundings</h2>
 *
 * {@link DatabaseQuery DatabaseQueries} can be run via {@link #executeQuery(DatabaseQuery)}.
 * Note that queries only act on the GroundAtoms persisted in Partitions and
 * GroundAtoms with FunctionalPredicates.
 */
public abstract class Database implements ReadableDatabase, WritableDatabase {

	public abstract GroundAtom getAtom(Predicate predicate, Constant... arguments);

	public abstract boolean hasAtom(StandardPredicate predicate, Constant... arguments);

	public abstract int countAllGroundAtoms(StandardPredicate predicate);

	public abstract int countAllGroundRandomVariableAtoms(StandardPredicate predicate);

	public abstract List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate);

	public abstract List<RandomVariableAtom> getAllGroundRandomVariableAtoms(StandardPredicate predicate);

	public abstract List<ObservedAtom> getAllGroundObservedAtoms(StandardPredicate predicate);

	public abstract boolean deleteAtom(GroundAtom a);

	public abstract void commit(RandomVariableAtom atom);

	public abstract void commit(Collection<RandomVariableAtom> atoms);

	public abstract void commit(Collection<RandomVariableAtom> atoms, int partitionId);

	public abstract void moveToWritePartition(StandardPredicate predicate, int oldPartitionId);

	public abstract ResultList executeQuery(DatabaseQuery query);

	public abstract ResultList executeGroundingQuery(Formula formula);

	public abstract boolean isClosed(StandardPredicate predicate);

	/**
	 * @return the DataStore backing this Database
	 */
	public abstract DataStore getDataStore();

	public abstract void close();
}
