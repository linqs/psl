/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.atom.AtomCache;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.atom.VariableAssignment;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.QueryPreparer;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import com.healthmarketscience.sqlbuilder.DeleteQuery;

/**
 *
 * @author Eric Norris (enorris@cs.umd.edu)
 *
 */
public class RDBMSDatabase implements Database {
	private static final Logger log = LoggerFactory.getLogger(RDBMSDatabase.class);
	/**
	 * The backing data store that created this database.
	 */
	protected final RDBMSDataStore parentDataStore;

	/**
	 * The connection to the JDBC database
	 */
	protected final Connection dbConnection;

	/**
	 * The partition ID in which this database writes.
	 */
	protected final Partition writePartition;
	protected final int writeID;

	/**
	 * The partition IDs that this database reads from.
	 */
	protected final Partition[] readPartitions;
	protected final List<Integer> readIDs;

	/**
	 * Predicates that, for the purpose of this database, are closed.
	 */
	protected final Set<StandardPredicate> closedPredicates;

	/**
	 * Mapping from a predicate to its database handle.
	 */
	protected final Map<Predicate, RDBMSPredicateHandle> predicateHandles;

	/**
	 * The atom cache for this database.
	 */
	protected final AtomCache cache;

	/**
	 * The following map predicates to pre-compiled SQL statements.
	 */
	protected final Map<Predicate, PreparedStatement> queryStatement;
	protected final Map<Predicate, PreparedStatement> updateStatement;
	protected final Map<Predicate, PreparedStatement> insertStatement;
	protected final Map<Predicate, PreparedStatement> deleteStatement;


	/**
	 * The following keeps track of bulk atoms to be committed.
	 */
	protected final Set<RandomVariableAtom> pendingInserts;
	protected final Set<RandomVariableAtom> pendingUpdates;

	/*
	 * Keeps track of the open / closed status of this database.
	 */
	protected boolean closed;

	/**
	 * The constructor for the RDBMSDatabase. Note: This assumes the parent
	 * {@link RDBMSDataStore} will register predicates with this database.
	 * @param parent
	 * @param con
	 * @param write
	 * @param read
	 * @param closed
	 */
	public RDBMSDatabase(RDBMSDataStore parent, Connection con,
			Partition write, Partition[] read, Set<StandardPredicate> closed) {
		// Store the connection / DataStore information
		this.parentDataStore = parent;
		this.dbConnection = con;

		// Store the partition this class has write access to
		this.writePartition = write;
		this.writeID = write.getID();

		// Store the partitions this class has read access to
		this.readPartitions = read;
		this.readIDs = new ArrayList<Integer>(read.length);
		for (int i = 0; i < read.length; i ++)
			this.readIDs.add(read[i].getID());
		if (!this.readIDs.contains(writeID))
			this.readIDs.add(writeID);

		// Add the set of predicates to treat as closed
		this.closedPredicates = new HashSet<StandardPredicate>();
		if (closed != null)
			this.closedPredicates.addAll(closed);

		// Initialize internal variables
		this.predicateHandles = new HashMap<Predicate, RDBMSPredicateHandle>();
		this.cache = new AtomCache(this);
		this.queryStatement = new HashMap<Predicate, PreparedStatement>();
		this.updateStatement = new HashMap<Predicate, PreparedStatement>();
		this.insertStatement = new HashMap<Predicate, PreparedStatement>();
		this.deleteStatement = new HashMap<Predicate, PreparedStatement>();
		this.pendingInserts = new HashSet<RandomVariableAtom>();
		this.pendingUpdates = new HashSet<RandomVariableAtom>();
		this.closed = false;
	}

	/**
	 * Adds a RDBMSPredicateHandle to this Database. Expected to be called only
	 * immediately after construction by parent DataStore, in order to preserve
	 * contract that only predicates registered with the DataStore at time of
	 * construction are registered with this Database.
	 *
	 * @param ph predicate to register
	 */
	void registerPredicate(RDBMSPredicateHandle ph) {
		if (predicateHandles.containsKey(ph.predicate()))
			throw new IllegalArgumentException("Predicate has already been registered!");
		predicateHandles.put(ph.predicate(), ph);

		// Create PreparedStatement for predicate
		createQueryStatement(ph);
		if (!closedPredicates.contains(ph.predicate())) {
			createUpdateStatement(ph);
			createInsertStatement(ph);
			createDeleteStatement(ph);
		}
	}

