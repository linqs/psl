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
package edu.umd.cs.psl.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.rule.Rule;
import edu.umd.cs.psl.model.term.ConstantType;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModelLoaderTest {
	private DataStore dataStore;

	private StandardPredicate singlePredicate;
	private StandardPredicate doublePredicate;

	@Before
	public void setup() {
		ConfigBundle config = new EmptyBundle();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true), config);

		PredicateFactory factory = PredicateFactory.getFactory();

		singlePredicate = factory.createStandardPredicate("Single", ConstantType.UniqueID);
		dataStore.registerPredicate(singlePredicate);

		doublePredicate = factory.createStandardPredicate("Double", ConstantType.UniqueID, ConstantType.UniqueID);
		dataStore.registerPredicate(doublePredicate);
	}

	public void assertModel(String input, String[] expectedRules) {
		Model model = null;

		try {
			model = ModelLoader.load(dataStore, input);
		} catch (IOException ex) {
			fail("IOException thrown from ModelLoader.load(): " + ex);
		}

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

	public void assertRule(String input, String expectedRule) {
		Rule rule = null;

		try {
			rule = ModelLoader.loadRule(dataStore, input);
		} catch (IOException ex) {
			fail("IOException thrown from ModelLoader.loadRule(): " + ex);
		}

		assertEquals(
				String.format("Rule mismatch. Expected: [%s], found [%s].", expectedRule, rule.toString()),
				expectedRule,
				rule.toString()
		);
	}

	@Test
	public void testBase() {
		String input =
			"1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
			"5: Single(B) & Double(B, A) >> Single(A) ^2\n";
		String[] expected = new String[]{
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{5.0} ( SINGLE(B) & DOUBLE(B, A) ) >> SINGLE(A) {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	// Shortest rule we can think of.
	public void testShortRule() {
		String input =
			"~Single(A) .";
		String[] expected = new String[]{
			"{constraint} ~( SINGLE(A) )"
		};

		assertModel(input, expected);
	}

	@Test
	// Very long rule.
	public void testLongRule() {
		List<String> parts = new ArrayList<String>();
		for (char i = 'A'; i < 'Z'; i++) {
			parts.add(String.format("Single(%c) & Double(%c, Z)", i, i));
		}
		String input = String.format("1: %s >> Single(Z) ^2", StringUtils.join(parts, " & "));
		String expected = String.format("{1.0} ( %s ) >> SINGLE(Z) {squared}", StringUtils.join(parts, " & ").toUpperCase());

		assertModel(input, new String[]{expected});
	}

	@Test
	// General check for comment support.
	public void testComments() {
		String input =
			"# This is a comment!\n" +
			"#This is a comment!\n" +
			"## This is another comment (but actually the same form).\n" +
			"      # This is a comment!\n" +
			"\n" +
			"//This is a comment!\n" +
			"// This is a comment!\n" +
			"//// This is another comment (but actually the same form).\n" +
			"      // This is a comment!\n" +
			"\n" +
			"/* Block time! */\n" +
			"/* Block time!\n" +
			" Sill in a comment\n" +
			"*/\n" +
			"/** Block time (javadoc style)!\n" +
			" * Sill in a comment\n" +
			" */\n" +
			"\n" +
			"1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
			"// Another comment.\n" +
			"1: Single(A) & Double(A, B) >> Single(B) ^2 // Inline comment!\n" +
			"1: Single(A) & Double(A, B) >> Single(B) ^2 # Inline comment!\n" +
			"1: Single(A) & Double(A, B) >> Single(B) ^2 /* Inline comment! */\n" +
			"1: Single(A) & Double(A, B) >> Single(B) // ^2 // Changing a rule.\n" +
			"1: Single(A) & Double(A, B) /* & Single(C) */ >> Single(B) ^2 // Inside of other syntax.\n" +
			"";
		String[] expected = new String[]{
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	public void testStringConstants() {
		String input =
			"1: Single(A) & Double(A, \"bar\") & Single(\"bar\") >> Double(A, \"bar\") ^2\n" +
			"1: Single(A) & Double(A, 'bar') & Single('bar') >> Double(A, 'bar') ^2\n";
		String[] expected = new String[]{
			"{1.0} ( SINGLE(A) & DOUBLE(A, 'bar') & SINGLE('bar') ) >> DOUBLE(A, 'bar') {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, 'bar') & SINGLE('bar') ) >> DOUBLE(A, 'bar') {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	// We are actually testing both numeric constants and coefficients.
	public void testNumericConstants() {
		/* TODO(eriq): Awaiting word from Steve/Jay about numeric constants being allowed?
			"1: Single(A) & Double(A, 1) >> Single(B) ^2\n" +
		*/
		String input =
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
			"";
		String[] expected = new String[]{
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
			"1.0: 1.2E-10 * SINGLE(A) = 1.0 ^2"
		};

		assertModel(input, expected);
	}

	@Test
	// Using some more unusual identifiers.
	public void testIdentifiers() {
		String input =
			"1: Single(A1) >> Single(A1) ^2\n" +
			"1: Single(A1A) >> Single(A1A) ^2\n" +
			"1: Single(A_A) >> Single(A_A) ^2\n" +
			"1: Single(A_1) >> Single(A_1) ^2\n" +
			"1: Single(A__) >> Single(A__) ^2\n" +
			"";
		String[] expected = new String[]{
			"{1.0} SINGLE(A1) >> SINGLE(A1) {squared}",
			"{1.0} SINGLE(A1A) >> SINGLE(A1A) {squared}",
			"{1.0} SINGLE(A_A) >> SINGLE(A_A) {squared}",
			"{1.0} SINGLE(A_1) >> SINGLE(A_1) {squared}",
			"{1.0} SINGLE(A__) >> SINGLE(A__) {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	// Test possible values for the rule weight.
	public void testWeight() {
		String input =
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
			"";
		String[] expected = new String[]{
			"{1.0} SINGLE(A) >> SINGLE(A)",
			"{0.0} SINGLE(A) >> SINGLE(A)",
			"{0.5} SINGLE(A) >> SINGLE(A)",
			"{999999.0} SINGLE(A) >> SINGLE(A)",
			"{9.999999999E9} SINGLE(A) >> SINGLE(A)",
			"{1.0} SINGLE(A) >> SINGLE(A)",
			"{0.001} SINGLE(A) >> SINGLE(A)",
			"{1.0E-8} SINGLE(A) >> SINGLE(A)",
			"{2.0E10} SINGLE(A) >> SINGLE(A)",
			"{2.0E10} SINGLE(A) >> SINGLE(A)",
			"{2.0E-10} SINGLE(A) >> SINGLE(A)",
			"{2.5E10} SINGLE(A) >> SINGLE(A)",
			"{2.5E-10} SINGLE(A) >> SINGLE(A)"
		};

		assertModel(input, expected);
	}

	@Test
	public void testImpliedBy() {
		String input =
			"1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
			"1: Single(B) << Single(A) & Double(A, B) ^2\n";
		String[] expected = new String[]{
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	public void testDisjunction() {
		String input =
			"1: Single(A) | Double(A, B) << Single(B) & Single(A) ^2\n" +
			"1: Single(A) & Double(B, C) >> Single(B) | Single(C) ^2\n";
		String[] expected = new String[]{
			"{1.0} ( SINGLE(B) & SINGLE(A) ) >> ( SINGLE(A) | DOUBLE(A, B) ) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(B, C) ) >> ( SINGLE(B) | SINGLE(C) ) {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	public void testNegation() {
		String input =
			"1: ~Single(A) & ~~Double(A, B) >> ~~~Single(B) ^2\n";
		String[] expected = new String[]{
			"{1.0} ( ~( SINGLE(A) ) & ~( ~( DOUBLE(A, B) ) ) ) >> ~( ~( ~( SINGLE(B) ) ) ) {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	public void testTermEquality() {
		String input =
			"1: A == B & Double(A, B) >> Single(B) ^2\n" +
			"1: A == 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' == B & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' == 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"1: A ~= B & Double(A, B) >> Single(B) ^2\n" +
			"1: A ~= 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' ~= B & Double(A, B) >> Single(B) ^2\n" +
			"1: 'Foo' ~= 'Bar' & Double(A, B) >> Single(B) ^2\n" +
			"";
		String[] expected = new String[]{
			"{1.0} ( #EQUAL(A, B) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( #EQUAL(A, 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( #EQUAL('Foo', B) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( #EQUAL('Foo', 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( #NOTEQUAL(A, B) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( #NOTEQUAL(A, 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( #NOTEQUAL('Foo', B) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( #NOTEQUAL('Foo', 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) {squared}"
		};

		assertModel(input, expected);
	}

	@Test
	public void testAlternativeSyntax() {
		String input =
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
			"";
		String[] expected = new String[]{
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"{1.0} DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"{1.0} DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"{1.0} DOUBLE(A, B) >> ( SINGLE(A) | SINGLE(B) )",
			"{1.0} ( #NOTEQUAL(A, B) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( #NOTEQUAL(A, B) & DOUBLE(A, B) ) >> SINGLE(B)"
		};

		assertModel(input, expected);
	}

	@Test
	public void testMultiplyCoefficient() {
		String input =
			"1: 1 Single(A) = 1 ^2\n" +
			"1: 1 * Single(A) = 1 ^2\n" +
			"";
		String[] expected = new String[]{
			"1.0: 1.0 * SINGLE(A) = 1.0 ^2",
			"1.0: 1.0 * SINGLE(A) = 1.0 ^2"
		};

		assertModel(input, expected);
	}

	@Test
	// Just throw in a bunch of arithmetic rules used in other tests.
	public void testGeneralArithmetic() {
		String input =
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
			"";
		String[] expected = new String[]{
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
		};

		assertModel(input, expected);
	}

	@Test
	public void testLoadRuleBase() {
		String input = "1: Single(A) & Double(A, B) >> Single(B) ^2";
		String expected = "{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}";
		assertRule(input, expected);
	}

	@Test
	public void testLoadRuleBadCount() {
		// Having zero rules is a parse error, so the exception is different.
		try {
			assertRule("// Just a comment", "");
			fail("ModelLoader.LoadRule() with no rule did not throw an exception.");
		} catch (org.antlr.v4.runtime.NoViableAltException ex) {
			// Exception expected.
		}

		String input =
			"1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
			"5: Single(B) & Double(B, A) >> Single(A) ^2\n";
		String expected = "{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}";

		try {
			assertRule(input, expected);
			fail("ModelLoader.LoadRule() with more than one rule did not throw an exception.");
		} catch (IllegalArgumentException ex) {
			// Exception expected.
		}
	}

	@Test
	// Floats must have a leading number (".1" is not good, must be "0.1").
	public void testLeadDigitOnFloat() {
		String[] input = new String[]{
			".1: Single(A) & Double(A, B) >> Single(B) ^2",
			"1: .1 Single(A) = 1 ^2"
		};
		String[] expected = new String[]{
			"{0.1} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"1.0: 0.1 * SINGLE(A) = 1.0 ^2"
		};

		for (int i = 0; i < input.length; i++) {
			try {
				assertRule(input[i], expected[i]);
				fail(String.format("Rule: %d - Exception not thrown when float used without leading digit.", i));
			} catch (Exception ex) {
				// Exception expected.
			}
		}
	}

	@Test
	// Various misc syntax errors.
	public void testGeneralBadSyntax() {
		String[] input = new String[]{
			// Missing comma
			"1: Single(A) & Double(A B) >> Single(B) ^2",
			// Unknown predicate
			"1: Unknown(A) & Double(A, B) >> Single(B) ^2",
			// Mismatched quotes.
			"1: Single(A) & Double(\"Foo', B) >> Single(B) ^2",
			// Mismatched parens.
			"1: ( Single(A) & Double(A, B) >> Single(B) ^2",
			"1: Single(A) & Double(A, B) ) >> Single(B) ^2",
			// Missing unweighted period.
			"Single(A) & Double(A, B) >> Single(B) ^2",
			// Negative weight
			"-1: Single(A) & Double(A B) >> Single(B) ^2"
		};
		String[] expected = new String[]{
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( UNKNOWN(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE('Foo', B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{constraint} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}",
			"{-1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) {squared}"
		};

		for (int i = 0; i < input.length; i++) {
			try {
				assertRule(input[i], expected[i]);
				fail(String.format("Rule: %d - Exception not thrown on general syntax error.", i));
			} catch (Exception ex) {
				// Exception expected.
			}
		}
	}

	@Test
	public void testBadSquaring() {
		String[] input = new String[]{
			// TODO(eriq): This is a bad input but not caught by the parser.
			// "1: Single(A) & Double(A, B) >> Single(B) ^2.5",
			"1: Single(A) & Double(A, B) >> Single(B) ^3",
			"1: Single(A) & Double(A, B) >> Single(B) ^-1",
			"1: Single(A) & Double(A, B) >> Single(B) ^-2.0",
			"1: Single(A) & Double(A, B) >> Single(B) ^-2.5",
			"1: Single(A) & Double(A, B) >> Single(B) ^-3"
		};
		String[] expected = new String[]{
			// "{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
			"{1.0} ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)"
		};

		for (int i = 0; i < input.length; i++) {
			try {
				assertRule(input[i], expected[i]);
				fail(String.format("Rule: %d - Exception not thrown on bad square error.", i));
			} catch (Exception ex) {
				// Exception expected.
			}
		}
	}
}
