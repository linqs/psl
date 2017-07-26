/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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

import org.joda.time.DateTime;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.healthmarketscience.sqlbuilder.CreateTableQuery;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The RDMBSDataStore is an RDBMS implementation of the DataStore interface.
 * It will connect to any RDBMS that has a supporting {@link DatabaseDriver} implementation, and
 * through the {@link ConfigBundle} can use custom names for its value and partition columns.
 * <p>
 * The ConfigBundle can also specify whether or not this RDBMSDatabase should use
 * {@link RDBMSUniqueStringID} for its UniqueID implementation (as opposed to using
 * {@link RDBMSUniqueIntID}).
 * @author Eric Norris <enorris@cs.umd.edu>
 *
 */
public class RDBMSDataStore implements DataStore {
	private static final Logger log = LoggerFactory.getLogger(RDBMSDataStore.class);

	// Map for database registration
	private static final BiMap<ReadOnlyDatabase, String> registeredDatabases = HashBiMap.create();
	private static int databaseCounter = 0;

	/**
	 * Prefix of property keys used by this class.
	 *
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "rdbmsdatastore";

	/** Name of metadata table **/
	public static final String METADATA_TABLENAME = CONFIG_PREFIX + "_metadata";

	/** Key for boolean property of whether to use {@link RDBMSUniqueStringID} as a UniqueID. */
	public static final String USE_STRING_ID_KEY = CONFIG_PREFIX + ".usestringids";

	/** Default value for the USE_STRING_ID_KEY property */
	public static final boolean USE_STRING_ID_DEFAULT = true;

	protected Pattern predicatePattern;

	/*
	 * This DataStore's connection to the RDBMS + the data loader associated
	 * with it.
	 */
	protected final Connection connection;
	protected final RDBMSDataLoader dataloader;

	/*
	 * This Database Driver associated to the datastore.
	 */
	protected final DatabaseDriver dbDriver;

	/*
	 * Metadata
	 */
	protected RDBMSDataStoreMetadata metadata;

	/*
	 * The list of databases matched with their read partitions, and the set of
	 * all write partitions open in this database.
	 */
	protected final Multimap<Partition,RDBMSDatabase> openDatabases;
	protected final Set<Partition> writePartitionIDs;

	/*
	 * The predicates registered with this DataStore
	 */
	protected final Map<Predicate, PredicateInfo> predicates;

	protected final boolean stringUniqueIDs;

	/**
	 * Returns an RDBMSDataStore that utilizes the connection created by the {@link DatabaseDriver}.
	 * @param dbDriver	the DatabaseDriver that contains a connection to the backing database.
	 * @param config	the configuration for this DataStore.
	 */
	public RDBMSDataStore(DatabaseDriver dbDriver, ConfigBundle config) {
		this.predicatePattern = getPredicateTablePattern();

		// Initialize all private variables
		this.openDatabases = HashMultimap.create();
		this.writePartitionIDs = new HashSet<Partition>();
		this.predicates = new HashMap<Predicate, PredicateInfo>();

		// Keep database driver locally for generating different query dialets
		this.dbDriver = dbDriver;

		// Connect to the database
		this.connection = dbDriver.getConnection();

		// Set up the data loader
		this.dataloader = new RDBMSDataLoader(connection);

		//Initialize metadata
		initializeMetadata(connection, METADATA_TABLENAME);

		// Store the type of unique ID this RDBMS will use
		this.stringUniqueIDs = config.getBoolean(USE_STRING_ID_KEY, USE_STRING_ID_DEFAULT);

		// Read in any predicates that exist in the database
		deserializePredicates();

		// Register the DataStore class for external functions
		if (dbDriver.isSupportExternalFunction()) {
			ExternalFunctions.registerFunctionAlias(connection);
		}
	}

	/**
	 * Helper method to read from metadata table and store results into metadata object
	 */
	protected void initializeMetadata(Connection conn, String tblName){
		this.metadata = new RDBMSDataStoreMetadata(conn, tblName);
		metadata.createMetadataTable();
	}

	protected Pattern getPredicateTablePattern() {
		return Pattern.compile("(\\w+)" + PredicateInfo.PREDICATE_TABLE_SUFFIX);
	}

