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

import org.linqs.psl.database.atom.AtomCache;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Parallel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
 *
 * <h2>Persisting RandomVariableAtoms</h2>
 *
 * A RandomVariableAtom can be persisted (including updated) in the write
 * Partition via {@link #commit(RandomVariableAtom)}.
 *
 * <h2>Querying for Groundings</h2>
 *
 * {@link DatabaseQuery DatabaseQueries} can be run via {@link #executeQuery(DatabaseQuery)}.
 * Note that queries only act on the GroundAtoms persisted in Partitions and
 * GroundAtoms with FunctionalPredicates.
 */
public abstract class Database {
    private static final String THREAD_QUERY_ATOM_KEY = Database.class.getName() + "::" + QueryAtom.class.getName();

    /**
     * The backing data store that created this database.
     * Connection are obtained from here.
     */
    protected final DataStore parentDataStore;

    /**
     * The partition ID in which this database writes.
     */
    protected final Partition writePartition;
    protected final int writeID;

    /**
     * The partition IDs that this database only reads from.
     */
    protected final List<Partition> readPartitions;
    protected final List<Integer> readIDs;

    protected final List<Integer> allPartitionIDs;

    /**
     * The atom cache for this database.
     */
    protected final AtomCache cache;

    protected AtomStore atomStore;

    /**
     * Keeps track of the open / closed status of this database.
     */
    protected boolean closed;

    public Database(DataStore parent, Partition write, Partition[] read) {
        this.parentDataStore = parent;
        this.writePartition = write;
        this.writeID = write.getID();

        this.readPartitions = Arrays.asList(read);
        this.readIDs = new ArrayList<Integer>(read.length);
        for (int i = 0; i < read.length; i++) {
            this.readIDs.add(read[i].getID());
        }

        if (readIDs.contains(Integer.valueOf(writeID))) {
            readIDs.remove(Integer.valueOf(writeID));
        }

        allPartitionIDs = new ArrayList<Integer>(readIDs.size() + 1);
        allPartitionIDs.addAll(readIDs);
        allPartitionIDs.add(writeID);

        this.cache = new AtomCache(this);
        atomStore = null;
    }

    public AtomStore getAtomStore() {
        if (atomStore != null) {
            atomStore = new AtomStore(this);
        }

        return atomStore;
    }

    /**
     * Returns all groundings of a Formula that match a DatabaseQuery.
     */
    public abstract ResultList executeQuery(DatabaseQuery query);

    /**
     * Like executeQuery(), but specifically for grounding queries.
     * This will use extra optimizations.
     */
    public abstract QueryResultIterable executeGroundingQuery(Formula formula);

    /**
     * Returns whether a StandardPredicate is closed in this Database.
     */
    public abstract boolean isClosed(StandardPredicate predicate);

    /**
     * Removes the GroundAtom from the Database, if it exists.
     */
    public abstract boolean deleteAtom(GroundAtom atom);

    /**
     * A batch form of commit().
     * When possible, this commit should be used.
     */
    public abstract void commit(Iterable<RandomVariableAtom> atoms);

    /**
     * A form of commit() that allows the caller to choose the specific partition
     * the atoms are committed to.
     * Should only be used if you REALLY know what you are doing.
     */
    public abstract void commit(Iterable<? extends GroundAtom> atoms, int partitionId);

    /**
     * Move all ground atoms of a predicate/partition combination into
     * the write partition.
     * Be careful not to call this while the database is in use.
     */
    public abstract void moveToWritePartition(StandardPredicate predicate, int oldPartitionId);

    /**
     * Move all ground atoms of a predicate/partition combination into
     * the specified partition.
     * Be careful not to call this while the database is in use.
     */
    public abstract void moveToPartition(StandardPredicate predicate, int oldPartitionId, int newPartitionId);

    /**
     * Releases the {@link Partition Partitions} used by this Database.
     */
    public abstract void close();

    /**
     * Returns the GroundAtom for the given Predicate and GroundTerms.
     *
     * Any GroundAtom with a {@link StandardPredicate} can be retrieved if and only
     * if its Predicate was registered with the DataStore at the time of the Database's
     * instantiation. Any GroundAtom with an ExternalFunctionalPredicate} can also be retrieved.
     * This method first checks the {@link AtomCache} to see if the GroundAtom already
     * exists in memory. If it does, then that object is returned.
     *
     * If the GroundAtom does not exist in memory, then it will be instantiated and
     * stored in the AtomCache before being returned. The subtype and state of the
     * instantiated GroundAtom depends on several factors:
     * <ul>
     *     <li>
     *         If the GroundAtom is persisted in a read Partition, then it will be
     *         instantiated as an {@link ObservedAtom} with the persisted state.
     *     </li>
     *     <li>
     *         If the GroundAtom is persisted in the write Partition, then it will be
     *         instantiated with the persisted state. It will be instantiated as an
     *         ObservedAtom if its Predicate is closed and as a {@link RandomVariableAtom}
     *         if it is open.
     *     </li>
     *     <li>
     *         If the GroundAtom has a StandardPredicate but is not persisted
     *         in any of the Database's partitions, it will be instantiated with a truth
     *         value of 0.0. It will be instantiated as an ObservedAtom if its Predicate
     *         is closed and as a RandomVariableAtom if it is open.
     *     </li>
     *     <li>
     *         If the GroundAtom has an ExternalFunctionalPredicate, then it will be
     *         instantiated as an ObservedAtom with the functionally defined
     *         truth value.
     *     </li>
     * </ul>
     *
     * @throws IllegalStateException if the Atom is persisted in multiple read Partitions
     */
    public abstract GroundAtom getAtom(Predicate predicate, Constant... arguments);

    /**
     * The full version of getAtom().
     * This version includes more detailed options on what how/when to create an atom.
     *
     * @param create Create an atom if one does not exist.
     * @param queryDBForClosed Query the database for atoms from closed predicates not in the cache,
     * @param trivialValue Do not create observed atoms that would have this value (just return null).
     */
    public abstract GroundAtom getAtom(StandardPredicate predicate,
            boolean create, boolean queryDBForClosed, double trivialValue,
            Constant... arguments);

    /**
     * Check to see if a ground atom exists in the database.
     * This looks for a real ground atom and ignores the closed-world assumption.
     * If found, the atom will be cached for subsequent requests to this or getAtom().
     */
    public boolean hasAtom(StandardPredicate predicate, Constant... arguments) {
        return getAtom(predicate, false, true, -1.0, arguments) != null;
    }

    public boolean hasCachedAtom(StandardPredicate predicate, Constant... arguments) {
        // Only allocate one GetAtom per thread.
        QueryAtom queryAtom = null;
        if (!Parallel.hasThreadObject(THREAD_QUERY_ATOM_KEY)) {
            queryAtom = new QueryAtom(predicate, arguments);
            Parallel.putThreadObject(THREAD_QUERY_ATOM_KEY, queryAtom);
        } else {
            queryAtom = (QueryAtom)(Parallel.getThreadObject(THREAD_QUERY_ATOM_KEY));
            queryAtom.assume(predicate, arguments);
        }

        return hasCachedAtom(queryAtom);
    }

    public boolean hasCachedAtom(QueryAtom atom) {
        return cache.getCachedAtom(atom) != null;
    }

    /**
     * Get a count of all the ground atoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public int countAllGroundAtoms(StandardPredicate predicate) {
        return countAllGroundAtoms(predicate, allPartitionIDs);
    }

    public abstract int countAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions);

    /**
     * Get a count of all the ground RandomVariableAtoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public int countAllGroundRandomVariableAtoms(StandardPredicate predicate) {
        // Closed predicates have no random variable atoms.
        if (isClosed(predicate)) {
            return 0;
        }

        // All the atoms should be random vairable, since we are pulling from the write parition of an open predicate.
        List<Integer> partitions = new ArrayList<Integer>(1);
        partitions.add(writeID);

        return countAllGroundAtoms(predicate, partitions);
    }

    /**
     * Fetch all the GroundAtoms in this database's cache.
     */
    public Iterable<GroundAtom> getAllCachedAtoms() {
        return cache.getCachedAtoms();
    }

    /**
     * Fetch all the RandomVariableAtoms in this database's cache.
     */
    public Iterable<RandomVariableAtom> getAllCachedRandomVariableAtoms() {
        return cache.getCachedRandomVariableAtoms();
    }

    /**
     * Fetch all the ground atoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate) {
        return getAllGroundAtoms(predicate, allPartitionIDs);
    }

    public abstract List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions);

    /**
     * Fetch all the ground RandomVariableAtoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public List<RandomVariableAtom> getAllGroundRandomVariableAtoms(StandardPredicate predicate) {
        // Closed predicates have no random variable atoms.
        if (isClosed(predicate)) {
            return new ArrayList<RandomVariableAtom>();
        }

        // All the atoms should be random vairable, since we are pulling from the write parition of an open predicate.
        List<Integer> partitions = new ArrayList<Integer>(1);
        partitions.add(writeID);
        List<GroundAtom> groundAtoms = getAllGroundAtoms(predicate, partitions);

        List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>(groundAtoms.size());
        for (GroundAtom atom : groundAtoms) {
            // This is only possible if the predicate is partially observed and this ground atom
            // was specified as a target and an observation.
            if (atom instanceof ObservedAtom) {
                throw new IllegalStateException(String.format(
                        "Found a ground atom (%s) that is both observed and a target." +
                        " An atom can only be one at a time. Check your data files.",
                        atom));
            }

            atoms.add((RandomVariableAtom)atom);
        }

        return atoms;
    }

    /**
     * Fetch all the ground ObservedAtoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public List<ObservedAtom> getAllGroundObservedAtoms(StandardPredicate predicate) {
        // Note that even open predicates may have observed atoms (partially observed predicates).

        // Can't have observed atoms without read partitions.
        if (readIDs.size() == 0) {
            return new ArrayList<ObservedAtom>();
        }

        // Only pull from the read partitions.
        List<GroundAtom> groundAtoms = getAllGroundAtoms(predicate, readIDs);

        // All the atoms will be observed since we are pulling from only read partitions.
        List<ObservedAtom> atoms = new ArrayList<ObservedAtom>(groundAtoms.size());
        for (GroundAtom atom : groundAtoms) {
            // This is only possible if the predicate is partially observed and this ground atom
            // was specified as a target and an observation.
            if (atom instanceof RandomVariableAtom) {
                throw new IllegalStateException(String.format(
                        "Found a ground atom (%s) that is both observed and a target." +
                        " An atom can only be one at a time. Check your data files.",
                        atom));
            }

            atoms.add((ObservedAtom)atom);
        }

        return atoms;
    }

    /**
     * Persists a RandomVariableAtom in this Database's write Partition.
     *
     * If the RandomVariableAtom has already been persisted in the write Partition,
     * it will be updated.
     */
    public void commit(RandomVariableAtom atom) {
        List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>(1);
        atoms.add(atom);
        commit(atoms);
    }

    /**
     * Commit all RandomVariableAtoms in the database's cache.
     * This defaults to all cached atoms.
     */
    public void commitCachedAtoms() {
        commitCachedAtoms(false);
    }

    /**
     * Commit all RandomVariableAtoms in the database's cache.
     */
    public void commitCachedAtoms(boolean onlyPersisted) {
        if (!onlyPersisted) {
            commit(getAllCachedRandomVariableAtoms());
        } else {
            commit(IteratorUtils.filter(getAllCachedRandomVariableAtoms(), new IteratorUtils.FilterFunction<RandomVariableAtom>() {
                @Override
                public boolean keep(RandomVariableAtom atom) {
                    return atom.getPersisted();
                }
            }));
        }
    }

    /**
     * @return the DataStore backing this Database
     */
    public DataStore getDataStore() {
        return parentDataStore;
    }

    public AtomCache getCache() {
        return cache;
    }

    public List<Partition> getReadPartitions() {
        return Collections.unmodifiableList(readPartitions);
    }

    public Partition getWritePartition() {
        return writePartition;
    }

    /**
     * Output all random variables to stdout in a human readable format: Foo('a', 'b') = 1.0.
     */
    public void outputRandomVariableAtoms() {
        for (StandardPredicate openPredicate : parentDataStore.getRegisteredPredicates()) {
            for (GroundAtom atom : getAllGroundRandomVariableAtoms(openPredicate)) {
                System.out.println(atom.toString() + " = " + atom.getValue());
            }
        }
    }

    /**
     * Output all random variables in a tab separated format.
     */
    public void outputRandomVariableAtoms(String outputDirectoryPath) {
        File outputDirectory = new File(outputDirectoryPath);
        FileUtils.mkdir(outputDirectory);

        for (StandardPredicate predicate : parentDataStore.getRegisteredPredicates()) {
            if (isClosed(predicate)) {
                continue;
            }

            List<RandomVariableAtom> atoms = getAllGroundRandomVariableAtoms(predicate);
            if (atoms.size() == 0) {
                continue;
            }

            File outputFile = new File(outputDirectory, predicate.getName() + ".txt");
            try (BufferedWriter bufferedPredWriter = FileUtils.getBufferedWriter(outputFile)) {
                StringBuilder row = new StringBuilder();
                for (GroundAtom atom : atoms) {
                    row.setLength(0);

                    for (Constant term : atom.getArguments()) {
                        row.append(term.rawToString());
                        row.append("\t");
                    }
                    row.append(atom.getValue());
                    row.append(System.lineSeparator());

                    bufferedPredWriter.write(row.toString());
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error writing predicate " + predicate + ".", ex);
            }
        }
    }

    /**
     * Get the number of RandomVariableAtoms in the database's cache.
     */
    public int getCachedRVACount() {
        return cache.getRVACount();
    }

    public int getCachedObsCount() {
        return cache.getObsCount();
    }
}
