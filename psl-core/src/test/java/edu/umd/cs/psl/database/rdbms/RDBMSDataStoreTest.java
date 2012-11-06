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
package edu.umd.cs.psl.database.rdbms;

import java.io.File;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.DataStoreContractTest;

public class RDBMSDataStoreTest extends DataStoreContractTest {

	@Override
	public DataStore getDataStore() {
		RDBMSDataStore dataStore = new RDBMSDataStore(DatabaseDriver.H2, DatabaseDriver.Type.Disk, "psldb", "./", false, "truth", "confidence", "partition", false);
		return dataStore;
	}

	@Override
	public void cleanUp() {
		File file;
		file = new File("./psldb.h2.db");
		file.delete();
		file = new File("./psldb.trace.db");
		file.delete();
	}

}
