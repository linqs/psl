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
package edu.umd.cs.psl.database.RDBMS;

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
import edu.umd.cs.psl.database.PredicateDBType;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.loading.Updater;
import edu.umd.cs.psl.model.predicate.Predicate;

public class RDBMSDataStore implements DataStore {
	
	private static final Logger log = LoggerFactory.getLogger(RDBMSDataStore.class);
	
	protected static final String defaultValueColumnSuffix = "_value";
	protected static final String defaultConfidenceColumnSuffix = "_confidence";
	protected static final String defaultPslColumnName = "psl";
	protected static final String defaultPartitionColumnName = "part";
	
	
	private final Map<Predicate,PredicateDBInfo> predicates;
	protected String valueColumnSuffix;
	protected String confidenceColumnSuffix;
	protected String pslColumnName;
	protected String partitionColumnName;
	
	private Connection connection;
	private RDBMSDataLoader dataloader;
	
	private final Multimap<Partition,RDBMSDatabase> openDatabases;
	private final Set<Partition> writePartitionIDs;
	
	public RDBMSDataStore() {
		this(defaultValueColumnSuffix, defaultConfidenceColumnSuffix,defaultPslColumnName, defaultPartitionColumnName);
		
	}
	
	public RDBMSDataStore(String valueColName, String confidenceColName, String pslColName, String partitionColName) {
		valueColumnSuffix = valueColName;
		confidenceColumnSuffix = confidenceColName;
		pslColumnName =  pslColName;
		partitionColumnName = partitionColName;
		
		predicates = new HashMap<Predicate,PredicateDBInfo>();
		openDatabases= HashMultimap.create();
		writePartitionIDs = new HashSet<Partition>();
	}
	
	@Override
	public void registerPredicate(Predicate predicate, List<String> argnames, PredicateDBType type) {
		registerPredicate(predicate,argnames,type,DataFormat.getDefaultFormat(predicate));
	}

	@Override
	public void registerPredicate(Predicate predicate, List<String> argnames, PredicateDBType type, DataFormat[] format) {
		if (predicates.containsKey(predicate)) throw new AssertionError("Predicate has already been registered: " + predicate);
		predicates.put(predicate, getDefaultPredicateDBInfo(predicate,argnames,type,format));
	}
	
	private PredicateDBInfo getDefaultPredicateDBInfo(Predicate predicate, List<String> argnames, PredicateDBType type, DataFormat[] format) {
		String[] argNames = argnames.toArray(new String[argnames.size()]);
		String[] valueCols = new String[predicate.getNumberOfValues()];
		String[] confidenceCols = new String[predicate.getNumberOfValues()];
		for (int i=0;i<predicate.getNumberOfValues();i++) {
			valueCols[i] = predicate.getValueName(i)+valueColumnSuffix;
			confidenceCols[i] = predicate.getValueName(i)+confidenceColumnSuffix;
		}
		return new PredicateDBInfo(predicate,argNames,type,predicate.getName(),
				(type==PredicateDBType.Closed?null:pslColumnName),
				valueCols,confidenceCols,partitionColumnName,format);
	}
	
