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

	private static final double DEFAULT_UNOBSERVED_VALUE = 0.0;

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
	 * Do not close these statements until the entire db closes.
	 */
	private final Map<Predicate, PreparedStatement> queryStatements;
	private final Map<Predicate, PreparedStatement> queryAllWriteStatements;
	private final Map<Predicate, PreparedStatement> upsertStatements;
	private final Map<Predicate, PreparedStatement> deleteStatements;

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
		this.queryAllWriteStatements = new HashMap<Predicate, PreparedStatement>();
		this.upsertStatements = new HashMap<Predicate, PreparedStatement>();
		this.deleteStatements = new HashMap<Predicate, PreparedStatement>();

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
		QueryAtom queryAtom = new QueryAtom(atom.getPredicate(), atom.getArguments());

		if (cache.getCachedAtom(queryAtom) != null) {
			cache.removeCachedAtom(queryAtom);
		}

		PredicateInfo predciate = getPredicateInfo(atom.getPredicate());
		Term[] arguments = atom.getArguments();

		PreparedStatement statement = getAtomDelete(predicates.get(atom.getPredicate()));

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

	@Override
	public List<RandomVariableAtom> getAllGroundRandomVariableAtoms(StandardPredicate predicate) {
		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

		// Closed predicates have no random variable atoms.
		if (isClosed(predicate)) {
			return atoms;
		}

		PredicateInfo predicateInfo = getPredicateInfo(predicate);

		// Columns for each argument to the predicate.
		List<String> argumentCols = predicateInfo.argumentColumns();
		Constant[] arguments = new Constant[argumentCols.size()];

		try (ResultSet results = getAllWriteAtomQuery(predicateInfo).executeQuery()) {
			while (results.next()) {
				double value = results.getDouble(PredicateInfo.VALUE_COLUMN_NAME);
				if (results.wasNull()) {
					value = Double.NaN;
				}

				for (int i = 0; i < argumentCols.size(); i++) {
					arguments[i] = extractConstantFromResult(results, argumentCols.get(i), predicate.getArgumentType(i));
				}

				atoms.add(cache.instantiateRandomVariableAtom(predicate, arguments, value));
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error fetching all ground random variable atoms for: " + predicate, ex);
		}

		return atoms;
	}

	@Override
	public void commit(Iterable<RandomVariableAtom> atoms) {
		if (closed) {
			throw new IllegalStateException("Cannot commit on a closed database.");
		}

		// Split the atoms up by predicate.
		Map<Predicate, List<RandomVariableAtom>> atomsByPredicate = new HashMap<Predicate, List<RandomVariableAtom>>();

		for (RandomVariableAtom atom : atoms) {
			if (!atomsByPredicate.containsKey(atom.getPredicate())) {
				atomsByPredicate.put(atom.getPredicate(), new ArrayList<RandomVariableAtom>());
			}

			atomsByPredicate.get(atom.getPredicate()).add(atom);
		}

		// Upsert each predicate batch.
		for (Map.Entry<Predicate, List<RandomVariableAtom>> entry : atomsByPredicate.entrySet()) {
			PreparedStatement statement = getAtomUpsert(getPredicateInfo(entry.getKey()));

			try {
				// Set all the upsert params.
				for (RandomVariableAtom atom : entry.getValue()) {
					// Partition
					statement.setInt(1, writeID);

					// Value
					statement.setDouble(2, atom.getValue());

					// Args
					Term[] arguments = atom.getArguments();
					for (int i = 0; i < arguments.length; i++) {
						if (arguments[i] instanceof Attribute) {
							statement.setObject(3 + i, ((Attribute)arguments[i]).getValue());
						} else if (arguments[i] instanceof UniqueID) {
							statement.setObject(3 + i, ((UniqueID)arguments[i]).getInternalID());
						} else {
							throw new IllegalArgumentException("Unknown argument type: " + arguments[i].getClass());
						}
					}

					statement.addBatch();
				}

				// Execute all the upserts for this predicate.
				statement.executeBatch();
				statement.clearBatch();
			} catch (SQLException ex) {
				throw new RuntimeException("Error doing batch commit for: " + entry.getKey(), ex);
			}
		}
	}

	@Override
	public void commit(RandomVariableAtom atom) {
		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>(1);
		atoms.add(atom);
		commit(atoms);
	}

	@Override
	public ResultList executeQuery(DatabaseQuery query) {
		if (closed) {
			throw new IllegalStateException("Cannot perform query on database that was closed.");
		}

		Formula formula = query.getFormula();
		VariableAssignment partialGrounding = query.getPartialGrounding();
		Set<Variable> projectTo = query.getProjectionSubset();

		VariableTypeMap varTypes = formula.collectVariables(new VariableTypeMap());
		if (projectTo.size() == 0) {
			projectTo.addAll(varTypes.getVariables());
			projectTo.removeAll(partialGrounding.getVariables());
		}

		// Construct query from formula
		Formula2SQL sqler = new Formula2SQL(partialGrounding, projectTo, this, query.getDistinct());
		String queryString = sqler.getSQL(formula);
		log.trace(queryString);

		// Create and initialize ResultList
		int i = 0;
		RDBMSResultList results = new RDBMSResultList(projectTo.size());
		for (int varIndex = 0; varIndex < query.getNumVariables(); varIndex++) {
			if (projectTo.contains(query.getVariable(varIndex))) {
				results.setVariable(query.getVariable(varIndex), i++);
			}
		}

		try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(queryString)) {
			while (resultSet.next()) {
				Constant[] res = new Constant[projectTo.size()];
				for (Variable var : projectTo) {
					if (partialGrounding.hasVariable(var)) {
						res[results.getPos(var)] = partialGrounding.getVariable(var);
					} else {
						res[results.getPos(var)] = extractConstantFromResult(resultSet, var.getName(), varTypes.getType(var));
					}
				}
				results.addResult(res);
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error executing database query: (" + query + ") -- [" + queryString + "]", ex);
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
		if (closed) {
			throw new IllegalStateException("Cannot close database after it has been closed.");
		}

		parentDataStore.releasePartitions(this);
		closed = true;

		// Close all prepared statements
		try {
			for (PreparedStatement statement : queryStatements.values()) {
				statement.close();
			}

			for (PreparedStatement statement : queryAllWriteStatements.values()) {
				statement.close();
			}

			for (PreparedStatement statement : upsertStatements.values()) {
				statement.close();
			}

			for (PreparedStatement statement : deleteStatements.values()) {
				statement.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error closing prepared statements.", e);
		}
	}

	private PreparedStatement getAllWriteAtomQuery(PredicateInfo predicate) {
		if (!queryAllWriteStatements.containsKey(predicate.predicate())) {
			queryAllWriteStatements.put(predicate.predicate(), predicate.createQueryAllWriteStatement(connection, writeID));
		}

		return queryAllWriteStatements.get(predicate.predicate());
	}

	private PreparedStatement getAtomQuery(PredicateInfo predicate) {
		if (!queryStatements.containsKey(predicate.predicate())) {
			queryStatements.put(predicate.predicate(), predicate.createQueryStatement(connection, readIDs));
		}

		return queryStatements.get(predicate.predicate());
	}

	private PreparedStatement getAtomUpsert(PredicateInfo predicate) {
		if (!upsertStatements.containsKey(predicate.predicate())) {
			upsertStatements.put(predicate.predicate(), predicate.createUpsertStatement(connection, parentDataStore.getDriver()));
		}

		return upsertStatements.get(predicate.predicate());
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

	private ResultSet queryDBForAtom(QueryAtom atom) {
		if (closed) {
			throw new IllegalStateException("Cannot query atom from closed database.");
		}

		Term[] arguments = atom.getArguments();
		PreparedStatement statement = getAtomQuery(predicates.get(atom.getPredicate()));

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

	private GroundAtom getAtom(StandardPredicate predicate, Constant... arguments) {
		// Ensure this database has this predicate.
		getPredicateInfo(predicate);

		QueryAtom queryAtom = new QueryAtom(predicate, arguments);
		GroundAtom result = cache.getCachedAtom(queryAtom);
		if (result != null) {
			return result;
		}

		try (ResultSet resultSet = queryDBForAtom(queryAtom)) {
			if (resultSet.next()) {
				double value = resultSet.getDouble(PredicateInfo.VALUE_COLUMN_NAME);
				if (resultSet.wasNull()) {
					value = Double.NaN;
				}

		 		int partition = resultSet.getInt(PredicateInfo.PARTITION_COLUMN_NAME);
		 		if (partition == writeID) {
		 			// Found in the write partition
		 			if (isClosed((StandardPredicate)predicate)) {
		 				// Predicate is closed, instantiate as ObservedAtom
		 				result = cache.instantiateObservedAtom(predicate, arguments, value);
		 			} else {
		 				// Predicate is open, instantiate as RandomVariableAtom
		 				result = cache.instantiateRandomVariableAtom((StandardPredicate)predicate, arguments, value);
		 			}
		 		} else {
		 			// Must be in a read partition, instantiate as ObservedAtom
		 			result = cache.instantiateObservedAtom(predicate, arguments, value);
		 		}

		 		if (resultSet.next()) {
		 			throw new IllegalStateException("Cannot have duplicate atoms, or atoms in multiple partitions in a single database");
				}
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error getting atom for " + predicate, ex);
		}

		if (result == null) {
			if (isClosed((StandardPredicate) predicate)) {
				result = cache.instantiateObservedAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
			} else {
				result = cache.instantiateRandomVariableAtom((StandardPredicate) predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
			}
		}

		return result;
	}

	private GroundAtom getAtom(FunctionalPredicate predicate, Constant... arguments) {
		QueryAtom queryAtom = new QueryAtom(predicate, arguments);
		GroundAtom result = cache.getCachedAtom(queryAtom);
		if (result != null) {
			return result;
		}

		double value = predicate.computeValue(new ReadOnlyDatabase(this), arguments);
		return cache.instantiateObservedAtom(predicate, arguments, value);
	}
}