	@Override
	public Set<StandardPredicate> getRegisteredPredicates() {
		Set<StandardPredicate> predicates = new HashSet<StandardPredicate>();
		for (Predicate p : predicateHandles.keySet())
			if (p instanceof StandardPredicate)
				predicates.add((StandardPredicate) p);
		return predicates;
	}

	protected void createQueryStatement(RDBMSPredicateHandle ph) {
		SelectQuery q = new SelectQuery();
		QueryPreparer preparer = new QueryPreparer();
		QueryPreparer.MultiPlaceHolder placeHolder = preparer.getNewMultiPlaceHolder();

		q.addAllColumns().addCustomFromTable(ph.tableName());
		q.addCondition(new InCondition(new CustomSql(ph.partitionColumn()), readIDs));
		for (int i = 0; i< ph.argumentColumns().length; i++) {
			q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]), placeHolder));
		}

		try {
			PreparedStatement ps = dbConnection.prepareStatement(q.toString());
			queryStatement.put(ph.predicate(), ps);
		} catch (SQLException e) {
			throw new RuntimeException("Could not create prepared statement.", e);
		}
	}

	protected void createUpdateStatement(RDBMSPredicateHandle ph) {
		UpdateQuery q = new UpdateQuery(ph.tableName());
		QueryPreparer preparer = new QueryPreparer();
		QueryPreparer.MultiPlaceHolder placeHolder = preparer.getNewMultiPlaceHolder();

		// First set placeholders for the arguments
		for (int i=0; i<ph.argumentColumns().length; i++) {
			q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]), placeHolder));
		}

		// Set the partition equal to the write partition
		q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.partitionColumn()), writeID ));

		// Set a placeholder for the value
		q.addCustomSetClause(ph.valueColumn(), placeHolder);

		// Set a placeholder for the confidence
		q.addCustomSetClause(ph.confidenceColumn(), placeHolder);

		try {
			PreparedStatement ps = dbConnection.prepareStatement(q.toString());
			updateStatement.put(ph.predicate(), ps);
		} catch (SQLException e) {
			throw new RuntimeException("Could not create prepared statement.", e);
		}
	}

	protected void createInsertStatement(RDBMSPredicateHandle ph) {
		InsertQuery q = new InsertQuery(ph.tableName());
		QueryPreparer preparer = new QueryPreparer();
		QueryPreparer.MultiPlaceHolder placeHolder = preparer.getNewMultiPlaceHolder();

		// First set placeholders for the arguments
		for (int i=0; i<ph.argumentColumns().length; i++) {
			q.addCustomColumn(ph.argumentColumns()[i], placeHolder);
		}

		// Set the partition equal to the write partition
		q.addCustomColumn(ph.partitionColumn(), writeID);

		// Set a placeholder for the value
		q.addCustomColumn(ph.valueColumn(), placeHolder);

		// Set a placeholder for the confidence
		q.addCustomColumn(ph.confidenceColumn(), placeHolder);

		try {
			PreparedStatement ps = dbConnection.prepareStatement(q.toString());
			insertStatement.put(ph.predicate(), ps);
		} catch (SQLException e) {
			throw new RuntimeException("Could not create prepared statement.", e);
		}
	}

	protected void createDeleteStatement(RDBMSPredicateHandle ph){
		DeleteQuery q = new DeleteQuery(ph.tableName());
		QueryPreparer preparer = new QueryPreparer();
		QueryPreparer.MultiPlaceHolder placeHolder = preparer.getNewMultiPlaceHolder();
		// First set placeholders for the arguments
		for (int i=0; i<ph.argumentColumns().length; i++) {
			q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]), placeHolder));
		}

		try {
			PreparedStatement ps = dbConnection.prepareStatement(q.toString());
			deleteStatement.put(ph.predicate(), ps);
		} catch (SQLException e) {
			throw new RuntimeException("Could not create prepared statement.", e);
		}
	}

	/**
	 * Helper method for getting a predicate handle
	 * @param p	The predicate to lookup
	 * @return	The handle associated with the predicate
	 */
	protected RDBMSPredicateHandle getHandle(Predicate p) {
		RDBMSPredicateHandle ph = predicateHandles.get(p);
		if (ph == null)
			throw new IllegalArgumentException("Predicate not registered with database.");

		return ph;
	}

	protected ResultSet queryDBForAtom(QueryAtom a) {
		if (closed)
			throw new IllegalStateException("Cannot query atom from closed database.");

		PreparedStatement ps = queryStatement.get(a.getPredicate());
		Term[] arguments = a.getArguments();
		try {
			for (int i = 0; i < arguments.length; i++) {
				int paramIndex = i + 1;
				Term argument = arguments[i];

				if (argument instanceof IntegerAttribute)
					ps.setInt(paramIndex, ((IntegerAttribute)argument).getValue());
				else if (argument instanceof DoubleAttribute)
					ps.setDouble(paramIndex, ((DoubleAttribute) argument).getValue());
				else if (argument instanceof StringAttribute)
					ps.setString(paramIndex, ((StringAttribute)argument).getValue());
				else if (argument instanceof LongAttribute)
					ps.setLong(paramIndex, ((LongAttribute) argument).getValue());
				else if (argument instanceof DateAttribute)
					ps.setDate(paramIndex, new java.sql.Date(((DateAttribute) argument).getValue().getMillis()));
				else if (argument instanceof RDBMSUniqueIntID)
					ps.setInt(paramIndex, ((RDBMSUniqueIntID) argument).getID());
				else if (argument instanceof RDBMSUniqueStringID)
					ps.setString(paramIndex, ((RDBMSUniqueStringID) argument).getID());
			}
			return ps.executeQuery();
		} catch (SQLException e) {
			throw new RuntimeException("Error querying DB for atom.", e);
		}
	}

	@Override
	public GroundAtom getAtom(Predicate p, Constant... arguments) {
		/*
		 * First, check cache to see if the atom exists.
		 * Yes, return atom.
		 * No, continue.
		 *
		 * Next, query database for atom.
		 * What partition is it in?
		 * Read?
		 * 		- Then instantiate as a persisted ObservedAtom
		 * Write?
		 * 		- Is the predicate closed?
		 * 		- Yes, instantiate as ObservedAtom.
		 * 		- No, instantiate as RandomVariableAtom.
		 * None?
		 * 		- Is the predicate standard?
		 * 		- Yes, is the predicate closed?
		 * 			- Yes, instantiate as ObservedAtom
		 * 			- No, instantiate as RandomVariableAtom
		 * 		- No, instantiate as ObservedAtom.
		 */
		if (p instanceof StandardPredicate)
			return getAtom((StandardPredicate)p, arguments);
		else if (p instanceof FunctionalPredicate)
			return getAtom((FunctionalPredicate)p, arguments);
		else
			throw new IllegalArgumentException("Unknown predicate type: " + p.getClass().toString());
	}

	@Override
	public boolean deleteAtom(GroundAtom a) {
		boolean deleted = false;
		QueryAtom qAtom = new QueryAtom(a.getPredicate(),a.getArguments());
		if (pendingInserts.contains(qAtom) || pendingUpdates.contains(qAtom))
			executePendingStatements();
		if(cache.getCachedAtom(qAtom)!=null){
			cache.removeCachedAtom(qAtom);
		}

		RDBMSPredicateHandle ph = getHandle(a.getPredicate());
		PreparedStatement stmt = deleteStatement.get(a.getPredicate());
		Term[] arguments = a.getArguments();
		int argIdx=1;
		try {
			// First, fill in arguments
			for (int i = 0; i < ph.argumentColumns().length; i++) {
				if (arguments[i] instanceof Attribute) {
					stmt.setObject(argIdx, ((Attribute)arguments[i]).getValue());
				} else if (arguments[i] instanceof UniqueID) {
					stmt.setObject(argIdx, ((UniqueID)arguments[i]).getInternalID());
				} else
					throw new IllegalArgumentException("Unknown argument type: " + arguments[i].getClass());
				argIdx++;
			}
			int changed = stmt.executeUpdate();
			if(changed > 0){ deleted = true; }
		} catch (SQLException e) {
			throw new RuntimeException("Error deleting atom.", e);
		}
		return deleted;
	}

	/**
	 * Given a ResultSet, column name, and ConstantType,
	 * get the value as a Constnt from the results.
	 */
	protected Constant extractConstantFromResult(ResultSet results, String columnName, ConstantType type) {
		try {
			switch (type) {
				case Double:
					return new DoubleAttribute(results.getDouble(columnName));
				case Integer:
					return new IntegerAttribute(results.getInt(columnName));
				case String:
					return new StringAttribute(results.getString(columnName));
				case Long:
					return new LongAttribute(results.getLong(columnName));
				case Date:
					return new DateAttribute(new DateTime(results.getDate(columnName).getTime()));
				case UniqueID:
					return getUniqueID(results.getObject(columnName));
				default:
					throw new IllegalArgumentException("Unknown argument type: " + type);
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error extracting constant from ResultSet.", ex);
		}
	}

	@Override
	public List<RandomVariableAtom> getAllGroundRandomVariableAtoms(StandardPredicate predicate) {
		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

		// Closed predicates have no random variable atoms.
		if (isClosed(predicate)) {
			return atoms;
		}

		// Take no chances with pending operations.
		executePendingStatements();

		RDBMSPredicateHandle predicateHandle = getHandle(predicate);

		// Get all groundings for this predicate that are in the write partition.
		SelectQuery query = new SelectQuery();
		// SELECT *
		query.addAllColumns();
		// FROM predicateTable
		query.addCustomFromTable(predicateHandle.tableName());
		// WHERE partition = writeParition
		query.addCondition(
			BinaryCondition.equalTo(
				new CustomSql(predicateHandle.partitionColumn()),
				writeID
			)
		);

		// Columns for each argument to the predicate.
		String[] argumentCols = predicateHandle.argumentColumns();

		ResultSet results = null;
		Statement stmt = null;

		try {
			stmt = dbConnection.createStatement();
			results = stmt.executeQuery(query.toString());

			while (results.next()) {
				double value = results.getDouble(predicateHandle.valueColumn());
				if (results.wasNull()) {
					value = Double.NaN;
				}

				double confidence = results.getDouble(predicateHandle.confidenceColumn());
				if (results.wasNull()) {
					confidence = Double.NaN;
				}

				Constant[] arguments = new Constant[argumentCols.length];
				for (int i = 0; i < argumentCols.length; i++) {
					arguments[i] = extractConstantFromResult(results, argumentCols[i], predicate.getArgumentType(i));
				}

				atoms.add(cache.instantiateRandomVariableAtom(predicate, arguments, value, confidence));
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error fetching results.", ex);
		} finally {
			if (results != null) {
				try {
					results.close();
				} catch (SQLException ex) {
					// Do nothing.
				}
			}

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException ex) {
					// Do nothing.
				}
			}
		}

		return atoms;
	}

	protected GroundAtom getAtom(StandardPredicate p, Constant... arguments) {
		RDBMSPredicateHandle ph = getHandle(p);
		QueryAtom qAtom = new QueryAtom(p, arguments);
		GroundAtom result = cache.getCachedAtom(qAtom);
		if (result != null)
			return result;

		if (pendingInserts.contains(qAtom) || pendingUpdates.contains(qAtom))
			executePendingStatements();

		ResultSet rs = queryDBForAtom(qAtom);
		try {
			if (rs.next()) {
					double value = rs.getDouble(ph.valueColumn());
					// need to check whether the previous double is null, if so set it specifically to NaN
					if (rs.wasNull()) value = Double.NaN;
		 		double confidence = rs.getDouble(ph.confidenceColumn());
		 		if (rs.wasNull()) confidence = Double.NaN;

		 		int partition = rs.getInt(ph.partitionColumn());
		 		if (partition == writeID) {
		 			// Found in the write partition
		 			if (isClosed((StandardPredicate) p)) {
		 				// Predicate is closed, instantiate as ObservedAtom
		 				result = cache.instantiateObservedAtom(p, arguments, value, confidence);
		 			} else {
		 				// Predicate is open, instantiate as RandomVariableAtom
		 				result = cache.instantiateRandomVariableAtom((StandardPredicate) p, arguments, value, confidence);
		 			}
		 		} else {
		 			// Must be in a read partition, instantiate as ObservedAtom
		 			result = cache.instantiateObservedAtom(p, arguments, value, confidence);
		 		}
		 		if (rs.next())
		 			throw new IllegalStateException("Atom cannot exist in more than one partition.");
			}
			rs.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error analyzing results from atom query.", e);
		}

		if (result == null) {
			if (isClosed((StandardPredicate) p))
				result = cache.instantiateObservedAtom(p, arguments, 0.0, Double.NaN);
			else
				result = cache.instantiateRandomVariableAtom((StandardPredicate) p, arguments, 0.0, Double.NaN);
		}

		return result;
	}

	protected GroundAtom getAtom(FunctionalPredicate p, Constant... arguments) {
		QueryAtom qAtom = new QueryAtom(p, arguments);
		GroundAtom result = cache.getCachedAtom(qAtom);
		if (result != null)
			return result;

		double value = p.computeValue(new ReadOnlyDatabase(this), arguments);
		return cache.instantiateObservedAtom(p, arguments, value, Double.NaN);
	}

	@Override
	public void commit(RandomVariableAtom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		QueryAtom qAtom = new QueryAtom(atom.getPredicate(), atom.getArguments());

		boolean foundAtom = false;
		ResultSet rs = queryDBForAtom(qAtom);
		try {
			if (rs.next()) {
				// Found atom, only update it if it is in write partition
				foundAtom = true;
				int partition = rs.getInt(ph.partitionColumn());
				if (partition == writeID)
					// Store it in the list of atoms to be updated
					pendingUpdates.add(atom);
			}
			rs.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error analyzing results from query.", e);
		}

		if (!foundAtom) {
			// Did not find atom, store it in the list of atoms to be inserted.
			pendingInserts.add(atom);
		}
	}

	/**
	 * Helper method to fill in the fields of a PreparedStatement for an update
	 * @param atom
	 */
	protected PreparedStatement updateAtom(RandomVariableAtom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		PreparedStatement update = updateStatement.get(atom.getPredicate());
		int sqlIndex = 1;

		Term[] arguments = atom.getArguments();
		try {
			// Update the value for the atom
			update.setDouble(sqlIndex, atom.getValue());
			sqlIndex ++;

			// Update the confidence value
			if (Double.isNaN(atom.getConfidenceValue())) {
				update.setNull(sqlIndex, java.sql.Types.DOUBLE);
			} else {
				update.setDouble(sqlIndex, atom.getConfidenceValue());
			}

			sqlIndex ++;

			// Next, fill in arguments
			for (int i = 0; i < ph.argumentColumns().length; i++) {
				if (arguments[i] instanceof Attribute) {
					update.setObject(sqlIndex, ((Attribute)arguments[i]).getValue());
				} else if (arguments[i] instanceof UniqueID) {
					update.setObject(sqlIndex, ((UniqueID)arguments[i]).getInternalID());
				} else
					throw new IllegalArgumentException("Unknown argument type: " + arguments[i].getClass());
				sqlIndex ++;
			}

			// Batch the command for later execution
			update.addBatch();

			return update;
		} catch (SQLException e) {
			throw new RuntimeException("Error updating atom.", e);
		}
	}

	/**
	 * Helper method to fill in the fields of a PreparedStatement for an insert
	 * @param atom
	 */
	protected PreparedStatement insertAtom(RandomVariableAtom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		PreparedStatement insert = insertStatement.get(atom.getPredicate());
		int sqlIndex = 1;

		Term[] arguments = atom.getArguments();
		try {
			// First, fill in arguments
			for (int i = 0; i < ph.argumentColumns().length; i++) {
				if (arguments[i] instanceof Attribute) {
					insert.setObject(sqlIndex, ((Attribute)arguments[i]).getValue());
				} else if (arguments[i] instanceof UniqueID) {
					insert.setObject(sqlIndex, ((UniqueID)arguments[i]).getInternalID());
				} else
					throw new IllegalArgumentException("Unknown argument type: " + arguments[i].getClass());
				sqlIndex ++;
			}

			// Update the value for the atom
			insert.setDouble(sqlIndex, atom.getValue());
			sqlIndex ++;

			// Update the confidence value
			if (Double.isNaN(atom.getConfidenceValue())) {
				insert.setNull(sqlIndex, java.sql.Types.DOUBLE);
			} else {
				insert.setDouble(sqlIndex, atom.getConfidenceValue());
			}
			// Batch the command for later execution
			insert.addBatch();

			return insert;
		} catch (SQLException e) {
			throw new RuntimeException("Error inserting atom.", e);
		}
	}

	protected void executePendingStatements() {
		int pendingOperationCount = pendingInserts.size() + pendingUpdates.size();
		if (pendingOperationCount == 0)
			return;

		// Store all of the PendingStatements that need to be executed
		Set<PreparedStatement> pendingStatements = new HashSet<PreparedStatement>();
		for (RandomVariableAtom atom : pendingInserts)
			pendingStatements.add(insertAtom(atom));
		for (RandomVariableAtom atom : pendingUpdates)
			pendingStatements.add(updateAtom(atom));

		log.trace("Executing a batch of {} statements.", pendingOperationCount);
		int success = 0;

		try {
			for (PreparedStatement ps : pendingStatements) {
				int[] changes = ps.executeBatch();
				for (int change : changes)
					success += change;
			}
			if (success != pendingOperationCount)
				throw new RuntimeException("Return code indicates that not all " +
						"statements were executed successfully. [code: " +
						success + ", pending: " + pendingOperationCount + "]");

			// Reset all of the pending commits
			pendingInserts.clear();
			pendingUpdates.clear();
		} catch (SQLException e) {
			throw new RuntimeException("Error when executing batched statements.", e);
		}
	}

	@Override
	public ResultList executeQuery(DatabaseQuery query) {
		if (closed)
			throw new IllegalStateException("Cannot perform query on database that was closed.");

		executePendingStatements();

		Formula f = query.getFormula();
		VariableAssignment partialGrounding = query.getPartialGrounding();
		Set<Variable> projectTo = query.getProjectionSubset();

		VariableTypeMap varTypes = f.collectVariables(new VariableTypeMap());
		if (projectTo.size() == 0) {
			projectTo.addAll(varTypes.getVariables());
			projectTo.removeAll(partialGrounding.getVariables());
		}

		// Construct query from formula
		Formula2SQL sqler = new Formula2SQL(partialGrounding, projectTo, this);
		String queryString = sqler.getSQL(f);
		log.trace(queryString);

		// Create and initialize ResultList
		int i = 0;
		RDBMSResultList results = new RDBMSResultList(projectTo.size());
		for (int varIndex = 0; varIndex < query.getNumVariables(); varIndex++)
			if (projectTo.contains(query.getVariable(varIndex)))
				results.setVariable(query.getVariable(varIndex), i++);

		try  {
			Statement stmt = dbConnection.createStatement();
			try {
				ResultSet rs = stmt.executeQuery(queryString);
				try {
					while (rs.next()) {
						Constant[] res = new Constant[projectTo.size()];
						for (Variable var : projectTo) {
							i = results.getPos(var);
							if (partialGrounding.hasVariable(var)) {
								res[i] = partialGrounding.getVariable(var);
							} else {
								res[i] = extractConstantFromResult(rs, var.getName(), varTypes.getType(var));
							}
						}
						results.addResult(res);
					}
				} finally {
					rs.close();
				}
			} finally {
				stmt.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error executing database query.", e);
		}
		log.trace("Number of results: {}",results.size());
		return results;
	}

	@Override
	public boolean isClosed(StandardPredicate predicate) {
		return closedPredicates.contains(predicate);
	}

	@Override
	public UniqueID getUniqueID(Object key) {
		return parentDataStore.getUniqueID(key);
	}

	@Override
	public DataStore getDataStore() {
		return parentDataStore;
	}

	@Override
	public AtomCache getAtomCache() {
		return cache;
	}

	@Override
	public void close() {
		if (closed)
			throw new IllegalStateException("Cannot close database after it has been closed.");

		executePendingStatements();
		parentDataStore.releasePartitions(this);
		closed = true;

		// Close all prepared statements
		try {
			for (PreparedStatement ps : queryStatement.values())
				ps.close();
			for (PreparedStatement ps : updateStatement.values())
				ps.close();
			for (PreparedStatement ps : insertStatement.values())
				ps.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error closing prepared statements.", e);
		}
	}
}