	private void getConnection(DatabaseDriver db, DatabaseDriver.Type type, String name, String folder, boolean empty) {
		if (name==null) throw new IllegalArgumentException("Need to specify a name for the database.");
		switch(type) {
			case Disk:
				if (folder==null) throw new IllegalArgumentException("Need to specify folder where disk database is to be stored.");
				connection = db.getDatabase(folder,name, empty);
				break;
			case Memory: 
				connection = db.getMemoryDatabase(name);
				break;
			default: throw new IllegalArgumentException("Type can be one of 'disk' or 'memory', but was given: " + type);
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
	public Database getDatabase(Partition writeID, Partition... partitionIDs) {
		return getDatabase(writeID,new HashSet<Predicate>(),partitionIDs);
	}
	
	@Override
	public Database getDatabase(Partition writeID, Set<Predicate> toclose, Partition... partitionIDs) {
		RDBMSDatabase db = new RDBMSDatabase(this,connection, writeID, partitionIDs);
		for (PredicateDBInfo predinfo : predicates.values()) {
			boolean close = toclose.contains(predinfo.predicate);
			db.registerPredicate(getPredicateHandle(predinfo,close));
		}
		if (writePartitionIDs.contains(writeID)) throw new IllegalArgumentException("The specified write partition ID is already used by another database: " + writeID);
		if (openDatabases.containsKey(writeID)) throw new IllegalArgumentException("The specified write partition ID is also a read partition: " + writeID);
		for (Partition partID : partitionIDs) {
			assert !openDatabases.containsEntry(partID, db);
			openDatabases.put(partID, db);
		}
		writePartitionIDs.add(writeID);
		return db;
	}
	
	void closeDatabase(RDBMSDatabase db, Partition writeID, Partition[] partitionIDs) {
		for (Partition partID : partitionIDs) {
			assert openDatabases.containsEntry(partID, db);
			openDatabases.remove(partID, db);
		}
		if (!writePartitionIDs.remove(writeID)) throw new IllegalArgumentException("Database has not been opened with this data store!");
	}
	
	private void setupDataloader() {
		List<RDBMSPredicateHandle> predicateHandles = new ArrayList<RDBMSPredicateHandle>();
		for (PredicateDBInfo predinfo : predicates.values()) {
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
		for (PredicateDBInfo predinfo : predicates.values()) {
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
			for (PredicateDBInfo pred : predicates.values()) {
				String sql = "DELETE FROM "+pred.tableName+" WHERE " + pred.partitioncol +"="+partID.getID();
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
	
//	private RDBMSPredicateHandle getPredicateHandle(Predicate predicate, boolean toclose) {
//		return getPredicateHandle(predicate.getName(),toclose);
//	}
//	
//	private RDBMSPredicateHandle getPredicateHandle(Predicate predicate) {
//		return getPredicateHandle(predicate.getName());
//	}
	
	private RDBMSPredicateHandle getPredicateHandle(PredicateDBInfo predinfo) {
		return getPredicateHandle(predinfo,false);
	}
	
	private RDBMSPredicateHandle getPredicateHandle(PredicateDBInfo predinfo, boolean toclose) {
		if (!predicates.containsKey(predinfo.predicate)) throw new AssertionError("Refering to undefined predicate : " + predinfo.predicate);
		return predinfo.getPredicateHandle(toclose);
	}
	
	
	private void createTableFor(PredicateDBInfo ph) {
		CreateTableQuery q = new CreateTableQuery(ph.tableName);
		
		List<String> hashIndexes = new ArrayList<String>(ph.argColumns.length);
		StringBuilder keyColumns = new StringBuilder();
		
		for (int i=0;i<ph.argColumns.length;i++) {
			String colName = ph.argColumns[i];
			keyColumns.append(colName).append(", ");
			if (ph.predicate.getArgumentType(i).isEntity()) {
				hashIndexes.add(colName);
			}
			q.addCustomColumn(colName+" " + RDBMSColumnTypeMap.getColumnType(ph.columnTypes[i]), ColumnConstraint.NOT_NULL);
		}
		q.addCustomColumn(ph.partitioncol+" INT DEFAULT 0", ColumnConstraint.NOT_NULL);
		keyColumns.append(ph.partitioncol);
		for (int j=0;j<ph.valuecols.length;j++) {
			q.addCustomColumn(ph.valuecols[j]+" DOUBLE", ColumnConstraint.NOT_NULL);
		}
		for (int j=0;j<ph.confidencecols.length;j++) {
			q.addCustomColumns(ph.confidencecols[j]+" DOUBLE");
		}
		if (ph.type!=PredicateDBType.Closed) {
			q.addCustomColumn(ph.pslcol+" INT DEFAULT 0", ColumnConstraint.NOT_NULL);
		}
			
		try {
			Statement stmt = connection.createStatement();

			try {
			    //stmt.executeUpdate(q.getDropQuery().validate().toString());
			    stmt.executeUpdate(q.validate().toString());
			    //Create indexes
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

	
}

