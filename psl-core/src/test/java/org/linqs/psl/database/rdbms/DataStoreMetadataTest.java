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

import org.linqs.psl.database.Partition;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class DataStoreMetadataTest extends PSLBaseTest {
    private TestModel.ModelInformation model;

    @Before
    public void setup() {
        model = TestModel.getModel();
    }

    @Test
    public void testGetAllPartitions() {
        DataStoreMetadata metadata = ((RDBMSDataStore)model.dataStore).getMetadata();
        Map<String, String> actual = metadata.getAllValuesByType(DataStoreMetadata.PARTITION_NAMESPACE, DataStoreMetadata.NAME_KEY);

        Map<String, String> expected = new HashMap<String, String>();
        expected.put(TestModel.PARTITION_OBSERVATIONS, "1");
        expected.put(TestModel.PARTITION_TARGETS, "2");
        expected.put(TestModel.PARTITION_TRUTH, "3");

        assertEquals(expected, actual);
    }

    @Test
    public void testMultiplePartitions() {
        DataStoreMetadata metadata = ((RDBMSDataStore)model.dataStore).getMetadata();

        Partition partition1;
        Partition partition2;

        partition1 = model.dataStore.getNewPartition();
        partition2 = model.dataStore.getNewPartition();
        assertNotEquals(partition1.getID(), partition2.getID());

        // Check the first partition we get against the model's first partition.
        assertNotEquals(partition1.getID(), model.observationPartition.getID());

        partition1 = model.dataStore.getPartition("testpartition");
        partition2 = model.dataStore.getPartition("testpartition");
        assertEquals(partition1.getID(), partition2.getID());

        partition1 = model.dataStore.getPartition("testpartition1");
        partition2 = model.dataStore.getPartition("testpartition2");
        assertNotEquals(partition1.getID(), partition2.getID());

        partition1 = model.dataStore.getNewPartition();
        partition2 = model.dataStore.getNewPartition();
        assertNotEquals(partition1.getID(), partition2.getID());
    }

    @After
    public void cleanup() {
        model.dataStore.close();
    }
}
