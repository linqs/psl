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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * MySQL Connection Wrapper.
 */
public class MySQLDriver implements DatabaseDriver {

	// The connection to MySQL
	private final Connection dbConnection;

	// Wrapper for rdbms DML
	private void executeUpdate(String query) throws SQLException {
		Statement stmt = null;
		stmt = dbConnection.createStatement();
		stmt.executeUpdate(query);
		stmt.close();
	}

	/**
	 * Constructor for the MySQL database driver
	 * On Performance:
	 * Use the non-memory specific model, innodb as default
	 * (Note one can change to use MyISAM or Memory engine. For
	 * PSL, as there's no transactions, Innodb may be an overhead
	 * however the indexes availability should be taken into
	 * consideration, for simplicity, use default innodb
	 * here.)
	 *
	 * On Configuration:
	 * Use the localhost, user root and no pwd for now
	 * One potentially can connect to mysql clusters, or use
	 * other user/pwd.
	 *
	 * @param dbname mysql database name, similiar to H2 path
	 * @param clearDB whether to delete the database
	 */
	public MySQLDriver(String dbname, boolean clearDB) {
		try {
			// load driver
			Class.forName("com.mysql.jdbc.Driver").newInstance();

			// get connection
			dbConnection = DriverManager.getConnection("jdbc:mysql://localhost/?user=root&password=");

			// clean db if specified
			if (clearDB) {
				executeUpdate("DROP DATABASE IF EXISTS " + dbname);
			}

			// create db if not exists
			executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbname);
			executeUpdate("USE " + dbname);

		} catch (ClassNotFoundException e) {
			throw new RuntimeException(
					"Could not find mysql connector. Please check classpath.", e);
		} catch (InstantiationException e) {
			throw new RuntimeException(
					"Could not initiate mysql connector. Please check the jar and its version.", e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(
					"Could not initiate mysql connector. Please check the jar and its version.", e);
		} catch (SQLException e) {
			throw new RuntimeException("Database error: " + e.getMessage(), e);
		}
	}

	@Override
	public Connection getConnection() {
		return dbConnection;
	}

	@Override
	public boolean isSupportExternalFunction() {
		return false;
	}

	@Override
	public String getTypeName(ConstantType type) {
		switch (type) {
			case Double:
				return "DOUBLE";
			case Integer:
				return "INT";
			case String:
				return "VARCHAR(256)";
			case Long:
				return "BIGINT";
			case Date:
				return "DATE";
			case UniqueIntID:
				return "INT";
			case UniqueStringID:
				return "VARCHAR(256)";
			case UniqueID:
				throw new IllegalArgumentException("UniqueID too general, use specific form of UniqueID.");
			default:
				throw new IllegalStateException("Unknown ConstantType: " + type);
		}
	}

	@Override
	public String getSurrogateKeyColumnDefinition(String columnName) {
		return columnName + " INT AUTO_INCREMENT PRIMARY KEY";
	}

	@Override
	public String getDoubleTypeName() {
		return "DOUBLE";
	}

	@Override
	public PreparedStatement getUpsert(Connection connection, String tableName,
			String[] columns, String[] keyColumns) {
		List<String> updateValues = new ArrayList<String>();
		for (String column : columns) {
			updateValues.add(String.format("`%s` = VALUES(`%s`)", column, column));
		}

		// MySQL usees the "INSERT ... ON DUPLICATE KEY" syntax.
		List<String> sql = new ArrayList<String>();
		sql.add("INSERT INTO `" + tableName + "`");
		sql.add("	(`" + StringUtils.join(columns, "`, `") + "`)");
		sql.add("VALUES");
		sql.add("	(" + StringUtils.repeat("?", ", ", columns.length) + ")");
		sql.add("ON DUPLICATE KEY UPDATE");
		sql.add("	" + StringUtils.join(updateValues, ", "));

		try {
			return connection.prepareStatement(StringUtils.join(sql, "\n"));
		} catch (SQLException ex) {
			throw new RuntimeException("Could not prepare MySQL upsert for " + tableName, ex);
		}
	}
}
