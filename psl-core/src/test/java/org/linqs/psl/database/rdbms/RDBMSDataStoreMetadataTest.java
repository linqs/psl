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
package org.linqs.psl.database.rdbms;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.TestModelFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RDBMSDataStoreMetadataTest {
	private TestModelFactory.ModelInformation model;

	@Before
	public void setup() {
		model = TestModelFactory.getModel();
	}

	@Test
	public void testGetAllPartitions() {
		RDBMSDataStoreMetadata metadata = ((RDBMSDataStore)model.dataStore).metadata;
		Map<String, String> actual = metadata.getAllValuesByType(metadata.mdTableName, "Partition", "name");

		Map<String, String> expected = new HashMap<String, String>();
		expected.put(TestModelFactory.PARTITION_OBSERVATIONS, "1");
		expected.put(TestModelFactory.PARTITION_TARGETS, "2");
		expected.put(TestModelFactory.PARTITION_TRUTH, "3");

		assertEquals(expected, actual);
	}

	@After
	public void cleanup() {
		model.dataStore.close();
	}
}
