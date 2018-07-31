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
package org.linqs.psl.model.rule.logical;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.linqs.psl.PSLTest;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AbstractLogicalRuleTest {
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
			1.0,
			true
		);

		PSLTest.assertRule(rule, "1.0: ( SINGLECLOSED(A) & DOUBLECLOSED(A, B) ) >> SINGLEOPENED(B) ^2");
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
				1.0,
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
				1.0,
				true
			);

			fail("An exception was not thrown when two unbound variables were encountered.");
		} catch (IllegalArgumentException ex) {
			assertTrue("Error message does not contain unbound variables.", ex.getMessage().contains("[A, B]"));
		}
	}

	@After
	public void cleanup() {
		database.close();
		dataStore.close();
	}
}
