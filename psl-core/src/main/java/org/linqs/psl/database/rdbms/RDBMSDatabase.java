/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.LongAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A view on the datastore with specific partitions activated.
 * Keep in mind that the upstream datstore/driver usere a connection pool and we should close
 * out connections and statements after we are done with them.
 */
public class RDBMSDatabase extends Database {
    private static final Logger log = LoggerFactory.getLogger(RDBMSDatabase.class);

    private static final float DEFAULT_UNOBSERVED_VALUE = 0.0f;

    private static final String THREAD_QUERY_ATOM_KEY = RDBMSDatabase.class.getName() + "::" + QueryAtom.class.getName();

    /**
     * Predicates that, for the purpose of this database, are closed.
     */
    private final Set<Predicate> closedPredicates;

    private int fetchSize;

    public RDBMSDatabase(RDBMSDataStore parent,
            Partition write, Partition[] read,
            Set<StandardPredicate> closed) {
        super(parent, write, read);

        fetchSize = Options.RDBMS_FETCH_SIZE.getInt();

        this.closedPredicates = new HashSet<Predicate>();
        if (closed != null) {
            this.closedPredicates.addAll(closed);
        }

        this.closed = false;
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
         *     - Then instantiate as a persisted ObservedAtom
         * Write?
         *     - Is the predicate closed?
         *         - Yes, instantiate as ObservedAtom.
         *         - No, instantiate as RandomVariableAtom.
         * None?
         *     - Is the predicate standard?
         *         - Yes, is the predicate closed?
         *             - Yes, instantiate as ObservedAtom
         *             - No, instantiate as RandomVariableAtom
         *         - No, instantiate as ObservedAtom.
         *     - Is the predicate functional?
         *         - Yes, call the function and instantiate as an ObservedAtom.
         *         - No, unknown state.
         */
        if (predicate instanceof StandardPredicate) {
            return getAtom((StandardPredicate)predicate, arguments);
        } else if (predicate instanceof FunctionalPredicate) {
            return getAtom((FunctionalPredicate)predicate, arguments);
        } else {
            throw new IllegalStateException("Unknown predicate type: " + predicate.getClass().toString());
        }
    }

    @Override
    public boolean deleteAtom(GroundAtom atom) {
        return deleteAtom(atom, Arrays.asList(writeID));
    }

    public boolean deleteAtomAllPartitions(GroundAtom atom) {
        return deleteAtom(atom, allPartitionIDs);
    }

