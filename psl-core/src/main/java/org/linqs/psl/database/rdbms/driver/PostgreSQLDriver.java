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

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import org.json.JSONArray;
import org.json.JSONObject;
import org.postgresql.PGConnection;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL Connection Wrapper.
 */
public class PostgreSQLDriver extends DatabaseDriver {
    private static final String ENCODING = "UTF-8";

    private static final Logger log = Logger.getLogger(PostgreSQLDriver.class);

    public PostgreSQLDriver(String databaseName, boolean clearDatabase) {
        this(Options.POSTGRES_HOST.getString(), Options.POSTGRES_PORT.getString(), databaseName, clearDatabase);
    }

    public PostgreSQLDriver(String host, String port, String databaseName, boolean clearDatabase) {
        this(host, port,
                Options.POSTGRES_USER.getString(), (String)Options.POSTGRES_PASSWORD.getUnlogged(),
                databaseName, clearDatabase);
    }

    public PostgreSQLDriver(String host, String port, String user, String password, String databaseName, boolean clearDatabase) {
        this(formatConnectionString(host, port, user, password, databaseName), databaseName, clearDatabase);
    }

    public PostgreSQLDriver(String connectionString, String databaseName, boolean clearDatabase) {
        super("org.postgresql.Driver", connectionString, clearDatabase);

        log.debug("Connected to PostgreSQL database: " + databaseName);
    }

    @Override
    protected void clearDatabase() {
        executeUpdate("DROP SCHEMA public CASCADE");
        executeUpdate("CREATE SCHEMA public");
        executeUpdate("GRANT ALL ON SCHEMA public TO public");
    }

    @Override
    public boolean supportsBulkCopy() {
        return true;
    }

    public void bulkCopy(String path, String delimiter, boolean hasTruth,
            PredicateInfo predicateInfo, Partition partition) {
        String sql = String.format("COPY %s(%s%s) FROM STDIN WITH DELIMITER '%s'",
                predicateInfo.tableName(),
                ListUtils.join(", ", predicateInfo.argumentColumns()),
                hasTruth ? (", " + PredicateInfo.VALUE_COLUMN_NAME) : "",
                delimiter);

        // First change the tables default value for the partition.
        setColumnDefault(predicateInfo.tableName(), PredicateInfo.PARTITION_COLUMN_NAME, "'" + partition.getID() + "'");

        try (
            Connection connection = getConnection();
            FileInputStream inFile = new FileInputStream(path);
        ) {
            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            pgConnection.getCopyAPI().copyIn(sql, inFile);
        } catch (SQLException ex) {
            throw new RuntimeException("Could not perform bulk insert on " + predicateInfo.predicate(), ex);
        } catch (IOException ex) {
            throw new RuntimeException("Error bulk copying file: " + path, ex);
        } finally {
            // Make sure to change the table's default partition value back (to nothing).
            dropColumnDefault(predicateInfo.tableName(), PredicateInfo.PARTITION_COLUMN_NAME);
        }

        // Check for any bad values that got inserted.
        String query = String.format("SELECT COUNT(*) FROM %s WHERE %s < 0.0 OR %s > 1.0",
                predicateInfo.tableName(),
                PredicateInfo.VALUE_COLUMN_NAME,
                PredicateInfo.VALUE_COLUMN_NAME);

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet result = statement.executeQuery();
        ) {
            result.next();
            int badValuesCount = result.getInt(1);

            if (badValuesCount != 0) {
                throw new IllegalArgumentException(String.format(
                        "Found %d invalid truth value(s) for predicate %s (table '%s'). Values must be between 0 and 1 inclusive.",
                        badValuesCount, predicateInfo.predicate().getName(), predicateInfo.tableName()));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to check results of bulk copy on table: " + predicateInfo.tableName(), ex);
        }
    }

