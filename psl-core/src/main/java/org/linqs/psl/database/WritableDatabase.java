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

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;

/**
 * A database for persisting {@link GroundAtom GroundAtoms}.
 *
 * To retrieve {@link GroundAtom GroundAtoms} use a ReadableDatabase.
 */
public interface WritableDatabase {
    /**
     * Removes the GroundAtom from the Database, if it exists.
     */
    public boolean deleteAtom(GroundAtom atom);

    /**
     * Persists a RandomVariableAtom in this Database's write Partition.
     *
     * If the RandomVariableAtom has already been persisted in the write Partition,
     * it will be updated.
     */
    public void commit(RandomVariableAtom atom);

    /**
     * A batch form of commit().
     * When possible, this commit should be used.
     */
    public void commit(Iterable<RandomVariableAtom> atoms);

    /**
     * Commit all RandomVariableAtoms in the database's cache.
     * This defaults to all cached atoms.
     */
    public void commitCachedAtoms();

    /**
     * Commit all RandomVariableAtoms in the database's cache.
     */
    public void commitCachedAtoms(boolean onlyPersisted);

    /**
     * A form of commit() that allows the caller to choose the specific partition
     * the atoms are committed to.
     * Should only be used if you REALLY know what you are doing.
     */
    public void commit(Iterable<? extends GroundAtom> atoms, int partitionId);

    /**
     * Move all ground atoms of a predicate/partition combination into
     * the write partition.
     * Be careful not to call this while the database is in use.
     */
    public void moveToWritePartition(StandardPredicate predicate, int oldPartitionId);

    /**
     * Move all ground atoms of a predicate/partition combination into
     * the specified partition.
     * Be careful not to call this while the database is in use.
     */
    public void moveToPartition(StandardPredicate predicate, int oldPartitionId, int newPartitionId);

    /**
     * Releases the {@link Partition Partitions} used by this Database.
     */
    public void close();
}
