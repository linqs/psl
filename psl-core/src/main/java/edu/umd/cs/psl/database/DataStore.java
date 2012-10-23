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

import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.loading.Updater;
import edu.umd.cs.psl.model.atom.StandardAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * Organizes {@link GroundAtom GroundAtoms} into {@link Partition Partitions}
 * and makes them available via {@link Database Databases}.
 */
public interface DataStore {

	/**
	 * Registers a Predicate so that {@link GroundAtom GroundAtoms} of that
	 * Predicate can be stored in and/or used by this DataStore.
	 * <p>
	 * If the Predicate is a {@link StandardPredicate}, then its Atoms will
	 * be stored in this DataStore. In that case, the
	 * {@link DataFormat DataFormats} of the arguments will be determined
	 * by {@link DataFormat#getDefaultFormat(Predicate, boolean)}.
	 * <p>
	 * All {@link SpecialPredicate SpecialPredicates} are already registered.
	 * 
	 * @param predicate  the predicate to register
	 * @param argnames  names of arguments
	 */
	public void registerPredicate(Predicate predicate, List<String> argnames);
	
	/**
	 * Registers a StandardPredicate so that {@link StandardAtom StandardAtoms}
	 * of that Predicate can be stored in and used by this DataStore.
	 * 
	 * @param predicate  the predicate to register
	 * @param argnames  names of arguments
	 * @param formats  the DataFormats to use for the arguments
	 */
	public void registerPredicate(Predicate predicate, List<String> argnames, DataFormat[] formats);
	
	/**
	 * Creates a Database that can read from and write to a {@link Partition} and
	 * additionally read from a set of additional Partitions.
	 * 
	 * @param write  the Partition to write to and read from
	 * @param read  additional Partitions to read from
	 * @return a new Database backed by this DataStore
	 * @throws IllegalArgumentException  if write is in use or if read is the
	 *                                       write Partition of another Database
	 */
	public Database getDatabase(Partition write, Partition... read);
	
	/**
	 * Creates a Database that can read from and write to a {@link Partition} and
	 * additionally read from a set of additional Partitions.
	 * <p>
	 * Additionally, defines a set of StandardPredicates as closed in the Database,
	 * meaning that all Atoms of that Predicate are ObservedAtoms.
	 * 
	 * @param write  the Partition to write to and read from
	 * @param toClose  set of StandardPredicates to close
	 * @param read  additional Partitions to read from
	 * @return a new Database backed by this DataStore
	 * @throws IllegalArgumentException  if write is in use or if read is the
	 *                                       write Partition of another Database
	 */
	public Database getDatabase(Partition write, Set<StandardPredicate> toClose, Partition... read);
	
	/**
	 * Creates an Inserter for inserting new {@link StandardAtom} information
	 * into a {@link Partition}.
	 * 
	 * @param predicate  the Predicate of the Atoms to be inserted
	 * @param partition  the Partition into which Atoms will be inserted
	 * @return the Inserter
	 * @throws IllegalArgumentException  if partition is in use
	 */
	public Inserter getInserter(StandardPredicate predicate, Partition partition);
	
	/**
	 * Creates an Updater for updating {@link StandardAtom} information
	 * in a {@link Partition}.
	 * 
	 * @param predicate  the Predicate of the Atoms to be updated
	 * @param partition  the Partition of the Atoms to be updated
	 * @return the Updater
	 * @throws IllegalArgumentException  if partition is in use
	 */
	public Updater getUpdater(StandardPredicate predicate, Partition partition);
	
	/**
	 * @return a set of Predicates registered with this DataStore
	 */
	public Set<Predicate> getRegisteredPredicates();
	
	/**
	 * Deletes all {@link Atom Atoms} in a Partition.
	 *  
	 * @param partition  the partition to delete
	 * @return the number of Atoms deleted
	 * @throws IllegalArgumentException  if partition is in use
	 */
	public int deletePartition(Partition partition);
	
	/**
	 * Releases all resources and locks obtained by this DataStore.
	 * 
	 * @throws IllegalStateException  if any Partitions are in use
	 */
	public void close();
	
}
