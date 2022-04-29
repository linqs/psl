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

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.SelectivityHistogram;
import org.linqs.psl.database.rdbms.TableStats;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.StringUtils;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.JSONArray;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL Connection Wrapper.
 */
public class PostgreSQLDriver implements DatabaseDriver {
    private static final int MAX_STATS = 10000;
    private static final String ENCODING = "UTF-8";

    private static final Logger log = LoggerFactory.getLogger(PostgreSQLDriver.class);

    private final HikariDataSource dataSource;
    private final double statsPercentage;

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
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Could not find postgres driver. Please check classpath.", ex);
        }

        log.debug("Connecting to PostgreSQL database: " + databaseName);

        statsPercentage = Options.POSTGRES_STATS_PERCENTAGE.getDouble();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);
        config.setMaximumPoolSize(Math.max(8, Parallel.getNumThreads() * 2));
        config.setMaxLifetime(0);
        dataSource = new HikariDataSource(config);

        if (clearDatabase) {
            executeUpdate("DROP SCHEMA public CASCADE");
            executeUpdate("CREATE SCHEMA public");
            executeUpdate("GRANT ALL ON SCHEMA public TO public");
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
    public void setColumnDefault(String tableName, String columnName, String defaultValue) {
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
    public String finalizeCreateTable(CreateTableQuery createTable) {
        // Use unlogged tables.
        return createTable.validate().toString().replace("CREATE TABLE", "CREATE UNLOGGED TABLE");
    }

    @Override
    public String getStringAggregate(String columnName, String delimiter, boolean distinct) {
        if (delimiter.contains("'")) {
            throw new IllegalArgumentException("Delimiter (" + delimiter + ") may not contain a single quote.");
        }

        return String.format("STRING_AGG(DISTINCT CAST(%s AS TEXT), '%s')",
                columnName, delimiter);
    }

    @Override
    public TableStats getTableStats(PredicateInfo predicate) {
        List<String> sql = new ArrayList<String>();
        sql.add("SELECT");
        sql.add("    UPPER(attname) AS col,");
        sql.add("    (SELECT COUNT(*) FROM " + predicate.tableName() + ") AS tableCount,");
        sql.add("    CASE WHEN n_distinct >= 0");
        sql.add("        THEN n_distinct / (SELECT COUNT(*) FROM " + predicate.tableName() + ")");
        sql.add("        ELSE -1.0 * n_distinct");
        sql.add("        END AS selectivity,");
        // Because pg_stats is a special system table,
        // it does not have entries that specify what type of array it is (what delim it has).
        // This means that many normal array methods will crash on it.
        // So instead, we convert it to JSON and parse it outside the DB.
        sql.add("    array_to_json(histogram_bounds) AS histogram,");
        sql.add("    array_to_json(most_common_vals) AS most_common_vals,");
        sql.add("    array_to_json(most_common_freqs) AS most_common_freqs");
        sql.add("FROM pg_stats");
        sql.add("WHERE");
        sql.add("    UPPER(tablename) = '" + predicate.tableName().toUpperCase() + "'");
        sql.add("    AND UPPER(attname) NOT IN ('PARTITION_ID', 'VALUE')");

        TableStats stats = null;

        // TODO(eriq): Increase the sampling rate of the table (ALTER TABLE SET STATISTICS) before indexing.

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(ListUtils.join(System.lineSeparator(), sql));
            ResultSet result = statement.executeQuery();
        ) {
            while (result.next()) {
                if (stats == null) {
                    stats = new TableStats(result.getInt(2));
                }

                String columnName = result.getString(1).toUpperCase();
                stats.addColumnSelectivity(columnName, result.getDouble(3));

                SelectivityHistogram histogram = parseHistogram(result.getString(4), result.getString(5), result.getString(6), stats.getCount());
                if (histogram != null) {
                    stats.addColumnHistogram(columnName, histogram);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to get stats from table: " + predicate.tableName(), ex);
        }

        return stats;
    }

    // Because we do not know what column type we will be dealing with ahead of times, we have some unchecked calls.
    @SuppressWarnings("unchecked")
    private SelectivityHistogram parseHistogram(String rawBounds, String rawMostCommonVals, String rawMostCommonCounts, int rowCount) {
        List<Comparable> bounds = null;
        List<Integer> counts = null;
        Map<Comparable, Integer> mostCommonHistogram = null;

        // Try to parse the bucket histogram.
        if (rawBounds != null) {
            JSONArray histogram = new JSONArray(rawBounds);

            if (histogram.length() > 0) {
                bounds = new ArrayList<Comparable>();
                counts = new ArrayList<Integer>();

                int bucketCount = rowCount / (histogram.length() - 1);
                bounds.add(convertHistogramBound(histogram.get(0)));

                for (int i = 1; i < histogram.length(); i++) {
                    bounds.add(convertHistogramBound(histogram.get(i)));
                    counts.add(Integer.valueOf(bucketCount));
                }
            }
        }

        // Check if the most common values were supplied.
        if (rawMostCommonVals != null) {
            JSONArray mostCommonVals = new JSONArray(rawMostCommonVals);
            JSONArray mostCommonCounts = new JSONArray(rawMostCommonCounts);

            if (mostCommonVals.length() > 0) {
                mostCommonHistogram = new HashMap<Comparable, Integer>();

                for (int i = 0; i < mostCommonVals.length(); i++) {
                    // The most common values come in as proportion of the total rows in the table.
                    // So, we will normalize them to raw counts.
                    double proportion = ((Number)mostCommonCounts.get(i)).doubleValue();
                    int count = Math.max(1, (int)(proportion * rowCount));

                    mostCommonHistogram.put(convertHistogramBound(mostCommonVals.get(i)), Integer.valueOf(count));
                }
            }
        }

        SelectivityHistogram histogram = null;

        if (bounds != null) {
            histogram = new SelectivityHistogram();

            if (mostCommonHistogram != null) {
                // If we got both, then put the most common vals back into the buckets.
                addMostCommonValsToBuckets(bounds, counts, mostCommonHistogram);
            }

            histogram.addHistogramBounds(bounds, counts);
        } else if (mostCommonHistogram != null) {
            histogram = new SelectivityHistogram();
            histogram.addHistogramExact(mostCommonHistogram);
        }

        return histogram;
    }

    // Because we do not know what column type we will be dealing with ahead of times, we have some unchecked calls.
    @SuppressWarnings("unchecked")
    private void addMostCommonValsToBuckets(List<Comparable> bounds, List<Integer> counts, Map<Comparable, Integer> mostCommonHistogram) {
        List<Comparable> sortedKeys = new ArrayList<Comparable>(mostCommonHistogram.keySet());
        Collections.sort(sortedKeys);

        int currentCommonIndex = 0;
        int bucketIndex = 0;

        while (true) {
            // If we examined all the common values, then we are done.
            if (currentCommonIndex == sortedKeys.size()) {
                break;
            }
            Comparable currentCommonValue = sortedKeys.get(currentCommonIndex);

            // If there are no more buckets, then the common value must be in the last bucket.
            if (bucketIndex == counts.size()) {
                currentCommonIndex++;

                int index = counts.size() - 1;
                counts.set(index, Integer.valueOf(counts.get(index).intValue() + mostCommonHistogram.get(currentCommonValue).intValue()));

                continue;
            }

            Comparable bucketStartValue = bounds.get(bucketIndex + 0);
            Comparable bucketEndValue = bounds.get(bucketIndex + 1);

            // If the current value is past this bucket, then move the bucket forward.
            if (currentCommonValue.compareTo(bucketEndValue) > 0) {
                bucketIndex++;
                continue;
            }

            // Now the common value must be either before or in this bucket.
            // It is only possible to be before this bucket if this is the first bucket.
            // Either way, put the common value in this bucekt.

            currentCommonIndex++;

            counts.set(bucketIndex, Integer.valueOf(counts.get(bucketIndex).intValue() + mostCommonHistogram.get(currentCommonValue).intValue()));
        }
    }

    private Comparable convertHistogramBound(Object bound) {
        if (bound instanceof Long) {
            return Integer.valueOf(((Long)bound).intValue());
        } else if (bound instanceof Integer) {
            return (Integer)bound;
        } else {
            return bound.toString();
        }
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
    public void updateTableStats(PredicateInfo predicate) {
        int count = 0;
        try (Connection connection = getConnection()) {
            count = predicate.getCount(connection);
        } catch (SQLException ex) {
            throw new RuntimeException(String.format("Could not get table count for stats update: " + predicate));
        }

        int statsCount = (int)Math.min(MAX_STATS, count * statsPercentage);
        if (statsCount == 0) {
            return;
        }

        for (String col : predicate.argumentColumns()) {
            executeUpdate(String.format("ALTER TABLE %s ALTER COLUMN %s SET STATISTICS %d", predicate.tableName(), col, statsCount));
        }
    }
}
