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
package edu.umd.cs.psl.model.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.application.groundrulestore.GroundRuleStore;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.RDBMSUniqueStringID;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomCache;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.atom.SimpleAtomManager;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Implication;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.rule.GroundRule;
import edu.umd.cs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import edu.umd.cs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import edu.umd.cs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariable;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import edu.umd.cs.psl.model.rule.logical.UnweightedLogicalRule;
import edu.umd.cs.psl.model.rule.logical.WeightedLogicalRule;
import edu.umd.cs.psl.model.term.Constant;
import edu.umd.cs.psl.model.term.ConstantType;
import edu.umd.cs.psl.model.term.UniqueID;
import edu.umd.cs.psl.model.term.Variable;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RuleStringTest {
	private DataStore dataStore;
	private Database database;
	private ConfigBundle config;
	private Partition obsPartition;

	private StandardPredicate singlePredicate;
	private StandardPredicate doublePredicate;
	private StandardPredicate singleOpened;

	private ArithmeticRuleExpression arithmeticBaseRule;
	private Formula logicalBaseRule;

	@Before
	public void setup() {
		config = new EmptyBundle();
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true), config);

		// Predicates
		PredicateFactory factory = PredicateFactory.getFactory();

		singlePredicate = factory.createStandardPredicate("SinglePredicate", ConstantType.UniqueID);
		dataStore.registerPredicate(singlePredicate);

		doublePredicate = factory.createStandardPredicate("DoublePredicate", ConstantType.UniqueID, ConstantType.UniqueID);
		dataStore.registerPredicate(doublePredicate);

		// Rules

		// The base expression for all the logicalrules: SinglePredicate(A) & SinglePredicate(B) -> DoublePredicate(A, B)
		logicalBaseRule = new Implication(
				new Conjunction(
					new QueryAtom(singlePredicate, new Variable("A")),
					new QueryAtom(singlePredicate, new Variable("B"))
				),
				new QueryAtom(doublePredicate, new Variable("A"), new Variable("B"))
		);

		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("A"))),
			(SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("B")))
		);

		// Base Rule: SinglePredicate(A) + SinglePredicate(B) = 1
		arithmeticBaseRule = new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1));

		// Data
		obsPartition = dataStore.getNewPartition();

		Inserter inserter = dataStore.getInserter(singlePredicate, obsPartition);
		inserter.insert(dataStore.getUniqueID("Alice"));
		inserter.insert(dataStore.getUniqueID("Bob"));

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		database = dataStore.getDatabase(dataStore.getNewPartition(), toClose);
	}

	private void compareGroundRules(List<String> expected, Rule rule, GroundRuleStore store) {
		List<String> actual = new ArrayList<String>();
		for (GroundRule groundRule : store.getGroundKernels(rule)) {
			actual.add(groundRule.toString());
		}

		assertEquals("Size mismatch in comparing rules.", expected.size(), actual.size());

		Collections.sort(expected);
		Collections.sort(actual);

		for (int i = 0; i < expected.size(); i++) {
			assertEquals(
					String.format("Rule %d mismatch. Expected: [%s], found [%s].", i, expected.get(i), actual.get(i)),
					expected.get(i),
					actual.get(i));
		}
	}

	@Test
	public void testLogicalRuleString() {
		// Base Rule: SinglePredicate(A) & SinglePredicate(B) -> DoublePredicate(A, B)
		Rule rule;

		// Unweighted (Not Squared)
		rule = new UnweightedLogicalRule(logicalBaseRule);
		assertEquals("{constraint}: ( SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B)", rule.toString());

		// Weighted, Squared
		rule = new WeightedLogicalRule(logicalBaseRule, 10.0, true);
		assertEquals("10.0: ( SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B) {squared}", rule.toString());

		// Weighted, Not Squared
		rule = new WeightedLogicalRule(logicalBaseRule, 10.0, false);
		assertEquals("10.0: ( SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B)", rule.toString());
	}

	@Test
	public void testArithmeticRuleString() {
		// Base Rule: SinglePredicate(A) + SinglePredicate(B) = 1
		List<Coefficient> coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		List<SummationAtomOrAtom> atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("A"))),
			(SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("B")))
		);

		Rule rule;

		// Unweighted (Not Squared)
		rule = new UnweightedArithmeticRule(arithmeticBaseRule);
		assertEquals("{constraint}: 1.0 * SINGLEPREDICATE(A) + 1.0 * SINGLEPREDICATE(B) = 1.0", rule.toString());

		// Weighted, Squared
		rule = new WeightedArithmeticRule(arithmeticBaseRule,	10.0, true);
		assertEquals("10.0: 1.0 * SINGLEPREDICATE(A) + 1.0 * SINGLEPREDICATE(B) = 1.0 {squared}", rule.toString());

		// Weighted, Not Squared
		rule = new WeightedArithmeticRule(arithmeticBaseRule,	10.0, false);
		assertEquals("10.0: 1.0 * SINGLEPREDICATE(A) + 1.0 * SINGLEPREDICATE(B) = 1.0", rule.toString());
	}

	@Test
	public void testGroundLogicalRuleString() {
		GroundRuleStore store = new ADMMReasoner(config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;

		// Unweighted (Not Squared)
		rule = new UnweightedLogicalRule(logicalBaseRule);
		// Remember, all rules will be in DNF.
		expected = Arrays.asList(
			"{constraint}: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Alice', 'Alice') )",
			"{constraint}: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Alice', 'Bob') )",
			"{constraint}: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Bob', 'Alice') )",
			"{constraint}: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Bob', 'Bob') )"
		);
		rule.groundAll(manager, store);
		compareGroundRules(expected, rule, store);

		// Weighted, Squared
		rule = new WeightedLogicalRule(logicalBaseRule, 10.0, true);
		expected = Arrays.asList(
			"10.0: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Alice', 'Alice') ) {squared}",
			"10.0: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Alice', 'Bob') ) {squared}",
			"10.0: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Bob', 'Alice') ) {squared}",
			"10.0: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Bob', 'Bob') ) {squared}"
		);
		rule.groundAll(manager, store);
		compareGroundRules(expected, rule, store);

		// Weighted, Not Squared
		rule = new WeightedLogicalRule(logicalBaseRule, 10.0, false);
		expected = Arrays.asList(
			"10.0: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Alice', 'Alice') )",
			"10.0: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Alice', 'Bob') )",
			"10.0: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Bob', 'Alice') )",
			"10.0: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Bob', 'Bob') )"
		);
		rule.groundAll(manager, store);
		compareGroundRules(expected, rule, store);
	}

	@Test
	public void testGroundArithmeticRuleString() {
		GroundRuleStore store = new ADMMReasoner(config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;

		// Unweighted (Not Squared)
		rule = new UnweightedArithmeticRule(arithmeticBaseRule);
		// Remember, equality inserts two rules (<= and >=).
		expected = Arrays.asList(
			"{constraint}: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Alice') <= 1.0",
			"{constraint}: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Alice') >= 1.0",
			"{constraint}: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Bob') <= 1.0",
			"{constraint}: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Bob') >= 1.0",
			"{constraint}: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Alice') <= 1.0",
			"{constraint}: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Alice') >= 1.0",
			"{constraint}: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Bob') <= 1.0",
			"{constraint}: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Bob') >= 1.0"
		);
		rule.groundAll(manager, store);
		compareGroundRules(expected, rule, store);

		// Weighted, Squared
		rule = new WeightedArithmeticRule(arithmeticBaseRule,	10.0, true);
		expected = Arrays.asList(
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Alice') <= 1.0 {squared}",
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Alice') >= 1.0 {squared}",
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Bob') <= 1.0 {squared}",
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Bob') >= 1.0 {squared}",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Alice') <= 1.0 {squared}",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Alice') >= 1.0 {squared}",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Bob') <= 1.0 {squared}",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Bob') >= 1.0 {squared}"
		);
		rule.groundAll(manager, store);
		compareGroundRules(expected, rule, store);

		// Weighted, Not Squared
		rule = new WeightedArithmeticRule(arithmeticBaseRule,	10.0, false);
		expected = Arrays.asList(
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Alice') <= 1.0",
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Alice') >= 1.0",
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Bob') <= 1.0",
			"10.0: 1.0 SINGLEPREDICATE('Alice') 1.0 SINGLEPREDICATE('Bob') >= 1.0",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Alice') <= 1.0",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Alice') >= 1.0",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Bob') <= 1.0",
			"10.0: 1.0 SINGLEPREDICATE('Bob') 1.0 SINGLEPREDICATE('Bob') >= 1.0"
		);
		rule.groundAll(manager, store);
		compareGroundRules(expected, rule, store);
	}

	@After
	public void cleanup() {
		database.close();
		dataStore.close();
	}
}
