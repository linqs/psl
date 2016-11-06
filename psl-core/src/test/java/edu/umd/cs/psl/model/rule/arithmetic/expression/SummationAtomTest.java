/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.model.rule.arithmetic.expression;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationAtom;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariable;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import edu.umd.cs.psl.model.term.ConstantType;

public class SummationAtomTest {
	private DataStore dataStore;
	private ConfigBundle config;

	private StandardPredicate singlePredicate;
	private StandardPredicate doublePredicate;

	@Before
	public void setup() {
		config = new EmptyBundle();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true), config);

		PredicateFactory factory = PredicateFactory.getFactory();

		singlePredicate = factory.createStandardPredicate("SingleClosed", ConstantType.UniqueID);
		dataStore.registerPredicate(singlePredicate);

		doublePredicate = factory.createStandardPredicate("DoubleClosed", ConstantType.UniqueID, ConstantType.UniqueID);
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
