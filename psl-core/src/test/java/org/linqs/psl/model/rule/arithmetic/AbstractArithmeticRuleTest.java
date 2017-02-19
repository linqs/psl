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
package org.linqs.psl.model.rule.arithmetic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.linqs.psl.PSLTest;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.EmptyBundle;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.SimpleAtomManager;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Max;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Min;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AbstractArithmeticRuleTest {
	private DataStore dataStore;
	private Database database;
	private ConfigBundle config;

	private StandardPredicate singleClosed;
	private StandardPredicate doubleClosed;
	private StandardPredicate singleOpened;

	@Before
	public void setup() {
		config = new EmptyBundle();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true), config);

		PredicateFactory factory = PredicateFactory.getFactory();

		singleClosed = factory.createStandardPredicate("SingleClosed", ConstantType.UniqueID);
		dataStore.registerPredicate(singleClosed);

		doubleClosed = factory.createStandardPredicate("DoubleClosed", ConstantType.UniqueID, ConstantType.UniqueID);
		dataStore.registerPredicate(doubleClosed);

		singleOpened = factory.createStandardPredicate("SingleOpened", ConstantType.UniqueID);
		dataStore.registerPredicate(singleOpened);

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(singleClosed);
		toClose.add(doubleClosed);
		database = dataStore.getDatabase(dataStore.getNewPartition(), toClose);
	}

	@Test
	public void testBase() {
		// SingleClosed(A) + SingleClosed(B) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A"))),
			(SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("B")))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		assertEquals("1.0 * SINGLECLOSED(A) + 1.0 * SINGLECLOSED(B) = 1.0 .", rule.toString());
	}

	@Test
	public void testMultipleSumsDuplicates() {
		// SingleClosed(+A) + SingleClosed(+A) = 1
		// Cannot use a sum variable multiple times in a rule.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		try {
			AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
					coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));
			fail("IllegalArgumentException not thrown when duplicate summation variables were used.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testSumWithConstant() {
		// DoubleClosed(+A, 'Foo') = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(doubleClosed, new SummationVariableOrTerm[]{
				new SummationVariable("A"),
				dataStore.getUniqueID("Foo")
			}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		assertEquals("1.0 * DOUBLECLOSED(+A, 'Foo') = 1.0 .", rule.toString());
	}

	@Test
	public void testMultipleSumsNoDuplicates() {
		// SingleClosed(+A) + SingleClosed(+B) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("B")}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		assertEquals("1.0 * SINGLECLOSED(+A) + 1.0 * SINGLECLOSED(+B) = 1.0 .", rule.toString());
	}

	@Test
	// Summation Variable used as a term.
	public void testSumVariableUsedAsTerm() {
		// SingleClosed(+A) + SingleClosed(A) = 1
		// This will fail since A is supposed to be a summation variable and cannot be used as a term.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
			(SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A")))
		);

		try {
			AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
					coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));
			fail("IllegalArgumentException not thrown when summation variable is used as a term.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testSingleSelect() {
		// SingleClosed(+A) = 1 . {A: SingleClosed(A)}
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("A")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));
		AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);

		PSLTest.assertRule(rule, "1.0 * SINGLECLOSED(+A) = 1.0 .\n{A : SINGLECLOSED(A)}");
	}

	@Test
	public void testDisjunctiveSelect() {
		// SingleClosed(+A) = 1 . {A: SingleClosed(A) || DoubleClosed(A, A) }
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("A"),
				new Disjunction(
						new QueryAtom(singleClosed, new Variable("A")),
						new QueryAtom(doubleClosed, new Variable("A"), new Variable("A"))
				)
		);

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));
		AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);

		PSLTest.assertRule(rule, "1.0 * SINGLECLOSED(+A) = 1.0 .\n{A : ( SINGLECLOSED(A) | DOUBLECLOSED(A, A) )}", true);
	}

	@Test
	public void testSingleSelectDifferentVariable() {
		// DoubleClosed(+A, B) = 1 . {A: SingleClosed(B)}
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(doubleClosed, new SummationVariableOrTerm[]{
				new SummationVariable("A"),
				new Variable("B")
			})),
			(SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("B")))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("B")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));
		AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);

		PSLTest.assertRule(rule, "1.0 * DOUBLECLOSED(+A, B) = 1.0 .\n{A : SINGLECLOSED(B)}");
	}

	@Test
	public void testMultipleSelects() {
		// SingleClosed(+A) + SingleClosed(+B) = 1 . {A: SingleClosed(A)} {B: SingleClosed(B)}
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("B")}))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("A")));
		selects.put(new SummationVariable("B"), new QueryAtom(singleClosed, new Variable("B")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));
		AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);

		PSLTest.assertRule(rule, "1.0 * SINGLECLOSED(+A) + 1.0 * SINGLECLOSED(+B) = 1.0 .\n{A : SINGLECLOSED(A)}\n{B : SINGLECLOSED(B)}", true);
	}

	@Test
	public void testSelectArgNotInExpression() {
		// SingleClosed(+A) = 1 . {B: SingleClosed(B)}
		// Fail: the select argument (B) is unknown.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("B"), new QueryAtom(singleClosed, new Variable("B")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));

		try {
			AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);
			fail("IllegalArgumentException not thrown when unknown variable used as select argument.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testUnknownVariableInSelect() {
		// SingleClosed(+A) = 1 . {A: SingleClosed(B)}
		// Fail: a variable in the select does not appear in the arithmetic expression.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("B")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));

		try {
			AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);
			fail("IllegalArgumentException not thrown when unknown variable appears in select.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testSelectWithNoSummation() {
		// SingleClosed(A) = 1 . {A: SingleClosed(A)}
		// Fail: a select requires a summation atom.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A")))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("A")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));

		try {
			AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);
			fail("IllegalArgumentException not thrown when select appears without summation.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testSelectWithNonSummationArgument() {
		// DoubleClosed(+A, B) = 1 . {B: SingleClosed(A)}
		// Fail: select arguments must be summation variable.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(doubleClosed, new SummationVariableOrTerm[]{
				new SummationVariable("A"),
				new Variable("B")
			}))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("B"), new QueryAtom(singleClosed, new Variable("A")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));

		try {
			AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);
			fail("IllegalArgumentException not thrown when non-summation variable used as select argument.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testSelectOpenPredicate() {
		// SingleClosed(+A) = 1 . {A: SingleOpened(A)}
		// Fail: predicates in select must be closed.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		Map<SummationVariable, Formula> selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("A"), new QueryAtom(singleOpened, new Variable("A")));

		ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));
		AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, selects);

		SimpleAtomManager atomManager = new SimpleAtomManager(database);
		ADMMReasoner reasoner = new ADMMReasoner(config);

		try {
			rule.groundAll(atomManager, reasoner);
			fail("IllegalArgumentException not thrown when trying to ground an open predicate in the select.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testSimpleCardinality() {
		// |A| SingleClosed(+A) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("A")))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		PSLTest.assertRule(rule, "|A| * SINGLECLOSED(+A) = 1.0 .");
	}

	@Test
	public void testDoubleCardinality() {
		// |A| SingleClosed(+A) = |A|
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("A")))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new Cardinality(new SummationVariable("A"))));

		PSLTest.assertRule(rule, "|A| * SINGLECLOSED(+A) = |A| .");
	}

	@Test
	public void testTwoCardinalitySummations() {
		// |A| SingleClosed(+A) + |B| SingleClosed(+B) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("A"))),
			(Coefficient)(new Cardinality(new SummationVariable("B")))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("B")}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		PSLTest.assertRule(rule, "|A| * SINGLECLOSED(+A) + |B| * SINGLECLOSED(+B) = 1.0 .", true);
	}

	@Test
	public void testCardinalityOnNonSummation() {
		// |A| SingleClosed(A) = 1
		// Fail: Cardinality is only valid on a summation variable.
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("A")))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A")))
		);

		try {
			AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
					coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));
			fail("IllegalArgumentException not thrown when cardinality used on non-summation variable.");
		} catch (IllegalArgumentException ex) {
			// Exception is expected.
		}
	}

	@Test
	public void testSimpleCoefficientFunction() {
		// @Max[|A|, 0] SingleClosed(+A) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Max(new Cardinality(new SummationVariable("A")), new ConstantNumber(0)))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		PSLTest.assertRule(rule, "@Max[|A|, 0.0] * SINGLECLOSED(+A) = 1.0 .");
	}

	@Test
	public void testCoefficientFunctionNoCardinality() {
		// @Max[1, 0] SingleClosed(+A) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Max(new ConstantNumber(1), new ConstantNumber(0)))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		PSLTest.assertRule(rule, "@Max[1.0, 0.0] * SINGLECLOSED(+A) = 1.0 .");
	}

	@Test
	public void testCoefficientFunctionAllCardinality() {
		// @Max[|A|, |B|] SingleClosed(+A) + SingleClosed(+B) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Max(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B")))),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
			(SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("B")}))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		PSLTest.assertRule(rule, "@Max[|A|, |B|] * SINGLECLOSED(+A) + 1.0 * SINGLECLOSED(+B) = 1.0 .", true);
	}

	@Test
	public void testCoefficientFunctionNoSummation() {
		// @Min[1, 0] SingleClosed(A) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new Min(new ConstantNumber(1), new ConstantNumber(0)))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A")))
		);

		AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
				coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)));

		PSLTest.assertRule(rule, "@Min[1.0, 0.0] * SINGLECLOSED(A) = 1.0 .");
	}

	@After
	public void cleanup() {
		database.close();
		dataStore.close();
	}
}
