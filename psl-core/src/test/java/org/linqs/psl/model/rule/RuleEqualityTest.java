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
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
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
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RuleEqualityTest extends PSLBaseTest {
    private DataStore dataStore;

    private StandardPredicate singlePredicate;
    private StandardPredicate doublePredicate;

    private ArithmeticRuleExpression arithmeticBaseRule;
    private Formula logicalBaseRule;

    @Before
    public void setup() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, this.getClass().getName(), true));

        // Predicates
        singlePredicate = StandardPredicate.get("SinglePredicate", ConstantType.UniqueStringID);
        dataStore.registerPredicate(singlePredicate);

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
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(singlePredicate, new Variable("B")))
        );

        // Base Rule: SinglePredicate(A) + SinglePredicate(B) = 1
        arithmeticBaseRule = new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1));
    }

    @Test
    public void testLogicalBase() {
        assertEquals(new UnweightedLogicalRule(logicalBaseRule), new UnweightedLogicalRule(logicalBaseRule));
        assertEquals(new WeightedLogicalRule(logicalBaseRule, 1.0f, true), new WeightedLogicalRule(logicalBaseRule, 1.0f, true));
        assertEquals(new WeightedLogicalRule(logicalBaseRule, 1.0f, false), new WeightedLogicalRule(logicalBaseRule, 1.0f, false));

        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 1.0f, true), new WeightedLogicalRule(logicalBaseRule, 1.0f, false));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 1.0f, true), new WeightedLogicalRule(logicalBaseRule, 0.0f, false));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 1.0f, false), new WeightedLogicalRule(logicalBaseRule, 1.0f, true));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 0.0f, false), new WeightedLogicalRule(logicalBaseRule, 1.0f, true));

        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 5.0f, true), new WeightedLogicalRule(logicalBaseRule, 1.0f, false));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 5.0f, true), new WeightedLogicalRule(logicalBaseRule, 5.0f, false));

        assertNotEquals(new UnweightedLogicalRule(logicalBaseRule), new WeightedLogicalRule(logicalBaseRule, 1.0f, true));
        assertNotEquals(new UnweightedLogicalRule(logicalBaseRule), new WeightedLogicalRule(logicalBaseRule, 1.0f, false));
        assertNotEquals(new UnweightedLogicalRule(logicalBaseRule), new WeightedLogicalRule(logicalBaseRule, 0.0f, true));
        assertNotEquals(new UnweightedLogicalRule(logicalBaseRule), new WeightedLogicalRule(logicalBaseRule, 0.0f, false));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 1.0f, true), new UnweightedLogicalRule(logicalBaseRule));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 1.0f, false), new UnweightedLogicalRule(logicalBaseRule));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 0.0f, false), new UnweightedLogicalRule(logicalBaseRule));
        assertNotEquals(new WeightedLogicalRule(logicalBaseRule, 0.0f, false), new UnweightedLogicalRule(logicalBaseRule));
    }

    @Test
    public void testArithmeticBase() {
        assertEquals(new UnweightedArithmeticRule(arithmeticBaseRule), new UnweightedArithmeticRule(arithmeticBaseRule));
        assertEquals(new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true));
        assertEquals(new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, false), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, false));

        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, false));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true), new WeightedArithmeticRule(arithmeticBaseRule, 0.0f, false));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, false), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 0.0f, false), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true));

        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 5.0f, true), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, false));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 5.0f, true), new WeightedArithmeticRule(arithmeticBaseRule, 5.0f, false));

        assertNotEquals(new UnweightedArithmeticRule(arithmeticBaseRule), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true));
        assertNotEquals(new UnweightedArithmeticRule(arithmeticBaseRule), new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, false));
        assertNotEquals(new UnweightedArithmeticRule(arithmeticBaseRule), new WeightedArithmeticRule(arithmeticBaseRule, 0.0f, true));
        assertNotEquals(new UnweightedArithmeticRule(arithmeticBaseRule), new WeightedArithmeticRule(arithmeticBaseRule, 0.0f, false));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, true), new UnweightedArithmeticRule(arithmeticBaseRule));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 1.0f, false), new UnweightedArithmeticRule(arithmeticBaseRule));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 0.0f, false), new UnweightedArithmeticRule(arithmeticBaseRule));
        assertNotEquals(new WeightedArithmeticRule(arithmeticBaseRule, 0.0f, false), new UnweightedArithmeticRule(arithmeticBaseRule));
    }

    @After
    public void cleanup() {
        dataStore.close();
    }
}
