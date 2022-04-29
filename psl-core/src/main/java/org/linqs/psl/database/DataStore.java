/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.Set;

/**
 * Makes {@link GroundAtom GroundAtoms} available via {@link Database Databases}.
 * <p>
 * GroundAtoms with {@link StandardPredicate StandardPredicates} can be persisted
 * in a DataStore's {@link Partition Partitions}. If a StandardPredicate has not
 * been persisted before in a DataStore, it must be registered via
 * {@link #registerPredicate(StandardPredicate)}.
 */
public interface DataStore {
    /**
     * Registers a StandardPredicate so that {@link GroundAtom GroundAtoms} of that
     * StandardPredicate can be persisted in this DataStore.
     * <p>
     * If GroundAtoms of a StandardPredicate were already persisted in this DataStore
     * at initialization, that StandardPredicate is already registered.
     *
     * @param predicate  the predicate to register
     */
    public void registerPredicate(StandardPredicate predicate);

    /**
     * Gets a new {@link Partition} of the DataStore with the given name.
     * If the partition doesn't exist, a new one will be created and added to the DataStore
     * metadata.
     * @param partitionName a human-readable name for the partition
     */
    public Partition getPartition(String partitionName);

    /**
     * Creates a Database that can read from and write to a {@link Partition} and
     * optionally read from additional Partitions.
     *
     * @param write the Partition to write to and read from
     * @param read additional Partitions to read from
     * @return a new Database backed by this DataStore
     * @throws IllegalArgumentException if write is in use or if read is the write
     *  Partition of another Database
     */
    public Database getDatabase(Partition write, Partition... read);

    public Database getDatabase(Partition write, StandardPredicate[] toClose, Partition... read);

    /**
     * Creates a Database that can read from and write to a {@link Partition} and
     * optionally read from additional Partitions.
     * <p>
     * Additionally, defines a set of StandardPredicates as closed in the Database,
     * meaning that all GroundAtoms of that Predicate are ObservedAtoms.
     *
     * @param write the Partition to write to and read from
     * @param toClose set of StandardPredicates to close
     * @param read additional Partitions to read from
     * @return a new Database backed by this DataStore
     * @throws IllegalArgumentException  if write is in use or if read is the
     *  write Partition of another Database
     */
    public Database getDatabase(Partition write, Set<StandardPredicate> toClose, Partition... read);

    /**
     * Get all the currenly open databases associated with this data store.
     */
    public Iterable<Database> getOpenDatabases();

    /**
     * Creates an Inserter for persisting new {@link GroundAtom GroundAtoms}
     * in a {@link Partition}.
     *
     * @param predicate the Predicate of the Atoms to be inserted
     * @param partition the Partition into which Atoms will be inserted
     * @return the Inserter
     * @throws IllegalArgumentException if partition is in use or predicate is
     *  not registered
     */
    public Inserter getInserter(StandardPredicate predicate, Partition partition);

    /**
     * Returns the set of StandardPredicates registered with this DataStore.
     */
    public Set<StandardPredicate> getRegisteredPredicates();

    /**
     * @return a set containing all {@link Partition Partitions} of this DataStore
     */
    public Set<Partition> getPartitions();

    /**
     * Deletes all {@link GroundAtom GroundAtoms} persisted in a Partition.
     *
     * @param partition  the partition to delete
     * @return the number of Atoms deleted
     * @throws IllegalArgumentException  if partition is in use
     */
    public int deletePartition(Partition partition);

    /**
     * Requests a new {@link Partition} that is assigned an auto-generated name
     * and the next unused ID. This partition will remain in the datastore
     * metadata unless explicitly deleted.
     *
     * @return new, unused partition
     */
    public Partition getNewPartition();

    /**
     * Releases all resources and locks obtained by this DataStore.
     *
     * @throws IllegalStateException  if any Partitions are in use
     */
    public void close();
}
