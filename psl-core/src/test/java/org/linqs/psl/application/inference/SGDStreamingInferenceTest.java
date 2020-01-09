/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
package org.linqs.psl.application.inference;

import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.SGDStreamingInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.predicate.StandardPredicate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class SGDStreamingInferenceTest {
    /**
     * A quick test that only checks to see if SGDStreamingInference is running.
     * This is not a targeted or exhaustive test, just a starting point.
     */
    @Test
    public void baseTest() {
        TestModel.ModelInformation info = TestModel.getModel();

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        SGDStreamingInference mpe = new SGDStreamingInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }

    /**
     * Same as testBase(), but using postgres.
     */
    @Test
    public void testBasePostgres() {
        DatabaseDriver driver = DatabaseTestUtil.getPostgresDriver();
        if (driver == null) {
            return;
        }

        TestModel.ModelInformation info = TestModel.getModel(false, driver);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        SGDStreamingInference mpe = new SGDStreamingInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }
}
