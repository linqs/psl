/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.database.DataStoreTest;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;

import org.junit.Test;

public abstract class RDBMSDataStoreTest extends DataStoreTest {
    @Test
    public void testBaseExplain() {
        if (datastore == null) {
            return;
        }

        DatabaseDriver driver = ((RDBMSDataStore)datastore).getDriver();
        if (!driver.canExplain()) {
            return;
        }

        String query = "SELECT * FROM " + DataStoreMetadata.METADATA_TABLENAME;

        DatabaseDriver.ExplainResult result = driver.explain(query);
        assertNotNull(result);
    }

    @Test
    public void testLongPredicateName() {
        if (datastore == null) {
            return;
        }

        super.testLongPredicateName();

        // Indexing the database will also check the length of index names.
        ((RDBMSDataStore)datastore).indexPredicates();
    }
}
