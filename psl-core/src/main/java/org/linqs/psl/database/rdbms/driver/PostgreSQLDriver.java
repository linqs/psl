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
package org.linqs.psl.database.rdbms.driver;

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.StringUtils;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL Connection Wrapper.
 */
public class PostgreSQLDriver implements DatabaseDriver {
	public static final String DEFAULT_HOST = "localhost";
	public static final String DEFAULT_PORT = "5432";

	private static final Logger log = LoggerFactory.getLogger(PostgreSQLDriver.class);

	private final HikariDataSource dataSource;

	public PostgreSQLDriver(String databaseName, boolean clearDatabase) {
		this(DEFAULT_HOST, DEFAULT_PORT, databaseName, clearDatabase);
	}

	public PostgreSQLDriver(String host, String port, String databaseName, boolean clearDatabase) {
		this(String.format("jdbc:postgresql://%s:%s/%s?loggerLevel=OFF", host, port, databaseName), databaseName, clearDatabase);
	}

	public PostgreSQLDriver(String connectionString, String databaseName, boolean clearDatabase) {
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException ex) {
			throw new RuntimeException("Could not find postgres driver. Please check classpath.", ex);
		}

		log.debug("Connecting to PostgreSQL database: " + databaseName);

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
				StringUtils.join(predicateInfo.argumentColumns(), ", "),
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
	}

	/**
	 * Set a default value for a column.
	 * The passed in default should already be prepped to be put in the query
	 * (ie string values should already be quoted).
	 */
	public void setColumnDefault(String tableName, String columnName, String defaultValue) {
		String sql = String.format("ALTER TABLE %s ALTER COLUMN %s SET DEFAULT %s", tableName, columnName, defaultValue);

		try (Connection connection = getConnection()) {
			PreparedStatement statement = connection.prepareStatement(sql);
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

		try (Connection connection = getConnection()) {
			PreparedStatement statement = connection.prepareStatement(sql);
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
			case Date:
				return "DATE";
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
		sql.add("	(" + StringUtils.join(columns, ", ") + ")");
		sql.add("VALUES");
		sql.add("	(" + StringUtils.repeat("?", ", ", columns.length) + ")");
		sql.add("ON CONFLICT");
		sql.add("	(" + StringUtils.join(keyColumns, ", ") + ")");
		sql.add("DO UPDATE SET");
		sql.add("	" + StringUtils.join(updateValues, ", "));

		return StringUtils.join(sql, "\n");
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
		sql.add("	UPPER(attname) AS col,");
		sql.add("	(SELECT COUNT(*) FROM " + predicate.tableName() + ") AS tableCount,");
		sql.add("	CASE WHEN n_distinct >= 0");
		sql.add("		THEN n_distinct / (SELECT COUNT(*) FROM " + predicate.tableName() + ")");
		sql.add("		ELSE -1.0 * n_distinct");
		sql.add("		END AS selectivity,");
		// Because pg_stats is a special system table,
		// it does not have entries that specify what type of array it is (what delim it has).
		// This means that many normal array methods will crash on it.
		// So instead, we convert it to JSON and parse it outside the DB.
		sql.add("	array_to_json(histogram_bounds) AS histogram");
		sql.add("FROM pg_stats");
		sql.add("WHERE");
		sql.add("	UPPER(tablename) = '" + predicate.tableName().toUpperCase() + "'");
		sql.add("	AND UPPER(attname) NOT IN ('PARTITION_ID', 'VALUE')");

		TableStats stats = null;

		// TODO(eriq): Increase the sampling rate of the table (ALTER TABLE SET STATISTICS) before indexing.

		// TEST
		// System.out.println(StringUtils.join(sql, "\n"));

		try (
			Connection connection = getConnection();
			PreparedStatement statement = connection.prepareStatement(StringUtils.join(sql, "\n"));
			ResultSet result = statement.executeQuery();
		) {
			while (result.next()) {
				if (stats == null) {
					stats = new TableStats(result.getInt(2));
				}

				String columnName = result.getString(1);
				stats.addColumnSelectivity(columnName, result.getDouble(3));

				/*

				// TEST
				if (result.getString(4) == null) {
					continue;
				}

				// TEST
				System.out.println("--- " + predicate.tableName() + "." + columnName + " ---");
				System.out.println(result.getString(4));
				System.out.println("---");

				// TODO(eriq): If the histogram is null, then we have exact values in most_common_vals/most_common_freqs.
				//  We will need to build an exact historgram.
				// TODO(eriq): We should put the values in most_common_vals back into the historgram to get a more
				//  accurate distribution.

				Object parsed = JSONValue.parse(result.getString(4));
				if (!(parsed instanceof JSONArray)) {
					throw new IllegalStateException("Histogram in unexpected format. Expected JSON array, got: " + parsed.getClass().getName());
				}
				JSONArray histogram = (JSONArray)parsed;

				List<Comparable> bounds = new ArrayList<Comparable>();
				List<Integer> counts = new ArrayList<Integer>();

				if (histogram.size() > 0) {
					int bucketCount = stats.getCount() / (histogram.size() - 1);
					bounds.add(convertHistogramBound(histogram.get(0)));

					for (int i = 1; i < histogram.size(); i++) {
						bounds.add(convertHistogramBound(histogram.get(i)));
						counts.add(new Integer(bucketCount));
					}

					stats.addColumnHistogram(columnName, bounds, counts);
				}
				*/
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Failed to get stats from table: " + predicate.tableName(), ex);
		}

		return stats;
	}

	private Comparable convertHistogramBound(Object bound) {
		if (bound instanceof Long) {
			return new Integer(((Long)bound).intValue());
		} else if (bound instanceof Integer) {
			return new Integer(((Integer)bound).intValue());
		} else {
			return bound.toString();
		}
	}

	@Override
	public void updateTableStats() {
		executeUpdate("VACUUM ANALYZE");
	}
}
