/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;

public class PostgresDataStoreTest extends RDBMSDataStoreTest {
    @Override
    public DataStore getDataStore(boolean clearDB, boolean persisted) {
        DatabaseDriver driver = DatabaseTestUtil.getPostgresDriver(clearDB);
        if (driver == null) {
            return null;
        }

        return new RDBMSDataStore(driver);
    }

    @Override
    public void cleanUp() {
        DatabaseTestUtil.cleanPostgresDriver();
    }
}
