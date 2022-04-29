/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.database.rdbms.driver;

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.TableStats;
import org.linqs.psl.model.term.ConstantType;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * An interface to a specific RDBMS backend.
 * All connections from drivers should be from thread-safe connection pools.
 */
public interface DatabaseDriver {
    /**
     * Close out any outstanding connections and cleanup.
     */
    public void close();

    /**
     * Returns a connection to the database.
     * Database drivers are expected to fully connect at instantiation (i.e. in the constructor).
     * Implementation are expected to use connection pools.
     * The connections retutned from this methods should be safe to close() without significant performance impact.
     *
     * @return the connection to the database, as specified in the DatabaseDriver constructor
     */
    public Connection getConnection();

    /**
     * Returns whether the underline database supports bulk copying operations.
     */
    public boolean supportsBulkCopy();

    /**
     * Perform a bulk copy operation to load the file directly into the database.
     * May not be supported by all backends.
     */
    public void bulkCopy(String path, String delimiter, boolean hasTruth,
            PredicateInfo predicateInfo, Partition partition);

    /**
     * Get the type name for each argument type.
     */
    public String getTypeName(ConstantType type);

    /**
     * Get the SQL definition for a primary, surrogate (auto-increment) key
     * for use in a CREATE TABLE statement.
     */
    public String getSurrogateKeyColumnDefinition(String columnName);

    /**
     * Get the type name for a double type.
     */
    public String getDoubleTypeName();

    /**
     * Get the SQL for an upsert (merge) on the specified table and columns.
     * An "upsert" updates existing records and inserts where there is no record.
     * Most RDBMSs support some for of upsert, but the syntax is inconsistent.
     * The parameters for the statement should the the specified columns in order.
     * Some databases (like H2) require knowing the key columns we need to use.
     */
    public String getUpsert(String tableName, String[] columns, String[] keyColumns);

    /**
     * Gives the driver a chance to perform any final
     * manipulations to the CREATE TABLE statement.
     */
    public String finalizeCreateTable(CreateTableQuery createTable);

    /**
     * Get a string aggregating expression (one that
     * would appear in the SELECT clause of a grouping query.
     * Postgres uses STRING_AGG and H2 use GROUP_CONCAT.
     */
    public String getStringAggregate(String columnName, String delimiter, boolean distinct);

    /**
     * Get some statistics for a table.
     */
    public TableStats getTableStats(PredicateInfo predicate);

    /**
     * Make sure that all the database-level stats are up-to-date.
     * Is generally called after insertion and indexing.
     */
    public void updateDBStats();

    /**
     * Make sure that all the table statistics are up-to-date.
     * Is generally called after insertion and indexing.
     */
    public void updateTableStats(PredicateInfo predicate);
}
