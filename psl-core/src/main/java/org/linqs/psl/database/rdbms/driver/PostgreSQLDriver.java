/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
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

	// The connection to PostgreSQL
	private final Connection dbConnection;

	public PostgreSQLDriver(String databaseName, boolean clearDatabase) {
		this(DEFAULT_HOST, DEFAULT_PORT, databaseName, clearDatabase);
	}

	public PostgreSQLDriver(String host, String port, String databaseName, boolean clearDatabase) {
		this(String.format("jdbc:postgresql://%s:%s/%s?loggerLevel=OFF", host, port, databaseName), databaseName, clearDatabase);
	}

	public PostgreSQLDriver(String connectionString, String databaseName, boolean clearDatabase) {
		try {
			Class.forName("org.postgresql.Driver");
			dbConnection = DriverManager.getConnection(connectionString);

			if (clearDatabase) {
				executeUpdate("DROP SCHEMA public CASCADE");
				executeUpdate("CREATE SCHEMA public");
				executeUpdate("GRANT ALL ON SCHEMA public TO public");
			}
		} catch (ClassNotFoundException ex) {
			throw new RuntimeException("Could not find postgres connector. Please check classpath.", ex);
		} catch (SQLException ex) {
			throw new RuntimeException("Database error: " + ex.getMessage(), ex);
		}
	}

	@Override
	public Connection getConnection() {
		return dbConnection;
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
	public PreparedStatement getUpsert(Connection connection, String tableName,
			String[] columns, String[] keyColumns) {
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

		try {
			return connection.prepareStatement(StringUtils.join(sql, "\n"));
		} catch (SQLException ex) {
			throw new RuntimeException("Could not prepare PostgreSQL upsert for " + tableName, ex);
		}
	}

	private void executeUpdate(String query) throws SQLException {
		Statement stmt = null;
		stmt = dbConnection.createStatement();
		stmt.executeUpdate(query);
		stmt.close();
	}

	@Override
	public String finalizeCreateTable(CreateTableQuery createTable) {
		// Use unlogged tables.
		return createTable.validate().toString().replace("CREATE TABLE", "CREATE UNLOGGED TABLE");
	}
}
