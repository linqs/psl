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
package org.linqs.psl.database.rdbms.driver;

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.TableStats;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.StringUtils;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class H2DatabaseDriver implements DatabaseDriver {
    public enum Type {
        Disk, Memory
    }

    private static final Logger log = LoggerFactory.getLogger(H2DatabaseDriver.class);

    private final HikariDataSource dataSource;

    /**
     * Constructor for the H2 database driver.
     * @param dbType Type of database, either Disk or Memory.
     * @param path Path to database on disk, or name if type is Memory.
     * @param clearDB Whether to perform a DROP ALL on the database after connecting.
     */
    public H2DatabaseDriver(Type dbType, String path, boolean clearDB) {
        // Load the driver class.
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Could not find H2 driver. Please check classpath", ex);
        }

        log.debug("Connecting to H2 database: " + path);

        // Establish the connection to the specified DB type
        String connectionString = null;
        switch (dbType) {
            case Disk:
                connectionString = "jdbc:h2:" + path;
                break;
            case Memory:
                connectionString = "jdbc:h2:mem:" + path;
                break;
            default:
                throw new IllegalArgumentException("Unknown database type: " + dbType);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);
        config.setMaximumPoolSize(Math.max(8, Parallel.getNumThreads() * 2));
        config.setMaxLifetime(0);
        dataSource = new HikariDataSource(config);

        // Clear the database if specified
        if (clearDB) {
            clearDB();
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to get connection from pool.", ex);
        }
    }

    private void clearDB() {
        executeUpdate("DROP ALL OBJECTS");
    }

    private void executeUpdate(String sql) {
        try (
            Connection connection = getConnection();
            Statement stmt = connection.createStatement();
        ) {
            stmt.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to execute a general update: [" + sql + "].", ex);
        }
    }

    @Override
    public boolean supportsBulkCopy() {
        return false;
    }

    public void bulkCopy(String path, String delimiter, boolean hasTruth,
            PredicateInfo predicateInfo, Partition partition) {
        throw new UnsupportedOperationException("H2 does not support bulk copy.");
    }

    @Override
    public String getTypeName(ConstantType type) {
        switch (type) {
            case Double:
                return "DOUBLE";
            case Integer:
                return "INT";
            case String:
                return "VARCHAR";
            case Long:
                return "BIGINT";
            case UniqueIntID:
                return "INT";
            case UniqueStringID:
                return "VARCHAR(255)";
            default:
                throw new IllegalStateException("Unknown ConstantType: " + type);
        }
    }

    @Override
    public String getSurrogateKeyColumnDefinition(String columnName) {
        return columnName + " BIGINT IDENTITY PRIMARY KEY";
    }

    @Override
    public String getDoubleTypeName() {
        return "DOUBLE";
    }

    @Override
    public String getUpsert(String tableName, String[] columns, String[] keyColumns) {
        // H2 uses a "MERGE" syntax and requires a specified key.
        List<String> sql = new ArrayList<String>();
        sql.add("MERGE INTO " + tableName + "");
        sql.add("    (" + StringUtils.join(", ", columns) + ")");
        sql.add("KEY");
        sql.add("    (" + StringUtils.join(", ", keyColumns) + ")");
        sql.add("VALUES");
        sql.add("    (" + StringUtils.repeat("?", ", ", columns.length) + ")");

        return ListUtils.join(System.lineSeparator(), sql);
    }

    @Override
    public String finalizeCreateTable(CreateTableQuery createTable) {
        return createTable.validate().toString();
    }

    @Override
    public String getStringAggregate(String columnName, String delimiter, boolean distinct) {
        if (delimiter.contains("'")) {
            throw new IllegalArgumentException("Delimiter (" + delimiter + ") may not contain a single quote.");
        }

        return String.format("GROUP_CONCAT(DISTINCT CAST(%s AS TEXT) SEPARATOR '%s')",
                columnName, delimiter);
    }

    @Override
    public TableStats getTableStats(PredicateInfo predicate) {
        List<String> sql = new ArrayList<String>();
        sql.add("SELECT");
        sql.add("    UPPER(COLUMN_NAME) AS col,");
        sql.add("    (SELECT COUNT(*) FROM " + predicate.tableName() + ") AS tableCount,");
        sql.add("    SELECTIVITY / 100.0 AS selectivity");
        sql.add("FROM INFORMATION_SCHEMA.COLUMNS");
        sql.add("WHERE");
        sql.add("    UPPER(TABLE_NAME) = '" + predicate.tableName().toUpperCase() + "'");
        sql.add("    AND UPPER(COLUMN_NAME) NOT IN ('PARTITION_ID', 'VALUE')");

        TableStats stats = null;

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(ListUtils.join(System.lineSeparator(), sql));
            ResultSet result = statement.executeQuery();
        ) {
            while (result.next()) {
                if (stats == null) {
                    stats = new TableStats(result.getInt(2));
                }

                stats.addColumnSelectivity(result.getString(1), result.getDouble(3));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to get stats from table: " + predicate.tableName(), ex);
        }

        return stats;
    }

    @Override
    public void updateDBStats() {
        executeUpdate("ANALYZE");
    }

    @Override
    public void updateTableStats(PredicateInfo predicate) {
    }
}
