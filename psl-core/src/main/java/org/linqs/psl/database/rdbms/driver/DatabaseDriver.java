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
package org.linqs.psl.database.rdbms.driver;

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.Parallel;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * An abstract class  for a specific RDBMS backend.
 * All connections from drivers should be from thread-safe connection pools.
 */
public abstract class DatabaseDriver {
    private static final Logger log = LoggerFactory.getLogger(DatabaseDriver.class);

    protected HikariDataSource dataSource;

    public DatabaseDriver(String driverClass, String connectionString, boolean clearDatabase) {
        // Load the driver class.
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Could not find database driver (" + driverClass + "). Please check classpath.", ex);
        }

        log.debug("Connecting to database using driver: " + driverClass);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);
        config.setMaximumPoolSize(Math.max(8, Parallel.getNumThreads() * 2));
        config.setMaxLifetime(0);
        dataSource = new HikariDataSource(config);

        if (clearDatabase) {
            clearDatabase();
        }
    }

    /**
     * Close out any outstanding connections and cleanup.
     */
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /**
     * Returns a connection to the database.
     * Database drivers are expected to fully connect at instantiation (i.e. in the constructor).
     * Implementation are expected to use connection pools.
     * The connections retutned from this methods should be safe to close() without significant performance impact.
     *
     * @return the connection to the database, as specified in the DatabaseDriver constructor
     */
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to get connection from pool.", ex);
        }
    }

    /**
     * Returns whether the underline database supports bulk copying operations.
     */
    public boolean supportsBulkCopy() {
        return false;
    }

    /**
     * Perform a bulk copy operation to load the file directly into the database.
     * May not be supported by all backends.
     */
    public void bulkCopy(String path, String delimiter, boolean hasTruth,
            PredicateInfo predicateInfo, Partition partition) {
        throw new UnsupportedOperationException(this.getClass() + " does not support bulk copy.");
    }

    /**
     * Clear the context database of any existing tables/data.
     */
    protected abstract void clearDatabase();

    /**
     * Get the type name for each argument type.
     */
    public abstract String getTypeName(ConstantType type);

    /**
     * Get the SQL definition for a primary, surrogate (auto-increment) key
     * for use in a CREATE TABLE statement.
     */
    public abstract String getSurrogateKeyColumnDefinition(String columnName);

    /**
     * Get the type name for a double type.
     */
    public abstract String getDoubleTypeName();

    /**
     * Gives the driver a chance to perform any final
     * manipulations to the CREATE TABLE statement.
     */
    public String finalizeCreateTable(CreateTableQuery createTable) {
        return createTable.validate().toString();
    }

    /**
     * Get the SQL for an upsert (merge) on the specified table and columns.
     * An "upsert" updates existing records and inserts where there is no record.
     * Most RDBMSs support some for of upsert, but the syntax is inconsistent.
     * The parameters for the statement should the the specified columns in order.
     * Some databases (like H2) require knowing the key columns we need to use.
     */
    public abstract String getUpsert(String tableName, String[] columns, String[] keyColumns);

    /**
     * Make sure that all the database-level stats are up-to-date.
     * Is generally called after insertion and indexing.
     */
    public abstract void updateDBStats();

    /**
     * Take in a select query and return a select query string that limits the number of results to the specified amount.
     */
    public String setLimit(SelectQuery query, int count) {
        query.setFetchNext(count);
        return query.validate().toString();
    }

    public boolean canExplain() {
        return false;
    }

    public boolean canConcurrentWrite() {
        return true;
    }

    /**
     * Get query planing statistics for the given select statement.
     */
    public ExplainResult explain(String queryString) {
        throw new UnsupportedOperationException(this.getClass() + " does not support EXPLAIN.");
    }

    protected void executeUpdate(String sql) {
        try (
            Connection connection = getConnection();
            Statement stmt = connection.createStatement();
        ) {
            stmt.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to execute a general update: [" + sql + "].", ex);
        }
    }

    public static class ExplainResult {
        public final double totalCost;
        public final double startupCost;
        public final long rows;

        public ExplainResult(double totalCost, double startupCost, long rows) {
            this.startupCost = startupCost;
            this.totalCost = totalCost;
            this.rows = rows;
        }

        public String toString() {
            return String.format("Rows: %d, Total Cost: %f, Startup Cost: %f",
                    rows, totalCost, startupCost);
        }
    }
}
