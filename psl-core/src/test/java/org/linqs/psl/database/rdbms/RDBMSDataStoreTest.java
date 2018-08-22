/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.DataStoreTest;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.util.MathUtils;

import org.junit.Test;

import java.util.Map;

public abstract class RDBMSDataStoreTest extends DataStoreTest {
	@Test
	public void testGetSelectvity() {
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);

		Inserter inserter1 = datastore.getInserter(p1, datastore.getPartition("0"));
		Inserter inserter2 = datastore.getInserter(p2, datastore.getPartition("0"));

		for (int i = 0; i < 1000; i++) {
			inserter1.insert("" + (i / 1), "" + (i / 2));
			inserter2.insert("" + (i / 1), "" + (i / 4));
		}

		((RDBMSDataStore)datastore).indexPredicates();
		DatabaseDriver driver = ((RDBMSDataStore)datastore).getDriver();

		Map<String, Float> selectivity = driver.getSelectivity(((RDBMSDataStore)datastore).getPredicateInfo(p1));
		assertEquals(selectivity.get("UNIQUEINTID_0").floatValue(), 1.0 / 1.0, MathUtils.EPSILON);
		assertEquals(selectivity.get("UNIQUEINTID_1").floatValue(), 1.0 / 2.0, MathUtils.EPSILON);

		selectivity = driver.getSelectivity(((RDBMSDataStore)datastore).getPredicateInfo(p2));
		assertEquals(selectivity.get("STRING_0").floatValue(), 1.0 / 1.0, MathUtils.EPSILON);
		assertEquals(selectivity.get("STRING_1").floatValue(), 1.0 / 4.0, MathUtils.EPSILON);
	}
}