    /**
     * Set a default value for a column.
     * The passed in default should already be prepped to be put in the query
     * (ie string values should already be quoted).
     */
    private void setColumnDefault(String tableName, String columnName, String defaultValue) {
        String sql = String.format("ALTER TABLE %s ALTER COLUMN %s SET DEFAULT %s", tableName, columnName, defaultValue);

        try (
                Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Could not set the column default of %s for %s.%s.",
                    defaultValue, tableName, columnName), ex);
        }
    }

    /**
     * Remove the default value for a column.
     */
    public void dropColumnDefault(String tableName, String columnName) {
        String sql = String.format("ALTER TABLE %s ALTER COLUMN %s DROP DEFAULT", tableName, columnName);

        try (
                Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Could not drop the column default for %s.%s.",
                    tableName, columnName), ex);
        }
    }

    @Override
    public String getTypeName(ConstantType type) {
        switch (type) {
            case Double:
                return "DOUBLE PRECISION";
            case Integer:
                return "INT";
            case String:
                return "TEXT";
            case Long:
                return "BIGINT";
            case UniqueIntID:
                return "INT";
            case UniqueStringID:
                return "TEXT";
            default:
                throw new IllegalStateException("Unknown ConstantType: " + type);
        }
    }

    @Override
    public String getSurrogateKeyColumnDefinition(String columnName) {
        return columnName + " SERIAL PRIMARY KEY";
    }

    @Override
    public String getDoubleTypeName() {
        return "DOUBLE PRECISION";
    }

    @Override
    public String getUpsert(String tableName, String[] columns, String[] keyColumns) {
        List<String> updateValues = new ArrayList<String>();
        for (String column : columns) {
            updateValues.add(String.format("%s = EXCLUDED.%s", column, column));
        }

        // PostgreSQL uses the "INSERT ... ON CONFLICT" syntax.
        List<String> sql = new ArrayList<String>();
        sql.add("INSERT INTO " + tableName + "");
        sql.add("    (" + StringUtils.join(", ", columns) + ")");
        sql.add("VALUES");
        sql.add("    (" + StringUtils.repeat("?", ", ", columns.length) + ")");
        sql.add("ON CONFLICT");
        sql.add("    (" + StringUtils.join(", ", keyColumns) + ")");
        sql.add("DO UPDATE SET");
        sql.add("    " + ListUtils.join(", ", updateValues));

        return ListUtils.join(System.lineSeparator(), sql);
    }

    @Override
    public String finalizeCreateTable(CreateTableQuery createTable) {
        // Use unlogged tables.
        return createTable.validate().toString().replace("CREATE TABLE", "CREATE UNLOGGED TABLE");
    }

    private static String formatConnectionString(String host, String port, String user, String password, String databaseName) {
        String connectionString = String.format(
                "jdbc:postgresql://%s:%s/%s?loggerLevel=OFF",
                urlEncode(host), urlEncode(port), urlEncode(databaseName));

        if (user != null && user.length() > 0) {
            connectionString += "&user=" + urlEncode(user);
        }

        if (password != null && password.length() > 0) {
            connectionString += "&password=" + urlEncode(password);
        }

        return connectionString;
    }

    private static String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, ENCODING);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(String.format("Bad encoding: '%s'.", ENCODING), ex);
        }
    }

    @Override
    public void updateDBStats() {
        executeUpdate("VACUUM ANALYZE");
    }

    @Override
    public boolean canExplain() {
        return true;
    }

    @Override
    public ExplainResult explain(String queryString) {
        log.trace("EXPLAIN " + queryString);

        queryString = "EXPLAIN (FORMAT JSON) " + queryString;

        StringBuilder result = new StringBuilder();
        try (
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            ResultSet results = statement.executeQuery(queryString);
        ) {
            boolean hasResults = false;
            while (results.next()) {
                hasResults = true;
                result.append(results.getString(1));
            }

            if (!hasResults) {
                log.error(queryString);
                throw new RuntimeException("No results from an EXPLAIN.");
            }
        } catch (SQLException ex) {
            log.error(queryString);
            throw new RuntimeException("Error EXPLAINing.", ex);
        }

        JSONArray resultJSON = new JSONArray(result.toString());

        JSONObject plan = resultJSON.getJSONObject(0).getJSONObject("Plan");
        double totalCost = plan.getDouble("Total Cost");
        double startupCost = plan.getDouble("Startup Cost");
        long rows = plan.getLong("Plan Rows");

        log.trace("Estimated Cost: {}, Startup Cost: {}, Estimated Rows: {}", totalCost, startupCost, rows);

        return new ExplainResult(totalCost, startupCost, rows);
    }
}
