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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The RDMBSDataStore is an RDBMS implementation of the DataStore interface.
 * It will connect to any RDBMS that has a supporting {@link DatabaseDriver} implementation, and
 * through the {@link ConfigBundle} can use custom names for its value and partition columns.
 */
public class RDBMSDataStore implements DataStore {
	private static final Set<RDBMSDataStore> openDataStores = new HashSet<RDBMSDataStore>();

	// Map for database registration
	private static final BiMap<ReadOnlyDatabase, String> registeredDatabases = HashBiMap.create();
	private static int databaseCounter = 0;

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "rdbmsdatastore";

	/**
	 * Default value for the USE_STRING_ID_KEY property.
	 */
	public static final boolean USE_STRING_ID_DEFAULT = true;

	private final RDBMSDataLoader dataloader;

	/**
	 * This Database Driver associated to the datastore.
	 */
	private DatabaseDriver dbDriver;

	/**
	 * Metadata
	 */
	private DataStoreMetadata metadata;

	/**
	 * The list of databases matched with their read partitions, and the set of
	 * all write partitions open in this database.
	 */
	private final Multimap<Partition, Database> openDatabases;
	private final Set<Partition> writePartitionIDs;

	/**
	 * The predicates registered with this DataStore
	 */
	private final Map<Predicate, PredicateInfo> predicates;

	/**
	 * Returns an RDBMSDataStore that utilizes the connections returned by the {@link DatabaseDriver}.
	 * @param dbDriver the DatabaseDriver that contains a connection pool to the backing database.
	 * @param config the configuration for this DataStore.
	 */
	public RDBMSDataStore(DatabaseDriver dbDriver, ConfigBundle config) {
		openDataStores.add(this);

		// Initialize all private variables
		this.openDatabases = HashMultimap.create();
		this.writePartitionIDs = new HashSet<Partition>();
		this.predicates = new HashMap<Predicate, PredicateInfo>();

		// Keep database driver locally for generating different query dialets
		this.dbDriver = dbDriver;

		// Set up the data loader
		this.dataloader = new RDBMSDataLoader(this);

		// Initialize metadata
		this.metadata = new DataStoreMetadata(this);

		// Read in any predicates that exist in the database
		try (Connection connection = getConnection()) {
			for (StandardPredicate predicate : PredicateInfo.deserializePredicates(connection)) {
				registerPredicate(predicate, false);
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Unable to attempt to deserialize predicates.", ex);
		}

		// Register the DataStore class for external functions
		if (dbDriver.supportsExternalFunctions()) {
			try (Connection connection = getConnection()) {
				ExternalFunctions.registerFunctionAlias(connection);
			} catch (SQLException ex) {
				throw new RuntimeException("Unable to register external functions.", ex);
			}
		}
	}

	@Override
	public boolean supportsExternalFunctions() {
		return dbDriver.supportsExternalFunctions();
	}

	@Override
	public void registerPredicate(StandardPredicate predicate) {
		// All registered predicates are new predicates, because the
		// database reads in any predicates that already existed.
		registerPredicate(predicate, true);
	}

	private void registerPredicate(StandardPredicate predicate, boolean createTable) {
		if (predicates.containsKey(predicate)) {
			return;
		}

		PredicateInfo predicateInfo = new PredicateInfo(predicate);
		predicates.put(predicate, predicateInfo);

		if (createTable) {
			try (Connection connection = getConnection()) {
				predicateInfo.setupTable(connection, dbDriver);
			} catch (SQLException ex) {
				throw new RuntimeException("Unable to setup predicate table for: " + predicate + ".", ex);
			}
		}

		// Update the data loader with the new predicate
		dataloader.registerPredicate(predicateInfo);
	}

	@Override
	public Database getDatabase(Partition write, Partition... read) {
		return getDatabase(write, null, read);
	}

	@Override
	public Database getDatabase(Partition write, Set<StandardPredicate> toClose, Partition... read) {
		/*
		 * Checks that:
		 * 1. No other databases are writing to the specified write partition
		 * 2. No other databases are reading from this write partition
		 * 3. No other database is writing to the specified read partition(s)
		 */
		if (writePartitionIDs.contains(write))
			throw new IllegalArgumentException("The specified write partition ID is already used by another database.");
		if (openDatabases.containsKey(write))
			throw new IllegalArgumentException("The specified write partition ID is also a read partition.");
		for (Partition partition : read)
			if (writePartitionIDs.contains(partition))
				throw new IllegalArgumentException("Another database is writing to a specified read partition: " + partition);

		// Creates the database and registers the current predicates
		RDBMSDatabase db = new RDBMSDatabase(this, write, read, Collections.unmodifiableMap(predicates), toClose);

		// Register the write and read partitions as being associated with this database
		for (Partition partition : read) {
			openDatabases.put(partition, db);
		}
		writePartitionIDs.add(write);
		return db;
	}

	@Override
	public Collection<Database> getOpenDatabases() {
		return openDatabases.values();
	}

	@Override
	public Inserter getInserter(StandardPredicate predicate, Partition partition) {
		if (!predicates.containsKey(predicate))
			throw new IllegalArgumentException("Unknown predicate specified: " + predicate);
		if (writePartitionIDs.contains(partition) || openDatabases.containsKey(partition))
			throw new IllegalStateException("Partition [" + partition + "] is currently in use, cannot insert into it.");

		return dataloader.getInserter(predicates.get(predicate).predicate(), partition);
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
			openDatabases.remove(partition, db);
		}

		// Release the write partition in use by this database
		writePartitionIDs.remove(db.getWritePartition());

		registeredDatabases.remove(new ReadOnlyDatabase(db));
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
	 * Registers and returns an ID for a given RDBMSDatabase.
	 * If this database was already registered, returns the same ID that was returned initially.
	 * @param db	the RDBMSDatabase to register
	 * @return		the String ID for this database
	 */
	public static String getDatabaseID(RDBMSDatabase db) {
		ReadOnlyDatabase roDB = new ReadOnlyDatabase(db);
		if (registeredDatabases.containsKey(roDB)) {
			return registeredDatabases.get(roDB);
		}

		String id = "database" + (databaseCounter++);
		registeredDatabases.put(roDB, id);
		return id;
	}

	/**
	 * Get a read-only database given the id from getDatabaseID().
	 */
	public static ReadOnlyDatabase getDatabase(String databaseID) {
		if (registeredDatabases.containsValue(databaseID)) {
			return registeredDatabases.inverse().get(databaseID);
		}

		throw new IllegalArgumentException("No database registerd for id: " + databaseID);
	}
}
