/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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

public interface DatabaseDriver {
	
	/**
	 * Returns a connection to the database. Database drivers are expected to
	 * fully connect at instantiation (i.e. in the constructor).
	 * @return the connection to the database, as specified in the DatabaseDriver constructor
	 */
	public Connection getConnection();
}