	/**
	 * Helper method to register all existing predicates from the RDBMS.
	 */
	protected void deserializePredicates() {
		int numPredicates = 0;

		try {
			DatabaseMetaData dbMetaData = connection.getMetaData();
			ResultSet rs = dbMetaData.getTables(null, null, null, null);
			try {
				while (rs.next()) {
					String tableName = rs.getString("TABLE_NAME");

					// Extract the predicate name from a matching table name
					Matcher m = predicatePattern.matcher(tableName);
					if (m.find()) {
						String predicateName = m.group(1);
						if (createPredicateFromTable(tableName, predicateName)) {
							numPredicates++;
						}
					}
				}
			} finally {
				rs.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error reading database metadata.", e);
		} finally {
			log.debug("Registered {} pre-existing predicates from RDBMS.", numPredicates);
		}
	}

	/**
	 * Helper method to register a predicate from a given table
	 * @param tableName		the database table to analyze
	 * @param name			the name of the predicate in this table
	 * @return				a boolean indicating the success of deserializing the predicate
	 */
	protected boolean createPredicateFromTable(String tableName, String name) {
		PredicateFactory factory = PredicateFactory.getFactory();
		Pattern argumentPattern = Pattern.compile("(\\w+)_(\\d)");
		try {
			DatabaseMetaData dbMetaData = connection.getMetaData();
			ResultSet rs = dbMetaData.getColumns(null, null, tableName, null);
			try {
				ArrayList<ConstantType> args = new ArrayList<ConstantType>();
				while (rs.next()) {
					String columnName = rs.getString("COLUMN_NAME");
					Matcher m = argumentPattern.matcher(columnName);
					if (m.find()) {
						String argumentName = m.group(1).toLowerCase();
						int argumentLocation = Integer.parseInt(m.group(2));
						if (argumentName.equals("string")) {
							args.add(argumentLocation, ConstantType.String);
						} else if (argumentName.equals("integer")) {
							args.add(argumentLocation, ConstantType.Integer);
						} else if (argumentName.equals("double")) {
							args.add(argumentLocation, ConstantType.Double);
						} else if (argumentName.equals("uniqueid")) {
							args.add(argumentLocation, ConstantType.UniqueID);
						}
					}
				}
				// Check if any arguments were found at all
				if (args.size() == 0)
					return false;

				StandardPredicate p = factory.createStandardPredicate(name, args.toArray(new ConstantType[args.size()]));
				PredicateInfo pi = getDefaultPredicateDBInfo(p);
				predicates.put(p, pi);
				// Update the data loader with the new predicate
				dataloader.registerPredicate(pi);
				return true;
			} finally {
				rs.close();
			}
		} catch (SQLException e) {
			return false;
		}
	}

	@Override
	public void registerPredicate(StandardPredicate predicate) {
		if (predicates.containsKey(predicate)) {
			return;
		}

		PredicateInfo pi = getDefaultPredicateDBInfo(predicate);
		predicates.put(predicate, pi);

		// All registered predicates are new predicates, because the
		// database reads in any predicates that already existed.
		pi.setupTable(connection, dbDriver, stringUniqueIDs);

		// Update the data loader with the new predicate
		dataloader.registerPredicate(pi);
	}

	protected PredicateInfo getDefaultPredicateDBInfo(Predicate predicate) {
		// Construct argnames from Predicate argument types
		String[] argNames = new String[predicate.getArity()];
		for (int i = 0; i < argNames.length; i ++) {
			argNames[i] = predicate.getArgumentType(i).getName() + "_" + i;
		}

		return new PredicateInfo(predicate, argNames, predicate.getName());
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
		RDBMSDatabase db = new RDBMSDatabase(this, connection, write, read, Collections.unmodifiableMap(predicates), toClose);

		// Register the write and read partitions as being associated with this database
		for (Partition partition : read) {
			openDatabases.put(partition, db);
		}
		writePartitionIDs.add(write);
		return db;
	}

	@Override
	public UniqueID getUniqueID(Object key) {
		if (stringUniqueIDs)
			return new RDBMSUniqueStringID(key.toString());
		else {
			Integer intKey;

			if (key instanceof String) {
				try {
					intKey = Integer.parseInt((String) key);
				}
				catch (NumberFormatException e) {
					throw new IllegalArgumentException("Key for UniqueID is a string that could not be parsed as an integer, " +
							"but this DataStore is set to use integer UniqueIDs.");
				}
			}
			else if (key instanceof Integer) {
				intKey = (Integer) key;
			}
			else
				throw new IllegalArgumentException("Key for UniqueID is not an integer or a string representation of an integer, " +
						"but this DataStore is set to use integer UniqueIDs.");

			return new RDBMSUniqueIntID(intKey);
		}
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
		int deletedEntries = 0;
		if (writePartitionIDs.contains(partition) || openDatabases.containsKey(partition))
			throw new IllegalArgumentException("Cannot delete partition that is in use.");
		try {
			Statement stmt = connection.createStatement();
			for (PredicateInfo pred : predicates.values()) {
				String sql = "DELETE FROM " + pred.tableName() + " WHERE " + PredicateInfo.PARTITION_COLUMN_NAME + " = " + partition.getID();
				deletedEntries+= stmt.executeUpdate(sql);
			}
			stmt.close();
			metadata.removePartition(partition);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
		return deletedEntries;
	}


	@Override
	public void close() {
		if (!openDatabases.isEmpty())
			throw new IllegalStateException("Cannot close data store when databases are still open!");
		try {
			connection.close();
		} catch (SQLException e) {
			throw new RuntimeException("Could not close database.", e);
		}
	}


	protected void releasePartitions(RDBMSDatabase db) {
		if (!db.getDataStore().equals(this))
			throw new IllegalArgumentException("Database has not been opened with this data store.");

		// Release the read partition(s) in use by this database
		for (Partition partition : db.getReadPartitions()) {
			openDatabases.remove(partition, db);
		}

		// Release the write partition in use by this database
		writePartitionIDs.remove(db.getWritePartition());

		registeredDatabases.remove(new ReadOnlyDatabase(db));
	}

	public Partition getNewPartition(){
		int partnum = getNextPartition();
		return new Partition(partnum,"AnonymousPartition_"+Integer.toString(partnum));
	}

	protected int getNextPartition() {
		int maxPartition = 0;
		maxPartition = metadata.getMaxPartition();
		return maxPartition+1;
		/*try {
			Statement stmt = connection.createStatement();
			for (PredicateInfo pred : predicates.values()) {
				String sql = "SELECT MAX(" + PredicateInfo.PARTITION_COLUMN_NAME + ") FROM " + pred.tableName;
				ResultSet result = stmt.executeQuery(sql);
				while(result.next()) {
					maxPartition = Math.max(maxPartition, result.getInt(1));
				}
			}
			stmt.close();
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
		return maxPartition + 1; */
	}

	@Override
	public Partition getPartition(String partitionName) {
		Partition p = metadata.getPartitionByName(partitionName);
		if(p == null){
			p = new Partition(getNextPartition(), partitionName);
			metadata.addPartition(p);
		}
		return p;
	}

	@Override
	public Set<Partition> getPartitions() {
		return metadata.getAllPartitions();
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
