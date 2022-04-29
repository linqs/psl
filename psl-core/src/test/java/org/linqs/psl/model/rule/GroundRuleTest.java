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
package org.linqs.psl.model.rule;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.atom.AtomCache;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.database.atom.SimpleAtomManager;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
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
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
public class GroundRuleTest extends PSLBaseTest {
    private static final double EPSILON = 0.00001;
    private TestModel.ModelInformation model;
    private Database database;

    @Before
    public void setup() {
        initModel(true);
    }

    @After
    public void cleanup() {
        database.close();
        model.dataStore.close();
    }

    private void initModel(boolean useNice) {
        initModel(useNice, false);
    }

    private void initModel(boolean useNice, boolean addSelfTargets) {
        if (database != null) {
            database.close();
            database = null;
        }

        if (model != null) {
            model.dataStore.close();
            model = null;
        }

        model = TestModel.getModel(useNice);

        // Add in self friendship ground atoms so we can get more groundings on some tests.
        // (Specifically arithmetic tests where we query for even negative atoms.)
        if (addSelfTargets) {
            Inserter inserter = model.dataStore.getInserter(model.predicates.get("Friends"), model.targetPartition);
            inserter.insert("Alice", "Alice");
            inserter.insert("Bob", "Bob");
            inserter.insert("Charlie", "Charlie");
            inserter.insert("Derek", "Derek");
            inserter.insert("Eugene", "Eugene");
        }

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(model.predicates.get("Nice"));
        toClose.add(model.predicates.get("Person"));
        database = model.dataStore.getDatabase(model.targetPartition, toClose, model.observationPartition);
    }

