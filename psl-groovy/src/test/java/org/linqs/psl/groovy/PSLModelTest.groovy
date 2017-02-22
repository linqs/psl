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
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.ConstantType;

import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.groovy.PSLModel;

import java.util.Arrays;

public class PSLModelTest {
	private PSLModel model;
	private DataStore dataStore;

	@Before
	public void setup() {
		ConfigBundle config = new EmptyBundle();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true), config);

		model = new PSLModel(this, dataStore);

		model.add(predicate: "Single", types: [ConstantType.UniqueID]);
		model.add(predicate: "Double", types: [ConstantType.UniqueID, ConstantType.UniqueID]);
		model.add(predicate: "Sim", types: [ConstantType.UniqueID, ConstantType.UniqueID]);
	}

	/**
	 * Convenience call for the common functionality of assertModel() (alphabetize).
	 */
	public void assertModel(String[] expectedRules) {
		assertModel(expectedRules, true);
	}

	/**
	 * Assert that the current model has the given rules.
	 *
	 * If, for some reason, the exact format of the output is not known (like with summations which
	 * may order the summation terms in different ways), then you can use |alphabetize| to sort all
	 * characters in both strings (actual and expected) before comparing.
	 * Only alphabetize if it is really necessary since it makes the output much harder to interpret.
	 */
	public void assertModel(String[] expectedRules, boolean alphabetize) {
		int ruleCount = 0;

		if (alphabetize) {
			for (Rule rule : model.getRules()) {
				String alphaRule = sort(rule.toString());
				String alphaExpected = sort(expectedRules[ruleCount]);

				assertEquals(
						String.format("Rule %d mismatch. Expected (before alphabetizing): [%s], found [%s].", ruleCount, expectedRules[ruleCount], rule.toString()),
						alphaExpected,
						alphaRule
				);
				ruleCount++;
			}
		} else {
			for (Rule rule : model.getRules()) {
				assertEquals(
						String.format("Rule %d mismatch. Expected: [%s], found [%s].", ruleCount, expectedRules[ruleCount], rule.toString()),
						expectedRules[ruleCount],
						rule.toString()
				);
				ruleCount++;
			}
		}

		assertEquals("Mismatch in expected rule count.", expectedRules.length, ruleCount);
	}

	/**
	 * Compare two Arrays of strings for equality.
	 *
	 * If, for some reason, the content but not exact format of the output is not known;
	 * then you can use |alphabetize| to sort all
	 * characters in both strings (actual and expected) before comparing.
	 * Only alphabetize if it is really necessary because it can hide errors in order that are expected.
	 */
	public static void compareStrings(String[] expected, String[] actual, boolean alphabetize) {
		assertEquals("Size mismatch.", expected.length, actual.length);

		for (int i = 0; i < expected.length; i++) {
			if (alphabetize) {
				assertEquals(
					String.format("String %d mismatch. (Before alphabetize) expected: [%s], found [%s].", i, expected[i], actual[i]),
					sort(expected[i]),
					sort(actual[i])
				);
			} else {
				assertEquals(
					String.format("String %d mismatch. Expected: [%s], found [%s].", i, expected[i], actual[i]),
					expected[i],
					actual[i]
				);
			}
		}
	}

	private static String sort(String string) {
		char[] chars = string.toCharArray();
		Arrays.sort(chars);
		return new String(chars);
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
	public void testStringRuleBadArgs() {
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

		try {
			model.add(
				rule: "Single(A) & Sim(A, B) >> Single(B)",
				weight: 1
			);
			fail("IllegalArgumentException not thrown when only one argument was supplied to a partial.");
		} catch (IllegalArgumentException ex) {
			// Exception expected.
		}

		try {
			model.add(
				rule: "Single(A) & Sim(A, B) >> Single(B)",
				squared: true
			);
			fail("IllegalArgumentException not thrown when only one argument was supplied to a partial.");
		} catch (IllegalArgumentException ex) {
			// Exception expected.
		}
	}

	@Test
	// String rules take no arguments except for the rule itself.
	public void testStringRuleArgs() {
		model.add(
			rule: "Single(A) & Sim(A, B) >> Single(B)",
			squared: true,
			weight: 1
		);

		model.add(
			rule: "Single(A) & Sim(A, B) >> Single(B)",
			squared: false,
			weight: 5
		);

		model.add(
			rule: "Single(A) & Sim(A, B) >> Single(B) ."
		);

		String[] expected = [
			"1.0: ( SINGLE(A) & SIM(A, B) ) >> SINGLE(B) ^2",
			"5.0: ( SINGLE(A) & SIM(A, B) ) >> SINGLE(B)",
			"( SINGLE(A) & SIM(A, B) ) >> SINGLE(B) ."
		];

		assertModel(expected);
	}

	@Test
	public void testAddRules() {
		String input =
			"~Single(A) .\n" +
			"1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
			"5: Single(B) & Double(B, A) >> Single(A) ^2\n" +
			"1: Single(A) & Double(A, \"bar\") & Single(\"bar\") >> Double(A, \"bar\") ^2\n" +
			"1: Single(A) & Double(A, 'bar') & Single('bar') >> Double(A, 'bar') ^2\n" +
			"1: 1 Single(A) = 1 ^2\n" +
			"1: 1.0 Single(A) = 1 ^2\n" +
			"1: 1.5 Single(A) = 1 ^2\n" +
			"1: 0.5 Single(A) = 1 ^2\n" +
			"1: -1.0 Single(A) = 1 ^2\n" +
			"1: 5E10 Single(A) = 1 ^2\n" +
			"1: 5e10 Single(A) = 1 ^2\n" +
			"1: -5e10 Single(A) = 1 ^2\n" +
			"1: 5e-10 Single(A) = 1 ^2\n" +
			"1: 1.2e10 Single(A) = 1 ^2\n" +
			"1: -1.2e10 Single(A) = 1 ^2\n" +
			"1: 1.2e-10 Single(A) = 1 ^2\n" +
			"1: Single(A1) >> Single(A1) ^2\n" +
			"1: Single(A1A) >> Single(A1A) ^2\n" +
			"1: Single(A_A) >> Single(A_A) ^2\n" +
			"1: Single(A_1) >> Single(A_1) ^2\n" +
			"1: Single(A__) >> Single(A__) ^2\n" +
			"1: Single(A) >> Single(A)\n" +
			"0: Single(A) >> Single(A)\n" +
			"0.5: Single(A) >> Single(A)\n" +
			"999999: Single(A) >> Single(A)\n" +
			"9999999999: Single(A) >> Single(A)\n" +
			"0000000001: Single(A) >> Single(A)\n" +
			"0.001: Single(A) >> Single(A)\n" +
			"0.00000001: Single(A) >> Single(A)\n" +
			"2E10: Single(A) >> Single(A)\n" +
			"2e10: Single(A) >> Single(A)\n" +
			"2e-10: Single(A) >> Single(A)\n" +
			"2.5e10: Single(A) >> Single(A)\n" +
			"2.5e-10: Single(A) >> Single(A)\n" +
			"1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
			"1: Single(B) << Single(A) & Double(A, B) ^2\n" +
			"1: Single(A) | Double(A, B) << Single(B) & Single(A) ^2\n" +
			"1: Single(A) & Double(B, C) >> Single(B) | Single(C) ^2\n" +
			"1: ~Single(A) & ~~Double(A, B) >> ~~~Single(B) ^2\n" +
			"1: A == B & Double(A, B) >> Single(B) ^2\n" +
			"1: A == 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' == B & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' == 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"1: A ~= B & Double(A, B) >> Single(B) ^2\n" +
			"1: A ~= 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' ~= B & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' ~= 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"1: Single(A) & Double(A, B) >> Single(B)\n" +
			"1: Single(A) && Double(A, B) >> Single(B)\n" +
			"1: Single(A) & Double(A, B) >> Single(B)\n" +
			"1: Single(A) & Double(A, B) -> Single(B)\n" +
			"1: Single(A) | Single(B) << Double(A, B)\n" +
			"1: Single(A) || Single(B) << Double(A, B)\n" +
			"1: Single(A) | Single(B) << Double(A, B)\n" +
			"1: Single(A) | Single(B) <- Double(A, B)\n" +
			"1: A != B & Double(A, B) >> Single(B)\n" +
			"1: A ~= B & Double(A, B) >> Single(B)\n" +
			"1: 1 Single(A) = 1 ^2\n" +
			"1: 1 * Single(A) = 1 ^2\n" +
			"Single(A) + Single(B) = 1 .\n" +
			"Double(+A, 'Foo') = 1 .\n" +
			"Single(+A) + Single(+B) = 1 .\n" +
			"Single(+A) = 1 . {A: Single(A)}\n" +
			"Single(+A) = 1 . {A: Single(A) || Double(A, A) }\n" +
			"Double(+A, B) = 1 . {A: Single(B)}\n" +
			"Single(+A) + Single(+B) = 1 . {A: Single(A)} {B: Single(B)}\n" +
			"|A| Single(+A) = 1 .\n" +
			"|A| Single(+A) = |A| .\n" +
			"|A| Single(+A) + |B| Single(+B) = 1 .\n" +
			"@Max[|A|, 0] Single(+A) = 1 .\n" +
			"@Max[1, 0] Single(+A) = 1 .\n" +
			"@Max[|A|, |B|] Single(+A) + Single(+B) = 1 .\n" +
			"@Min[1, 0] Single(A) = 1 .\n" +
			""
		;

		String[] expected = [
			"~( SINGLE(A) ) .",
			"1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"5.0: ( SINGLE(B) & DOUBLE(B, A) ) >> SINGLE(A) ^2",
			"1.0: ( SINGLE(A) & DOUBLE(A, 'bar') & SINGLE('bar') ) >> DOUBLE(A, 'bar') ^2",
			"1.0: ( SINGLE(A) & DOUBLE(A, 'bar') & SINGLE('bar') ) >> DOUBLE(A, 'bar') ^2",
			"1.0: 1.0 * SINGLE(A) = 1.0 ^2",
			"1.0: 1.0 * SINGLE(A) = 1.0 ^2",
			"1.0: 1.5 * SINGLE(A) = 1.0 ^2",
			"1.0: 0.5 * SINGLE(A) = 1.0 ^2",
			"1.0: -1.0 * SINGLE(A) = 1.0 ^2",
			"1.0: 5.0E10 * SINGLE(A) = 1.0 ^2",
			"1.0: 5.0E10 * SINGLE(A) = 1.0 ^2",
			"1.0: -5.0E10 * SINGLE(A) = 1.0 ^2",
			"1.0: 5.0E-10 * SINGLE(A) = 1.0 ^2",
			"1.0: 1.2E10 * SINGLE(A) = 1.0 ^2",
			"1.0: -1.2E10 * SINGLE(A) = 1.0 ^2",
			"1.0: 1.2E-10 * SINGLE(A) = 1.0 ^2",
			"1.0: SINGLE(A1) >> SINGLE(A1) ^2",
			"1.0: SINGLE(A1A) >> SINGLE(A1A) ^2",
			"1.0: SINGLE(A_A) >> SINGLE(A_A) ^2",
			"1.0: SINGLE(A_1) >> SINGLE(A_1) ^2",
			"1.0: SINGLE(A__) >> SINGLE(A__) ^2",
			"1.0: SINGLE(A) >> SINGLE(A)",
			"0.0: SINGLE(A) >> SINGLE(A)",
			"0.5: SINGLE(A) >> SINGLE(A)",
			"999999.0: SINGLE(A) >> SINGLE(A)",
			"9.999999999E9: SINGLE(A) >> SINGLE(A)",
			"1.0: SINGLE(A) >> SINGLE(A)",
			"0.001: SINGLE(A) >> SINGLE(A)",
			"1.0E-8: SINGLE(A) >> SINGLE(A)",
			"2.0E10: SINGLE(A) >> SINGLE(A)",
			"2.0E10: SINGLE(A) >> SINGLE(A)",
			"2.0E-10: SINGLE(A) >> SINGLE(A)",
			"2.5E10: SINGLE(A) >> SINGLE(A)",
			"2.5E-10: SINGLE(A) >> SINGLE(A)",
			"1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( SINGLE(B) & SINGLE(A) ) >> ( SINGLE(A) | DOUBLE(A, B) ) ^2",
			"1.0: ( SINGLE(A) & DOUBLE(B, C) ) >> ( SINGLE(B) | SINGLE(C) ) ^2",
			"1.0: ( ~( SINGLE(A) ) & ~( ~( DOUBLE(A, B) ) ) ) >> ~( ~( ~( SINGLE(B) ) ) ) ^2",
			"1.0: ( (A == B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( (A == 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( ('Foo' == B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( ('Foo' == 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( (A != B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( (A != 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( ('Foo' != B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( ('Foo' != 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
			"1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"1.0: DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"1.0: DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"1.0: DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"1.0: DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"1.0: ( (A != B) & DOUBLE(A, B) ) >> SINGLE(B)",
			"1.0: ( (A != B) & DOUBLE(A, B) ) >> SINGLE(B)",
			"1.0: 1.0 * SINGLE(A) = 1.0 ^2",
			"1.0: 1.0 * SINGLE(A) = 1.0 ^2",
			"1.0 * SINGLE(A) + 1.0 * SINGLE(B) = 1.0 .",
			"1.0 * DOUBLE(+A, 'Foo') = 1.0 .",
			"1.0 * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 .",
			"1.0 * SINGLE(+A) = 1.0 .\n{A : SINGLE(A)}",
			"1.0 * SINGLE(+A) = 1.0 .\n{A : ( SINGLE(A) | DOUBLE(A, A) )}",
			"1.0 * DOUBLE(+A, B) = 1.0 .\n{A : SINGLE(B)}",
			"1.0 * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 .\n{A : SINGLE(A)}\n{B : SINGLE(B)}",
			"|A| * SINGLE(+A) = 1.0 .",
			"|A| * SINGLE(+A) = |A| .",
			"|A| * SINGLE(+A) + |B| * SINGLE(+B) = 1.0 .",
			"@Max[|A|, 0.0] * SINGLE(+A) = 1.0 .",
			"@Max[1.0, 0.0] * SINGLE(+A) = 1.0 .",
			"@Max[|A|, |B|] * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 .",
			"@Min[1.0, 0.0] * SINGLE(A) = 1.0 ."
		];

		model.addRules(input);
		assertModel(expected, true);
	}

	@Test
	public void testDNFResolution() {
		Formula[] formulas = [
			(~(Single(A) & Single(B) & Sim(A, B))).getFormula(),
			((Single(A) & Single(B)) >> ~Sim(A, B)).getFormula()
		];

		String[] actualToString = new String[formulas.length];
		for (int i = 0; i < formulas.length; i++) {
			actualToString[i] = formulas[i].toString();
		}

		String[] actualDNF = new String[formulas.length];
		for (int i = 0; i < formulas.length; i++) {
			actualDNF[i] = formulas[i].getDNF().toString();
		}

		String[] expectedToString = [
			"~( ( ( SINGLE(A) & SINGLE(B) ) & SIM(A, B) ) )",
			"( SINGLE(A) & SINGLE(B) ) >> ~( SIM(A, B) )"
		];

		String[] expectedDNF = [
			"( ~( SINGLE(A) ) | ~( SINGLE(B) ) | ~( SIM(A, B) ) )",
			"( ~( SINGLE(A) ) | ~( SINGLE(B) ) | ~( SIM(A, B) ) )"
		];

		compareStrings(expectedToString, actualToString, true);
		compareStrings(expectedDNF, actualDNF, true);
	}
}
