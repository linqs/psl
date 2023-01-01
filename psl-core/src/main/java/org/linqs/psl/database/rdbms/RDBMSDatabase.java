/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
import org.linqs.psl.database.RawQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
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
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A view on the datastore with specific partitions activated.
 * Keep in mind that the upstream datstore/driver usere a connection pool and we should close
 * out connections and statements after we are done with them.
 */
public class RDBMSDatabase extends Database {
    private static final Logger log = Logger.getLogger(RDBMSDatabase.class);

    private int fetchSize;

    public RDBMSDatabase(RDBMSDataStore parent,
            Partition write, Partition[] read,
            Set<StandardPredicate> closed) {
        super(parent, write, read, closed);
        fetchSize = Options.RDBMS_FETCH_SIZE.getInt();
        this.closed = false;
    }

    @Override
    public void commit(Iterable<? extends GroundAtom> atoms) {
        if (closed) {
            throw new IllegalStateException("Cannot commit on a closed database.");
        }

        // Split the atoms up by predicate.
        Map<Predicate, List<GroundAtom>> atomsByPredicate = new HashMap<Predicate, List<GroundAtom>>();

        for (GroundAtom atom : atoms) {
            if (!(atom.getPredicate() instanceof StandardPredicate)) {
                continue;
            }

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
                        statement.setShort(1, atom.getPartition());

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
    public QueryResultIterable executeGroundingQuery(Formula formula) {
        return executeQueryIterator(formula, false);
    }

    @Override
    public ResultList executeQuery(DatabaseQuery query) {
        return executeQuery(query.getFormula(), query.getDistinct(), query.getIgnoreVariables());
    }

    @Override
    public QueryResultIterable executeQueryIterator(RawQuery rawQuery) {
        return executeQueryIterator(rawQuery.getProjectionMap(), rawQuery.getVariableTypes(), rawQuery.getSQL());
    }

    @Override
    public ResultList executeSQL(RawQuery rawQuery) {
        if (closed) {
            throw new IllegalStateException("Cannot perform query on database that was closed.");
        }

        Map<Variable, Integer> projectionMap = rawQuery.getProjectionMap();
        VariableTypeMap varTypes = rawQuery.getVariableTypes();
        String queryString = rawQuery.getSQL();

        int[] orderedIndexes = new int[projectionMap.size()];
        ConstantType[] orderedTypes = new ConstantType[projectionMap.size()];

        RDBMSResultList results = initQueryResults(projectionMap, varTypes, orderedIndexes, orderedTypes);

        for (Constant[] row : executeQueryIterator(queryString, projectionMap, orderedIndexes, orderedTypes)) {
            results.addResult(row);
        }

        log.trace("Number of results: {}", results.size());

        return results;
    }

    @Override
    public int countAllGroundAtoms(StandardPredicate predicate, List<Short> partitions) {
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

    @Override
    public List<GroundAtom> getAllGroundAtoms(StandardPredicate predicate, List<Short> partitions) {
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

    private ResultList executeQuery(Map<Variable, Integer> projectionMap, VariableTypeMap varTypes, String queryString) {
        if (closed) {
            throw new IllegalStateException("Cannot perform query on database that was closed.");
        }

        int[] orderedIndexes = new int[projectionMap.size()];
        ConstantType[] orderedTypes = new ConstantType[projectionMap.size()];

        RDBMSResultList results = initQueryResults(projectionMap, varTypes, orderedIndexes, orderedTypes);
        for (Constant[] row : executeQueryIterator(queryString, projectionMap, orderedIndexes, orderedTypes)) {
            results.addResult(row);
        }

        return results;
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

    /**
     * Note that the constants are in the order specified by the projection map.
     */
    private QueryResultIterable executeQueryIterator(Map<Variable, Integer> projectionMap, VariableTypeMap varTypes, String queryString) {
        if (closed) {
            throw new IllegalStateException("Cannot perform query on database that was closed.");
        }

        int[] orderedIndexes = new int[projectionMap.size()];
        ConstantType[] orderedTypes = new ConstantType[projectionMap.size()];
        initQueryResults(projectionMap, varTypes, orderedIndexes, orderedTypes);

        return executeQueryIterator(queryString, projectionMap, orderedIndexes, orderedTypes);
    }

    private QueryResultIterable executeQueryIterator(String queryString, Map<Variable, Integer> projectionMap, int[] orderedIndexes, ConstantType[] orderedTypes) {
        if (closed) {
            throw new IllegalStateException("Cannot perform query on database that was closed.");
        }

        log.trace(queryString);

        return new RDBMSQueryResultIterable(queryString, projectionMap, orderedIndexes, orderedTypes);
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
     * Extract and instantiate a single ground atom from a ResultSet.
     * The ResultSet MUST already be primed (next() should have been already called).
     * Will throw if there is no next().
     */
    private GroundAtom extractGroundAtomFromResult(ResultSet resultSet, StandardPredicate predicate, Constant[] arguments)
            throws SQLException {
        float value = resultSet.getFloat(PredicateInfo.VALUE_COLUMN_NAME);
        if (resultSet.wasNull()) {
            value = Float.NaN;
        } else if (value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(String.format(
                    "Attempt to instantiate an atom with a truth value outside of [0, 1]. Value: %f, Predicate: %s, Arguments: [%s].",
                    value, predicate, StringUtils.join(", ", arguments)));
        }

        short partition = resultSet.getShort(PredicateInfo.PARTITION_COLUMN_NAME);
        if (partition == writeID) {
            // Found in the write partition
            if (isClosed((StandardPredicate)predicate)) {
                // Predicate is closed, instantiate as ObservedAtom
                return new ObservedAtom(predicate, arguments, value, partition);
            }

            // Predicate is open, instantiate as RandomVariableAtom
            return new RandomVariableAtom((StandardPredicate)predicate, arguments, value, partition);
        }

        // Must be in a read partition, instantiate as ObservedAtom
        return new ObservedAtom(predicate, arguments, value, partition);
    }

    private PreparedStatement getAtomUpsert(Connection connection, PredicateInfo predicate) {
        return predicate.createUpsertStatement(connection, ((RDBMSDataStore)parentDataStore).getDriver());
    }

    private Connection getConnection() {
        return ((RDBMSDataStore)parentDataStore).getConnection();
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
        public void reuse(Collection<Constant[]> reuseConstants) {
            iterator.reuse(reuseConstants);
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
     * If the iterator is not run to exhaustion, then it MUST BE closed to prevent database resource leakage.
     */
    private class RDBMSQueryResultIterator implements Iterator<Constant[]>, AutoCloseable {
        private String queryString;
        private int[] orderedIndexes;
        private ConstantType[] orderedTypes;

        private Connection connection;
        private Statement statement;
        private ResultSet resultSet;

        private Constant[] next;

        private Queue<Constant[]> reusePool;

        public RDBMSQueryResultIterator(String queryString, int[] orderedIndexes, ConstantType[] orderedTypes) {
            this.queryString = queryString;
            this.orderedIndexes = orderedIndexes;
            this.orderedTypes = orderedTypes;

            reusePool = new ConcurrentLinkedQueue<Constant[]>();

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

        public void reuse(Collection<Constant[]> reuseConstants) {
            // Check for close.
            if (reusePool != null) {
                reusePool.addAll(reuseConstants);
            }
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
                next = reusePool.poll();
                if (next == null) {
                    next = new Constant[orderedIndexes.length];
                }

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

            if (reusePool != null) {
                reusePool.clear();
                reusePool = null;
            }
        }
    }
}
