/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.database.rdbms.driver;

import java.sql.Connection;
import java.sql.DriverManager;
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
			throw new IllegalArgumentException("Unknown database type: "
					+ dbType);
		}

		// Clear the database if specified
		if (clearDB)
			clearDB();
	}

	public Connection getDiskDatabase(String path) {
		try {
			return DriverManager.getConnection("jdbc:h2:" + path);
		} catch (SQLException e) {
			throw new RuntimeException(
					"Could not connect to database: " + path, e);
		}
	}

	public Connection getMemoryDatabase(String path) {
		try {
			return DriverManager.getConnection("jdbc:h2:mem:" + path);
		} catch (SQLException e) {
			throw new RuntimeException(
					"Could not connect to database: " + path, e);
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
  public boolean isSupportExternalFunction() {
    return true;
  }

  @Override
  public String createHashIndex(String index_name, String table_name, String column_name) {
  	return "CREATE HASH INDEX " + index_name + " ON " + table_name + " (" + column_name + " ) ";
  }

  @Override
  public String castStringWithModifiersForIndexing(String column_name) {
  	return column_name;
  }

  @Override
  public String createPrimaryKey(String table_name, String columns) {
  	return "CREATE PRIMARY KEY HASH ON " + table_name + " (" + columns + " ) ";	
  }
}
