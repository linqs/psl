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

import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.Hash;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.StringUtils;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CreateIndexQuery;
import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.DeleteQuery;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.QueryPreparer;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PredicateInfo {
    private static final Logger log = LoggerFactory.getLogger(PredicateInfo.class);

    public static final String PREDICATE_TABLE_SUFFIX = "_PREDICATE";
    public static final String PARTITION_COLUMN_NAME = "partition_id";
    public static final String VALUE_COLUMN_NAME = "value";

    // Postgres has a compile-time limit set on identifiers (64 including null).
    public static final int MAX_TABLE_NAME_LENGTH = 63;

    // Prefix any hashed table names with this.
    // Most DBMS don't like starting an identifier with a number, so prefix an alpha to be safe.
    // "H" for hash.
    public static final String HASH_PREFIX = "H";

    private final Predicate predicate;
    private final List<String> argCols;
    private final String tableName;

    private Map<String, String> cachedSQL;
    private boolean indexed;
    private int count;

    private TableStats tableStats;

    public PredicateInfo(Predicate predicate) {
        assert(predicate != null);

        this.predicate = predicate;
        this.tableName = constructTableName(predicate.getName());

        argCols = new ArrayList<String>(predicate.getArity());
        for (int i = 0; i < predicate.getArity(); i++) {
            argCols.add(predicate.getArgumentType(i).getName() + "_" + i);
        }

        cachedSQL = new HashMap<String, String>();
        this.indexed = false;
        count = -1;
        tableStats = null;
    }

    public List<String> argumentColumns() {
        return Collections.unmodifiableList(argCols);
    }

    public String tableName() {
        return tableName;
    }

    public Predicate predicate() {
        return predicate;
    }

    public boolean indexed() {
        return indexed;
    }

    public synchronized TableStats getTableStats(DatabaseDriver dbDriver) {
        if (tableStats != null) {
            return tableStats;
        }

        tableStats = dbDriver.getTableStats(this);
        return tableStats;
    }

    public void setupTable(Connection connection, DatabaseDriver dbDriver) {
        createTable(connection, dbDriver);
    }

    /**
     * Create a prepared statement to count all the ground atoms for this predicate (within the specified partitions).
     * You can specify no partitions with null or an empty list.
     */
    public PreparedStatement createCountAllStatement(Connection connection, List<Integer> partitions) {
        return prepareSQL(connection, buildCountAllStatement(partitions));
    }

    /**
     * Create a prepared statement to query all the atoms for this predicate (within the specified partitions).
     * You can specify no partitions with null or an empty list.
     * The columns will ALWAYS be in the following order: partition, value, data columns (determined by getArgumentColumns()).
     */
    public PreparedStatement createQueryAllStatement(Connection connection, List<Integer> partitions) {
        return prepareSQL(connection, buildQueryAllStatement(partitions));
    }

    /**
     * Create a prepared statement that queries for all random variable atoms
     * (atoms in the write partition) of this predicate.
     * No query parameters need filling.
     */
    public PreparedStatement createQueryAllWriteStatement(Connection connection, int writePartition) {
        List<Integer> partitions = new ArrayList<Integer>(1);
        partitions.add(writePartition);
        return createQueryAllStatement(connection, partitions);
    }

    /**
     * Create a prepared statement that queries for one specific atom.
     * The variables left to set in the query are the predciate arguments.
     */
    public PreparedStatement createQueryStatement(Connection connection, List<Integer> readPartitions) {
        return prepareSQL(connection, buildQueryStatement(readPartitions));
    }

    /**
     * Create a prepared statement that upserts.
     * The variables left to set in the query are the partition, value, and predciate arguments.
     */
    public PreparedStatement createUpsertStatement(Connection connection, DatabaseDriver dbDriver) {
        return prepareSQL(connection, buildUpsertStatement(dbDriver));
    }

    /**
     * Create a prepared statement that deletes ground atoms that match all the arguments.
     * Note that we will only delete from the provided partitions.
     */
    public PreparedStatement createDeleteStatement(Connection connection, List<Integer> partitions) {
        return prepareSQL(connection, buildDeleteStatement(partitions));
    }

    /**
     * Create a prepared statement that changes moves atoms from one partition to another.
     */
    public PreparedStatement createPartitionMoveStatement(Connection connection, int oldPartition, int newPartition) {
        return prepareSQL(connection, buildPartitionMoveStatement(oldPartition, newPartition));
    }

    /**
     * Get a count of all the rows in the table.
     */
    public int getCount(Connection connection) {
        if (count != -1) {
            return count;
        }

        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet result = statement.executeQuery();
        ) {
            result.next();
            count = result.getInt(1);
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to get count from table: " + tableName, ex);
        }

        return count;
    }

    private void createTable(Connection connection, DatabaseDriver dbDriver) {
        CreateTableQuery createTable = new CreateTableQuery(tableName);

        // First add non-variable columns: partition and value.
        createTable.addCustomColumns(PARTITION_COLUMN_NAME + " INT NOT NULL");
        createTable.addCustomColumns(VALUE_COLUMN_NAME + " " + dbDriver.getDoubleTypeName() + " NOT NULL DEFAULT 1.0");

        // Now add the variable columns.
        List<String> uniqueColumns = new ArrayList<String>();

        // Add columns for each predicate argument
        for (int i = 0; i < argCols.size(); i++) {
            String colName = argCols.get(i);

            ConstantType type = predicate.getArgumentType(i);
            String typeName = dbDriver.getTypeName(type);

            // All unique columns get added to a unique constraint.
            if (type == ConstantType.UniqueIntID || type == ConstantType.UniqueStringID) {
                uniqueColumns.add(colName);
            }

            createTable.addCustomColumns(colName + " " + typeName + " NOT NULL");
        }

        // Add a unique constraint for all the unique ids (and partition).
        // The partition column MUST be the last one (since H2 is super picky).
        uniqueColumns.add(PARTITION_COLUMN_NAME);
        if (uniqueColumns.size() > 1) {
            createTable.addCustomConstraints("UNIQUE(" + ListUtils.join(", ", uniqueColumns) + ")");
        }

        // We have an additional constraint that all atoms in a partition must be unique.
        // If all columns are UniqueIDs, when we are already done.
        // Add 1 since we already put the partition in |uniqueColumns|.
        if (uniqueColumns.size() < (argCols.size() + 1)) {
            // We want the partition to be last.
            uniqueColumns.remove(PARTITION_COLUMN_NAME);
            for (String colName : argCols) {
                if (!uniqueColumns.contains(colName)) {
                    uniqueColumns.add(colName);
                }
            }
            uniqueColumns.add(0, PARTITION_COLUMN_NAME);

            createTable.addCustomConstraints("UNIQUE(" + ListUtils.join(", ", uniqueColumns) + ")");
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(dbDriver.finalizeCreateTable(createTable));
        } catch(SQLException ex) {
            throw new RuntimeException("Error creating table for predicate: " + predicate.getName(), ex);
        }
    }

    public synchronized void index(Connection connection, DatabaseDriver dbDriver) {
        if (indexed) {
            return;
        }
        indexed = true;

        List<String> indexes = new ArrayList<String>();

        // The primary index used for grounding.
        CreateIndexQuery createIndex = new CreateIndexQuery(tableName, "IX_" + tableName + "_GROUNDING");

        // The column order is very important: data columns, then partition.
        for (String colName : argCols) {
            createIndex.addCustomColumns(colName);
        }
        createIndex.addCustomColumns(PARTITION_COLUMN_NAME);
        indexes.add(createIndex.validate().toString());

        // Create simple index on each column.
        // Often the query planner will choose a small index over the full one for specific parts of the query.
        for (String colName : argCols) {
            createIndex = new CreateIndexQuery(tableName, "IX_" + tableName + "_" + colName);
            createIndex.addCustomColumns(colName);
            indexes.add(createIndex.validate().toString());
        }

        // Include the partition.
        createIndex = new CreateIndexQuery(tableName, "IX_" + tableName + "_" + PARTITION_COLUMN_NAME);
        createIndex.addCustomColumns(PARTITION_COLUMN_NAME);
        indexes.add(createIndex.validate().toString());

        try (Statement statement = connection.createStatement()) {
            for (String index : indexes) {
                statement.executeUpdate(index);
            }
        } catch(SQLException ex) {
            throw new RuntimeException("Error creating index on table for predicate: " + predicate.getName(), ex);
        }
    }

    private synchronized String buildCountAllStatement(List<Integer> partitions) {
        assert(partitions != null);

        String key = "countAll_" + partitions.toString();
        if (cachedSQL.containsKey(key)) {
            return cachedSQL.get(key);
        }

        SelectQuery query = new SelectQuery();

        query.addCustomColumns(new CustomSql("COUNT(*)"));
        query.addCustomFromTable(tableName);

        // If there is only 1 partition, just do equality, otherwise use IN.
        // All DBMSs should optimize a single IN the same as equality, but just in case.
        if (partitions.size() == 1) {
            query.addCondition(BinaryCondition.equalTo(new CustomSql(PARTITION_COLUMN_NAME), partitions.get(0)));
        } else if (partitions.size() > 1) {
            query.addCondition(new InCondition(new CustomSql(PARTITION_COLUMN_NAME), partitions));
        }

        String sql = query.validate().toString();
        cachedSQL.put(key, sql);
        return sql;
    }

    private synchronized String buildQueryAllStatement(List<Integer> partitions) {
        assert(partitions != null);

        String key = "queryAll_" + partitions.toString();
        if (cachedSQL.containsKey(key)) {
            return cachedSQL.get(key);
        }

        SelectQuery query = new SelectQuery();

        // Select everything in a predictable order.
        query.addCustomColumns(new CustomSql(PARTITION_COLUMN_NAME));
        query.addCustomColumns(new CustomSql(VALUE_COLUMN_NAME));
        for (String colName : argCols) {
            query.addCustomColumns(new CustomSql(colName));
        }

        query.addCustomFromTable(tableName);

        // If there is only 1 partition, just do equality, otherwise use IN.
        // All DBMSs should optimize a single IN the same as equality, but just in case.
        if (partitions.size() == 1) {
            query.addCondition(BinaryCondition.equalTo(new CustomSql(PARTITION_COLUMN_NAME), partitions.get(0)));
        } else if (partitions.size() > 1) {
            query.addCondition(new InCondition(new CustomSql(PARTITION_COLUMN_NAME), partitions));
        }

        String sql = query.validate().toString();
        cachedSQL.put(key, sql);
        return sql;
    }

    private synchronized String buildQueryStatement(List<Integer> readPartitions) {
        String key = "query_" + readPartitions.toString();
        if (cachedSQL.containsKey(key)) {
            return cachedSQL.get(key);
        }

        SelectQuery query = new SelectQuery();
        QueryPreparer.MultiPlaceHolder placeHolder = (new QueryPreparer()).getNewMultiPlaceHolder();

        // Seelct *
        query.addAllColumns();
        query.addCustomFromTable(tableName);

        // We only want to query from the read partitions.
        query.addCondition(new InCondition(new CustomSql(PARTITION_COLUMN_NAME), readPartitions));

        for (String colName : argCols) {
            query.addCondition(BinaryCondition.equalTo(new CustomSql(colName), placeHolder));
        }

        String sql = query.validate().toString();
        cachedSQL.put(key, sql);
        return sql;
    }

    private synchronized String buildUpsertStatement(DatabaseDriver dbDriver) {
        String key = "upsert";
        if (cachedSQL.containsKey(key)) {
            return cachedSQL.get(key);
        }

        // Columns with data in them: partition, value, argument.
        String[] columns = new String[2 + argCols.size()];

        // Columns to treat as a key: partition, arguments.
        String[] keyColumns = new String[1 + argCols.size()];

        columns[0] = PredicateInfo.PARTITION_COLUMN_NAME;
        columns[1] = PredicateInfo.VALUE_COLUMN_NAME;

        keyColumns[0] = PredicateInfo.PARTITION_COLUMN_NAME;

        for (int i = 0; i < argCols.size(); i++) {
            columns[2 + i] = argCols.get(i);
            keyColumns[1 + i] = argCols.get(i);
        }

        String sql = dbDriver.getUpsert(tableName, columns, keyColumns);
        cachedSQL.put(key, sql);
        return sql;
    }

    private synchronized String buildDeleteStatement(List<Integer> partitions) {
        String key = "delete_" + partitions.toString();
        if (cachedSQL.containsKey(key)) {
            return cachedSQL.get(key);
        }

        DeleteQuery delete = new DeleteQuery(tableName);
        QueryPreparer.MultiPlaceHolder placeHolder = (new QueryPreparer()).getNewMultiPlaceHolder();

        // Delete from all provided partitions.
        delete.addCondition(new InCondition(new CustomSql(PARTITION_COLUMN_NAME), partitions));

        // Set placeholders for the arguments.
        for (String colName : argCols) {
            delete.addCondition(BinaryCondition.equalTo(new CustomSql(colName), placeHolder));
        }

        String sql = delete.validate().toString();
        cachedSQL.put(key, sql);
        return sql;
    }

    private synchronized String buildPartitionMoveStatement(int oldPartition, int newPartition) {
        String key = "movePartition_" + oldPartition + "_" + newPartition;
        if (cachedSQL.containsKey(key)) {
            return cachedSQL.get(key);
        }

        UpdateQuery update = new UpdateQuery(tableName);

        update.addCondition(BinaryCondition.equalTo(new CustomSql(PARTITION_COLUMN_NAME), oldPartition));
        update.addCustomSetClause(new CustomSql(PARTITION_COLUMN_NAME), newPartition);

        String sql = update.validate().toString();
        cachedSQL.put(key, sql);
        return sql;
    }

    private PreparedStatement prepareSQL(Connection connection, String sql) {
        try {
            return connection.prepareStatement(sql);
        } catch (SQLException ex) {
            throw new RuntimeException("Could not create prepared statement from (" + sql + ").", ex);
        }
    }

    /**
     * Construct the name that will be used for a predicate.
     * This will typically be just appending the table suffix.
     * However if the name length exceeds MAX_TABLE_NAME_LENGTH,
     * then instead a truncated hash of the predicate name will be used.
     */
    private static String constructTableName(String predicateName) {
        String tableName = predicateName + PREDICATE_TABLE_SUFFIX;
        if (tableName.length() <= MAX_TABLE_NAME_LENGTH) {
            return tableName;
        }

        tableName = HASH_PREFIX + Hash.sha(predicateName) + PREDICATE_TABLE_SUFFIX;
        if (tableName.length() > MAX_TABLE_NAME_LENGTH) {
            int truncateSize = tableName.length() - MAX_TABLE_NAME_LENGTH + HASH_PREFIX.length();
            tableName = HASH_PREFIX + tableName.substring(truncateSize, tableName.length());
        }

        return tableName;
    }
}
