/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * An abstraction layer for retrieving and persisting ground atoms from DataStore.
 *
 * A Database can be thought of as a view into a DataStore that respects partitions.
 *
 * Most users should not interact with a Database directly,
 * and instead use the AtomStore associated with the Database (getAtomStore()).
 */
public abstract class Database {
    /**
     * The backing data store that created this database.
     * Connection are obtained from here.
     */
    protected final DataStore parentDataStore;

    /**
     * Predicates that, for the purpose of this database, are closed.
     */
    private final Set<Predicate> closedPredicates;

    /**
     * The partition ID in which this database writes.
     */
    protected final Partition writePartition;
    protected final short writeID;

    /**
     * The partition IDs that this database only reads from.
     */
    protected final List<Partition> readPartitions;
    protected final List<Short> readIDs;

    protected final List<Short> allPartitionIDs;

    protected PersistedAtomStore atomStore;

    /**
     * Keeps track of the open / closed status of this database.
     */
    protected boolean closed;

    public Database(DataStore parent, Partition write, Partition[] read, Set<StandardPredicate> closed) {
        this.parentDataStore = parent;
        this.writePartition = write;
        this.writeID = write.getID();

        this.closedPredicates = new HashSet<Predicate>();
        if (closed != null) {
            this.closedPredicates.addAll(closed);
        }

        this.readPartitions = Arrays.asList(read);
        this.readIDs = new ArrayList<Short>(read.length);
        for (int i = 0; i < read.length; i++) {
            this.readIDs.add(Short.valueOf(read[i].getID()));
        }

        if (readIDs.contains(Short.valueOf(writeID))) {
            readIDs.remove(Short.valueOf(writeID));
        }

        allPartitionIDs = new ArrayList<Short>(readIDs.size() + 1);
        allPartitionIDs.addAll(readIDs);
        allPartitionIDs.add(writeID);

        atomStore = null;
    }

    public void close() {
        if (closed) {
            return;
        }

        if (atomStore != null) {
            atomStore.close();
            atomStore = null;
        }

        parentDataStore.releasePartitions(this);
        closed = true;
    }

    /**
     * Commit the specified atoms to the database.
     * The atoms will be placed into their respective partitions.
     */
    public abstract void commit(Iterable<? extends GroundAtom> atoms);

    /**
     * Like executeQuery(), but specifically for grounding queries.
     * This will use extra optimizations.
     */
    public abstract QueryResultIterable executeGroundingQuery(Formula formula);

    /**
     * Returns all groundings of a Formula that match a DatabaseQuery.
     */
    public abstract ResultList executeQuery(DatabaseQuery query);

    public abstract QueryResultIterable executeQueryIterator(RawQuery rawQuery);

    /**
     * A more general form for executeQuery().
     */
    public abstract ResultList executeSQL(RawQuery rawQuery);

    /**
     * Get a count of all the ground atoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     */
    public abstract int countAllGroundAtoms(StandardPredicate predicate, List<Short> partitions);

    /**
     * Fetch all the ground atoms for a predicate.
     * By "ground", we mean that it exists in the database.
     * This will not leverage the closed world assumption for any atoms.
     * Atoms returned by this method need to be managed.
     * Callers should heavily favor using an AtomStore over this method.
     */
    public abstract List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate, List<Short> partitions);

    public int countAllGroundAtoms(StandardPredicate predicate) {
        return countAllGroundAtoms(predicate, allPartitionIDs);
    }

    public List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate) {
        return getAllGroundAtoms(predicate, allPartitionIDs);
    }

    public PersistedAtomStore getAtomStore() {
        if (closed) {
            throw new IllegalStateException("Cannot get an AtomStore from a closed database.");
        }

        if (atomStore == null) {
            initAtomStore();
        }

        return atomStore;
    }

    /**
     * @return the DataStore backing this Database
     */
    public DataStore getDataStore() {
        return parentDataStore;
    }

    public List<Partition> getReadPartitions() {
        return Collections.unmodifiableList(readPartitions);
    }

    public Partition getWritePartition() {
        return writePartition;
    }

    /**
     * Returns whether a StandardPredicate is closed in this Database.
     */
    public boolean isClosed(Predicate predicate) {
        return closedPredicates.contains(predicate);
    }

    /**
     * Output all random variables to stdout in a human readable format: Foo('a', 'b') = 1.0.
     */
    public void outputRandomVariableAtoms() {
        for (StandardPredicate openPredicate : parentDataStore.getRegisteredPredicates()) {
            for (GroundAtom atom : getAtomStore().getRandomVariableAtoms(openPredicate)) {
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

            Iterator<RandomVariableAtom> atoms = getAtomStore().getRandomVariableAtoms(predicate).iterator();
            if (!atoms.hasNext()) {
                continue;
            }

            File outputFile = new File(outputDirectory, predicate.getName() + ".txt");
            try (BufferedWriter bufferedPredWriter = FileUtils.getBufferedWriter(outputFile)) {
                StringBuilder row = new StringBuilder();
                while (atoms.hasNext()) {
                    GroundAtom atom = atoms.next();

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

    private synchronized void initAtomStore() {
        // While waiting, another thread may have initialized the atom store.
        if (atomStore != null) {
            return;
        }

        atomStore = new PersistedAtomStore(this);
    }
}
