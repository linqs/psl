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

public class H2DatabaseDriver implements DatabaseDriver {

	public enum Type {
		Disk, Memory
	}

	// The connection to the H2 database
	private final Connection dbConnection;

	/**
	 * Constructor for the H2 database driver.
	 * @param dbType	Type of database, either Disk or Memory.
	 * @param path		Path to database on disk, or name if type is Memory.
	 * @param clearDB	Whether to perform a DROP ALL on the database after connecting.
	 */
	public H2DatabaseDriver(Type dbType, String path, boolean clearDB) {
		// Attempt to load H2 class files
		try {
			Class.forName("org.h2.Driver");
		} catch (ClassNotFoundException e1) {
			throw new RuntimeException(
					"Could not find database drivers for H2 database. Please add H2 library to class path.");
		}

		// Establish the connection to the specified DB type
		switch (dbType) {
		case Disk:
			this.dbConnection = getDiskDatabase(path);
			break;
		case Memory:
			this.dbConnection = getMemoryDatabase(path);
			break;
		default:
			throw new IllegalArgumentException("Unknown database type: " + dbType);
		}

		// Clear the database if specified
		if (clearDB) {
			clearDB();
		}
	}

	public Connection getDiskDatabase(String path) {
		try {
			return DriverManager.getConnection("jdbc:h2:" + path);
		} catch (SQLException e) {
			throw new RuntimeException("Could not connect to database: " + path, e);
		}
	}

	public Connection getDiskDatabase(String path, String options) {
		try {
			return DriverManager.getConnection("jdbc:h2:" + path + options);
		} catch (SQLException e) {
			throw new RuntimeException(
					"Could not connect to database: " + path, e);
		}
	}

	public Connection getMemoryDatabase(String path) {
		try {
			return DriverManager.getConnection("jdbc:h2:mem:" + path);
		} catch (SQLException e) {
			throw new RuntimeException("Could not connect to database: " + path, e);
		}
	}

	private void clearDB() {
		try {
			Statement stmt = dbConnection.createStatement();
			stmt.executeUpdate("DROP ALL OBJECTS");
		} catch (SQLException e) {
			throw new RuntimeException("Could not clear database.", e);
		}
	}

	@Override
	public Connection getConnection() {
		return dbConnection;
	}

	@Override
	public boolean supportsExternalFunctions() {
		return true;
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
	public PreparedStatement getUpsert(Connection connection, String tableName,
			String[] columns, String[] keyColumns) {
		// H2 uses a "MERGE" syntax and requires a specified key.
		List<String> sql = new ArrayList<String>();
		sql.add("MERGE INTO " + tableName + "");
		sql.add("	(" + StringUtils.join(columns, ", ") + ")");
		sql.add("KEY");
		sql.add("	(" + StringUtils.join(keyColumns, ", ") + ")");
		sql.add("VALUES");
		sql.add("	(" + StringUtils.repeat("?", ", ", columns.length) + ")");

		try {
			return connection.prepareStatement(StringUtils.join(sql, "\n"));
		} catch (SQLException ex) {
			throw new RuntimeException("Could not prepare MySQL upsert for " + tableName, ex);
		}
	}

	@Override
	public String finalizeCreateTable(CreateTableQuery createTable) {
		return createTable.validate().toString();
	}
}
