/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
public abstract class Database implements ReadableDatabase, WritableDatabase {
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
    }

    public abstract GroundAtom getAtom(StandardPredicate predicate, boolean create, Constant... arguments);

    public boolean hasAtom(StandardPredicate predicate, Constant... arguments) {
        return getAtom(predicate, false, arguments) != null;
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

    public int countAllGroundAtoms(StandardPredicate predicate) {
        return countAllGroundAtoms(predicate, allPartitionIDs);
    }

    public abstract int countAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions);

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

    public Iterable<GroundAtom> getAllCachedAtoms() {
        return cache.getCachedAtoms();
    }

    public Iterable<RandomVariableAtom> getAllCachedRandomVariableAtoms() {
        return cache.getCachedRandomVariableAtoms();
    }

    public List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate) {
        return getAllGroundAtoms(predicate, allPartitionIDs);
    }

    public abstract List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions);

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

    public void commit(RandomVariableAtom atom) {
        List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>(1);
        atoms.add(atom);
        commit(atoms);
    }

    public void commitCachedAtoms() {
        commitCachedAtoms(false);
    }

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

    public int getCachedRVACount() {
        return cache.getRVACount();
    }

    public int getCachedObsCount() {
        return cache.getObsCount();
    }
}
