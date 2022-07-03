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
package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.SimpleAtomManager;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
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
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AbstractArithmeticRuleTest extends PSLBaseTest {
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

    @After
    public void cleanup() {
        database.close();
        dataStore.close();
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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

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
                    coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
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
                new UniqueStringID("Foo")
            }))
        );

        AbstractArithmeticRule rule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

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
                    coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
            fail("IllegalArgumentException not thrown when summation variable is used as a term.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }
    }

    @Test
    public void testSingleFilter() {
        // SingleClosed(+A) = 1 . {A: SingleClosed(A)}
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("A")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
        AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(expression, filters);

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "1.0 * SINGLECLOSED(+A) = 1.0 .   {A : SINGLECLOSED(A)}");
    }

    @Test
    public void testDisjunctiveFilter() {
        // SingleClosed(+A) = 1 . {A: SingleClosed(A) || DoubleClosed(A, A) }
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"),
                new Disjunction(
                        new QueryAtom(singleClosed, new Variable("A")),
                        new QueryAtom(doubleClosed, new Variable("A"), new Variable("A"))
                )
        );

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
        AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(expression, filters);

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "1.0 * SINGLECLOSED(+A) = 1.0 .   {A : ( SINGLECLOSED(A) | DOUBLECLOSED(A, A) )}", true);
    }

    @Test
    public void testSingleFilterDifferentVariable() {
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

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("B")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
        AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(expression, filters);

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "1.0 * DOUBLECLOSED(+A, B) = 1.0 .   {A : SINGLECLOSED(B)}");
    }

    @Test
    public void testMultipleFilters() {
        // SingleClosed(+A) + SingleClosed(+B) = 1 . {A: SingleClosed(A)} {B: SingleClosed(B)}
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("B")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("A")));
        filters.put(new SummationVariable("B"), new QueryAtom(singleClosed, new Variable("B")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
        AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(expression, filters);

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "1.0 * SINGLECLOSED(+A) + 1.0 * SINGLECLOSED(+B) = 1.0 .   {A : SINGLECLOSED(A)}   {B : SINGLECLOSED(B)}", true);
    }

    @Test
    public void testFilterArgNotInExpression() {
        // SingleClosed(+A) = 1 . {B: SingleClosed(B)}
        // Fail: the filter argument (B) is unknown.
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(singleClosed, new Variable("B")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        try {
            AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
            fail("IllegalArgumentException not thrown when unknown variable used as filter argument.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }
    }

    @Test
    public void testUnknownVariableInFilter() {
        // SingleClosed(+A) = 1 . {A: SingleClosed(B)}
        // Fail: a variable in the filter does not appear in the arithmetic expression.
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("B")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        try {
            AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
            fail("IllegalArgumentException not thrown when unknown variable appears in filter.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }
    }

    @Test
    public void testFilterWithNoSummation() {
        // SingleClosed(A) = 1 . {A: SingleClosed(A)}
        // Fail: a filter requires a summation atom.
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A")))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"), new QueryAtom(singleClosed, new Variable("A")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        try {
            AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
            fail("IllegalArgumentException not thrown when filter appears without summation.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }
    }

    @Test
    public void testFilterWithNonSummationArgument() {
        // DoubleClosed(+A, B) = 1 . {B: SingleClosed(A)}
        // Fail: filter arguments must be summation variable.
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(doubleClosed, new SummationVariableOrTerm[]{
                new SummationVariable("A"),
                new Variable("B")
            }))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(singleClosed, new Variable("A")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        try {
            AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);
            fail("IllegalArgumentException not thrown when non-summation variable used as filter argument.");
        } catch (IllegalArgumentException ex) {
            // Exception is expected.
        }
    }

    @Test
    public void testFilterOpenPredicate() {
        // SingleClosed(+A) = 1 . {A: SingleOpened(A)}
        // Fail: predicates in filter must be closed.
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"), new QueryAtom(singleOpened, new Variable("A")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
        AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);

        SimpleAtomManager atomManager = new SimpleAtomManager(database);
        GroundRuleStore groundRuleStore = new MemoryGroundRuleStore();

        try {
            rule.groundAll(atomManager, groundRuleStore);
            fail("IllegalArgumentException not thrown when trying to ground an open predicate in the filter.");
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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "|A| * SINGLECLOSED(+A) = 1.0 .");
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
                coefficients, atoms, FunctionComparator.EQ, new Cardinality(new SummationVariable("A"))));
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new Cardinality(new SummationVariable("A"))));

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "|A| * SINGLECLOSED(+A) = |A| .");
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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "|A| * SINGLECLOSED(+A) + |B| * SINGLECLOSED(+B) = 1.0 .", true);
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
                    coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "@Max[|A|, 0.0] * SINGLECLOSED(+A) = 1.0 .");
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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "@Max[1.0, 0.0] * SINGLECLOSED(+A) = 1.0 .");
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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "@Max[|A|, |B|] * SINGLECLOSED(+A) + 1.0 * SINGLECLOSED(+B) = 1.0 .", true);
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
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));
        AbstractArithmeticRule equalityTestRule = new UnweightedArithmeticRule(new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1)));

        assertEquals(rule, equalityTestRule);
        assertRule(rule, "@Min[1.0, 0.0] * SINGLECLOSED(A) = 1.0 .");
    }

    /**
     * Test a few instances where the hash should not match.
     * The rules will use at most one coefficient/atom so the hash ordering is consistent.
     */
    @Test
    public void testHash() {
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;

        // 1 * SingleClosed(A) = 1
        coefficients = Arrays.asList((Coefficient)(new ConstantNumber(1)));
        atoms = Arrays.asList((SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A"))));
        ArithmeticRuleExpression expression1 = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        // 0 * SingleClosed(A) = 1
        coefficients = Arrays.asList((Coefficient)(new ConstantNumber(0)));
        atoms = Arrays.asList((SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A"))));
        ArithmeticRuleExpression expression2 = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        // 1 * SingleClosed(A) = 0
        coefficients = Arrays.asList((Coefficient)(new ConstantNumber(1)));
        atoms = Arrays.asList((SummationAtomOrAtom)(new QueryAtom(singleClosed, new Variable("A"))));
        ArithmeticRuleExpression expression3 = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(0));

        // 1 * SingleOpened(A) = 1
        coefficients = Arrays.asList((Coefficient)(new ConstantNumber(1)));
        atoms = Arrays.asList((SummationAtomOrAtom)(new QueryAtom(singleOpened, new Variable("A"))));
        ArithmeticRuleExpression expression4 = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));

        assertNotEquals(expression1.hashCode(), expression2.hashCode());
        assertNotEquals(expression1.hashCode(), expression3.hashCode());
        assertNotEquals(expression1.hashCode(), expression4.hashCode());

        assertNotEquals(expression2.hashCode(), expression3.hashCode());
        assertNotEquals(expression2.hashCode(), expression4.hashCode());

        assertNotEquals(expression3.hashCode(), expression4.hashCode());
    }

    @Test
    public void testSimpleSplit() {
        // SingleClosed(+A) = 1 . {A: SingleClosed(A) || DoubleClosed(A, A) }
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"),
                new Disjunction(
                        new QueryAtom(singleClosed, new Variable("A")),
                        new QueryAtom(doubleClosed, new Variable("A"), new Variable("A"))
                )
        );

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
        AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);

        // Expected split:
        // SingleClosed(+A) = 1 . {A: SingleClosed(A) }
        // SingleClosed(+A) = 1 . {A: DoubleClosed(A, A) }
        String[] expected = new String[]{
            "1.0 * SINGLECLOSED(+A) = 1.0 .   {A : SINGLECLOSED(A)}",
            "1.0 * SINGLECLOSED(+A) = 1.0 .   {A : DOUBLECLOSED(A, A)}",
        };

        assertTrue(rule.requiresSplit());
        List<Rule> splitRules = rule.split();

        // Swap expected order if necessary.
        if (splitRules.get(0).toString().contains("DOUBLECLOSED")) {
            String temp = expected[0];
            expected[0] = expected[1];
            expected[1] = temp;
        }

        assertRules(splitRules.toArray(new Rule[0]), expected, false);
    }

    @Test
    public void testConjunctiveSplit() {
        // SingleClosed(+A) + SingleClosed(+B) = 1 . {A: SingleClosed(A) || DoubleClosed(A, A) } {B: SingleClosed(B)}
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1)),
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("A")})),
            (SummationAtomOrAtom)(new SummationAtom(singleClosed, new SummationVariableOrTerm[]{new SummationVariable("B")}))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("A"),
                new Disjunction(
                        new QueryAtom(singleClosed, new Variable("A")),
                        new QueryAtom(doubleClosed, new Variable("A"), new Variable("A"))
                )
        );
        filters.put(new SummationVariable("B"), new QueryAtom(singleClosed, new Variable("B")));

        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
        AbstractArithmeticRule rule = new UnweightedArithmeticRule(expression, filters);

        // Expected split:
        // SingleClosed(+A) = 1 . {A: SingleClosed(A) } {B: SingleClosed(B)}
        // SingleClosed(+A) = 1 . {A: DoubleClosed(A, A) } {B: SingleClosed(B)}
        String[] expected = new String[]{
            "1.0 * SINGLECLOSED(+A) + 1.0 * SINGLECLOSED(+B) = 1.0 .   {A : SINGLECLOSED(A)}   {B : SINGLECLOSED(B)}",
            "1.0 * SINGLECLOSED(+A) + 1.0 * SINGLECLOSED(+B) = 1.0 .   {A : DOUBLECLOSED(A, A)}   {B : SINGLECLOSED(B)}",
        };

        assertTrue(rule.requiresSplit());
        List<Rule> splitRules = rule.split();

        // Swap expected order if necessary.
        if (splitRules.get(0).toString().contains("DOUBLECLOSED")) {
            String temp = expected[0];
            expected[0] = expected[1];
            expected[1] = temp;
        }

        assertRules(splitRules.toArray(new Rule[0]), expected, true);
    }
}
