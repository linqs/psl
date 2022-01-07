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
package org.linqs.psl.parser;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;

import org.junit.After;
import org.junit.Before;

public abstract class LoaderTest {
    private DataStore dataStore;

    private StandardPredicate singlePredicate;
    private StandardPredicate doublePredicate;

    @Before
    public void setup() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(H2DatabaseDriver.Type.Memory, this.getClass().getName(), true));

        singlePredicate = StandardPredicate.get("Single", ConstantType.UniqueStringID);
        dataStore.registerPredicate(singlePredicate);

        doublePredicate = StandardPredicate.get("Double", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        dataStore.registerPredicate(doublePredicate);
    }

    @After
    public void shutDown() {
        if (dataStore != null) {
            dataStore.close();
            dataStore = null;
        }
    }
}
