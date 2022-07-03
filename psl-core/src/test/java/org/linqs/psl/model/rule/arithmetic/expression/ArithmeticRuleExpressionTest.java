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
package org.linqs.psl.model.rule.arithmetic.expression;

import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ArithmeticRuleExpressionTest extends PSLBaseTest {
    @Test
    public void testHash() {
        TestModel.ModelInformation model = TestModel.getModel();

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

        ArithmeticRuleExpression expression1 = new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1));
        ArithmeticRuleExpression expression2 = new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1));

        assertEquals(expression1.hashCode(), expression2.hashCode());
        assertEquals(expression1, expression2);
    }
}
