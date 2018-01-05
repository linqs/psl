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
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Attribute;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DateAttribute;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.LongAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.QueryPreparer;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import com.healthmarketscience.sqlbuilder.DeleteQuery;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * A view on the datastore with specific partitions activated.
 * Keep in mind that the upstream datstore/driver usere a connection pool and we should close
 * out connections and statements after we are done with them.
 */
public class RDBMSDatabase implements Database {
	private static final Logger log = LoggerFactory.getLogger(RDBMSDatabase.class);

	private static final double DEFAULT_UNOBSERVED_VALUE = 0.0;

	/**
	 * The backing data store that created this database.
	 * Connection are obtained from here.
	 */
	private final RDBMSDataStore parentDataStore;

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

	/*
	 * Keeps track of the open / closed status of this database.
	 */
	private boolean closed;

	public RDBMSDatabase(RDBMSDataStore parent,
			Partition write, Partition[] read,
			Map<Predicate, PredicateInfo> predicates,
			Set<StandardPredicate> closed) {
		this.parentDataStore = parent;
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
		if (closed) {
			throw new IllegalStateException("Cannot query atom from closed database.");
		}

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
		QueryAtom queryAtom = new QueryAtom(atom.getPredicate(), atom.getArguments());
		if (cache.getCachedAtom(queryAtom) != null) {
			cache.removeCachedAtom(queryAtom);
		}

		try (
			Connection connection = getConnection();
			PreparedStatement statement = getAtomDelete(connection, getPredicateInfo(atom.getPredicate()), atom.getArguments());
		) {
			if (statement.executeUpdate() > 0) {
				return true;
			}

			return false;
		} catch (SQLException ex) {
			throw new RuntimeException("Error deleting atom: " + atom, ex);
		}
	}

	@Override
	public int countAllGroundAtoms(StandardPredicate predicate) {
		List<Integer> partitions = new ArrayList<Integer>();
		partitions.addAll(readIDs);
		partitions.add(writeID);

		return countAllGroundAtoms(predicate, partitions);
	}

	@Override
	public int countAllGroundRandomVariableAtoms(StandardPredicate predicate) {
		// Closed predicates have no random variable atoms.
		if (isClosed(predicate)) {
			return 0;
		}

		// All the atoms should be random vairable, since we are pulling from the write parition of an open predicate.
		List<Integer> partitions = new ArrayList<Integer>(1);
		partitions.add(writeID);

		return countAllGroundAtoms(predicate, partitions);
	}

