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

import java.sql.Connection;

public interface DatabaseDriver {
	
	/**
	 * Returns a connection to the database. Database drivers are expected to
	 * fully connect at instantiation (i.e. in the constructor).
	 * @return the connection to the database, as specified in the DatabaseDriver constructor
	 */
	public Connection getConnection();

  /**
   * Returns whether the underline database supports external java functions. 
   * Distinguish from H2 Java External Function Support, which is very special.
   * @return true if support H2 in memory java method, false if not support
   */
  public boolean isSupportExternalFunction();

  /**
   * Template for hash index creation for different drivers.
   * Hash index is useful for PSL shared literal joins. 
   *
   * JDBC has poor support for index creation DML. Often db schema is not dynamically generated, 
   * but hand tuned by DBA. PSL is opposite, db schema is generated on the fly.
   * Configuration bundle is not good place for put index creation queries due to its inflexibility.
   */
  public String createHashIndex(String index_name, String table_name, String column_name);

  /**
   * Primary key creation syntax is not friendly in JDBC. 
   * The template method for each database driver to return the proper clause.
   */
  public String createPrimaryKey(String table_name, String columns);

  /**
   * String type is not friendly to index. Different database retreat it 
   * differently. For example in a hash index, often a prefix of the string
   * is useful enough for indexing purpose. A full string index, not only 
   * reduces query time, but also increases inserting time. 
   * H2 has no complain about string, but mysql does have a limit of string prefix.
   */
  public String castStringWithModifiersForIndexing(String column_name);
}
