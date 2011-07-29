/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.database.RDBMS;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public enum DatabaseDriver {
	
	H2 {

		@Override
		public Connection getDatabase(String folder, String name, boolean empty) {
			try {
				Class.forName("org.h2.Driver");
			} catch (ClassNotFoundException e1) {
				throw new RuntimeException("Could not find database drivers for H2 database. Please add H2 library to class path.");
			}
			try {
				Connection db = DriverManager.getConnection("jdbc:h2:"+folder+name);
				if (empty)
					clearDB(db);
				return db;
			} catch (SQLException e) {
				throw new AssertionError("Could not connect to database: " + e.getMessage());
			}
		}

		@Override
		public Connection getMemoryDatabase(String name) {
			try {
				Class.forName("org.h2.Driver");
			} catch (ClassNotFoundException e1) {
				throw new RuntimeException("Could not find database drivers for H2 database. Please add H2 library to class path.");
			}
			try {
				return DriverManager.getConnection("jdbc:h2:mem:"+name);
			} catch (SQLException e) {
				throw new AssertionError("Could not create database: " + e.getMessage());
			}
		}
		
		private void clearDB(Connection db) {
			try {
				Statement stmt = db.createStatement();
				stmt.executeUpdate("DROP ALL OBJECTS");
			} catch (SQLException e) {
				throw new AssertionError("Could not delete all objects from db: " + e.getMessage());
			}
		}
		
	};
	
	public abstract Connection getDatabase(String folder, String name, boolean empty);
	
	public Connection getDatabase(String folder, String name) {
		return getDatabase(folder,name,false);
	}
	
	public abstract Connection getMemoryDatabase(String name);
 
	public enum Type { Disk, Memory };

	
}
