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
package org.linqs.psl.database.rdbms;

import java.io.File;

import org.linqs.psl.config.EmptyBundle;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.DataStoreContractTest;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;

public class RDBMSDataStoreTest extends DataStoreContractTest {
	
	private String dbPath;
	private String dbName;

	@Override
	public DataStore getDataStore(boolean clearDB) {
		dbPath = System.getProperty("java.io.tmpdir") + "/";
		dbName = "rdbmsDataStoreTest";
		DatabaseDriver driver = new H2DatabaseDriver(H2DatabaseDriver.Type.Disk, dbPath + dbName, clearDB);
		RDBMSDataStore dataStore = new RDBMSDataStore(driver, new EmptyBundle());
		return dataStore;
	}

	@Override
	public void cleanUp() {
		File file;
		file = new File(dbPath + dbName + ".h2.db");
		file.delete();
		file = new File(dbPath + dbName + ".trace.db");
		file.delete();
	}

}
