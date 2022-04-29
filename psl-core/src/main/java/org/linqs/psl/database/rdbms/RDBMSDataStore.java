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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The RDMBSDataStore is an RDBMS implementation of the DataStore interface.
 * It will connect to any RDBMS that has a supporting {@link DatabaseDriver} implementation.
 */
public class RDBMSDataStore implements DataStore {
    private static final Logger log = LoggerFactory.getLogger(RDBMSDataStore.class);

    private static final Set<RDBMSDataStore> openDataStores = new HashSet<RDBMSDataStore>();

    /**
     * This Database Driver associated to the datastore.
     */
    private DatabaseDriver dbDriver;

    /**
     * Metadata
     */
    private DataStoreMetadata metadata;

    /**
     * All the read partitions mapped to the databases that are using them.
     */
    private final Map<Partition, List<Database>> openDatabases;

    /**
     * All write partitions open in this DataStore.
     */
    private final Set<Partition> writePartitionIDs;

    /**
     * The predicates registered with this DataStore
     */
    private final Map<Predicate, PredicateInfo> predicates;

    /**
     * Indicates that all predicates have been indexed.
     */
    private boolean predicatesIndexed;

    /**
     * Returns an RDBMSDataStore that utilizes the connections returned by the {@link DatabaseDriver}.
     */
    public RDBMSDataStore(DatabaseDriver dbDriver) {
        openDataStores.add(this);

        // Initialize all private variables
        this.openDatabases = new HashMap<Partition, List<Database>>();
        this.writePartitionIDs = new HashSet<Partition>();
        this.predicates = new HashMap<Predicate, PredicateInfo>();

        // Keep database driver locally for generating different query dialets
        this.dbDriver = dbDriver;

        // Initialize metadata
        this.metadata = new DataStoreMetadata(this);

        // We start with no predicates to index.
        predicatesIndexed = true;
    }

    @Override
    public void registerPredicate(StandardPredicate predicate) {
        if (predicates.containsKey(predicate)) {
            return;
        }

        PredicateInfo predicateInfo = new PredicateInfo(predicate);
        predicates.put(predicate, predicateInfo);

        // If we add a table, we need to index.
        predicatesIndexed = false;

        try (Connection connection = getConnection()) {
            predicateInfo.setupTable(connection, dbDriver);
        } catch (SQLException ex) {
            throw new RuntimeException("Unable to setup predicate table for: " + predicate + ".", ex);
        }
    }

    @Override
    public Database getDatabase(Partition write, Partition... read) {
        return getDatabase(write, (Set<StandardPredicate>)null, read);
    }

    @Override
    public Database getDatabase(Partition write, StandardPredicate[] toClose, Partition... read) {
        if (toClose == null) {
            return getDatabase(write, (Set<StandardPredicate>)null, read);
        }

        Set<StandardPredicate> closeSet = new HashSet<StandardPredicate>();
        for (StandardPredicate predicate : toClose) {
            closeSet.add(predicate);
        }

        return getDatabase(write, closeSet, read);
    }

    @Override
    public Database getDatabase(Partition write, Set<StandardPredicate> toClose, Partition... read) {
        /*
         * Checks that:
         * 1. No other databases are writing to the specified write partition
         * 2. No other databases are reading from this write partition
         * 3. No other database is writing to the specified read partition(s)
         */
        if (writePartitionIDs.contains(write)) {
            throw new IllegalArgumentException("The specified write partition ID is already used by another database.");
        } else if (openDatabases.containsKey(write)) {
            throw new IllegalArgumentException("The specified write partition ID is also a read partition.");
        }

        for (Partition partition : read) {
            if (writePartitionIDs.contains(partition)) {
                throw new IllegalArgumentException("Another database is writing to a specified read partition: " + partition);
            }
        }

        // Make sure all the predicates are indexed.
        // We wait until now, so data can be loaded without needed to update the indexes.
        indexPredicates();

        // Creates the database and registers the current predicates
        RDBMSDatabase db = new RDBMSDatabase(this, write, read, toClose);

        // Register the write and read partitions as being associated with this database
        for (Partition partition : read) {
            if (!openDatabases.containsKey(partition)) {
                openDatabases.put(partition, new ArrayList<Database>());
            }

            openDatabases.get(partition).add(db);
        }

        writePartitionIDs.add(write);

        return db;
    }

