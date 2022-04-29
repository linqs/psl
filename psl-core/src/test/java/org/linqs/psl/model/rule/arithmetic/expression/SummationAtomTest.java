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
package org.linqs.psl.model.rule.arithmetic.expression;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SummationAtomTest extends PSLBaseTest {
    private DataStore dataStore;

    private StandardPredicate singlePredicate;
    private StandardPredicate doublePredicate;

    @Before
    public void setup() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true));

        singlePredicate = StandardPredicate.get("SingleClosed", ConstantType.UniqueStringID);
        dataStore.registerPredicate(singlePredicate);

        doublePredicate = StandardPredicate.get("DoubleClosed", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        dataStore.registerPredicate(doublePredicate);
    }

    @Test
    public void testValidateArgLength1() {
        try {
            new SummationAtom(singlePredicate, new SummationVariableOrTerm[]{});
            fail("IllegalArgumentException not thrown when less than the number of arguments (1) was supplied.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }

        try {
            new SummationAtom(singlePredicate, new SummationVariableOrTerm[]{
                    new SummationVariable("A"),
                    new SummationVariable("B")
            });
            fail("IllegalArgumentException not thrown when more than the number of arguments (1) was supplied.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }
    }

    @Test
    public void testValidateArgLength2() {
        try {
            new SummationAtom(doublePredicate, new SummationVariableOrTerm[]{new SummationVariable("A")});
            fail("IllegalArgumentException not thrown when less than the number of arguments (2) was supplied.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }

        try {
            new SummationAtom(doublePredicate, new SummationVariableOrTerm[]{
                    new SummationVariable("A"),
                    new SummationVariable("B"),
                    new SummationVariable("C")
            });
            fail("IllegalArgumentException not thrown when more than the number of arguments (2) was supplied.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }
    }

    @After
    public void cleanup() {
        dataStore.close();
    }
}
