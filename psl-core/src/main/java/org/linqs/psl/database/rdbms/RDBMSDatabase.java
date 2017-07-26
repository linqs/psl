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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final RDBMSDataStore parentDataStore;

	/**
	 * The connection to the JDBC database
	 */
	private final Connection connection;

	/**
	 * The partition ID in which this database writes.
	 */
	private final Partition writePartition;
	private final int writeID;

	/**
	 * The partition IDs that this database reads from.
	 */
	private final List<Partition> readPartitions;
	private final List<Integer> readIDs;

	/**
	 * Predicates that, for the purpose of this database, are closed.
	 */
	private final Set<Predicate> closedPredicates;

	/**
	 * Mapping from a predicate to its database handle.
	 */
	private final Map<Predicate, PredicateInfo> predicates;

	/**
	 * The atom cache for this database.
	 */
	private final AtomCache cache;

	/**
	 * The following map predicates to pre-compiled SQL statements.
	 */
	private final Map<Predicate, PreparedStatement> queryStatements;
	private final Map<Predicate, PreparedStatement> updateStatements;
	private final Map<Predicate, PreparedStatement> insertStatements;
	private final Map<Predicate, PreparedStatement> deleteStatements;


	/**
	 * The following keeps track of bulk atoms to be committed.
	 */
	private final Set<RandomVariableAtom> pendingInserts;
	private final Set<RandomVariableAtom> pendingUpdates;

	/*
	 * Keeps track of the open / closed status of this database.
	 */
	private boolean closed;

	public RDBMSDatabase(RDBMSDataStore parent, Connection con,
			Partition write, Partition[] read,
			Map<Predicate, PredicateInfo> predicates,
			Set<StandardPredicate> closed) {
		this.parentDataStore = parent;
		this.connection = con;
		this.writePartition = write;
		this.writeID = write.getID();

		this.readPartitions = Arrays.asList(read);
		this.readIDs = new ArrayList<Integer>(read.length);
		for (int i = 0; i < read.length; i ++) {
			this.readIDs.add(read[i].getID());
		}

		if (!this.readIDs.contains(writeID)) {
			this.readIDs.add(writeID);
		}

		this.predicates = new HashMap<Predicate, PredicateInfo>();
		for (Map.Entry<Predicate, PredicateInfo> entry : predicates.entrySet()) {
			this.predicates.put(entry.getKey(), entry.getValue());
		}

		this.closedPredicates = new HashSet<Predicate>();
		if (closed != null) {
			this.closedPredicates.addAll(closed);
		}

		this.cache = new AtomCache(this);

		this.queryStatements = new HashMap<Predicate, PreparedStatement>();
		this.updateStatements = new HashMap<Predicate, PreparedStatement>();
		this.insertStatements = new HashMap<Predicate, PreparedStatement>();
		this.deleteStatements = new HashMap<Predicate, PreparedStatement>();

		this.pendingInserts = new HashSet<RandomVariableAtom>();
		this.pendingUpdates = new HashSet<RandomVariableAtom>();

		this.closed = false;
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

	/**
	 * Helper method for getting a predicate handle
	 * @param predicate	The predicate to lookup
	 * @return	The handle associated with the predicate
	 */
	public PredicateInfo getPredicateInfo(Predicate predicate) {
		PredicateInfo info = predicates.get(predicate);
		if (info == null) {
			throw new IllegalArgumentException("Predicate not registered with database.");
		}

		return info;
	}

	public ResultSet queryDBForAtom(QueryAtom atom) {
		if (closed) {
			throw new IllegalStateException("Cannot query atom from closed database.");
		}

		PreparedStatement statement = getAtomQuery(predicates.get(atom.getPredicate()));
		Term[] arguments = atom.getArguments();
		try {
			for (int i = 0; i < arguments.length; i++) {
				int paramIndex = i + 1;
				Term argument = arguments[i];

				if (argument instanceof IntegerAttribute) {
					statement.setInt(paramIndex, ((IntegerAttribute)argument).getValue());
				} else if (argument instanceof DoubleAttribute) {
					statement.setDouble(paramIndex, ((DoubleAttribute) argument).getValue());
				} else if (argument instanceof StringAttribute) {
					statement.setString(paramIndex, ((StringAttribute)argument).getValue());
				} else if (argument instanceof LongAttribute) {
					statement.setLong(paramIndex, ((LongAttribute) argument).getValue());
				} else if (argument instanceof DateAttribute) {
					statement.setDate(paramIndex, new java.sql.Date(((DateAttribute) argument).getValue().getMillis()));
				} else if (argument instanceof RDBMSUniqueIntID) {
					statement.setInt(paramIndex, ((RDBMSUniqueIntID) argument).getID());
				} else if (argument instanceof RDBMSUniqueStringID) {
					statement.setString(paramIndex, ((RDBMSUniqueStringID) argument).getID());
				}
			}

			return statement.executeQuery();
		} catch (SQLException ex) {
			throw new RuntimeException("Error querying DB for atom.", ex);
		}
	}

	@Override
	public GroundAtom getAtom(Predicate predicate, Constant... arguments) {
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
		if (predicate instanceof StandardPredicate) {
			return getAtom((StandardPredicate)predicate, arguments);
		} else if (predicate instanceof FunctionalPredicate) {
			return getAtom((FunctionalPredicate)predicate, arguments);
		} else {
			throw new IllegalArgumentException("Unknown predicate type: " + predicate.getClass().toString());
		}
	}

	@Override
	public boolean deleteAtom(GroundAtom atom) {
		boolean deleted = false;

		QueryAtom qAtom = new QueryAtom(atom.getPredicate(), atom.getArguments());
		if (pendingInserts.contains(qAtom) || pendingUpdates.contains(qAtom)) {
			executePendingStatements();
		}

		if (cache.getCachedAtom(qAtom) != null) {
			cache.removeCachedAtom(qAtom);
		}

		PredicateInfo predciate = getPredicateInfo(atom.getPredicate());
		PreparedStatement statement = getAtomDelete(predicates.get(atom.getPredicate()));
		Term[] arguments = atom.getArguments();

		try {
			// Fill in all the query parameters (1-indexed).
			int paramIndex = 1;
			for (Term argument : arguments) {
				if (argument instanceof Attribute) {
					statement.setObject(paramIndex, ((Attribute)argument).getValue());
				} else if (argument instanceof UniqueID) {
					statement.setObject(paramIndex, ((UniqueID)argument).getInternalID());
				} else {
					throw new IllegalArgumentException("Unknown argument type: " + argument.getClass());
				}

				paramIndex++;
			}

			if (statement.executeUpdate() > 0) {
				deleted = true;
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error deleting atom: " + atom, ex);
		}

		return deleted;
	}

	// TODO(eriq): here

	@Override
	public List<RandomVariableAtom> getAllGroundRandomVariableAtoms(StandardPredicate predicate) {
		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

		// Closed predicates have no random variable atoms.
		if (isClosed(predicate)) {
			return atoms;
		}

		// Take no chances with pending operations.
		executePendingStatements();

		PredicateInfo predicateHandle = getPredicateInfo(predicate);

		// Get all groundings for this predicate that are in the write partition.
		SelectQuery query = new SelectQuery();
		// SELECT *
		query.addAllColumns();
		// FROM predicateTable
		query.addCustomFromTable(predicateHandle.tableName());
		// WHERE partition = writeParition
		query.addCondition(
			BinaryCondition.equalTo(
				new CustomSql(PredicateInfo.PARTITION_COLUMN_NAME),
				writeID
			)
		);

		// Columns for each argument to the predicate.
		List<String> argumentCols = predicateHandle.argumentColumns();

		ResultSet results = null;
		Statement statement = null;

		try {
			statement = connection.createStatement();
			results = statement.executeQuery(query.toString());

			while (results.next()) {
				double value = results.getDouble(PredicateInfo.VALUE_COLUMN_NAME);
				if (results.wasNull()) {
					value = Double.NaN;
				}

				Constant[] arguments = new Constant[argumentCols.size()];
				for (int i = 0; i < argumentCols.size(); i++) {
					arguments[i] = extractConstantFromResult(results, argumentCols.get(i), predicate.getArgumentType(i));
				}

				atoms.add(cache.instantiateRandomVariableAtom(predicate, arguments, value));
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

			if (statement != null) {
				try {
					statement.close();
				} catch (SQLException ex) {
					// Do nothing.
				}
			}
		}

		return atoms;
	}

	protected GroundAtom getAtom(StandardPredicate predicate, Constant... arguments) {
		PredicateInfo ph = getPredicateInfo(predicate);
		QueryAtom qAtom = new QueryAtom(predicate, arguments);
		GroundAtom result = cache.getCachedAtom(qAtom);
		if (result != null)
			return result;

		if (pendingInserts.contains(qAtom) || pendingUpdates.contains(qAtom))
			executePendingStatements();

		ResultSet rs = queryDBForAtom(qAtom);
		try {
			if (rs.next()) {
				double value = rs.getDouble(PredicateInfo.VALUE_COLUMN_NAME);
				// Need to check whether the previous double is null, if so set it specifically to NaN
				if (rs.wasNull()) {
					value = Double.NaN;
				}

		 		int partition = rs.getInt(PredicateInfo.PARTITION_COLUMN_NAME);
		 		if (partition == writeID) {
		 			// Found in the write partition
		 			if (isClosed((StandardPredicate) predicate)) {
		 				// Predicate is closed, instantiate as ObservedAtom
		 				result = cache.instantiateObservedAtom(predicate, arguments, value);
		 			} else {
		 				// Predicate is open, instantiate as RandomVariableAtom
		 				result = cache.instantiateRandomVariableAtom((StandardPredicate) predicate, arguments, value);
		 			}
		 		} else {
		 			// Must be in a read partition, instantiate as ObservedAtom
		 			result = cache.instantiateObservedAtom(predicate, arguments, value);
		 		}
		 		if (rs.next())
		 			throw new IllegalStateException("Atom cannot exist in more than one partition.");
			}
			rs.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error analyzing results from atom query.", e);
		}

		if (result == null) {
			if (isClosed((StandardPredicate) predicate))
				result = cache.instantiateObservedAtom(predicate, arguments, 0.0);
			else
				result = cache.instantiateRandomVariableAtom((StandardPredicate) predicate, arguments, 0.0);
		}

		return result;
	}

	protected GroundAtom getAtom(FunctionalPredicate predicate, Constant... arguments) {
		QueryAtom qAtom = new QueryAtom(predicate, arguments);
		GroundAtom result = cache.getCachedAtom(qAtom);
		if (result != null)
			return result;

		double value = predicate.computeValue(new ReadOnlyDatabase(this), arguments);
		return cache.instantiateObservedAtom(predicate, arguments, value);
	}

	@Override
	public void commit(RandomVariableAtom atom) {
		PredicateInfo ph = getPredicateInfo(atom.getPredicate());
		QueryAtom qAtom = new QueryAtom(atom.getPredicate(), atom.getArguments());

		boolean foundAtom = false;
		ResultSet rs = queryDBForAtom(qAtom);
		try {
			if (rs.next()) {
				// Found atom, only update it if it is in write partition
				foundAtom = true;
				int partition = rs.getInt(PredicateInfo.PARTITION_COLUMN_NAME);
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
		PredicateInfo ph = getPredicateInfo(atom.getPredicate());
		PreparedStatement update = getAtomUpdate(predicates.get(atom.getPredicate()));
		int sqlIndex = 1;

		Term[] arguments = atom.getArguments();
		try {
			// Update the value for the atom
			update.setDouble(sqlIndex, atom.getValue());
			sqlIndex ++;

			// Next, fill in arguments
			for (int i = 0; i < ph.argumentColumns().size(); i++) {
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
		PredicateInfo ph = getPredicateInfo(atom.getPredicate());
		PreparedStatement insert = getAtomInsert(predicates.get(atom.getPredicate()));
		int sqlIndex = 1;

		Term[] arguments = atom.getArguments();
		try {
			// Set the value for the atom
			insert.setDouble(sqlIndex, atom.getValue());
			sqlIndex ++;

			// First, fill in arguments
			for (int i = 0; i < ph.argumentColumns().size(); i++) {
				if (arguments[i] instanceof Attribute) {
					insert.setObject(sqlIndex, ((Attribute)arguments[i]).getValue());
				} else if (arguments[i] instanceof UniqueID) {
					insert.setObject(sqlIndex, ((UniqueID)arguments[i]).getInternalID());
				} else
					throw new IllegalArgumentException("Unknown argument type: " + arguments[i].getClass());
				sqlIndex ++;
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
			for (PreparedStatement statement : pendingStatements) {
				int[] changes = statement.executeBatch();
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
			Statement statement = connection.createStatement();
			try {
				ResultSet rs = statement.executeQuery(queryString);
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
				statement.close();
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

	public List<Partition> getReadPartitions() {
		return Collections.unmodifiableList(readPartitions);
	}

	public Partition getWritePartition() {
		return writePartition;
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
			for (PreparedStatement statement : queryStatements.values())
				statement.close();
			for (PreparedStatement statement : updateStatements.values())
				statement.close();
			for (PreparedStatement statement : insertStatements.values())
				statement.close();
			for (PreparedStatement statement : deleteStatements.values())
				statement.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error closing prepared statements.", e);
		}
	}

	private PreparedStatement getAtomQuery(PredicateInfo predicate) {
		if (!queryStatements.containsKey(predicate.predicate())) {
			queryStatements.put(predicate.predicate(), predicate.createQueryStatement(connection, readIDs));
		}

		return queryStatements.get(predicate.predicate());
	}

	private PreparedStatement getAtomUpdate(PredicateInfo predicate) {
		if (!updateStatements.containsKey(predicate.predicate())) {
			updateStatements.put(predicate.predicate(), predicate.createUpdateStatement(connection, writeID));
		}

		return updateStatements.get(predicate.predicate());
	}

	private PreparedStatement getAtomInsert(PredicateInfo predicate) {
		if (!insertStatements.containsKey(predicate.predicate())) {
			insertStatements.put(predicate.predicate(), predicate.createInsertStatement(connection, writeID));
		}

		return insertStatements.get(predicate.predicate());
	}

	private PreparedStatement getAtomDelete(PredicateInfo predicate) {
		if (!deleteStatements.containsKey(predicate.predicate())) {
			deleteStatements.put(predicate.predicate(), predicate.createDeleteStatement(connection, writeID));
		}

		return deleteStatements.get(predicate.predicate());
	}

	/**
	 * Given a ResultSet, column name, and ConstantType,
	 * get the value as a Constnt from the results.
	 */
	private Constant extractConstantFromResult(ResultSet results, String columnName, ConstantType type) {
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
}
