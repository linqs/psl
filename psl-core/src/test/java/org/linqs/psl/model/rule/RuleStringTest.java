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
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.SimpleAtomManager;
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
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.UnweightedLogicalRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RuleStringTest extends PSLBaseTest {
    private DataStore dataStore;
    private Database database;
    private Partition obsPartition;
    private Partition targetPartition;

    private StandardPredicate singlePredicate;
    private StandardPredicate singleIntPredicate;
    private StandardPredicate doublePredicate;
    private StandardPredicate singleOpened;

    private ArithmeticRuleExpression arithmeticBaseRule;
    private Formula logicalBaseRule;

    @Before
    public void setup() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true));

        // Predicates
        singlePredicate = StandardPredicate.get("SinglePredicate", ConstantType.UniqueStringID);
        dataStore.registerPredicate(singlePredicate);

        singleIntPredicate = StandardPredicate.get("SingleIntPredicate", ConstantType.UniqueIntID);
        dataStore.registerPredicate(singleIntPredicate);

        doublePredicate = StandardPredicate.get("DoublePredicate", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
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
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("B"))),
            (SummationAtomOrAtom)(new QueryAtom(doublePredicate, new Variable("A"), new Variable("B")))
        );

        // Base Rule: SinglePredicate(A) + SinglePredicate(B) + DoublePredicate(A, B) = 1
        arithmeticBaseRule = new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        // Data
        obsPartition = dataStore.getNewPartition();
        targetPartition = dataStore.getNewPartition();

        Inserter inserter = dataStore.getInserter(singlePredicate, obsPartition);
        inserter.insert(new UniqueStringID("Alice"));
        inserter.insert(new UniqueStringID("Bob"));

        inserter = dataStore.getInserter(doublePredicate, targetPartition);
        inserter.insert(new UniqueStringID("Alice"), new UniqueStringID("Alice"));
        inserter.insert(new UniqueStringID("Alice"), new UniqueStringID("Bob"));
        inserter.insert(new UniqueStringID("Bob"), new UniqueStringID("Alice"));
        inserter.insert(new UniqueStringID("Bob"), new UniqueStringID("Bob"));

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        database = dataStore.getDatabase(targetPartition, toClose, obsPartition);
    }

    @Test
    public void testLogicalRuleString() {
        // Base Rule: SinglePredicate(A) & SinglePredicate(B) -> DoublePredicate(A, B)
        Rule rule;

        // Unweighted (Not Squared)
        rule = new UnweightedLogicalRule(logicalBaseRule);
        assertEquals("( SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B) .", rule.toString());

        // Weighted, Squared
        rule = new WeightedLogicalRule(logicalBaseRule, 10.0f, true);
        assertEquals("10.0: ( SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B) ^2", rule.toString());

        // Weighted, Not Squared
        rule = new WeightedLogicalRule(logicalBaseRule, 10.0f, false);
        assertEquals("10.0: ( SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B)", rule.toString());
    }

    @Test
    public void testArithmeticRuleString() {
        // Base Rule: SinglePredicate(A) + SinglePredicate(B) = 1
        Rule rule;

        // Unweighted (Not Squared)
        rule = new UnweightedArithmeticRule(arithmeticBaseRule);
        assertEquals("1.0 * SINGLEPREDICATE(A) + 1.0 * SINGLEPREDICATE(B) + 1.0 * DOUBLEPREDICATE(A, B) = 1.0 .", rule.toString());

        // Weighted, Squared
        rule = new WeightedArithmeticRule(arithmeticBaseRule, 10.0f, true);
        assertEquals("10.0: 1.0 * SINGLEPREDICATE(A) + 1.0 * SINGLEPREDICATE(B) + 1.0 * DOUBLEPREDICATE(A, B) = 1.0 ^2", rule.toString());

        // Weighted, Not Squared
        rule = new WeightedArithmeticRule(arithmeticBaseRule, 10.0f, false);
        assertEquals("10.0: 1.0 * SINGLEPREDICATE(A) + 1.0 * SINGLEPREDICATE(B) + 1.0 * DOUBLEPREDICATE(A, B) = 1.0", rule.toString());
    }

    @Test
    public void testGroundLogicalRuleString() {
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new SimpleAtomManager(database);

        Rule rule;
        List<String> expected;

        // Unweighted (Not Squared)
        rule = new UnweightedLogicalRule(logicalBaseRule);
        // Remember, all rules will be in DNF.
        expected = Arrays.asList(
            "( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Alice', 'Alice') ) .",
            "( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Alice', 'Bob') ) .",
            "( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Bob', 'Alice') ) .",
            "( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Bob', 'Bob') ) ."
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Weighted, Squared
        rule = new WeightedLogicalRule(logicalBaseRule, 10.0f, true);
        expected = Arrays.asList(
            "10.0: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Alice', 'Alice') ) ^2",
            "10.0: ( ~( SINGLEPREDICATE('Alice') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Alice', 'Bob') ) ^2",
            "10.0: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Alice') ) | DOUBLEPREDICATE('Bob', 'Alice') ) ^2",
            "10.0: ( ~( SINGLEPREDICATE('Bob') ) | ~( SINGLEPREDICATE('Bob') ) | DOUBLEPREDICATE('Bob', 'Bob') ) ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Weighted, Not Squared
        rule = new WeightedLogicalRule(logicalBaseRule, 10.0f, false);
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
        GroundRuleStore store = new MemoryGroundRuleStore();
        AtomManager manager = new SimpleAtomManager(database);

        Rule rule;
        List<String> expected;

        // Unweighted (Not Squared)
        rule = new UnweightedArithmeticRule(arithmeticBaseRule);
        expected = Arrays.asList(
            "1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Alice', 'Alice') = 1.0 .",
            "1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Alice', 'Bob') = 1.0 .",
            "1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Bob', 'Alice') = 1.0 .",
            "1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Bob', 'Bob') = 1.0 ."
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Weighted, Squared
        rule = new WeightedArithmeticRule(arithmeticBaseRule, 10.0f, true);
        expected = Arrays.asList(
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Alice', 'Alice') <= 1.0 ^2",
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Alice', 'Alice') >= 1.0 ^2",
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Alice', 'Bob') <= 1.0 ^2",
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Alice', 'Bob') >= 1.0 ^2",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Bob', 'Alice') <= 1.0 ^2",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Bob', 'Alice') >= 1.0 ^2",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Bob', 'Bob') <= 1.0 ^2",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Bob', 'Bob') >= 1.0 ^2"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);

        // Weighted, Not Squared
        rule = new WeightedArithmeticRule(arithmeticBaseRule, 10.0f, false);
        expected = Arrays.asList(
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Alice', 'Alice') <= 1.0",
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Alice', 'Alice') >= 1.0",
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Alice', 'Bob') <= 1.0",
            "10.0: 1.0 * SINGLEPREDICATE('Alice') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Alice', 'Bob') >= 1.0",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Bob', 'Alice') <= 1.0",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Alice') + 1.0 * DOUBLEPREDICATE('Bob', 'Alice') >= 1.0",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Bob', 'Bob') <= 1.0",
            "10.0: 1.0 * SINGLEPREDICATE('Bob') + 1.0 * SINGLEPREDICATE('Bob') + 1.0 * DOUBLEPREDICATE('Bob', 'Bob') >= 1.0"
        );
        rule.groundAll(manager, store);
        compareGroundRules(expected, rule, store);
    }

    @Test
    public void testLogicalIntRule() {
        // Base Rule: SingleIntPredicate('1') & SinglePredicate(A) & SinglePredicate(B) -> DoublePredicate(A, B)
        Rule rule;

        Formula baseRule = new Implication(
                new Conjunction(
                    new QueryAtom(singleIntPredicate, new UniqueIntID(1)),
                    new QueryAtom(singlePredicate, new Variable("A")),
                    new QueryAtom(singlePredicate, new Variable("B"))
                ),
                new QueryAtom(doublePredicate, new Variable("A"), new Variable("B"))
        );

        // Unweighted (Not Squared)
        rule = new UnweightedLogicalRule(baseRule);
        assertEquals("( SINGLEINTPREDICATE('1') & SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B) .", rule.toString());

        // Weighted, Squared
        rule = new WeightedLogicalRule(baseRule, 10.0f, true);
        assertEquals("10.0: ( SINGLEINTPREDICATE('1') & SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B) ^2", rule.toString());

        // Weighted, Not Squared
        rule = new WeightedLogicalRule(baseRule, 10.0f, false);
        assertEquals("10.0: ( SINGLEINTPREDICATE('1') & SINGLEPREDICATE(A) & SINGLEPREDICATE(B) ) >> DOUBLEPREDICATE(A, B)", rule.toString());
    }

    @After
    public void cleanup() {
        database.close();
        dataStore.close();
    }
}
