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
package org.linqs.psl.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.java.PSLModel;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.test.PSLTest;

import org.junit.Before;
import org.junit.Test;

public class PSLModelTest {
    private PSLModel model;
    private DataStore dataStore;

    @Before
    public void setup() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true));

        model = new PSLModel(dataStore);

        model.addPredicate("Single", ConstantType.UniqueStringID);
        model.addPredicate("Double", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        model.addPredicate("Sim", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
    }

    @Test
    public void testBaseAddPredicate() {
        model.addPredicate("TestSingleA", ConstantType.UniqueStringID);
        model.addPredicate("TestSimA", ConstantType.UniqueStringID, ConstantType.UniqueStringID);

        model.addPredicate("TestSingleB", new ConstantType[]{ConstantType.UniqueStringID});
        model.addPredicate("TestSimB", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});
    }

    @Test
    public void testBaseAddRule() {
        model.addRule("10: Single(A) & Sim(A, B) >> Single(B) ^2");
        model.addWeightedRule("Single(A) & Sim(A, B) >> Single(B)", 20.0f, false);

        model.addRule("Sim(A, B) = Sim(B, A) .");
        model.addUnweightedRule("Sim(B, A) = Sim(A, B) .");

        model.addRule("Sim(B, C) = Sim(C, B)", false, 40.0f, true);

        String[] expected = new String[]{
            "10.0: ( SINGLE(A) & SIM(A, B) ) >> SINGLE(B) ^2",
            "20.0: ( SINGLE(A) & SIM(A, B) ) >> SINGLE(B)",
            "1.0 * SIM(A, B) + -1.0 * SIM(B, A) = 0.0 .",
            "1.0 * SIM(B, A) + -1.0 * SIM(A, B) = 0.0 .",
            "1.0 * SIM(B, C) + -1.0 * SIM(C, B) = 0.0 ."
        };

        PSLTest.assertModel(model, expected);
    }
}
