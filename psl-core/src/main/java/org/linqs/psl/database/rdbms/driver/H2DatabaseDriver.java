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

import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.TableStats;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class H2DatabaseDriver extends DatabaseDriver {
    public enum Type {
        Disk, Memory
    }

    private static final Logger log = Logger.getLogger(H2DatabaseDriver.class);

    /**
     * Constructor for the H2 database driver.
     * @param dbType Type of database, either Disk or Memory.
     * @param path Path to database on disk, or name if type is Memory.
     * @param clearDatabase Whether to perform a DROP ALL on the database after connecting.
     */
    public H2DatabaseDriver(Type dbType, String path, boolean clearDatabase) {
        super("org.h2.Driver", buildConnectionString(dbType, path), clearDatabase);

        log.debug("Connected to H2 database: " + path);
    }

    private static String buildConnectionString(Type dbType, String path) {
        switch (dbType) {
            case Disk:
                return "jdbc:h2:" + path;
            case Memory:
                return "jdbc:h2:mem:" + path;
            default:
                throw new IllegalArgumentException("Unknown database type: " + dbType);
        }
    }

    @Override
    protected void clearDatabase() {
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
