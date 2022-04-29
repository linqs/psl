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

import org.linqs.psl.database.DataStoreTest;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;

import org.junit.Test;

public abstract class RDBMSDataStoreTest extends DataStoreTest {
    @Test
    public void testGetTableStats() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);
        datastore.registerPredicate(p2);

        Inserter inserter1 = datastore.getInserter(p1, datastore.getPartition("0"));
        Inserter inserter2 = datastore.getInserter(p2, datastore.getPartition("0"));

        final int MIN = 0;
        final int COUNT = 1000;
        final int MAX = MIN + COUNT - 1;

        for (int i = MIN; i < COUNT; i++) {
            inserter1.insert("" + (i / 1), "" + (i / 2));
            inserter2.insert("" + (i / 1), "" + (i / 4));
        }

        ((RDBMSDataStore)datastore).indexPredicates();
        DatabaseDriver driver = ((RDBMSDataStore)datastore).getDriver();

        TableStats stats = driver.getTableStats(((RDBMSDataStore)datastore).getPredicateInfo(p1));
        assertEquals(stats.getCount(), COUNT);
        assertEquals(stats.getSelectivity("UNIQUEINTID_0"), 1.0 / 1.0);
        assertEquals(stats.getCardinality("UNIQUEINTID_0"), COUNT / 1);
        assertEquals(stats.getSelectivity("UNIQUEINTID_1"), 1.0 / 2.0);
        assertEquals(stats.getCardinality("UNIQUEINTID_1"), COUNT / 2);

        stats = driver.getTableStats(((RDBMSDataStore)datastore).getPredicateInfo(p2));
        assertEquals(stats.getCount(), COUNT);
        assertEquals(stats.getSelectivity("STRING_0"), 1.0 / 1.0);
        assertEquals(stats.getCardinality("STRING_0"), COUNT / 1);
        assertEquals(stats.getSelectivity("STRING_1"), 1.0 / 4.0);
        assertEquals(stats.getCardinality("STRING_1"), COUNT / 4);
    }

   // TODO(eriq): Add a test that estimates join sizes with histograms.

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