    public boolean deleteAtom(GroundAtom atom, List<Integer> partitions) {
        QueryAtom queryAtom = new QueryAtom(atom.getPredicate(), atom.getArguments());
        if (cache.getCachedAtom(queryAtom) != null) {
            cache.removeCachedAtom(queryAtom);
        }

        try (
            Connection connection = getConnection();
            PreparedStatement statement = getAtomDelete(connection,
                    ((RDBMSDataStore)parentDataStore).getPredicateInfo(atom.getPredicate()),
                    atom.getArguments(), partitions);
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
    public void commit(Iterable<RandomVariableAtom> atoms) {
        commit(atoms, writeID);
    }

    @Override
    public void commit(Iterable<? extends GroundAtom> atoms, int partitionId) {
        if (closed) {
            throw new IllegalStateException("Cannot commit on a closed database.");
        }

        // Split the atoms up by predicate.
        Map<Predicate, List<GroundAtom>> atomsByPredicate = new HashMap<Predicate, List<GroundAtom>>();

        for (GroundAtom atom : atoms) {
            if (!atomsByPredicate.containsKey(atom.getPredicate())) {
                atomsByPredicate.put(atom.getPredicate(), new ArrayList<GroundAtom>());
            }

            atomsByPredicate.get(atom.getPredicate()).add(atom);
        }

        try (Connection connection = getConnection()) {
            // Upsert each predicate batch.
            for (Map.Entry<Predicate, List<GroundAtom>> entry : atomsByPredicate.entrySet()) {
                try (PreparedStatement statement = getAtomUpsert(connection, ((RDBMSDataStore)parentDataStore).getPredicateInfo(entry.getKey()))) {
                    int batchSize = 0;

                    // Set all the upsert params.
                    for (GroundAtom atom : entry.getValue()) {
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

                        if (batchSize >= RDBMSInserter.DEFAULT_PAGE_SIZE) {
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
    public void moveToWritePartition(StandardPredicate predicate, int oldPartitionId) {
        moveToPartition(predicate, oldPartitionId, writeID);
    }

    @Override
    public void moveToPartition(StandardPredicate predicate, int oldPartitionId, int newPartitionId) {
        PredicateInfo predicateInfo = ((RDBMSDataStore)parentDataStore).getPredicateInfo(predicate);

        try (
            Connection connection = getConnection();
            PreparedStatement statement = predicateInfo.createPartitionMoveStatement(connection, oldPartitionId, newPartitionId);
        ) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error moving partitions for: " + predicate, ex);
        }
    }

    @Override
    public QueryResultIterable executeGroundingQuery(Formula formula) {
        return executeQueryIterator(formula, false);
    }

    @Override
    public ResultList executeQuery(DatabaseQuery query) {
        return executeQuery(query.getFormula(), query.getDistinct(), query.getIgnoreVariables());
    }

    private ResultList executeQuery(Formula formula, boolean isDistinct, Set<Variable> ignoreVariables) {
        VariableTypeMap varTypes = formula.collectVariables(new VariableTypeMap());
        Set<Variable> projectTo = new HashSet<Variable>(varTypes.getVariables());

        projectTo.removeAll(ignoreVariables);

        // Construct query from formula
        Formula2SQL sqler = new Formula2SQL(projectTo, this, isDistinct);
        String queryString = sqler.getSQL(formula);
        Map<Variable, Integer> projectionMap = sqler.getProjectionMap();

        return executeQuery(projectionMap, varTypes, queryString);
    }

    private QueryResultIterable executeQueryIterator(Formula formula, boolean isDistinct) {
        VariableTypeMap varTypes = formula.collectVariables(new VariableTypeMap());
        Set<Variable> projectTo = new HashSet<Variable>(varTypes.getVariables());

        // Construct query from formula
        Formula2SQL sqler = new Formula2SQL(projectTo, this, isDistinct);
        String queryString = sqler.getSQL(formula);
        Map<Variable, Integer> projectionMap = sqler.getProjectionMap();

        return executeQueryIterator(projectionMap, varTypes, queryString);
    }

    public ResultList executeQuery(RawQuery rawQuery) {
        return executeQuery(rawQuery.getProjectionMap(), rawQuery.getVariableTypes(), rawQuery.getSQL());
    }

    public QueryResultIterable executeQueryIterator(RawQuery rawQuery) {
        return executeQueryIterator(rawQuery.getProjectionMap(), rawQuery.getVariableTypes(), rawQuery.getSQL());
    }

    /**
     * A more general form for executeQuery().
     * @param projectionMap a mapping of each variable we want returned to the
     *  order it appears in the select statement.
     * @param varTypes the types for each variable in the projection.
     * @param queryString the SQL query.
     */
    public ResultList executeQuery(Map<Variable, Integer> projectionMap, VariableTypeMap varTypes, String queryString) {
        if (closed) {
            throw new IllegalStateException("Cannot perform query on database that was closed.");
        }

        int[] orderedIndexes = new int[projectionMap.size()];
        ConstantType[] orderedTypes = new ConstantType[projectionMap.size()];

        RDBMSResultList results = initQueryResults(projectionMap, varTypes, orderedIndexes, orderedTypes);

        for (Constant[] row : executeQueryIterator(queryString, projectionMap, orderedIndexes, orderedTypes)) {
            results.addResult(row);
        }

        log.trace("Number of results: {}", results.size());

        return results;
    }

    /**
     * Note that the constants are in the order specified by the projection map.
     */
    public QueryResultIterable executeQueryIterator(Map<Variable, Integer> projectionMap, VariableTypeMap varTypes, String queryString) {
        if (closed) {
            throw new IllegalStateException("Cannot perform query on database that was closed.");
        }

        int[] orderedIndexes = new int[projectionMap.size()];
        ConstantType[] orderedTypes = new ConstantType[projectionMap.size()];
        initQueryResults(projectionMap, varTypes, orderedIndexes, orderedTypes);

        return executeQueryIterator(queryString, projectionMap, orderedIndexes, orderedTypes);
    }

    public QueryResultIterable executeQueryIterator(String queryString, Map<Variable, Integer> projectionMap, int[] orderedIndexes, ConstantType[] orderedTypes) {
        if (closed) {
            throw new IllegalStateException("Cannot perform query on database that was closed.");
        }

        log.trace(queryString);

        return new RDBMSQueryResultIterable(queryString, projectionMap, orderedIndexes, orderedTypes);
    }

    private RDBMSResultList initQueryResults(Map<Variable, Integer> projectionMap, VariableTypeMap varTypes, int[] orderedIndexes, ConstantType[] orderedTypes) {
        RDBMSResultList results = new RDBMSResultList(projectionMap.size());
        for (Map.Entry<Variable, Integer> projection : projectionMap.entrySet()) {
            results.setVariable(projection.getKey(), projection.getValue().intValue());
        }

        for (Map.Entry<Variable, Integer> entry : results.getVariableMap().entrySet()) {
            Variable variable = entry.getKey();
            int index = entry.getValue().intValue();

            orderedIndexes[index] = projectionMap.get(variable);
            orderedTypes[index] = varTypes.getType(variable);
        }

        return results;
    }

    @Override
    public boolean isClosed(StandardPredicate predicate) {
        return closedPredicates.contains(predicate);
    }

    private Connection getConnection() {
        return ((RDBMSDataStore)parentDataStore).getConnection();
    }

    @Override
    public void close() {
        if (closed) {
            throw new IllegalStateException("Cannot close database after it has been closed.");
        }

        ((RDBMSDataStore)parentDataStore).releasePartitions(this);
        closed = true;
    }

    private PreparedStatement getAtomQuery(Connection connection, PredicateInfo predicate, Constant[] arguments) {
        PreparedStatement statement = predicate.createQueryStatement(connection, allPartitionIDs);

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
        return predicate.createUpsertStatement(connection, ((RDBMSDataStore)parentDataStore).getDriver());
    }

    private PreparedStatement getAtomDelete(Connection connection, PredicateInfo predicate, Term[] arguments, List<Integer> partitions) {
        PreparedStatement statement = predicate.createDeleteStatement(connection, partitions);

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
     * get the value as a Constant from the results.
     * columnIndex should be 0-indexed (even though jdbc uses 1-index).
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
        float value = resultSet.getFloat(PredicateInfo.VALUE_COLUMN_NAME);
        if (resultSet.wasNull()) {
            value = Float.NaN;
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
    @Override
    public GroundAtom getAtom(StandardPredicate predicate, boolean create, Constant... arguments) {
        // Only allocate one GetAtom per thread.
        QueryAtom queryAtom = null;
        if (!Parallel.hasThreadObject(THREAD_QUERY_ATOM_KEY)) {
            queryAtom = new QueryAtom(predicate, arguments);
            Parallel.putThreadObject(THREAD_QUERY_ATOM_KEY, queryAtom);
        } else {
            queryAtom = (QueryAtom)(Parallel.getThreadObject(THREAD_QUERY_ATOM_KEY));
            queryAtom.assume(predicate, arguments);
        }

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
        ((RDBMSDataStore)parentDataStore).getPredicateInfo(predicate);

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
            Connection conn = getConnection();
            PreparedStatement statement = getAtomQuery(conn, ((RDBMSDataStore)parentDataStore).getPredicateInfo(predicate), arguments);
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

    @Override
    public List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions) {
        List<GroundAtom> atoms = new ArrayList<GroundAtom>();
        PredicateInfo predicateInfo = ((RDBMSDataStore)parentDataStore).getPredicateInfo(predicate);

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

    @Override
    public int countAllGroundAtoms(StandardPredicate predicate, List<Integer> partitions) {
        PredicateInfo predicateInfo = ((RDBMSDataStore)parentDataStore).getPredicateInfo(predicate);

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

        float value = (float)predicate.computeValue(this, arguments);
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
        } else if (argument instanceof UniqueIntID) {
            statement.setInt(index, ((UniqueIntID)argument).getID());
        } else if (argument instanceof UniqueStringID) {
            statement.setString(index, ((UniqueStringID)argument).getID());
        } else {
            throw new IllegalArgumentException("Unknown argument type: " + argument.getClass());
        }
    }

    private class RDBMSQueryResultIterable implements QueryResultIterable {
        private Map<Variable, Integer> projectionMap;
        private RDBMSQueryResultIterator iterator;

        public RDBMSQueryResultIterable(String queryString, Map<Variable, Integer> projectionMap, int[] orderedIndexes, ConstantType[] orderedTypes) {
            this.projectionMap = Collections.unmodifiableMap(projectionMap);
            this.iterator = new RDBMSQueryResultIterator(queryString, orderedIndexes, orderedTypes);
        }

        @Override
        public Map<Variable, Integer> getVariableMap() {
            return projectionMap;
        }

        @Override
        public Iterator<Constant[]> iterator() {
            return iterator;
        }

        @Override
        public void close() {
            if (iterator != null) {
                iterator.close();
                iterator = null;
            }
        }
    }

    /**
     * An iterator that will execute a query and stream back the results.
     */
    private class RDBMSQueryResultIterator implements Iterator<Constant[]> {
        private String queryString;
        private int[] orderedIndexes;
        private ConstantType[] orderedTypes;

        private Connection connection;
        private Statement statement;
        private ResultSet resultSet;

        private Constant[] next;

        public RDBMSQueryResultIterator(String queryString, int[] orderedIndexes, ConstantType[] orderedTypes) {
            this.queryString = queryString;
            this.orderedIndexes = orderedIndexes;
            this.orderedTypes = orderedTypes;

            next = null;

            connection = null;
            statement = null;
            resultSet = null;

            try {
                connection = getConnection();
                connection.setAutoCommit(false);

                statement = connection.createStatement();
                statement.setFetchSize(fetchSize);

                resultSet = statement.executeQuery(queryString);
            } catch (SQLException ex) {
                close();
                throw new RuntimeException("Error executing query: [" + queryString + "],", ex);
            }

            // Since ResultSet does not have a hasNext(), we need to get the first result early.
            fetchNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Constant[] next() {
            Constant[] rtn = next;
            fetchNext();
            return rtn;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void fetchNext() {
            boolean hasNext = false;
            try {
                hasNext = resultSet.next();
            } catch (SQLException ex) {
                throw new RuntimeException("Error while fetching results for query: [" + queryString + "].", ex);
            }

            if (hasNext) {
                // Fetch the next result.
                next = new Constant[orderedIndexes.length];

                for (int i = 0; i < next.length; i++) {
                    next[i] = extractConstantFromResult(resultSet, orderedIndexes[i], orderedTypes[i]);
                }
            } else {
                // There are no more results, clean up!
                close();
            }
        }

        public void close() {
            next = null;

            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ex) {
                    // Ignore.
                }
                resultSet = null;
            }

            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ex) {
                    // Ignore.
                }
                statement = null;
            }

            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    // Ignore.
                }
                connection = null;
            }
        }
    }
}