    @Test
    public void testLogicalBase() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        // Use a simple atom manager so we don't throw a PAM exception.
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
            1.0f,
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
        compareGroundRules(expected, rule, store);
    }

    /**
     * Test that rules with grounding only predicates obey the predicates.
     * Note that gronding only predicates are handled at the grounding query level
     * and don't actually make it into the ground rule.
     * Remember, all rules will be in DNF.
     */
    @Test
    public void testLogicalGroundingOnlyPredicates() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        // Use a simple atom manager so we don't throw a PAM exception.
        AtomManager manager = new SimpleAtomManager(database);

        Rule rule;
        List<String> expected;

        // Nice(A) & Nice(B) & (A == B) -> Friends(A, B)
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.Equal, new Variable("A"), new Variable("B"))
                ),
                new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B"))
            ),
            1.0f,
            true
        );

        expected = Arrays.asList(
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Alice') ) | FRIENDS('Alice', 'Alice') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Bob') ) | FRIENDS('Bob', 'Bob') ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( NICE('Charlie') ) | FRIENDS('Charlie', 'Charlie') ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( NICE('Derek') ) | FRIENDS('Derek', 'Derek') ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( NICE('Eugene') ) | FRIENDS('Eugene', 'Eugene') ) ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Nice(A) & Nice(B) & (A != B) -> Friends(A, B)
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                ),
                new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B"))
            ),
            1.0f,
            true
        );

        expected = Arrays.asList(
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Bob') ) | FRIENDS('Alice', 'Bob') ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Charlie') ) | FRIENDS('Alice', 'Charlie') ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Derek') ) | FRIENDS('Alice', 'Derek') ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Eugene') ) | FRIENDS('Alice', 'Eugene') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Alice') ) | FRIENDS('Bob', 'Alice') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Charlie') ) | FRIENDS('Bob', 'Charlie') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Derek') ) | FRIENDS('Bob', 'Derek') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Eugene') ) | FRIENDS('Bob', 'Eugene') ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( NICE('Alice') ) | FRIENDS('Charlie', 'Alice') ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( NICE('Bob') ) | FRIENDS('Charlie', 'Bob') ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( NICE('Derek') ) | FRIENDS('Charlie', 'Derek') ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( NICE('Eugene') ) | FRIENDS('Charlie', 'Eugene') ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( NICE('Alice') ) | FRIENDS('Derek', 'Alice') ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( NICE('Bob') ) | FRIENDS('Derek', 'Bob') ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( NICE('Charlie') ) | FRIENDS('Derek', 'Charlie') ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( NICE('Eugene') ) | FRIENDS('Derek', 'Eugene') ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( NICE('Alice') ) | FRIENDS('Eugene', 'Alice') ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( NICE('Bob') ) | FRIENDS('Eugene', 'Bob') ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( NICE('Charlie') ) | FRIENDS('Eugene', 'Charlie') ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( NICE('Derek') ) | FRIENDS('Eugene', 'Derek') ) ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Nice(A) & Nice(B) & (A % B) -> Friends(A, B)
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.NonSymmetric, new Variable("A"), new Variable("B"))
                ),
                new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B"))
            ),
            1.0f,
            true
        );

        expected = Arrays.asList(
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Bob') ) | FRIENDS('Alice', 'Bob') ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Charlie') ) | FRIENDS('Alice', 'Charlie') ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Derek') ) | FRIENDS('Alice', 'Derek') ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( NICE('Eugene') ) | FRIENDS('Alice', 'Eugene') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Charlie') ) | FRIENDS('Bob', 'Charlie') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Derek') ) | FRIENDS('Bob', 'Derek') ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( NICE('Eugene') ) | FRIENDS('Bob', 'Eugene') ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( NICE('Derek') ) | FRIENDS('Charlie', 'Derek') ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( NICE('Eugene') ) | FRIENDS('Charlie', 'Eugene') ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( NICE('Eugene') ) | FRIENDS('Derek', 'Eugene') ) ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
    }

    @Test
    public void testArithmeticGroundingOnlyPredicates() {
        initModel(true, true);

        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;

        // Nice(A) + Nice(B) + (A == B) >= Friends(A, B)
        // Nice(A) + Nice(B) + (A == B) - Friends(A, B) >= 0
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(-1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(GroundingOnlyPredicate.Equal, new Variable("A"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LTE, new ConstantNumber(0)),
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Alice') + 1.0 * ('Alice' == 'Alice') + -1.0 * FRIENDS('Alice', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Bob') + 1.0 * ('Bob' == 'Bob') + -1.0 * FRIENDS('Bob', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Charlie') + 1.0 * ('Charlie' == 'Charlie') + -1.0 * FRIENDS('Charlie', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Derek') + 1.0 * ('Derek' == 'Derek') + -1.0 * FRIENDS('Derek', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Eugene') + 1.0 * ('Eugene' == 'Eugene') + -1.0 * FRIENDS('Eugene', 'Eugene') <= 0.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Ensure that the grounding only predicate is not contributing to the value (incompatibility) of the rule.
        for (GroundRule groundRule : store.getGroundRules(rule)) {
            // All should gave the value of 1.0: Both Nice values are 1,0, and the Friends starts at 1.0 (with -1 coefficient).
            assertEquals(1.0f, ((WeightedGroundRule)groundRule).getIncompatibility(), EPSILON);
        }

        // Nice(A) + Nice(B) + (A != B) <= Friends(A, B)
        // Nice(A) + Nice(B) + (A != B) - Friends(A, B) <= 0
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(-1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LTE, new ConstantNumber(0)),
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Bob') + 1.0 * ('Alice' != 'Bob') + -1.0 * FRIENDS('Alice', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Charlie') + 1.0 * ('Alice' != 'Charlie') + -1.0 * FRIENDS('Alice', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Derek') + 1.0 * ('Alice' != 'Derek') + -1.0 * FRIENDS('Alice', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Eugene') + 1.0 * ('Alice' != 'Eugene') + -1.0 * FRIENDS('Alice', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Alice') + 1.0 * ('Bob' != 'Alice') + -1.0 * FRIENDS('Bob', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Charlie') + 1.0 * ('Bob' != 'Charlie') + -1.0 * FRIENDS('Bob', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Derek') + 1.0 * ('Bob' != 'Derek') + -1.0 * FRIENDS('Bob', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Eugene') + 1.0 * ('Bob' != 'Eugene') + -1.0 * FRIENDS('Bob', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Alice') + 1.0 * ('Charlie' != 'Alice') + -1.0 * FRIENDS('Charlie', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Bob') + 1.0 * ('Charlie' != 'Bob') + -1.0 * FRIENDS('Charlie', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Derek') + 1.0 * ('Charlie' != 'Derek') + -1.0 * FRIENDS('Charlie', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Eugene') + 1.0 * ('Charlie' != 'Eugene') + -1.0 * FRIENDS('Charlie', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Alice') + 1.0 * ('Derek' != 'Alice') + -1.0 * FRIENDS('Derek', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Bob') + 1.0 * ('Derek' != 'Bob') + -1.0 * FRIENDS('Derek', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Charlie') + 1.0 * ('Derek' != 'Charlie') + -1.0 * FRIENDS('Derek', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Eugene') + 1.0 * ('Derek' != 'Eugene') + -1.0 * FRIENDS('Derek', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Alice') + 1.0 * ('Eugene' != 'Alice') + -1.0 * FRIENDS('Eugene', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Bob') + 1.0 * ('Eugene' != 'Bob') + -1.0 * FRIENDS('Eugene', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Charlie') + 1.0 * ('Eugene' != 'Charlie') + -1.0 * FRIENDS('Eugene', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Derek') + 1.0 * ('Eugene' != 'Derek') + -1.0 * FRIENDS('Eugene', 'Derek') <= 0.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Nice(A) + Nice(B) + (A % B) <= Friends(A, B)
        // Nice(A) + Nice(B) + (A % B) - Friends(A, B) <= 0
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(-1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(GroundingOnlyPredicate.NonSymmetric, new Variable("A"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LTE, new ConstantNumber(0)),
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Bob') + 1.0 * ('Alice' % 'Bob') + -1.0 * FRIENDS('Alice', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Charlie') + 1.0 * ('Alice' % 'Charlie') + -1.0 * FRIENDS('Alice', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Derek') + 1.0 * ('Alice' % 'Derek') + -1.0 * FRIENDS('Alice', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Eugene') + 1.0 * ('Alice' % 'Eugene') + -1.0 * FRIENDS('Alice', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Charlie') + 1.0 * ('Bob' % 'Charlie') + -1.0 * FRIENDS('Bob', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Derek') + 1.0 * ('Bob' % 'Derek') + -1.0 * FRIENDS('Bob', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Eugene') + 1.0 * ('Bob' % 'Eugene') + -1.0 * FRIENDS('Bob', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Derek') + 1.0 * ('Charlie' % 'Derek') + -1.0 * FRIENDS('Charlie', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Eugene') + 1.0 * ('Charlie' % 'Eugene') + -1.0 * FRIENDS('Charlie', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Eugene') + 1.0 * ('Derek' % 'Eugene') + -1.0 * FRIENDS('Derek', 'Eugene') <= 0.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
    }

    @Test
    public void testArithmeticBase() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;

        // 1.0: Nice(A) + Nice(B) >= 1 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                1.0f,
                true
        );

        expected = Arrays.asList(
            // "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Alice', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Alice', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Alice', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Alice', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Bob', 'Alice') >= 1.0 ^2",
            // "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Bob', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Bob', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Bob', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Charlie', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Charlie', 'Bob') >= 1.0 ^2",
            // "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Charlie', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Charlie', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Derek', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Derek', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Derek', 'Charlie') >= 1.0 ^2",
            // "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Derek', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Eugene', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Eugene', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Eugene', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
            // "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Eugene', 'Eugene') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // 1.0: Nice(A) + Nice(B) <= 1 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LTE, new ConstantNumber(1)),
                1.0f,
                true
        );

        expected = Arrays.asList(
            // "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Alice', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Alice', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Alice', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Alice', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Alice', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Bob', 'Alice') <= 1.0 ^2",
            // "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Bob', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Bob', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Bob', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Bob', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Charlie', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Charlie', 'Bob') <= 1.0 ^2",
            // "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Charlie', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Charlie', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Charlie', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Derek', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Derek', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Derek', 'Charlie') <= 1.0 ^2",
            // "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Derek', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Derek', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Alice') + 1.0 * FRIENDS('Eugene', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Bob') + 1.0 * FRIENDS('Eugene', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Charlie') + 1.0 * FRIENDS('Eugene', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Derek') + 1.0 * FRIENDS('Eugene', 'Derek') <= 1.0 ^2"
            // "1.0: 1.0 * NICE('Eugene') + 1.0 * NICE('Eugene') + 1.0 * FRIENDS('Eugene', 'Eugene') <= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // 1.0: Nice(A) + -1 * Nice(B) = 0 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(-1)),
            (Coefficient)(new ConstantNumber(1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)),
                1.0f,
                true
        );

        // Remember, equality puts in a <= and >=.
        expected = Arrays.asList(
            // "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Alice', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Alice', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Alice', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Alice', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Alice', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Bob', 'Alice') <= 1.0 ^2",
            // "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Bob', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Bob', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Bob', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Bob', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Charlie', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Charlie', 'Bob') <= 1.0 ^2",
            // "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Charlie', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Charlie', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Charlie', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Derek', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Derek', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Derek', 'Charlie') <= 1.0 ^2",
            // "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Derek', 'Derek') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Derek', 'Eugene') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Eugene', 'Alice') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Eugene', 'Bob') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Eugene', 'Charlie') <= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Eugene', 'Derek') <= 1.0 ^2",
            // "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Eugene', 'Eugene') <= 1.0 ^2",

            // "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Alice', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Alice', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Alice', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Alice', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Alice') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Bob', 'Alice') >= 1.0 ^2",
            // "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Bob', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Bob', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Bob', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Charlie', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Charlie', 'Bob') >= 1.0 ^2",
            // "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Charlie', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Charlie', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Derek', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Derek', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Derek', 'Charlie') >= 1.0 ^2",
            // "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Derek', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Alice') + 1.0 * FRIENDS('Eugene', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Bob') + 1.0 * FRIENDS('Eugene', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Charlie') + 1.0 * FRIENDS('Eugene', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Derek') + 1.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
            // "1.0: 1.0 * NICE('Eugene') + -1.0 * NICE('Eugene') + 1.0 * FRIENDS('Eugene', 'Eugene') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
    }

    @Test
    // Everyone is 100% Nice in this test.
    // |B| * Friends(A, +B) >= 1 {B: Nice(B)}
    // |B| * Friends(A, +B) >= 1 {B: !Nice(B)}
    public void testSelectBaseNice() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new Cardinality(new SummationVariable("B")))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(model.predicates.get("Nice"), new Variable("B")));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
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
        compareGroundRules(expected, rule, store);

        // Now negate the select.
        store = new MemoryGroundRuleStore();

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new Negation(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        // There will be no results because ground rules with no subs in the selects do not ground.
        expected = new ArrayList<String>();
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store, false);
    }

    @Test
    // Everyone except Eugene has non-zero niceness.
    // |B| * Friends(A, +B) >= 1 {B: Nice(B)}
    // |B| * Friends(A, +B) >= 1 {B: !Nice(B)}
    public void testFilterBaseNotNice() {
        // Reset the model to not use 100% nice.
        initModel(false);

        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new Cardinality(new SummationVariable("B")))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(model.predicates.get("Nice"), new Variable("B")));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
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
        compareGroundRules(expected, rule, store);

        // Now negate the select.
        store = new MemoryGroundRuleStore();

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new Negation(new QueryAtom(model.predicates.get("Nice"), new Variable("B"))));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        // Note that 'Eugene' is the only one because Nice('Eugene') = 0.
        // However, FRIENDS('Eugene', 'Eugene') does not appear in the observations.
        expected = Arrays.asList(
            "1.0: 1.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store, false);
    }

    @Test
    // Everyone except Eugene has non-zero niceness.
    // Person(A) + |B| * Friends(A, +B) >= 1 {B: Friends(B, 'Alice') && Nice(B)}
    // Person(A) + |B| * Friends(A, +B) >= 1 {B: Friends(B, 'Alice') || Nice(B)}
    public void testFilterConstant() {
        // Reset the model to not use 100% nice.
        initModel(false);

        database.close();

        // Use Person as the open predicate to ensure the ground rules are not trivial.
        // To do this, we also need to use the obs partition as targets.

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(model.predicates.get("Nice"));
        toClose.add(model.predicates.get("Friends"));
        database = model.dataStore.getDatabase(model.observationPartition, toClose, model.targetPartition);

        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new Cardinality(new SummationVariable("B")))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Person"), new Variable("A"))),
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(
            new SummationVariable("B"),
            new Conjunction(
                new QueryAtom(model.predicates.get("Friends"), new Variable("B"), new UniqueStringID("Alice")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
            )
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        // Note that 'Alice' is not friends with herself (in the filter), but others can because of the closed predicate.
        // Person(A) + |B| * Friends(A, +B) >= 1 {B: Friends(B, 'Alice') && Nice(B)}
        expected = Arrays.asList(
            "1.0: 1.0 * PERSON('Alice') + 3.0 * FRIENDS('Alice', 'Bob') + 3.0 * FRIENDS('Alice', 'Charlie') + 3.0 * FRIENDS('Alice', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * PERSON('Bob') + 3.0 * FRIENDS('Bob', 'Charlie') + 3.0 * FRIENDS('Bob', 'Derek') + 3.0 * FRIENDS('Bob', 'Bob') >= 1.0 ^2",
            "1.0: 1.0 * PERSON('Charlie') + 3.0 * FRIENDS('Charlie', 'Bob') + 3.0 * FRIENDS('Charlie', 'Derek') + 3.0 * FRIENDS('Charlie', 'Charlie') >= 1.0 ^2",
            "1.0: 1.0 * PERSON('Derek') + 3.0 * FRIENDS('Derek', 'Bob') + 3.0 * FRIENDS('Derek', 'Charlie') + 3.0 * FRIENDS('Derek', 'Derek') >= 1.0 ^2",
            "1.0: 1.0 * PERSON('Eugene') + 3.0 * FRIENDS('Eugene', 'Bob') + 3.0 * FRIENDS('Eugene', 'Charlie') + 3.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Now change the select to a disjunction.
        store = new MemoryGroundRuleStore();

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(
            new SummationVariable("B"),
            new Disjunction(
                new QueryAtom(model.predicates.get("Friends"), new Variable("B"), new UniqueStringID("Alice")),
                new QueryAtom(model.predicates.get("Nice"), new UniqueStringID("Alice"))
            )
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        try {
            rule.groundAll(manager, store);
            fail("Attempting to ground a disjunction without a split did not throw.");
        } catch (IllegalStateException ex) {
            // Expected
        }
    }

    @Test
    // |B| * Friends(A, +B) >= 1
    public void testSummationNoSelect() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new Cardinality(new SummationVariable("B")))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
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
        compareGroundRules(expected, rule, store);
    }

    @Test
    // Friends(+A, +B) >= 1
    // Friends(+A, +B) >= 1 {A: Nice(A)}
    // Friends(+A, +B) >= 1 {A: Nice(A)} {B: Nice(B)}
    public void testMultipleSummation() {
        // Reset the model to not use 100% nice.
        initModel(false);

        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new SummationVariable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
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
        compareGroundRules(expected, rule, store);

        // Add a select on A.
        store = new MemoryGroundRuleStore();

        filters.put(
            new SummationVariable("A"),
            new QueryAtom(model.predicates.get("Nice"), new Variable("A"))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
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
        compareGroundRules(expected, rule, store);

        // Add a select on B.
        store = new MemoryGroundRuleStore();

        filters.put(
            new SummationVariable("B"),
            new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
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
        compareGroundRules(expected, rule, store);
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

        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new Cardinality(new SummationVariable("A")))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new SummationVariable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(
            new SummationVariable("B"),
            new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        // |A|
        expected = Arrays.asList(
            "1.0: " +
                "16.0 * FRIENDS('Alice', 'Bob') + 16.0 * FRIENDS('Alice', 'Charlie') + 16.0 * FRIENDS('Alice', 'Derek') + " +
                "16.0 * FRIENDS('Bob', 'Alice') + 16.0 * FRIENDS('Bob', 'Charlie') + 16.0 * FRIENDS('Bob', 'Derek') + " +
                "16.0 * FRIENDS('Charlie', 'Alice') + 16.0 * FRIENDS('Charlie', 'Bob') + 16.0 * FRIENDS('Charlie', 'Derek') + " +
                "16.0 * FRIENDS('Derek', 'Alice') + 16.0 * FRIENDS('Derek', 'Bob') + 16.0 * FRIENDS('Derek', 'Charlie') + " +
                "16.0 * FRIENDS('Eugene', 'Alice') + 16.0 * FRIENDS('Eugene', 'Bob') + 16.0 * FRIENDS('Eugene', 'Charlie') + 16.0 * FRIENDS('Eugene', 'Derek') " +
                ">= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // |B|
        store = new MemoryGroundRuleStore();

        coefficients = Arrays.asList(
            (Coefficient)(new Cardinality(new SummationVariable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: " +
                "16.0 * FRIENDS('Alice', 'Bob') + 16.0 * FRIENDS('Alice', 'Charlie') + 16.0 * FRIENDS('Alice', 'Derek') + " +
                "16.0 * FRIENDS('Bob', 'Alice') + 16.0 * FRIENDS('Bob', 'Charlie') + 16.0 * FRIENDS('Bob', 'Derek') + " +
                "16.0 * FRIENDS('Charlie', 'Alice') + 16.0 * FRIENDS('Charlie', 'Bob') + 16.0 * FRIENDS('Charlie', 'Derek') + " +
                "16.0 * FRIENDS('Derek', 'Alice') + 16.0 * FRIENDS('Derek', 'Bob') + 16.0 * FRIENDS('Derek', 'Charlie') + " +
                "16.0 * FRIENDS('Eugene', 'Alice') + 16.0 * FRIENDS('Eugene', 'Bob') + 16.0 * FRIENDS('Eugene', 'Charlie') + 16.0 * FRIENDS('Eugene', 'Derek') " +
                ">= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // |A| + |B|
        store = new MemoryGroundRuleStore();

        coefficients = Arrays.asList(
            (Coefficient)(new Add(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: " +
                "32.0 * FRIENDS('Alice', 'Bob') + 32.0 * FRIENDS('Alice', 'Charlie') + 32.0 * FRIENDS('Alice', 'Derek') + " +
                "32.0 * FRIENDS('Bob', 'Alice') + 32.0 * FRIENDS('Bob', 'Charlie') + 32.0 * FRIENDS('Bob', 'Derek') + " +
                "32.0 * FRIENDS('Charlie', 'Alice') + 32.0 * FRIENDS('Charlie', 'Bob') + 32.0 * FRIENDS('Charlie', 'Derek') + " +
                "32.0 * FRIENDS('Derek', 'Alice') + 32.0 * FRIENDS('Derek', 'Bob') + 32.0 * FRIENDS('Derek', 'Charlie') + " +
                "32.0 * FRIENDS('Eugene', 'Alice') + 32.0 * FRIENDS('Eugene', 'Bob') + 32.0 * FRIENDS('Eugene', 'Charlie') + 32.0 * FRIENDS('Eugene', 'Derek') " +
                ">= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // |A| - |B|
        store = new MemoryGroundRuleStore();

        coefficients = Arrays.asList(
            (Coefficient)(new Subtract(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: " +
                "0.0 * FRIENDS('Alice', 'Bob') + 0.0 * FRIENDS('Alice', 'Charlie') + 0.0 * FRIENDS('Alice', 'Derek') + " +
                "0.0 * FRIENDS('Bob', 'Alice') + 0.0 * FRIENDS('Bob', 'Charlie') + 0.0 * FRIENDS('Bob', 'Derek') + " +
                "0.0 * FRIENDS('Charlie', 'Alice') + 0.0 * FRIENDS('Charlie', 'Bob') + 0.0 * FRIENDS('Charlie', 'Derek') + " +
                "0.0 * FRIENDS('Derek', 'Alice') + 0.0 * FRIENDS('Derek', 'Bob') + 0.0 * FRIENDS('Derek', 'Charlie') + " +
                "0.0 * FRIENDS('Eugene', 'Alice') + 0.0 * FRIENDS('Eugene', 'Bob') + 0.0 * FRIENDS('Eugene', 'Charlie') + 0.0 * FRIENDS('Eugene', 'Derek') " +
                ">= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // |A| * |B|
        store = new MemoryGroundRuleStore();

        coefficients = Arrays.asList(
            (Coefficient)(new Multiply(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: " +
                "256.0 * FRIENDS('Alice', 'Bob') + 256.0 * FRIENDS('Alice', 'Charlie') + 256.0 * FRIENDS('Alice', 'Derek') + " +
                "256.0 * FRIENDS('Bob', 'Alice') + 256.0 * FRIENDS('Bob', 'Charlie') + 256.0 * FRIENDS('Bob', 'Derek') + " +
                "256.0 * FRIENDS('Charlie', 'Alice') + 256.0 * FRIENDS('Charlie', 'Bob') + 256.0 * FRIENDS('Charlie', 'Derek') + " +
                "256.0 * FRIENDS('Derek', 'Alice') + 256.0 * FRIENDS('Derek', 'Bob') + 256.0 * FRIENDS('Derek', 'Charlie') + " +
                "256.0 * FRIENDS('Eugene', 'Alice') + 256.0 * FRIENDS('Eugene', 'Bob') + 256.0 * FRIENDS('Eugene', 'Charlie') + 256.0 * FRIENDS('Eugene', 'Derek') " +
                ">= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // |A| / |B|
        store = new MemoryGroundRuleStore();

        coefficients = Arrays.asList(
            (Coefficient)(new Divide(new Cardinality(new SummationVariable("A")), new Cardinality(new SummationVariable("B"))))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
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
        compareGroundRules(expected, rule, store);
    }

    @Test
    // |B| * Friends(A, +B) + Person(C) >= 1 {B: Friends(C, B)}
    // |B| * Friends(A, +B) + Person(C) >= 1 {B: Friends(C, B) && Nice(C)}
    public void testFilterBinary() {
        // Reset the model to not use 100% nice.
        initModel(false);

        database.close();

        // Use Nice as the open predicate to ensure the ground rules are not trivial.
        // To do this, we also need to use the obs partition as targets.

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(model.predicates.get("Person"));
        toClose.add(model.predicates.get("Friends"));
        database = model.dataStore.getDatabase(model.observationPartition, toClose, model.targetPartition);

        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new Cardinality(new SummationVariable("B"))),
            (Coefficient)(new ConstantNumber(1))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("C"))),
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            )),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Person"), new Variable("C")))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(
            new SummationVariable("B"),
            new QueryAtom(model.predicates.get("Friends"), new Variable("C"), new Variable("B"))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        // |B| * Friends(A, +B) + Person(C) >= 1 {B: Friends(C, B)}
        // Note that self friendship is allowed here since Friends is a closed predicate.
        expected = Arrays.asList(
            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Derek') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Eugene') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Add the additional clause to the select.
        store = new MemoryGroundRuleStore();

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(
            new SummationVariable("B"),
            new Conjunction(
                new QueryAtom(model.predicates.get("Friends"), new Variable("C"), new Variable("B")),
                new QueryAtom(model.predicates.get("Person"), new Variable("A"))
            )
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        // |B| * Friends(A, +B) + Person(C) >= 1 {B: Friends(C, B) & Nice(C)}
        expected = Arrays.asList(
            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Derek') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Alice', 'Alice') + 4.0 * FRIENDS('Alice', 'Bob') + 4.0 * FRIENDS('Alice', 'Charlie') + 4.0 * FRIENDS('Alice', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Derek') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Bob', 'Bob') + 4.0 * FRIENDS('Bob', 'Alice') + 4.0 * FRIENDS('Bob', 'Charlie') + 4.0 * FRIENDS('Bob', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Charlie', 'Charlie') + 4.0 * FRIENDS('Charlie', 'Bob') + 4.0 * FRIENDS('Charlie', 'Alice') + 4.0 * FRIENDS('Charlie', 'Derek') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 4.0 * FRIENDS('Derek', 'Eugene') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Derek', 'Derek') + 4.0 * FRIENDS('Derek', 'Bob') + 4.0 * FRIENDS('Derek', 'Charlie') + 4.0 * FRIENDS('Derek', 'Alice') + 1.0 * PERSON('Eugene') >= 1.0 ^2",

            "1.0: 1.0 * NICE('Alice') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') + 1.0 * PERSON('Alice') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Bob') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Bob') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Charlie') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Derek') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Charlie') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Derek') + 4.0 * FRIENDS('Eugene', 'Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Derek') >= 1.0 ^2",
            "1.0: 1.0 * NICE('Eugene') + 4.0 * FRIENDS('Eugene', 'Bob') + 4.0 * FRIENDS('Eugene', 'Charlie') + 4.0 * FRIENDS('Eugene', 'Derek') + 4.0 * FRIENDS('Eugene', 'Alice') + 1.0 * PERSON('Eugene') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
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
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients = new ArrayList<Coefficient>();
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new SummationVariable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(
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

            "16.0",
            "16.0",
            "16.0",
            "16.0",

            "1.0",
            "3.0",
            "1.0",
            "3.0",

            "1.0",
            "32.0",
            "1.0",
            "32.0"
        };

        for (int i = 0; i < testCoefficients.length; i++) {
            expected = Arrays.asList(expectedBase.replaceAll("__VAL__", expectedValues[i]));
            store = new MemoryGroundRuleStore();

            coefficients.clear();
            coefficients.add(testCoefficients[i]);

            rule = new WeightedArithmeticRule(
                    new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                    filters,
                    1.0f,
                    true
            );

            rule.groundAll(manager, store);
            compareGroundRules(expected, rule, store);
        }
    }

    // Ensure that exceptions are thrown when zero coefficients are divided by.
    // Note that here we are not interested in coefficients that are statically evaluated to zero,
    // instead we are interested in coefficients that require grounding to become zero (like with cardinality).
    // 1.0 * Friends(A, +B) + Nice(A) / (|B| - 5) >= 1 {B: Nice(B)}
    // Note that everyone is 100% nice in this test.
    @Test
    public void testArithmeticDivdeByZero() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f)),
            (Coefficient)(new Divide(
                new ConstantNumber(1.0f),
                new Subtract(
                    new Cardinality(new SummationVariable("B")),
                    new ConstantNumber(4.0f)
                )
            ))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            )),
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Nice"), new Variable("A")))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(model.predicates.get("Nice"), new Variable("B")));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        try {
            rule.groundAll(manager, store);
            fail("Divide by zero did not throw an ArithmeticException.");
        } catch (ArithmeticException ex) {
            // Expected
        }
    }

    @Test
    public void testArithmeticNegativePrior() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;

        // 1.0: Friends(A, B) = 0 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(0.0f)),
                1.0f,
                true
        );

        // Remember, equality puts in a <= and >= (when weighted).
        expected = Arrays.asList(
            "1.0: 1.0 * FRIENDS('Alice', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Alice', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Alice', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Alice', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Bob', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Bob', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Bob', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Bob', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Charlie', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Charlie', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Charlie', 'Derek') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Charlie', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Derek', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Derek', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Derek', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Derek', 'Eugene') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Eugene', 'Alice') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Eugene', 'Bob') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Eugene', 'Charlie') <= 0.0 ^2",
            "1.0: 1.0 * FRIENDS('Eugene', 'Derek') <= 0.0 ^2"
        );
        rule.groundAll(manager, store);
        // No need for order with one atom.
        compareGroundRules(expected, rule, store, false);
    }

    @Test
    /**
     * Make sure that variables that appear in the head but not the body are fine.
     * There was some concern about this before, but once we convert to the DNF,
     * there is not head.
     */
    public void testVariablesInHead() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;

        // Nice(A) -> !Friends(A, B)
        rule = new WeightedLogicalRule(
            new Implication(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Negation(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
            ),
            1.0f,
            true
        );

        // Remember, all rules will be in DNF.
        expected = Arrays.asList(
            "1.0: ( ~( NICE('Alice') ) | ~( FRIENDS('Alice', 'Bob') ) ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( FRIENDS('Alice', 'Charlie') ) ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( FRIENDS('Alice', 'Derek') ) ) ^2",
            "1.0: ( ~( NICE('Alice') ) | ~( FRIENDS('Alice', 'Eugene') ) ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( FRIENDS('Bob', 'Alice') ) ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( FRIENDS('Bob', 'Charlie') ) ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( FRIENDS('Bob', 'Derek') ) ) ^2",
            "1.0: ( ~( NICE('Bob') ) | ~( FRIENDS('Bob', 'Eugene') ) ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( FRIENDS('Charlie', 'Alice') ) ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( FRIENDS('Charlie', 'Bob') ) ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( FRIENDS('Charlie', 'Derek') ) ) ^2",
            "1.0: ( ~( NICE('Charlie') ) | ~( FRIENDS('Charlie', 'Eugene') ) ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( FRIENDS('Derek', 'Alice') ) ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( FRIENDS('Derek', 'Bob') ) ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( FRIENDS('Derek', 'Charlie') ) ) ^2",
            "1.0: ( ~( NICE('Derek') ) | ~( FRIENDS('Derek', 'Eugene') ) ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( FRIENDS('Eugene', 'Alice') ) ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( FRIENDS('Eugene', 'Bob') ) ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( FRIENDS('Eugene', 'Charlie') ) ) ^2",
            "1.0: ( ~( NICE('Eugene') ) | ~( FRIENDS('Eugene', 'Derek') ) ) ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
    }

    /**
     * Test for situations where rules with a single atom are trivial.
     */
    @Test
    public void testArithmeticSingleTrivials() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;

        // All trivial.
        // 1.0: Friends(A, B) >= 0.0 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(0.0f)),
                1.0f,
                true
        );

        expected = Arrays.asList();
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // All trivial.
        // 1.0: Friends(A, B) <= 1.0 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LTE, new ConstantNumber(1.0f)),
                1.0f,
                true
        );

        expected = Arrays.asList();
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // All trivial.
        // 1.0: -1.0 * Friends(A, B) >= -1.0 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(-1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(-1.0f)),
                1.0f,
                true
        );

        expected = Arrays.asList();
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // All trivial.
        // 1.0: -1.0 * Friends(A, B) <= 0.0 ^2
        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(-1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(model.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LTE, new ConstantNumber(0.0f)),
                1.0f,
                true
        );

        expected = Arrays.asList();
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
    }

    /**
     * Test a select caluse that has no atoms.
     * Friends(A, +B) >= 1 {B: B == 'Alice'}
     * Friends(A, +B) >= 1 {B: B != 'Alice'}
     * Friends(A, +B) >= 1 {B: A == 'Alice'}
     */
    @Test
    public void testSelectWithoutAtoms() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;
        List<String> expected;
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;
        Map<SummationVariable, Formula> filters;

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(GroundingOnlyPredicate.Equal, new Variable("B"), new UniqueStringID("Alice")));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: 1.0 * FRIENDS('Bob', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Charlie', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Derek', 'Alice') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Eugene', 'Alice') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Now swap the equality to not equals.

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("B"), new UniqueStringID("Alice")));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: 1.0 * FRIENDS('Alice', 'Bob') + 1.0 * FRIENDS('Alice', 'Charlie') + 1.0 * FRIENDS('Alice', 'Derek') + 1.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Bob', 'Charlie') + 1.0 * FRIENDS('Bob', 'Derek') + 1.0 * FRIENDS('Bob', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Charlie', 'Bob') + 1.0 * FRIENDS('Charlie', 'Derek') + 1.0 * FRIENDS('Charlie', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Derek', 'Bob') + 1.0 * FRIENDS('Derek', 'Charlie') + 1.0 * FRIENDS('Derek', 'Eugene') >= 1.0 ^2",
            "1.0: 1.0 * FRIENDS('Eugene', 'Bob') + 1.0 * FRIENDS('Eugene', 'Charlie') + 1.0 * FRIENDS('Eugene', 'Derek') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Now use another variable in the equality check.

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                model.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(GroundingOnlyPredicate.Equal, new Variable("A"), new UniqueStringID("Alice")));

        rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                filters,
                1.0f,
                true
        );

        expected = Arrays.asList(
            "1.0: 1.0 * FRIENDS('Alice', 'Bob') + 1.0 * FRIENDS('Alice', 'Charlie') + 1.0 * FRIENDS('Alice', 'Derek') + 1.0 * FRIENDS('Alice', 'Eugene') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
    }

    /**
     * Ensure a PAM exception is thrown for a logical rule.
     */
    @Test
    public void testLogicalAccessEcception() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new PersistedAtomManager(database);

        Rule rule;

        // Nice(A) && Nice(B) -> !Friends('__Missing1__', '__Missing2__')
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                ),
                new QueryAtom(model.predicates.get("Friends"), new UniqueStringID("__Missing1__"), new UniqueStringID("__Missing2__"))
            ),
            1.0f,
            true
        );

        try {
            rule.groundAll(manager, store);
            fail("PAM exception not thrown for a logcial rule.");
        } catch (Exception ex) {
            // Expected
        }
    }
}
