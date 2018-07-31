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

public class H2DatabaseDriver implements DatabaseDriver {
	public enum Type {
		Disk, Memory
	}

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
		try (
			Connection connection = getConnection();
			Statement stmt = connection.createStatement();
		) {
			stmt.executeUpdate("DROP ALL OBJECTS");
		} catch (SQLException e) {
			throw new RuntimeException("Could not clear database.", e);
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
			case Date:
				return "DATE";
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
		sql.add("	(" + StringUtils.join(columns, ", ") + ")");
		sql.add("KEY");
		sql.add("	(" + StringUtils.join(keyColumns, ", ") + ")");
		sql.add("VALUES");
		sql.add("	(" + StringUtils.repeat("?", ", ", columns.length) + ")");

		return StringUtils.join(sql, "\n");
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
}
