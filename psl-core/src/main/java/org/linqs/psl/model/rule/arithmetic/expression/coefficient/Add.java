/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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

public class Add extends BinaryCoefficient {
    public Add(Coefficient c1, Coefficient c2) {
        super(c1, c2);
    }

    @Override
    public float getValue(Map<SummationVariable, Integer> subs) {
        return c1.getValue(subs) + c2.getValue(subs);
    }

    @Override
    public String toString() {
        return "(" + c1.toString() + " + " + c2.toString() + ")";
    }

    @Override
    public Coefficient simplify() {
        Coefficient lhs = c1.simplify();
        Coefficient rhs = c2.simplify();

        // If both sides are constants, then just do the math.
        if (lhs instanceof ConstantNumber && rhs instanceof ConstantNumber) {
            return new ConstantNumber(getValue(null));
        }

        // If one side is a zero, then just return the other.
        if (lhs instanceof ConstantNumber && MathUtils.isZero(((ConstantNumber)lhs).value)) {
            return rhs;
        }

        if (rhs instanceof ConstantNumber && MathUtils.isZero(((ConstantNumber)rhs).value)) {
            return lhs;
        }

        return new Add(lhs, rhs);
    }
}
