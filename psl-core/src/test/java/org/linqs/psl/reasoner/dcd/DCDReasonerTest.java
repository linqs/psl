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
package org.linqs.psl.reasoner.dcd;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.mpe.DCDInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class DCDReasonerTest extends PSLBaseTest {
    /**
     * A quick test that only checks to see if DCDReasoner runs.
     * This is not a targeted or exhaustive test, just a starting point.
     */
    @Test
    public void baseTest() {
        TestModel.ModelInformation info = TestModel.getModel();

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = new DCDInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();
    }
}
