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
package org.linqs.psl.model.rule.arithmetic.expression.coefficient;

import java.util.Map;
import java.util.Set;

import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.MathUtils;

/**
 * A coefficient to represent division.
 * We will check for a divide by zero in three places:
 *  - On construction
 *  - When simplifying
 *  - when getting the actual value (using actual values)
 */
public class Divide extends BinaryCoefficient {
    public Divide(Coefficient c1, Coefficient c2) {
        super(c1, c2);

        // If the denoinator is 0, then throw an exception.
        if (c2 instanceof ConstantNumber && MathUtils.isZero(((ConstantNumber)c2).value)) {
            throw new ArithmeticException("Coefficient divides by zero");
        }
    }

    @Override
    public float getValue(Map<SummationVariable, Integer> subs) {
        float lhs = c1.getValue(subs);
        float rhs = c2.getValue(subs);

        // If the denoinator is 0, then throw an exception.
        if (MathUtils.isZero(rhs)) {
            throw new ArithmeticException("Coefficient divides by zero");
        }

        return lhs / rhs;
    }

    @Override
    public String toString() {
        return "(" + c1.toString() + " / " + c2.toString() + ")";
    }

    @Override
    public Coefficient simplify() {
        Coefficient lhs = c1.simplify();
        Coefficient rhs = c2.simplify();

        // If the denoinator is 0, then throw an exception.
        if (rhs instanceof ConstantNumber && MathUtils.isZero(((ConstantNumber)rhs).value)) {
            throw new ArithmeticException("Coefficient divides by zero");
        }

        // If the numerator is 0, then just return zero.
        if (lhs instanceof ConstantNumber && MathUtils.isZero(((ConstantNumber)lhs).value)) {
            return new ConstantNumber(0.0f);
        }

        // If the denoinator is 1, then just reutrn the numerator.
        if (rhs instanceof ConstantNumber && MathUtils.equals(((ConstantNumber)rhs).value, 1.0f)) {
            return lhs;
        }

        // If both sides are constants, then just do the math.
        if (lhs instanceof ConstantNumber && rhs instanceof ConstantNumber) {
            return new ConstantNumber(getValue(null));
        }

        return new Divide(lhs, rhs);
    }
}
