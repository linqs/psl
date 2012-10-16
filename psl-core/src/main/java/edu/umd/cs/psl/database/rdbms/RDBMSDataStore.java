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
package edu.umd.cs.psl.database.rdbms;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.CreateTableQuery.ColumnConstraint;

import edu.umd.cs.psl.database.DataFormat;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.loading.Updater;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class RDBMSDataStore implements DataStore {
	
	private static final Logger log = LoggerFactory.getLogger(RDBMSDataStore.class);
	
	protected static final String defaultValueColumnName = "value";
	protected static final String defaultConfidenceColumnName = "confidence";
	protected static final String defaultPSLColumnName = "psl";
	protected static final String defaultPartitionColumnName = "partition";
	
	private final Map<Predicate,RDBMSPredicateInfo> predicates;
	protected String valueColumnName;
	protected String confidenceColumnName;
	protected String pslColumnName;
	protected String partitionColumnName;
	
	private Connection connection;
	private RDBMSDataLoader dataloader;
	
	private final Multimap<Partition,RDBMSDatabase> openDatabases;
	private final Set<Partition> writePartitionIDs;
	
	public RDBMSDataStore() {
		this(defaultValueColumnName, defaultConfidenceColumnName,defaultPSLColumnName, defaultPartitionColumnName);
		
	}
	
	public RDBMSDataStore(String valueColName, String confidenceColName, String pslColName, String partitionColName) {
		valueColumnName = valueColName;
		confidenceColumnName = confidenceColName;
		pslColumnName =  pslColName;
		partitionColumnName = partitionColName;
		
		predicates = new HashMap<Predicate,RDBMSPredicateInfo>();
		openDatabases= HashMultimap.create();
		writePartitionIDs = new HashSet<Partition>();
	}
	
	@Override
	public void registerPredicate(Predicate predicate, List<String> argnames) {
		registerPredicate(predicate, argnames, DataFormat.getDefaultFormat(predicate));
	}

	@Override
	public void registerPredicate(Predicate predicate, List<String> argnames, DataFormat[] format) {
		if (predicates.containsKey(predicate))
			throw new IllegalArgumentException("Predicate has already been registered: " + predicate);
		predicates.put(predicate, getDefaultPredicateDBInfo(predicate, argnames, format));
	}
	
	private RDBMSPredicateInfo getDefaultPredicateDBInfo(Predicate predicate, List<String> argnames, DataFormat[] format) {
		String[] argNames = argnames.toArray(new String[argnames.size()]);
		return new RDBMSPredicateInfo(predicate, argNames, predicate.getName(),
				pslColumnName, valueColumnName, confidenceColumnName,
				partitionColumnName,format);
	}
	
	private void getConnection(DatabaseDriver db, DatabaseDriver.Type type, String name, String path, boolean empty) {
		if (name==null) throw new IllegalArgumentException("Need to specify a name for the RDBMS.");
		switch(type) {
			case Disk:
				if (path==null)
					throw new IllegalArgumentException("Need to specify path where disk database is to be stored.");
				connection = db.getDatabase(path,name, empty);
				break;
			case Memory: 
				connection = db.getMemoryDatabase(name);
				break;
			default:
				throw new IllegalArgumentException("Type can be one of 'disk' or 'memory', but was given: " + type);
		}
	}
	
	public void connect(DatabaseDriver db, DatabaseDriver.Type type, String name, String folder) {
		getConnection(db,type,name,folder,false);
		setupDataloader();
	}
	
	public void setup(DatabaseDriver db, DatabaseDriver.Type type, String name, String folder) {
		getConnection(db,type,name,folder,true);
		createTables();
		setupDataloader();
	}
	
	@Override
	public Database getDatabase(Partition write, Partition... read) {
		return getDatabase(write, new HashSet<StandardPredicate>(), read);
	}
	
	@Override
	public Database getDatabase(Partition write,
			Set<StandardPredicate> toClose, Partition... read) {
		
		RDBMSDatabase db = new RDBMSDatabase(this,connection, write, read, toClose);
		for (RDBMSPredicateInfo predinfo : predicates.values()) {
			db.registerPredicate(getPredicateHandle(predinfo));
		}
		
		/*
		 * Verifies that no other Database is reading from the specificed write partition
		 * and no other Database is writing to any of the specified read partitions
		 */
		if (writePartitionIDs.contains(write))
			throw new IllegalArgumentException("The specified write partition ID is already used by another database.");
		if (openDatabases.containsKey(write))
			throw new IllegalArgumentException("The specified write partition ID is also a read partition.");
		for (Partition partID : read)
			if (writePartitionIDs.contains(partID))
				throw new IllegalArgumentException("Another database is writing to a specified read partition: " + partID);
		
		for (Partition partID : read)
			openDatabases.put(partID, db);
		writePartitionIDs.add(write);
		return db;
	}

	void closeDatabase(RDBMSDatabase db, Partition writeID, Partition[] partitionIDs) {
		for (Partition partID : partitionIDs) {
			openDatabases.remove(partID, db);
		}
		if (!writePartitionIDs.remove(writeID))
			throw new IllegalArgumentException("Database has not been opened with this data store.");
	}
	
	private void setupDataloader() {
		List<RDBMSPredicateHandle> predicateHandles = new ArrayList<RDBMSPredicateHandle>();
		for (RDBMSPredicateInfo predinfo : predicates.values()) {
			predicateHandles.add(getPredicateHandle(predinfo));
		}
		dataloader = new RDBMSDataLoader(connection,predicateHandles);
	}

	@Override
	public Inserter getInserter(Predicate predicate, Partition partitionID) {
		if (!predicates.containsKey(predicate)) throw new IllegalArgumentException("Unknown predicate specified: " + predicate);
		if (writePartitionIDs.contains(partitionID) || openDatabases.containsKey(partitionID))
			throw new IllegalStateException("Partition ["+partitionID+"] is already in use. Can only be modified via Updater!");
		return dataloader.getInserter(predicates.get(predicate).predicate,partitionID);
	}
	
	@Override
	public Updater getUpdater(Predicate predicate, Partition partitionID) {
		throw new UnsupportedOperationException("Not yet implemented");
	}
	
	private void createTables() {
		for (RDBMSPredicateInfo predinfo : predicates.values()) {
			createTableFor(predinfo);
		}
	}
	
	//TODO: clean up statistics!!
	
	public double querySingleStats(String query, String var) {
		return querySingleStats(query,new String[]{var})[0];
	}
	
	public double[] querySingleStats(String query, String[] vars) {
		double res[][] = queryStats(query,vars);
		if (res.length>1) throw new IllegalArgumentException("The statistic query returned more than one result!");
		if (res.length<1) throw new IllegalArgumentException("The statistic query returned no result!");
		return res[0];
	}
		
	public double[] queryStats(String query, String var) {
		double[][] res = queryStats(query,new String[]{var});
		double[] newres = new double[res.length];
		for (int i=0;i<res.length;i++) newres[i] = res[i][0];
		return newres;
	}
	
	public double[][] queryStats(String query, String[] vars) {
		double[][] res=null;
		try {
			Statement stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY );
			try {
			    ResultSet rs = stmt.executeQuery(query);
			    rs.last();
			    int noRows = rs.getRow();
			    res = new double[noRows][vars.length];
			    rs.beforeFirst();
			    try {
			    	int j=0;
			    	while (rs.next()) {
				    	for (int i=0;i<vars.length;i++) {
				    		res[j][i]=rs.getDouble(vars[i]);
				    	}
				    	j++;
			    	}
			    	assert j==noRows : j + " vs. " + noRows;
			    } finally {
			        rs.close();
			    }
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			throw new AssertionError(e);
		}
		return res;
	}
	
	@Override
	public int deletePartition(Partition partID) {
		if (connection==null) throw new IllegalStateException("Not connected to a database!");
		int deletedEntries = 0;
		try {
			Statement stmt = connection.createStatement();
			for (RDBMSPredicateInfo pred : predicates.values()) {
				String sql = "DELETE FROM "+pred.tableName+" WHERE " + pred.partitionCol +"="+partID.getID();
				deletedEntries+= stmt.executeUpdate(sql);
			}
		} catch(SQLException e) {
			throw new AssertionError(e);
		}
		return deletedEntries;
	}
	
	@Override
	public void close() {
		if (!openDatabases.isEmpty()) throw new IllegalStateException("Cannot close data store when databases are still open!");
		if (connection==null) return;
		try {
			connection.close();
		} catch (SQLException e) {
			throw new AssertionError("Could not close database: " + e.getMessage());
		}
	}
	
	private RDBMSPredicateHandle getPredicateHandle(RDBMSPredicateInfo predinfo) {
		if (!predicates.containsKey(predinfo.predicate))
			throw new IllegalArgumentException("Predicate info. refers to an unregistered predicate: " + predinfo.predicate);
		return predinfo.getPredicateHandle();
	}
	
	private void createTableFor(RDBMSPredicateInfo ph) {
		CreateTableQuery q = new CreateTableQuery(ph.tableName);
		
		List<String> hashIndexes = new ArrayList<String>(ph.argCols.length);
		StringBuilder keyColumns = new StringBuilder();
		
		for (int i=0;i<ph.argCols.length;i++) {
			String colName = ph.argCols[i];
			keyColumns.append(colName).append(", ");
			if (ph.predicate.getArgumentType(i).isEntity()) {
				hashIndexes.add(colName);
			}
			q.addCustomColumn(colName+" " + RDBMSColumnTypeMap.getColumnType(ph.columnTypes[i]), ColumnConstraint.NOT_NULL);
		}
		q.addCustomColumn(ph.partitionCol+" INT DEFAULT 0", ColumnConstraint.NOT_NULL);
		keyColumns.append(ph.partitionCol);
		q.addCustomColumn(ph.valueCol + " DOUBLE", ColumnConstraint.NOT_NULL);
		q.addCustomColumns(ph.confidenceCol + " DOUBLE");
		q.addCustomColumn(ph.pslCol + " INT DEFAULT 0", ColumnConstraint.NOT_NULL);
			
		try {
			Statement stmt = connection.createStatement();

			try {
			    stmt.executeUpdate(q.validate().toString());
			    /* Creates indexes */
				for (String hashcol : hashIndexes) {
					stmt.executeUpdate("CREATE HASH INDEX " + ph.tableName+hashcol+"hashidx ON " + ph.tableName + " (" + hashcol + " ) ");
				}
				stmt.executeUpdate("CREATE PRIMARY KEY HASH ON " + ph.tableName + " (" + keyColumns.toString() + " ) ");
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			log.error("SQL error: {}",e.getMessage());
			throw new AssertionError(e);
		}
	
	}

	@Override
	public Inserter getInserter(StandardPredicate predicate, Partition partition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Updater getUpdater(StandardPredicate predicate, Partition partition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Predicate> getRegisteredPredicates() {
		// TODO Auto-generated method stub
		return null;
	}

	
}

