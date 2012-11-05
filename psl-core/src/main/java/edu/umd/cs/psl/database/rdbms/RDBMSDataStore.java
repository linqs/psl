package edu.umd.cs.psl.database.rdbms;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.CreateTableQuery.ColumnConstraint;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.loading.Updater;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.DoubleAttribute;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.IntegerAttribute;
import edu.umd.cs.psl.model.argument.StringAttribute;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class RDBMSDataStore implements DataStore {
	private static final Logger log = LoggerFactory.getLogger(RDBMSDataStore.class);
	
	/*
	 * The values for the PSL columns.
	 */
	private final String valueColumn;
	private final String confidenceColumn;
	private final String partitionColumn;
	
	/*
	 * This DataStore's connection to the RDBMS + the data loader associated
	 * with it.
	 */
	private final Connection connection;
	private final RDBMSDataLoader dataloader;
	
	/*
	 * TODO DataStore's should have a static collection of all the RDBMSs they are connected to, in order to prevent multiple connections to the same RDBMS.
	 */
	
	/*
	 * The list of databases matched with their read partitions, and the set of
	 * all write partitions open in this database.
	 */
	private final Multimap<Partition,RDBMSDatabase> openDatabases;
	private final Set<Partition> writePartitionIDs;
	
	/*
	 * The predicates registered with this DataStore
	 */
	private final Map<Predicate, RDBMSPredicateInfo> predicates;
	
	private static boolean stringUniqueIDs;
	
	public RDBMSDataStore(DatabaseDriver db, DatabaseDriver.Type type,
			String name, String folder, boolean setup, String valueCol, 
			String confidenceCol, String partitionCol, boolean useStringUniqueIDs) {
		// Set up column names
		this.valueColumn = valueCol;
		this.confidenceColumn = confidenceCol;
		this.partitionColumn = partitionCol;
		
		// Initialize all private variables
		this.openDatabases = HashMultimap.create();
		this.writePartitionIDs = new HashSet<Partition>();
		this.predicates = new HashMap<Predicate, RDBMSPredicateInfo>();
		
		// Connect to the database
		this.connection = connect(db, type, name, folder, setup);
		
		// Set up the data loader
		this.dataloader = new RDBMSDataLoader(connection);
		
		// Store the type of unique ID this RDBMS will use
		RDBMSDataStore.stringUniqueIDs = useStringUniqueIDs;
		
		// Read in any predicates that exist in the database
		deserializePredicates();
		
		// Register the DataStore class for external functions
		registerFunctionAlias();
	}
	
	private Connection connect(DatabaseDriver db, DatabaseDriver.Type type, String name, String folder, boolean setup) {
		Connection con = null;
		if (name==null) throw new IllegalArgumentException("Need to specify a name for the RDBMS.");
		switch(type) {
			case Disk:
				if (folder==null)
					throw new IllegalArgumentException("Need to specify path where disk database is to be stored.");
				con = db.getDatabase(folder,name, setup);
				break;
			case Memory: 
				con = db.getMemoryDatabase(name);
				break;
			default:
				throw new IllegalArgumentException("Type can be one of 'disk' or 'memory', but was given: " + type);
		}
		return con;
	}
	
	/**
	 * Helper method to register all existing predicates from the RDBMS.
	 */
	private void deserializePredicates() {
		int numPredicates = 0;
		try {
			DatabaseMetaData dbMetaData = connection.getMetaData();
			ResultSet rs = dbMetaData.getTables(null, null, null, null);
			try {
				while (rs.next()) {
					String predicateName = rs.getString("TABLE_NAME");
					if (createPredicateFromTable(predicateName))
						numPredicates++;
				}
			} finally {
				rs.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error reading database metadata.", e);
		} finally {
			log.debug("Registered {} pre-existing predicates from database.",
					numPredicates);
		}
	}
	
	private boolean createPredicateFromTable(String name) {
		// TODO Should this fail silently (like it does now) or generate a runtime exception?
		PredicateFactory factory = PredicateFactory.getFactory();
		Pattern predicatePattern = Pattern.compile("(\\w+)_(\\d)");
		try {
			DatabaseMetaData dbMetaData = connection.getMetaData();
			ResultSet rs = dbMetaData.getColumns(null, null, name, null);
			try {
				ArrayList<ArgumentType> args = new ArrayList<ArgumentType>();
				while (rs.next()) {
					String columnName = rs.getString("COLUMN_NAME");
					Matcher m = predicatePattern.matcher(columnName);
					if (m.find()) {
						String argumentName = m.group(1);
						args.add(ArgumentType.valueOf(argumentName));
					} else
						return false;
				}
				Predicate p = factory.createStandardPredicate(name, args.toArray(new ArgumentType[args.size()]));
				RDBMSPredicateInfo pi = getDefaultPredicateDBInfo(p);
				predicates.put(p, pi);
				return true;
			} finally {
				rs.close();
			}
		} catch (SQLException e) {
			return false;
		}
	}
	
	@Override
	public void registerPredicate(Predicate predicate) {
		if (predicates.containsKey(predicate))
			throw new IllegalArgumentException("Predicate has already been registered: " + predicate);
		RDBMSPredicateInfo pi = getDefaultPredicateDBInfo(predicate);
		predicates.put(predicate, pi);
		
		/*
		 * All registered predicates are new predicates, because the database
		 * reads in any predicates that already existed.
		 */
		createTableForPredicate(pi);
		
		// Update the data loader with the new predicate
		dataloader.registerPredicate(pi.getPredicateHandle());
	}

	private RDBMSPredicateInfo getDefaultPredicateDBInfo(Predicate predicate) {
		// Construct argnames from Predicate argument types
		String[] argNames = new String[predicate.getArity()];
		for (int i = 0; i < argNames.length; i ++)
			argNames[i] = predicate.getArgumentType(i).getName() + "_" + i;
		
		return new RDBMSPredicateInfo(predicate, argNames, predicate.getName(),
				valueColumn, confidenceColumn, partitionColumn);
	}
	
	private void createTableForPredicate(RDBMSPredicateInfo pi) {
		Predicate p = pi.predicate;
		
		CreateTableQuery q = new CreateTableQuery(pi.tableName);
		
		List<String> hashIndexes = new ArrayList<String>(pi.argCols.length);
		StringBuilder keyColumns = new StringBuilder();
		
		// Add columns for each predicate argument
		for (int i=0; i < pi.argCols.length; i++) {
			String colName = pi.argCols[i];
			String typeName;
			
			switch (pi.predicate.getArgumentType(i)) {
			case Double:
				typeName = "DOUBLE";
				break;
			case Integer:
				typeName = "INT";
				break;
			case String:
				typeName = "MEDIUMTEXT";
				break;
			case UniqueID:
				hashIndexes.add(colName);
				if (stringUniqueIDs)
					typeName = "VARCHAR(255)";
				else
					typeName = "INT";
				break;
			default:
				throw new IllegalStateException("Unknown ArgumentType for predicate " + p.getName());
			}
			
			keyColumns.append(colName).append(", ");
			q.addCustomColumn(colName + " " + typeName, ColumnConstraint.NOT_NULL);
		}
		
		// Add a column for partitioning
		keyColumns.append(pi.partitionCol);
		q.addCustomColumn(pi.partitionCol + " INT DEFAULT 0", ColumnConstraint.NOT_NULL);
		
		// Add columns for value and confidence
		q.addCustomColumn(pi.valueCol + " DOUBLE", ColumnConstraint.NOT_NULL);
		q.addCustomColumns(pi.confidenceCol + " DOUBLE");
		
		try {
			Statement stmt = connection.createStatement();

			try {
				// Create the table
			    stmt.executeUpdate(q.validate().toString());

			    // Create indexes for the table
				for (String hashcol : hashIndexes) {
					stmt.executeUpdate("CREATE HASH INDEX " + pi.tableName + hashcol + "hashidx ON " + pi.tableName + " (" + hashcol + " ) ");
				}
				stmt.executeUpdate("CREATE PRIMARY KEY HASH ON " + pi.tableName + " (" + keyColumns.toString() + " ) ");
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			throw new RuntimeException("Error creating table for predicate: " + p.getName(), e);
		}
	}
	
	@Override
	public Database getDatabase(Partition write, Partition... read) {
		return getDatabase(write, null, read);
	}

	@Override
	public Database getDatabase(Partition write,
			Set<StandardPredicate> toClose, Partition... read) {
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
		for (Partition partID : read)
			if (writePartitionIDs.contains(partID))
				throw new IllegalArgumentException("Another database is writing to a specified read partition: " + partID);
		
		// Creates the database and registers the current predicates
		RDBMSDatabase db = new RDBMSDatabase(this,connection, write, read, toClose);
		for (RDBMSPredicateInfo predinfo : predicates.values())
			db.registerPredicate(predinfo.getPredicateHandle());
		return db;
	}

	@Override
	public UniqueID getUniqueID(Object key) {
		if (key instanceof String)
			return new RDBMSUniqueStringID((String)key);
		else if (key instanceof Integer)
			return new RDBMSUniqueIntID((Integer)key);
		else
			throw new IllegalArgumentException("Cannot produce a UniqueID for type: " + key.getClass().getName());
	}

	@Override
	public Inserter getInserter(StandardPredicate predicate, Partition partition) {
		if (!predicates.containsKey(predicate))
			throw new IllegalArgumentException("Unknown predicate specified: " + predicate);
		if (writePartitionIDs.contains(partition) || openDatabases.containsKey(partition))
			throw new IllegalStateException("Partition [" + partition + "] is already in use. Can only be modified via Updater!");
		
		return dataloader.getInserter(predicates.get(predicate).predicate,partition);
	}

	@Override
	public Updater getUpdater(StandardPredicate predicate, Partition partition) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public Set<Predicate> getRegisteredPredicates() {
		return predicates.keySet();
	}

	@Override
	public int deletePartition(Partition partition) {
		int deletedEntries = 0;
		try {
			Statement stmt = connection.createStatement();
			for (RDBMSPredicateInfo pred : predicates.values()) {
				String sql = "DELETE FROM " + pred.tableName + " WHERE " + pred.partitionCol + " = " + partition.getID();
				deletedEntries+= stmt.executeUpdate(sql);
			}
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
		for (Partition partID : db.readPartitions)
			openDatabases.remove(partID, db);
		
		// Release the write partition in use by this database
		writePartitionIDs.remove(db.writePartition);
	}
	
	/*
	 * ########### Handling function calls
	 */
	
	private void registerFunctionAlias() {
		try {
			Statement stmt = connection.createStatement();
			try {
				stmt.executeUpdate("CREATE ALIAS IF NOT EXISTS "
						+ aliasFunctionName + " FOR \""
						+ getClass().getCanonicalName()
						+ ".registeredExternalFunctionCall\" ");
			} finally {
				stmt.close();
			}
		} catch (SQLException e) {
			log.error("SQL error: {}", e.getMessage());
			throw new AssertionError(e);
		}
	}
	
	static final String aliasFunctionName = "extFunctionCall";
	private static final BiMap<ExternalFunction, String> externalFunctions = HashBiMap.create();
	private static int externalFunctionCounter = 0;
	
	public static final String getSimilarityFunctionID(ExternalFunction extFun) {
		if (externalFunctions.containsKey(extFun)) {
			return externalFunctions.get(extFun);
		} else {
			String id = "extFun" + (externalFunctionCounter++);
			externalFunctions.put(extFun, id);
			return id;
		}
	}
	
	public static final double registeredExternalFunctionCall(String functionID, String... args) {
		ExternalFunction extFun = externalFunctions.inverse().get(functionID);
		if (extFun==null) 
			throw new IllegalArgumentException("Unknown external function alias: " + functionID);
		if (args.length!=extFun.getArgumentTypes().length) 
			throw new IllegalArgumentException("Number of arguments does not match arity of external function!");
		
		GroundTerm[] arguments = new GroundTerm[args.length];
		for (int i=0; i < args.length; i++) {
			if (args[i]==null)
				throw new IllegalArgumentException("Argument cannot be null!");
			
			ArgumentType t = extFun.getArgumentTypes()[i];
			switch (t) {
			case Double:
				arguments[i] = new DoubleAttribute(Double.parseDouble(args[i]));
				break;
			case Integer:
				arguments[i] = new IntegerAttribute(Integer.parseInt(args[i]));
				break;
			case String:
				arguments[i] = new StringAttribute(args[i]);
				break;
			case UniqueID:
				// TODO Static access? Technically all DataStores must share the same type
				if (stringUniqueIDs)
					arguments[i] = new RDBMSUniqueStringID(args[i]);
				else
					arguments[i] = new RDBMSUniqueIntID(Integer.parseInt(args[i]));
				break;
			default:
				throw new IllegalArgumentException("Unknown argument type: " + t.getName());
			}
		}
		return extFun.getValue(arguments);
	}
}
