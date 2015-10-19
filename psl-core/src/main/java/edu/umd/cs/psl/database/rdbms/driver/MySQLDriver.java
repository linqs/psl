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
   *  PSL, as there's no transactions, Innodb may be an overhead
   *  however the indexes availability should be taken into 
   *  consideration, for simplicity, use default innodb 
   *  here.)
   *
   * On Configuration:
   * Use the localhost, user root and no pwd for now
   * One potentially can connect to mysql clusters, or use 
   * other user/pwd.
   * 
   * @param dbname  mysql database name, similiar to H2 path
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
          "Could not find mysql connector j. Please check classpath.", e);
    } catch (InstantiationException e) {
      throw new RuntimeException(
          "Could not initiate mysql connector j. Please check the jar and its version.", e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Could not initiate mysql connector j. Please check the jar and its version.", e);
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

  /**
   * Create a full length hash index on the given column@table using the index_name.
   * For more information see: http://dev.mysql.com/doc/refman/5.1/en/create-index.html
   */
  @Override
  public String createHashIndex(String index_name, String table_name, String column_name) {
    return "CREATE INDEX " + index_name + " ON " + table_name + " (" + column_name + " ) USING HASH";
  }

  /**
   * At most use the first 255 characters in an index containing string
   */
  @Override
  public String castStringWithModifiersForIndexing(String column_name) {
    return column_name + "(255)";
  }

  /**
   * Create primary key clause
   * For more information see: http://dev.mysql.com/doc/refman/5.1/en/alter-table.html
   */
  @Override
  public String createPrimaryKey(String table_name, String columns) {
    return "ALTER TABLE " + table_name + " ADD PRIMARY KEY (" + columns + ")";
  }
}
