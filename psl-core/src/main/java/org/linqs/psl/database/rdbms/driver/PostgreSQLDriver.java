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

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.Parallel;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PostgreSQL Connection Wrapper.
 */
public class PostgreSQLDriver implements DatabaseDriver {
	public static final String DEFAULT_HOST = "localhost";
	public static final String DEFAULT_PORT = "5432";

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

		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(connectionString);
		config.setMaximumPoolSize(Math.min(8, Parallel.NUM_THREADS * 2));
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
	public boolean supportsExternalFunctions() {
		return false;
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
}
