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
package org.linqs.psl.model.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.PSLTest;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.EmptyBundle;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.Queries;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSUniqueStringID;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.AtomCache;
import org.linqs.psl.model.atom.AtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.SimpleAtomManager;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Add;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Divide;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Max;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Min;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Multiply;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Subtract;
import org.linqs.psl.model.rule.logical.UnweightedLogicalRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.UniqueID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Check for ground rules being created properly.
 */
public class GroundRuleTest {
	private TestModelFactory.ModelInformation model;
	private Database database;

	@Before
	public void setup() {
		initModel(true);
	}

	private void initModel(boolean useNice) {
		if (database != null) {
			database.close();
			database = null;
		}

		if (model != null) {
			model.dataStore.close();
			model = null;
		}

		model = TestModelFactory.getModel(useNice);
		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(model.predicates.get("Nice"));
		toClose.add(model.predicates.get("Person"));
		database = model.dataStore.getDatabase(model.targetPartition, toClose, model.observationPartition);
	}

	@Test
	public void testLogicalBase() {
		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;

		// Nice(A) & Nice(B) -> Friends(A, B)
		rule = new WeightedLogicalRule(
			new Implication(
				new Conjunction(
					new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
					new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
				),
				new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B"))
			),
			1.0,
			true
		);

		// Remember, all rules will be in DNF.
		expected = Arrays.asList(
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Alice') ) | FRIENDS('Alice', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Bob') ) | FRIENDS('Alice', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Charlie') ) | FRIENDS('Alice', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Derek') ) | FRIENDS('Alice', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Eugene') ) | FRIENDS('Alice', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Alice') ) | FRIENDS('Bob', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Bob') ) | FRIENDS('Bob', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Charlie') ) | FRIENDS('Bob', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Derek') ) | FRIENDS('Bob', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Eugene') ) | FRIENDS('Bob', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Alice') ) | FRIENDS('Charlie', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Bob') ) | FRIENDS('Charlie', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Charlie') ) | FRIENDS('Charlie', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Derek') ) | FRIENDS('Charlie', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Eugene') ) | FRIENDS('Charlie', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Alice') ) | FRIENDS('Derek', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Bob') ) | FRIENDS('Derek', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Charlie') ) | FRIENDS('Derek', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Derek') ) | FRIENDS('Derek', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Eugene') ) | FRIENDS('Derek', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Alice') ) | FRIENDS('Eugene', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Bob') ) | FRIENDS('Eugene', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Charlie') ) | FRIENDS('Eugene', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Derek') ) | FRIENDS('Eugene', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Eugene') ) | FRIENDS('Eugene', 'Eugene') ) ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	public void testLogicalSpecialPredicates() {
		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;

		// Nice(A) & Nice(B) & (A == B) -> Friends(A, B)
		rule = new WeightedLogicalRule(
			new Implication(
				new Conjunction(
					new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
					new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
					new QueryAtom(SpecialPredicate.Equal, new Variable("A"), new Variable("B"))
				),
				new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B"))
			),
			1.0,
			true
		);

		// Remember, all rules will be in DNF.
		expected = Arrays.asList(
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Alice') ) | ~( ('Alice' == 'Alice') ) | FRIENDS('Alice', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Bob') ) | ~( ('Bob' == 'Bob') ) | FRIENDS('Bob', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Charlie') ) | ~( ('Charlie' == 'Charlie') ) | FRIENDS('Charlie', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Derek') ) | ~( ('Derek' == 'Derek') ) | FRIENDS('Derek', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Eugene') ) | ~( ('Eugene' == 'Eugene') ) | FRIENDS('Eugene', 'Eugene') ) ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Nice(A) & Nice(B) & (A != B) -> Friends(A, B)
		rule = new WeightedLogicalRule(
			new Implication(
				new Conjunction(
					new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
					new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
					new QueryAtom(SpecialPredicate.NotEqual, new Variable("A"), new Variable("B"))
				),
				new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B"))
			),
			1.0,
			true
		);

		// Remember, all rules will be in DNF.
		expected = Arrays.asList(
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Bob') ) | ~( ('Alice' != 'Bob') ) | FRIENDS('Alice', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Charlie') ) | ~( ('Alice' != 'Charlie') ) | FRIENDS('Alice', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Derek') ) | ~( ('Alice' != 'Derek') ) | FRIENDS('Alice', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Eugene') ) | ~( ('Alice' != 'Eugene') ) | FRIENDS('Alice', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Alice') ) | ~( ('Bob' != 'Alice') ) | FRIENDS('Bob', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Charlie') ) | ~( ('Bob' != 'Charlie') ) | FRIENDS('Bob', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Derek') ) | ~( ('Bob' != 'Derek') ) | FRIENDS('Bob', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Eugene') ) | ~( ('Bob' != 'Eugene') ) | FRIENDS('Bob', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Alice') ) | ~( ('Charlie' != 'Alice') ) | FRIENDS('Charlie', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Bob') ) | ~( ('Charlie' != 'Bob') ) | FRIENDS('Charlie', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Derek') ) | ~( ('Charlie' != 'Derek') ) | FRIENDS('Charlie', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Eugene') ) | ~( ('Charlie' != 'Eugene') ) | FRIENDS('Charlie', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Alice') ) | ~( ('Derek' != 'Alice') ) | FRIENDS('Derek', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Bob') ) | ~( ('Derek' != 'Bob') ) | FRIENDS('Derek', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Charlie') ) | ~( ('Derek' != 'Charlie') ) | FRIENDS('Derek', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Eugene') ) | ~( ('Derek' != 'Eugene') ) | FRIENDS('Derek', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Alice') ) | ~( ('Eugene' != 'Alice') ) | FRIENDS('Eugene', 'Alice') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Bob') ) | ~( ('Eugene' != 'Bob') ) | FRIENDS('Eugene', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Charlie') ) | ~( ('Eugene' != 'Charlie') ) | FRIENDS('Eugene', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Eugene') ) | ~( NICE('Derek') ) | ~( ('Eugene' != 'Derek') ) | FRIENDS('Eugene', 'Derek') ) ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Nice(A) & Nice(B) & (A % B) -> Friends(A, B)
		rule = new WeightedLogicalRule(
			new Implication(
				new Conjunction(
					new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
					new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
					new QueryAtom(SpecialPredicate.NonSymmetric, new Variable("A"), new Variable("B"))
				),
				new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B"))
			),
			1.0,
			true
		);

		// Remember, all rules will be in DNF.
		expected = Arrays.asList(
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Bob') ) | ~( ('Alice' % 'Bob') ) | FRIENDS('Alice', 'Bob') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Charlie') ) | ~( ('Alice' % 'Charlie') ) | FRIENDS('Alice', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Derek') ) | ~( ('Alice' % 'Derek') ) | FRIENDS('Alice', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Alice') ) | ~( NICE('Eugene') ) | ~( ('Alice' % 'Eugene') ) | FRIENDS('Alice', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Charlie') ) | ~( ('Bob' % 'Charlie') ) | FRIENDS('Bob', 'Charlie') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Derek') ) | ~( ('Bob' % 'Derek') ) | FRIENDS('Bob', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Bob') ) | ~( NICE('Eugene') ) | ~( ('Bob' % 'Eugene') ) | FRIENDS('Bob', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Derek') ) | ~( ('Charlie' % 'Derek') ) | FRIENDS('Charlie', 'Derek') ) ^2",
			"1.0: ( ~( NICE('Charlie') ) | ~( NICE('Eugene') ) | ~( ('Charlie' % 'Eugene') ) | FRIENDS('Charlie', 'Eugene') ) ^2",
			"1.0: ( ~( NICE('Derek') ) | ~( NICE('Eugene') ) | ~( ('Derek' % 'Eugene') ) | FRIENDS('Derek', 'Eugene') ) ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	public void testArithmeticBase() {
		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;

		// 1.0: Nice(A) + Nice(B) >= 1 ^2
		coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
			(SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B")))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Eugene') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// 1.0: Nice(A) + Nice(B) <= 1 ^2
		coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(1))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
			(SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B")))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.SmallerThan, new ConstantNumber(1)),
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Eugene') <= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// 1.0: Nice(A) + -1 * Nice(B) = 0 ^2
		coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1)),
			(Coefficient)(new ConstantNumber(-1))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
			(SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B")))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.Equality, new ConstantNumber(1)),
				1.0,
				true
		);

		// Remember, equality puts in a <= and >=.
		expected = Arrays.asList(
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Eugene') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Alice') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Bob') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Charlie') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Derek') <= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Eugene') <= 1.0 ^2",

			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Eugene') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Alice') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Bob') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Charlie') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Derek') >= 1.0 ^2",
			"1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Eugene') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	// Everyone is 100% Nice in this test.
	// |B| * Friends(A, +B) >= 1 {B: Nice(B)}
	// |B| * Friends(A, +B) >= 1 {B: !Nice(B)}
	public void testSelectBaseNice() {
		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("B")))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
			))
		);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("B"), new QueryAtom(model.predicates.get("Nice"), new Variable("B")));

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		// Note that 'Eugene' is not present because Nice('Eugene') = 0.
		expected = Arrays.asList(
			"1.0: 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Eugene', 'Alice') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Now negate the select.
		store = new ADMMReasoner(model.config);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("B"), new Negation(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))));

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: 0.0 >= 1.0 ^2",
			"1.0: 0.0 >= 1.0 ^2",
			"1.0: 0.0 >= 1.0 ^2",
			"1.0: 0.0 >= 1.0 ^2",
			"1.0: 0.0 >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store, false);
	}

	@Test
	// Everyone except Eugene has non-zero niceness.
	// |B| * Friends(A, +B) >= 1 {B: Nice(B)}
	// |B| * Friends(A, +B) >= 1 {B: !Nice(B)}
	public void testSelectBaseNotNice() {
		// Reset the model to not use 100% nice.
		initModel(false);

		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("B")))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
			))
		);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("B"), new QueryAtom(model.predicates.get("Nice"), new Variable("B")));

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		// Note that 'Eugene' is not present because Nice('Eugene') = 0.
		expected = Arrays.asList(
			"1.0: 3.0 * FRIENDS('Alice', 'Bob') + 3.0 * FRIENDS('Alice', 'Charlie') + 3.0 * FRIENDS('Alice', 'Derek') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Bob', 'Alice') + 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Derek') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Charlie', 'Alice') + 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Derek') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Derek', 'Alice') + 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Charlie') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Eugene', 'Alice') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Now negate the select.
		store = new ADMMReasoner(model.config);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(new SummationVariable("B"), new Negation(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))));

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		// Note that 'Eugene' is the only one because Nice('Eugene') = 0.
		// However, FRIENDS('Eugene', 'Eugene') does not appear in the observations.
		expected = Arrays.asList(
			"1.0: 1.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
			"1.0: 1.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
			"1.0: 1.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
			"1.0: 1.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
			"1.0: 0.0 >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store, false);
	}

	@Test
	// Everyone except Eugene has non-zero niceness.
	// |B| * Friends(A, +B) >= 1 {B: Friends(B, 'Alice') && Nice(B)}
	// |B| * Friends(A, +B) >= 1 {B: Friends(B, 'Alice') || Nice(B)}
	public void testSelectConstant() {
		// Reset the model to not use 100% nice.
		initModel(false);

		database.close();

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(model.predicates.get("Nice"));
		toClose.add(model.predicates.get("Person"));
		toClose.add(model.predicates.get("Friends"));
		database = model.dataStore.getDatabase(model.targetPartition, toClose, model.observationPartition);

		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("B")))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
			))
		);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(
			new SummationVariable("B"),
			new Conjunction(
				new QueryAtom(model.predicates.get("Friends"), new Variable("B"), database.getUniqueID("Alice")),
				new QueryAtom(model.predicates.get("Nice"), database.getUniqueID("Alice"))
			)
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		// Note that 'Alice' is not friends with herself.
		expected = Arrays.asList(
			"1.0: 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Derek') + 3.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Derek') + 3.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Charlie') + 3.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Now change the select to a disjunction.
		store = new ADMMReasoner(model.config);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(
			new SummationVariable("B"),
			new Disjunction(
				new QueryAtom(model.predicates.get("Friends"), new Variable("B"), database.getUniqueID("Alice")),
				new QueryAtom(model.predicates.get("Nice"), database.getUniqueID("Alice"))
			)
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		// Note that 'Alice' is not friends with herself.
		expected = Arrays.asList(
			"1.0: 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Eugene', 'Alice') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	// |B| * Friends(A, +B) >= 1
	public void testSummationNoSelect() {
		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("B")))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
			))
		);

		selects = new HashMap<SummationVariable, Formula>();

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Eugene', 'Alice') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	// Friends(+A, +B) >= 1
	// Friends(+A, +B) >= 1 {A: Nice(A)}
	// Friends(+A, +B) >= 1 {A: Nice(A)} {B: Nice(B)}
	public void testMultipleSummation() {
		// Reset the model to not use 100% nice.
		initModel(false);

		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		coefficients = Arrays.asList(
			(Coefficient)(new ConstantNumber(1))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new SummationVariable("A"), new SummationVariable("B")}
			))
		);

		selects = new HashMap<SummationVariable, Formula>();

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"1.0 * FRIENDS('Alice', 'Bob') + 1.0 * FRIENDS('Alice', 'Charlie') + 1.0 * FRIENDS('Alice', 'Derek') + 1.0 * FRIENDS('Alice', 'Eugene') + " +
				"1.0 * FRIENDS('Bob', 'Alice') + 1.0 * FRIENDS('Bob', 'Charlie') + 1.0 * FRIENDS('Bob', 'Derek') + 1.0 * FRIENDS('Bob', 'Eugene') + " +
				"1.0 * FRIENDS('Charlie', 'Alice') + 1.0 * FRIENDS('Charlie', 'Bob') + 1.0 * FRIENDS('Charlie', 'Derek') + 1.0 * FRIENDS('Charlie', 'Eugene') + " +
				"1.0 * FRIENDS('Derek', 'Alice') + 1.0 * FRIENDS('Derek', 'Bob') + 1.0 * FRIENDS('Derek', 'Charlie') + 1.0 * FRIENDS('Derek', 'Eugene') + " +
				"1.0 * FRIENDS('Eugene', 'Alice') + 1.0 * FRIENDS('Eugene', 'Bob') + 1.0 * FRIENDS('Eugene', 'Charlie') + 1.0 * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Add a select on A.
		store = new ADMMReasoner(model.config);

		selects.put(
			new SummationVariable("A"),
			new QueryAtom(model.predicates.get("Nice"), new Variable("A"))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"1.0 * FRIENDS('Alice', 'Bob') + 1.0 * FRIENDS('Alice', 'Charlie') + 1.0 * FRIENDS('Alice', 'Derek') + 1.0 * FRIENDS('Alice', 'Eugene') + " +
				"1.0 * FRIENDS('Bob', 'Alice') + 1.0 * FRIENDS('Bob', 'Charlie') + 1.0 * FRIENDS('Bob', 'Derek') + 1.0 * FRIENDS('Bob', 'Eugene') + " +
				"1.0 * FRIENDS('Charlie', 'Alice') + 1.0 * FRIENDS('Charlie', 'Bob') + 1.0 * FRIENDS('Charlie', 'Derek') + 1.0 * FRIENDS('Charlie', 'Eugene') + " +
				"1.0 * FRIENDS('Derek', 'Alice') + 1.0 * FRIENDS('Derek', 'Bob') + 1.0 * FRIENDS('Derek', 'Charlie') + 1.0 * FRIENDS('Derek', 'Eugene') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Add a select on B.
		store = new ADMMReasoner(model.config);

		selects.put(
			new SummationVariable("B"),
			new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"1.0 * FRIENDS('Alice', 'Bob') + 1.0 * FRIENDS('Alice', 'Charlie') + 1.0 * FRIENDS('Alice', 'Derek') + " +
				"1.0 * FRIENDS('Bob', 'Alice') + 1.0 * FRIENDS('Bob', 'Charlie') + 1.0 * FRIENDS('Bob', 'Derek') + " +
				"1.0 * FRIENDS('Charlie', 'Alice') + 1.0 * FRIENDS('Charlie', 'Bob') + 1.0 * FRIENDS('Charlie', 'Derek') + " +
				"1.0 * FRIENDS('Derek', 'Alice') + 1.0 * FRIENDS('Derek', 'Bob') + 1.0 * FRIENDS('Derek', 'Charlie') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	// |A| * Friends(+A, +B) >= 1 {B: Nice(B)}
	// |B| * Friends(+A, +B) >= 1 {B: Nice(B)}
	// |A| + |B| * Friends(+A, +B) >= 1 {B: Nice(B)}
	// |A| - |B| * Friends(+A, +B) >= 1 {B: Nice(B)}
	// |A| * |B| * Friends(+A, +B) >= 1 {B: Nice(B)}
	// |A| / |B| * Friends(+A, +B) >= 1 {B: Nice(B)}
	public void testMultipleSummationCardinality() {
		// Reset the model to not use 100% nice.
		initModel(false);

		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("A")))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new SummationVariable("A"), new SummationVariable("B")}
			))
		);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(
			new SummationVariable("B"),
			new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"5.0 * FRIENDS('Alice', 'Bob') + 5.0 * FRIENDS('Alice', 'Charlie') + 5.0 * FRIENDS('Alice', 'Derek') + " +
				"5.0 * FRIENDS('Bob', 'Alice') + 5.0 * FRIENDS('Bob', 'Charlie') + 5.0 * FRIENDS('Bob', 'Derek') + " +
				"5.0 * FRIENDS('Charlie', 'Alice') + 5.0 * FRIENDS('Charlie', 'Bob') + 5.0 * FRIENDS('Charlie', 'Derek') + " +
				"5.0 * FRIENDS('Derek', 'Alice') + 5.0 * FRIENDS('Derek', 'Bob') + 5.0 * FRIENDS('Derek', 'Charlie') + " +
				"5.0 * FRIENDS('Eugene', 'Alice') + 5.0 * FRIENDS('Eugene', 'Bob') + 5.0 * FRIENDS('Eugene', 'Charlie') + 5.0 * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// |B|
		store = new ADMMReasoner(model.config);

		coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("B")))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + " +
				"4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + " +
				"4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Derek') + " +
				"4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + " +
				"4.0 * FRIENDS('Eugene', 'Alice') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// |A| + |B|
		store = new ADMMReasoner(model.config);

		coefficients = Arrays.asList(
			(Coefficient)(new Add(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"9.0 * FRIENDS('Alice', 'Bob') + 9.0 * FRIENDS('Alice', 'Charlie') + 9.0 * FRIENDS('Alice', 'Derek') + " +
				"9.0 * FRIENDS('Bob', 'Alice') + 9.0 * FRIENDS('Bob', 'Charlie') + 9.0 * FRIENDS('Bob', 'Derek') + " +
				"9.0 * FRIENDS('Charlie', 'Alice') + 9.0 * FRIENDS('Charlie', 'Bob') + 9.0 * FRIENDS('Charlie', 'Derek') + " +
				"9.0 * FRIENDS('Derek', 'Alice') + 9.0 * FRIENDS('Derek', 'Bob') + 9.0 * FRIENDS('Derek', 'Charlie') + " +
				"9.0 * FRIENDS('Eugene', 'Alice') + 9.0 * FRIENDS('Eugene', 'Bob') + 9.0 * FRIENDS('Eugene', 'Charlie') + 9.0 * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// |A| - |B|
		store = new ADMMReasoner(model.config);

		coefficients = Arrays.asList(
			(Coefficient)(new Subtract(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"1.0 * FRIENDS('Alice', 'Bob') + 1.0 * FRIENDS('Alice', 'Charlie') + 1.0 * FRIENDS('Alice', 'Derek') + " +
				"1.0 * FRIENDS('Bob', 'Alice') + 1.0 * FRIENDS('Bob', 'Charlie') + 1.0 * FRIENDS('Bob', 'Derek') + " +
				"1.0 * FRIENDS('Charlie', 'Alice') + 1.0 * FRIENDS('Charlie', 'Bob') + 1.0 * FRIENDS('Charlie', 'Derek') + " +
				"1.0 * FRIENDS('Derek', 'Alice') + 1.0 * FRIENDS('Derek', 'Bob') + 1.0 * FRIENDS('Derek', 'Charlie') + " +
				"1.0 * FRIENDS('Eugene', 'Alice') + 1.0 * FRIENDS('Eugene', 'Bob') + 1.0 * FRIENDS('Eugene', 'Charlie') + 1.0 * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// |A| * |B|
		store = new ADMMReasoner(model.config);

		coefficients = Arrays.asList(
			(Coefficient)(new Multiply(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"20.0 * FRIENDS('Alice', 'Bob') + 20.0 * FRIENDS('Alice', 'Charlie') + 20.0 * FRIENDS('Alice', 'Derek') + " +
				"20.0 * FRIENDS('Bob', 'Alice') + 20.0 * FRIENDS('Bob', 'Charlie') + 20.0 * FRIENDS('Bob', 'Derek') + " +
				"20.0 * FRIENDS('Charlie', 'Alice') + 20.0 * FRIENDS('Charlie', 'Bob') + 20.0 * FRIENDS('Charlie', 'Derek') + " +
				"20.0 * FRIENDS('Derek', 'Alice') + 20.0 * FRIENDS('Derek', 'Bob') + 20.0 * FRIENDS('Derek', 'Charlie') + " +
				"20.0 * FRIENDS('Eugene', 'Alice') + 20.0 * FRIENDS('Eugene', 'Bob') + 20.0 * FRIENDS('Eugene', 'Charlie') + 20.0 * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// |A| / |B|
		store = new ADMMReasoner(model.config);

		coefficients = Arrays.asList(
			(Coefficient)(new Divide(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		expected = Arrays.asList(
			"1.0: " +
				"1.25 * FRIENDS('Alice', 'Bob') + 1.25 * FRIENDS('Alice', 'Charlie') + 1.25 * FRIENDS('Alice', 'Derek') + " +
				"1.25 * FRIENDS('Bob', 'Alice') + 1.25 * FRIENDS('Bob', 'Charlie') + 1.25 * FRIENDS('Bob', 'Derek') + " +
				"1.25 * FRIENDS('Charlie', 'Alice') + 1.25 * FRIENDS('Charlie', 'Bob') + 1.25 * FRIENDS('Charlie', 'Derek') + " +
				"1.25 * FRIENDS('Derek', 'Alice') + 1.25 * FRIENDS('Derek', 'Bob') + 1.25 * FRIENDS('Derek', 'Charlie') + " +
				"1.25 * FRIENDS('Eugene', 'Alice') + 1.25 * FRIENDS('Eugene', 'Bob') + 1.25 * FRIENDS('Eugene', 'Charlie') + 1.25 * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	// |B| * Friends(A, +B) + Person(C) >= 1 {B: Friends(C, B)}
	// |B| * Friends(A, +B) + Person(C) >= 1 {B: Friends(C, B) && Nice(C)}
	public void testSelectBinary() {
		// Reset the model to not use 100% nice.
		initModel(false);

		database.close();

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(model.predicates.get("Nice"));
		toClose.add(model.predicates.get("Person"));
		toClose.add(model.predicates.get("Friends"));
		database = model.dataStore.getDatabase(model.targetPartition, toClose, model.observationPartition);

		GroundRuleStore store = new ADMMReasoner(model.config);
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients;
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		coefficients = Arrays.asList(
			(Coefficient)(new Cardinality(new SummationVariable("B"))),
			(Coefficient)(new ConstantNumber(1))
		);

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
			)),
			(SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Person"), new Variable("C")))
		);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(
			new SummationVariable("B"),
			new QueryAtom(model.predicates.get("Friends"), new Variable("C"), new Variable("B"))
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		// |B| * Friends(A, +B) + Person(C) >= 1 {B: Friends(C, B)}
		expected = Arrays.asList(
			"1.0: 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Alice', 'Charlie') + 3.0 * FRIENDS('Alice', 'Derek') + 3.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Alice', 'Bob') + 3.0 * FRIENDS('Alice', 'Derek') + 3.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Alice', 'Bob') + 3.0 * FRIENDS('Alice', 'Charlie') + 3.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Alice', 'Bob') + 3.0 * FRIENDS('Alice', 'Charlie') + 3.0 * FRIENDS('Alice', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Derek') + 3.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Bob', 'Alice') + 3.0 * FRIENDS('Bob', 'Derek') + 3.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Bob', 'Alice') + 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Bob', 'Alice') + 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Derek') + 3.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Charlie', 'Alice') + 3.0 * FRIENDS('Charlie', 'Derek') + 3.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Alice') + 3.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Alice') + 3.0 * FRIENDS('Charlie', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Charlie') + 3.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Derek', 'Charlie') + 3.0 * FRIENDS('Derek', 'Alice') + 3.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Alice') + 3.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Charlie') + 3.0 * FRIENDS('Derek', 'Alice') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Derek') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Derek') + 3.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Derek') + 3.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Eugene') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);

		// Add the additional clause to the select.
		store = new ADMMReasoner(model.config);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(
			new SummationVariable("B"),
			new Conjunction(
				new QueryAtom(model.predicates.get("Friends"), new Variable("C"), new Variable("B")),
				new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
			)
		);

		rule = new WeightedArithmeticRule(
				new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
				selects,
				1.0,
				true
		);

		// Note that 'Alice' is not friends with herself.
		expected = Arrays.asList(
			"1.0: 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Alice', 'Charlie') + 3.0 * FRIENDS('Alice', 'Derek') + 3.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Alice', 'Bob') + 3.0 * FRIENDS('Alice', 'Derek') + 3.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Alice', 'Bob') + 3.0 * FRIENDS('Alice', 'Charlie') + 3.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Derek') + 3.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Bob', 'Alice') + 3.0 * FRIENDS('Bob', 'Derek') + 3.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Bob', 'Alice') + 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Derek') + 3.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Charlie', 'Alice') + 3.0 * FRIENDS('Charlie', 'Derek') + 3.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Alice') + 3.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Charlie') + 3.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Derek', 'Charlie') + 3.0 * FRIENDS('Derek', 'Alice') + 3.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Alice') + 3.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 1.0 * PERSON('Eugene') >= 1.0 ^2",

			"1.0: 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Derek') + 1.0 * PERSON('Alice') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Derek') + 3.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Bob') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Derek') + 3.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
			"1.0: 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Derek') >= 1.0 ^2",
			"1.0: 1.0 * PERSON('Eugene') >= 1.0 ^2"
		);
		rule.groundAll(manager, store);
		PSLTest.compareGroundRules(expected, rule, store);
	}

	@Test
	// @Min[1, 2] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[1, 2] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Min[2, 1] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[2, 1] * Friends(+A, +B) >= 1 {B: Nice(B)}

	// @Min[|A|, |B|] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Min[|B|, |A|] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[|A|, |B|] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[|B|, |A|] * Friends(+A, +B) >= 1 {B: Nice(B)}

	// @Min[1, 1 + 2] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[1, 1 + 2] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Min[1 + 2, 1] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[1 + 2, 1] * Friends(+A, +B) >= 1 {B: Nice(B)}

	// @Min[1, |A| + |B|] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[1, |A| + |B|] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Min[|A| + |B|, 1] * Friends(+A, +B) >= 1 {B: Nice(B)}
	// @Max[|A| + |B|, 1] * Friends(+A, +B) >= 1 {B: Nice(B)}
	public void testCoefficientFunctions() {
		// Reset the model to not use 100% nice.
		initModel(false);

		GroundRuleStore store;
		AtomManager manager = new SimpleAtomManager(database);

		Rule rule;
		List<String> expected;
		List<Coefficient> coefficients = new ArrayList<Coefficient>();
		List<SummationAtomOrAtom> atoms;
		Map<SummationVariable, Formula> selects;

		atoms = Arrays.asList(
			(SummationAtomOrAtom)(new SummationAtom(
				model.predicates.get("Friends"),
				new SummationVariableOrTerm[]{new SummationVariable("A"), new SummationVariable("B")}
			))
		);

		selects = new HashMap<SummationVariable, Formula>();
		selects.put(
			new SummationVariable("B"),
			new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
		);

		String expectedBase =
			"1.0: " +
				"__VAL__ * FRIENDS('Alice', 'Bob') + __VAL__ * FRIENDS('Alice', 'Charlie') + __VAL__ * FRIENDS('Alice', 'Derek') + " +
				"__VAL__ * FRIENDS('Bob', 'Alice') + __VAL__ * FRIENDS('Bob', 'Charlie') + __VAL__ * FRIENDS('Bob', 'Derek') + " +
				"__VAL__ * FRIENDS('Charlie', 'Alice') + __VAL__ * FRIENDS('Charlie', 'Bob') + __VAL__ * FRIENDS('Charlie', 'Derek') + " +
				"__VAL__ * FRIENDS('Derek', 'Alice') + __VAL__ * FRIENDS('Derek', 'Bob') + __VAL__ * FRIENDS('Derek', 'Charlie') + " +
				"__VAL__ * FRIENDS('Eugene', 'Alice') + __VAL__ * FRIENDS('Eugene', 'Bob') + __VAL__ * FRIENDS('Eugene', 'Charlie') + __VAL__ * FRIENDS('Eugene', 'Derek') " +
				">= 1.0 ^2"
		;

		Coefficient[] testCoefficients = new Coefficient[]{
			(Coefficient)(new Min(new ConstantNumber(1), new ConstantNumber(2))),
			(Coefficient)(new Max(new ConstantNumber(1), new ConstantNumber(2))),
			(Coefficient)(new Min(new ConstantNumber(2), new ConstantNumber(1))),
			(Coefficient)(new Max(new ConstantNumber(2), new ConstantNumber(1))),

			(Coefficient)(new Min(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B")))),
			(Coefficient)(new Max(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B")))),
			(Coefficient)(new Min(new Cardinality(new SummationVariable("B")), new Cardinality(new SummationVariable("A")))),
			(Coefficient)(new Max(new Cardinality(new SummationVariable("B")), new Cardinality(new SummationVariable("A")))),

			(Coefficient)(new Min(new ConstantNumber(1), new Add(new ConstantNumber(1), new ConstantNumber(2)))),
			(Coefficient)(new Max(new ConstantNumber(1), new Add(new ConstantNumber(1), new ConstantNumber(2)))),
			(Coefficient)(new Min(new Add(new ConstantNumber(1), new ConstantNumber(2)), new ConstantNumber(1))),
			(Coefficient)(new Max(new Add(new ConstantNumber(1), new ConstantNumber(2)), new ConstantNumber(1))),

			(Coefficient)(new Min(new ConstantNumber(1), new Add(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))),
			(Coefficient)(new Max(new ConstantNumber(1), new Add(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))),
			(Coefficient)(new Min(new Add(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))), new ConstantNumber(1))),
			(Coefficient)(new Max(new Add(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))), new ConstantNumber(1)))
		};

		String[] expectedValues = new String[]{
			"1.0",
			"2.0",
			"1.0",
			"2.0",

			"4.0",
			"5.0",
			"4.0",
			"5.0",

			"1.0",
			"3.0",
			"1.0",
			"3.0",

			"1.0",
			"9.0",
			"1.0",
			"9.0"
		};

		for (int i = 0; i < testCoefficients.length; i++) {
			expected = Arrays.asList(expectedBase.replaceAll("__VAL__", expectedValues[i]));
			store = new ADMMReasoner(model.config);

			coefficients.clear();
			coefficients.add(testCoefficients[i]);

			rule = new WeightedArithmeticRule(
					new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LargerThan, new ConstantNumber(1)),
					selects,
					1.0,
					true
			);

			rule.groundAll(manager, store);
			PSLTest.compareGroundRules(expected, rule, store);
		}
	}

	@After
	public void cleanup() {
		database.close();
		model.dataStore.close();
	}
}