    public void indexPredicates() {
        if (predicatesIndexed) {
            return;
        }
        predicatesIndexed = true;

        List<PredicateInfo> toIndex = new ArrayList<PredicateInfo>();
        for (PredicateInfo predicateInfo : predicates.values()) {
            if (!predicateInfo.indexed()) {
                toIndex.add(predicateInfo);
            }
        }

        if (toIndex.size() == 0) {
            return;
        }

        // Index in parallel.
        log.debug("Indexing predicates.");
        Parallel.foreach(toIndex, new Parallel.Worker<PredicateInfo>() {
            @Override
            public void work(long index, PredicateInfo predicateInfo) {
                log.trace("Indexing " + predicateInfo.predicate());

                try (Connection connection = getConnection()) {
                    predicateInfo.index(connection, dbDriver);
                } catch (SQLException ex) {
                    throw new RuntimeException("Unable to index predicate: " + predicateInfo.predicate(), ex);
                }

                // Ensure that table stats are up-to-date.
                dbDriver.updateTableStats(predicateInfo);
            }
        });

        // Ensure that DB stats are up-to-date.
        dbDriver.updateDBStats();

        log.debug("Predicate indexing complete.");
    }

    @Override
    public Iterable<Database> getOpenDatabases() {
        Set<Database> databases = new HashSet<Database>();
        for (List<Database> partitionDatabases : openDatabases.values()) {
            databases.addAll(partitionDatabases);
        }

        return databases;
    }

    @Override
    public Inserter getInserter(StandardPredicate predicate, Partition partition) {
        if (!predicates.containsKey(predicate)) {
            throw new IllegalArgumentException("Unknown predicate specified: " + predicate);
        } else if (writePartitionIDs.contains(partition) || openDatabases.containsKey(partition)) {
            throw new IllegalStateException("Partition [" + partition + "] is currently in use, cannot insert into it.");
        }

        return new RDBMSInserter(this, predicates.get(predicate), partition);
    }

    @Override
    public Set<StandardPredicate> getRegisteredPredicates() {
        Set<StandardPredicate> standardPredicates = new HashSet<StandardPredicate>();
        for (Predicate predicate : predicates.keySet()) {
            if (predicate instanceof StandardPredicate) {
                standardPredicates.add((StandardPredicate) predicate);
            }
        }

        return standardPredicates;
    }

    @Override
    public int deletePartition(Partition partition) {
        if (writePartitionIDs.contains(partition) || openDatabases.containsKey(partition)) {
            throw new IllegalArgumentException("Cannot delete partition that is in use.");
        }

        int deletedEntries = 0;
        try (
            Connection connection = getConnection();
            Statement stmt = connection.createStatement();
        ) {
            for (PredicateInfo pred : predicates.values()) {
                String sql = "DELETE FROM " + pred.tableName() + " WHERE " + PredicateInfo.PARTITION_COLUMN_NAME + " = " + partition.getID();
                deletedEntries += stmt.executeUpdate(sql);
            }

            metadata.removePartition(partition);
        } catch(SQLException ex) {
            throw new RuntimeException(ex);
        }

        return deletedEntries;
    }


    @Override
    public void close() {
        openDataStores.remove(this);

        if (!openDatabases.isEmpty()) {
            throw new IllegalStateException("Cannot close data store when databases are still open!");
        }

        if (dbDriver != null) {
            dbDriver.close();
            dbDriver = null;
        }
    }

    public DataStoreMetadata getMetadata() {
        return metadata;
    }

    public void releasePartitions(RDBMSDatabase db) {
        if (!db.getDataStore().equals(this)) {
            throw new IllegalArgumentException("Database has not been opened with this data store.");
        }

        // Release the read partition(s) in use by this database
        for (Partition partition : db.getReadPartitions()) {
            openDatabases.get(partition).remove(db);

            if (openDatabases.get(partition).isEmpty()) {
                openDatabases.remove(partition);
            }
        }

        // Release the write partition in use by this database
        writePartitionIDs.remove(db.getWritePartition());
    }

    public Partition getNewPartition(){
        return metadata.getNewPartition();
    }

    @Override
    public Partition getPartition(String partitionName) {
        return metadata.getPartition(partitionName);
    }

    @Override
    public Set<Partition> getPartitions() {
        return metadata.getAllPartitions();
    }

    public int getPredicateRowCount(StandardPredicate predicate) {
        try (Connection connection = getConnection()) {
            return predicates.get(predicate).getCount(connection);
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to close connection for count.", ex);
        }
    }

    public DatabaseDriver getDriver() {
        return dbDriver;
    }

    public Connection getConnection() {
        return dbDriver.getConnection();
    }

    public static Set<RDBMSDataStore> getOpenDataStores() {
        return Collections.unmodifiableSet(openDataStores);
    }

    /**
     * Helper method for getting a predicate handle
     */
    public synchronized PredicateInfo getPredicateInfo(Predicate predicate) {
        PredicateInfo info = predicates.get(predicate);
        if (info == null) {
            throw new IllegalArgumentException("Predicate not registered with data store.");
        }

        return info;
    }
}
