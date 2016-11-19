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
package org.linqs.psl.groovy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.EmptyBundle;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.ConstantType;

import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.groovy.PSLModel;

public class PSLModelTest {
	private PSLModel model;
	private DataStore dataStore;

	@Before
	public void setup() {
		ConfigBundle config = new EmptyBundle();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true), config);

		model = new PSLModel(this, dataStore);

		model.add(predicate: "Single", types: [ConstantType.UniqueID]);
		model.add(predicate: "Sim", types: [ConstantType.UniqueID, ConstantType.UniqueID]);
	}

	public void assertModel(String[] expectedRules) {
		int i = 0;
		for (Rule rule : model.getRules()) {
			assertEquals(
					String.format("Rule %d mismatch. Expected: [%s], found [%s].", i, expectedRules[i], rule.toString()),
					expectedRules[i],
					rule.toString()
			);
			i++;
		}

		assertEquals("Mismatch in expected rule count.", expectedRules.length, i);
	}

	@Test
	// We already added predicates in setup(), but we will duplicate it for those just reading test lists.
	public void testBaseAddPredicate() {
		model.add(predicate: "TestSingle", types: [ConstantType.UniqueID]);
		model.add(predicate: "TestSim", types: [ConstantType.UniqueID, ConstantType.UniqueID]);
	}

	@Test
	public void testBaseAddRuleSyntactic() {
		model.add(
			rule: (Single(A) & Sim(A, B)) >> Single(B),
			squared: true,
			weight: 1
		);

		String[] expected = [
			"1.0: ( SINGLE(A) & SIM(A, B) ) >> SINGLE(B) ^2"
		];

		assertModel(expected);
	}

	@Test
	public void testBaseAddRuleString() {
		model.add(
			rule: "1: Single(A) & Sim(A, B) >> Single(B) ^2"
		);

		String[] expected = [
			"1.0: ( SINGLE(A) & SIM(A, B) ) >> SINGLE(B) ^2"
		];

		assertModel(expected);
	}

	@Test
	// String rules take no arguments except for the rule itself.
	public void testStringRuleNoArgs() {
		try {
			model.add(
				rule: "1: Single(A) & Sim(A, B) >> Single(B) ^2",
				squared: true,
				weight: 1
			);
			fail("IllegalArgumentException not thrown when more than just string rule is supplied to add.");
		} catch (IllegalArgumentException ex) {
			// Exception expected.
		}

		try {
			model.add(
				rule: "1: Single(A) & Sim(A, B) >> Single(B) ^2",
				squared: true
			);
			fail("IllegalArgumentException not thrown when more than just string rule is supplied to add.");
		} catch (IllegalArgumentException ex) {
			// Exception expected.
		}

		try {
			model.add(
				rule: "1: Single(A) & Sim(A, B) >> Single(B) ^2",
				weight: 1
			);
			fail("IllegalArgumentException not thrown when more than just string rule is supplied to add.");
		} catch (IllegalArgumentException ex) {
			// Exception expected.
		}
	}
}
