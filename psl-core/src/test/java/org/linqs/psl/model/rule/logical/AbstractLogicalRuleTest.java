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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AbstractLogicalRuleTest extends PSLBaseTest {
    private DataStore dataStore;
    private Database database;

    private StandardPredicate singleClosed;
    private StandardPredicate doubleClosed;
    private StandardPredicate singleOpened;

    @Before
    public void setup() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true));

        singleClosed = StandardPredicate.get("SingleClosed", ConstantType.UniqueStringID);
        dataStore.registerPredicate(singleClosed);

        doubleClosed = StandardPredicate.get("DoubleClosed", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
        dataStore.registerPredicate(doubleClosed);

        singleOpened = StandardPredicate.get("SingleOpened", ConstantType.UniqueStringID);
        dataStore.registerPredicate(singleOpened);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(singleClosed);
        toClose.add(doubleClosed);
        database = dataStore.getDatabase(dataStore.getNewPartition(), toClose);
    }

    @After
    public void cleanup() {
        database.close();
        dataStore.close();
    }

    @Test
    public void testBase() {
        // SingleClosed(A) & DoubleClosed(A, B) -> SingleOpen(B)
        AbstractLogicalRule rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(singleClosed, new Variable("A")),
                    new QueryAtom(doubleClosed, new Variable("A"), new Variable("B"))
                ),
                new QueryAtom(singleOpened, new Variable("B"))
            ),
            1.0f,
            true
        );

        assertRule(rule, "1.0: ( SINGLECLOSED(A) & DOUBLECLOSED(A, B) ) >> SINGLEOPENED(B) ^2");
    }

    @Test
    public void testUnboundVariable() {
        // SingleClosed(A) & !DoubleClosed(A, B) -> SingleOpen(B)
        // B is unbound.
        try {
            AbstractLogicalRule rule = new WeightedLogicalRule(
                new Implication(
                    new Conjunction(
                        new QueryAtom(singleClosed, new Variable("A")),
                        new Negation(new QueryAtom(doubleClosed, new Variable("A"), new Variable("B")))
                    ),
                    new QueryAtom(singleOpened, new Variable("B"))
                ),
                1.0f,
                true
            );

            fail("An exception was not thrown when a single unbound variable was encountered.");
        } catch (IllegalArgumentException ex) {
            assertTrue("Error message does not contain unbound variable.", ex.getMessage().contains("[B]"));
        }

        // !SingleClosed(A) & !DoubleClosed(A, B) -> SingleOpen(B)
        // A, B are unbound.
        try {
            AbstractLogicalRule rule = new WeightedLogicalRule(
                new Implication(
                    new Conjunction(
                        new Negation(new QueryAtom(singleClosed, new Variable("A"))),
                        new Negation(new QueryAtom(doubleClosed, new Variable("A"), new Variable("B")))
                    ),
                    new QueryAtom(singleOpened, new Variable("B"))
                ),
                1.0f,
                true
            );

            fail("An exception was not thrown when two unbound variables were encountered.");
        } catch (IllegalArgumentException ex) {
            assertTrue("Error message does not contain unbound variables.", ex.getMessage().contains("[A, B]"));
        }
    }

    /**
     * Test a few instances where the hash should match or not match.
     * The rules will use at most one positive and negative atom so the hash ordering is consistent.
     */
    @Test
    public void testHash() {
        // SingleClosed(A) -> SingleOpen(A)
        AbstractLogicalRule rule1 = new WeightedLogicalRule(
            new Implication(
                new QueryAtom(singleClosed, new Variable("A")),
                new QueryAtom(singleOpened, new Variable("A"))
            ),
            1.0f,
            true
        );

        // SingleOpen(A) -> SingleClosed(A)
        AbstractLogicalRule rule2 = new WeightedLogicalRule(
            new Implication(
                new QueryAtom(singleOpened, new Variable("A")),
                new QueryAtom(singleClosed, new Variable("A"))
            ),
            1.0f,
            true
        );

        // !SingleOpen(A) -> !SingleClosed(A)
        AbstractLogicalRule rule3 = new WeightedLogicalRule(
            new Implication(
                new Negation(new QueryAtom(singleOpened, new Variable("A"))),
                new Negation(new QueryAtom(singleClosed, new Variable("A")))
            ),
            1.0f,
            true
        );

        assertNotEquals(rule1.hashCode(), rule2.hashCode());
        assertEquals(rule1.hashCode(), rule3.hashCode());
        assertNotEquals(rule2.hashCode(), rule3.hashCode());
    }
}