	@Override
	public List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate) {
		List<Integer> partitions = new ArrayList<Integer>();
		partitions.addAll(readIDs);
		partitions.add(writeID);

		return getAllGroundAtoms(predicate, partitions);
	}

	@Override
	public List<RandomVariableAtom> getAllGroundRandomVariableAtoms(StandardPredicate predicate) {
		// Closed predicates have no random variable atoms.
		if (isClosed(predicate)) {
			return new ArrayList<RandomVariableAtom>();
		}

		// All the atoms should be random vairable, since we are pulling from the write parition of an open predicate.
		List<Integer> partitions = new ArrayList<Integer>(1);
		partitions.add(writeID);
		List<GroundAtom> groundAtoms = getAllGroundAtoms(predicate, partitions);

		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>(groundAtoms.size());
		for (GroundAtom atom : groundAtoms) {
			atoms.add((RandomVariableAtom)atom);
		}

		return atoms;
	}

	@Override
	public void commit(Iterable<RandomVariableAtom> atoms) {
		commit(atoms, writeID);
	}

	@Override
	public void commit(Iterable<RandomVariableAtom> atoms, int partitionId) {
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

		try (Connection connection = getConnection()) {
			// Upsert each predicate batch.
			for (Map.Entry<Predicate, List<RandomVariableAtom>> entry : atomsByPredicate.entrySet()) {
				try (PreparedStatement statement = getAtomUpsert(connection, getPredicateInfo(entry.getKey()))) {
					int batchSize = 0;

					// Set all the upsert params.
					for (RandomVariableAtom atom : entry.getValue()) {
						// Partition
						statement.setInt(1, partitionId);

						// Value
						statement.setDouble(2, atom.getValue());

						// Args
						Term[] arguments = atom.getArguments();
						for (int i = 0; i < arguments.length; i++) {
							setAtomArgument(statement, arguments[i], i + 3);
						}

						statement.addBatch();
						batchSize++;

						if (batchSize >= RDBMSDataLoader.DEFAULT_PAGE_SIZE) {
							statement.executeBatch();
							statement.clearBatch();
							batchSize = 0;
						}
					}

					if (batchSize > 0) {
						statement.executeBatch();
						statement.clearBatch();
					}
					statement.clearParameters();
				} catch (SQLException ex) {
					throw new RuntimeException("Error doing batch commit for: " + entry.getKey(), ex);
				}
			}
      } catch (SQLException ex) {
         throw new RuntimeException("Error doing batch commit.", ex);
		}
	}

	@Override
	public void commit(RandomVariableAtom atom) {
		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>(1);
		atoms.add(atom);
		commit(atoms);
	}

	@Override
	public void moveToWritePartition(StandardPredicate predicate, int oldPartitionId) {
		PredicateInfo predicateInfo = getPredicateInfo(predicate);

		try (
			Connection connection = getConnection();
			PreparedStatement statement = predicateInfo.createPartitionMoveStatement(connection, oldPartitionId, writeID);
		) {
			statement.executeUpdate();
		} catch (SQLException ex) {
			throw new RuntimeException("Error moving partitions for: " + predicate, ex);
		}
	}

	@Override
	public ResultList executeQuery(DatabaseQuery query) {
		Formula formula = query.getFormula();
		VariableAssignment partialGrounding = query.getPartialGrounding();
		Set<Variable> projectTo = new HashSet<Variable>(query.getProjectionSubset());

		VariableTypeMap varTypes = formula.collectVariables(new VariableTypeMap());
		if (projectTo.size() == 0) {
			projectTo.addAll(varTypes.getVariables());
			projectTo.removeAll(partialGrounding.getVariables());
		}

		// Construct query from formula
		Formula2SQL sqler = new Formula2SQL(partialGrounding, projectTo, this, query.getDistinct());
		String queryString = sqler.getSQL(formula);
		Map<Variable, Integer> projectionMap = sqler.getProjectionMap();

		return executeQuery(partialGrounding, projectionMap, varTypes, queryString);
	}

	/**
	 * A more general form for executeQuery().
	 * @param partialGrounding any variables that are already tied to constants.
	 * @param projectionMap a mapping of each variable we want returned to the
	 *  order it appears in the select statement.
	 * @param varTypes the types for each variable in the projection.
	 * @param queryString the SQL query.
	 */
	public ResultList executeQuery(VariableAssignment partialGrounding, Map<Variable, Integer> projectionMap,
			VariableTypeMap varTypes, String queryString) {
		if (closed) {
			throw new IllegalStateException("Cannot perform query on database that was closed.");
		}

		log.trace(queryString);

		// Create and initialize ResultList
		RDBMSResultList results = new RDBMSResultList(projectionMap.size());
		for (Map.Entry<Variable, Integer> projection : projectionMap.entrySet()) {
			results.setVariable(projection.getKey(), projection.getValue().intValue());
		}

		// Figure out all the partial variables ahead of time.
		// This will help us reduce memory usage when reading in the result set.
		// Since there are potentially many results, little boosts help.
		Constant[] orderedPartials = new Constant[projectionMap.size()];
		int[] orderedIndexes = new int[projectionMap.size()];
		ConstantType[] orderedTypes = new ConstantType[projectionMap.size()];
		for (Map.Entry<Variable, Integer> entry : results.getVariableMap().entrySet()) {
			Variable variable = entry.getKey();
			int index = entry.getValue().intValue();

			if (partialGrounding.hasVariable(variable)) {
				orderedPartials[index] = partialGrounding.getVariable(variable);
			} else {
				orderedPartials[index] = null;
			}

			orderedIndexes[index] = projectionMap.get(variable);
			orderedTypes[index] = varTypes.getType(variable);
		}

		try (
			Connection connection = getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(queryString)
		) {
			while (resultSet.next()) {
				Constant[] res = new Constant[orderedPartials.length];

				for (int i = 0; i < orderedPartials.length; i++) {
					if (orderedPartials[i] != null) {
						res[i] = orderedPartials[i];
					} else {
						res[i] = extractConstantFromResult(resultSet, orderedIndexes[i], orderedTypes[i]);
					}
				}

				results.addResult(res);
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error executing database query: [" + queryString + "]", ex);
		}

		log.trace("Number of results: {}", results.size());

		return results;
	}

	@Override
	public boolean isClosed(StandardPredicate predicate) {
		return closedPredicates.contains(predicate);
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

	private Connection getConnection() {
		return parentDataStore.getConnection();
	}

	@Override
	public void close() {
		if (closed) {
			throw new IllegalStateException("Cannot close database after it has been closed.");
		}

		parentDataStore.releasePartitions(this);
		closed = true;
	}

	private PreparedStatement getAtomQuery(Connection connection, PredicateInfo predicate, Constant[] arguments) {
		PreparedStatement statement = predicate.createQueryStatement(connection, readIDs);

      try {
         for (int i = 0; i < arguments.length; i++) {
            setAtomArgument(statement, arguments[i], i + 1);
         }
      } catch (SQLException ex) {
         throw new RuntimeException("Failed to set prepared statement atom arguments for " + predicate.predicate() + ".");
      }

		return statement;
	}

	private PreparedStatement getAtomUpsert(Connection connection, PredicateInfo predicate) {
		return predicate.createUpsertStatement(connection, parentDataStore.getDriver());
	}

	private PreparedStatement getAtomDelete(Connection connection, PredicateInfo predicate, Term[] arguments) {
		PreparedStatement statement = predicate.createDeleteStatement(connection, writeID);

      try {
         for (int i = 0; i < arguments.length; i++) {
            setAtomArgument(statement, arguments[i], i + 1);
         }
      } catch (SQLException ex) {
         throw new RuntimeException("Failed to set prepared statement atom arguments for " + predicate.predicate() + ".");
      }

		return statement;
	}

	/**
	 * Given a ResultSet, column name, and ConstantType,
	 * get the value as a Constnt from the results.
	 * columnIndex should be 0-indexed (eventhough jdbc uses 1-index).
	 */
	private Constant extractConstantFromResult(ResultSet results, int columnIndex, ConstantType type) {
		try {
			switch (type) {
				case Double:
					return new DoubleAttribute(results.getDouble(columnIndex + 1));
				case Integer:
					return new IntegerAttribute(results.getInt(columnIndex + 1));
				case String:
					return new StringAttribute(results.getString(columnIndex + 1));
				case Long:
					return new LongAttribute(results.getLong(columnIndex + 1));
				case Date:
					return new DateAttribute(new DateTime(results.getDate(columnIndex + 1).getTime()));
				case UniqueIntID:
					return new UniqueIntID(results.getInt(columnIndex + 1));
				case UniqueStringID:
					return new UniqueStringID(results.getString(columnIndex + 1));
				default:
					throw new IllegalArgumentException("Unknown argument type: " + type);
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error extracting constant from ResultSet.", ex);
		}
	}

	/**
	 * Extract a single ground atom from a ResultSet.
	 * The ResultSet MUST already be primed (next() should have been already called.
	 * Will throw if there is no next().
	 */
	private GroundAtom extractGroundAtomFromResult(ResultSet resultSet, StandardPredicate predicate, Constant[] arguments)
			throws SQLException {
		double value = resultSet.getDouble(PredicateInfo.VALUE_COLUMN_NAME);
		if (resultSet.wasNull()) {
			value = Double.NaN;
		}

		int partition = resultSet.getInt(PredicateInfo.PARTITION_COLUMN_NAME);
		if (partition == writeID) {
			// Found in the write partition
			if (isClosed((StandardPredicate)predicate)) {
				// Predicate is closed, instantiate as ObservedAtom
				return cache.instantiateObservedAtom(predicate, arguments, value);
			}

			// Predicate is open, instantiate as RandomVariableAtom
			return cache.instantiateRandomVariableAtom((StandardPredicate)predicate, arguments, value);
		}

		// Must be in a read partition, instantiate as ObservedAtom
		return cache.instantiateObservedAtom(predicate, arguments, value);
	}

	private GroundAtom getAtom(StandardPredicate predicate, Constant... arguments) {
		return getAtom(predicate, true, arguments);
	}

	/**
	 * @param create Create an atom if one does not exist.
	 */
	private GroundAtom getAtom(StandardPredicate predicate, boolean create, Constant... arguments) {
		QueryAtom queryAtom = new QueryAtom(predicate, arguments);
		GroundAtom result = cache.getCachedAtom(queryAtom);
		if (result != null) {
			return result;
		}

		return fetchAtom(predicate, create, arguments);
	}

	/**
	 * Get an atom from the database and put it in the cache.
	 */
	private GroundAtom fetchAtom(StandardPredicate predicate, boolean create, Constant... arguments) {
		// Ensure this database has this predicate.
		getPredicateInfo(predicate);

		GroundAtom result = queryDBForAtom(predicate, arguments);

		if (result != null || !create) {
			return result;
		}


		if (isClosed((StandardPredicate)predicate)) {
			result = cache.instantiateObservedAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
		} else {
			result = cache.instantiateRandomVariableAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
		}

		return result;
	}

	/**
	 * Get a ground atom from the database.
	 * Return null if one is not found.
	 */
	private GroundAtom queryDBForAtom(StandardPredicate predicate, Constant[] arguments) {
		try (
			Connection conn = parentDataStore.getConnection();
			PreparedStatement statement = getAtomQuery(conn, predicates.get(predicate), arguments);
			ResultSet resultSet = statement.executeQuery();
		) {
			if (!resultSet.next()) {
				return null;
			}

			GroundAtom result = extractGroundAtomFromResult(resultSet, predicate, arguments);

			if (resultSet.next()) {
				throw new IllegalStateException("Cannot have duplicate atoms, or atoms in multiple partitions in a single database");
			}

			return result;
		} catch (SQLException ex) {
			throw new RuntimeException("Error querying DB for atom.", ex);
		}
	}

	public boolean hasAtom(StandardPredicate predicate, Constant... arguments) {
		return getAtom(predicate, false, arguments) != null;
	}

	private List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions) {
		List<GroundAtom> atoms = new ArrayList<GroundAtom>();
		PredicateInfo predicateInfo = getPredicateInfo(predicate);

		// Columns for each argument to the predicate.
		List<String> argumentCols = predicateInfo.argumentColumns();
		Constant[] arguments = new Constant[argumentCols.size()];

		try (
			Connection connection = getConnection();
			PreparedStatement statement = predicateInfo.createQueryAllStatement(connection, partitions);
			ResultSet results = statement.executeQuery();
		) {
			while (results.next()) {
				for (int i = 0; i < argumentCols.size(); i++) {
					// As per PredicateInfo.createQueryAllStatement, the data columns are offset by two.
					arguments[i] = extractConstantFromResult(results, i + 2, predicate.getArgumentType(i));
				}

				atoms.add(extractGroundAtomFromResult(results, predicate, arguments));
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error fetching all ground atoms for: " + predicate, ex);
		}

		return atoms;
	}

	private int countAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions) {
		PredicateInfo predicateInfo = getPredicateInfo(predicate);

		try (
			Connection connection = getConnection();
			PreparedStatement statement = predicateInfo.createCountAllStatement(connection, partitions);
			ResultSet results = statement.executeQuery();
		) {
			if (!results.next()) {
				throw new RuntimeException("No results from a COUNT(*)");
			}

			return results.getInt(1);
		} catch (SQLException ex) {
			throw new RuntimeException("Error fetching all ground atoms for: " + predicate, ex);
		}
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

	/**
	 * Set the parameter given by the prepared statement and index to the specified argument.
	 */
	private void setAtomArgument(PreparedStatement statement, Term argument, int index) throws SQLException {
		if (argument instanceof IntegerAttribute) {
			statement.setInt(index, ((IntegerAttribute)argument).getValue());
		} else if (argument instanceof DoubleAttribute) {
			statement.setDouble(index, ((DoubleAttribute) argument).getValue());
		} else if (argument instanceof StringAttribute) {
			statement.setString(index, ((StringAttribute)argument).getValue());
		} else if (argument instanceof LongAttribute) {
			statement.setLong(index, ((LongAttribute) argument).getValue());
		} else if (argument instanceof DateAttribute) {
			statement.setDate(index, new java.sql.Date(((DateAttribute) argument).getValue().getMillis()));
		} else if (argument instanceof UniqueIntID) {
			statement.setInt(index, ((UniqueIntID)argument).getID());
		} else if (argument instanceof UniqueStringID) {
			statement.setString(index, ((UniqueStringID)argument).getID());
		} else {
			throw new IllegalArgumentException("Unknown argument type: " + argument.getClass());
		}
	}
}
