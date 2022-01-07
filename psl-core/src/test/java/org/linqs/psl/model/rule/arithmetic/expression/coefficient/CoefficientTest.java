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

import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.util.MathUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CoefficientTest extends PSLBaseTest {
    @Test
    // Avoid using cardinality so all end coefficients will be constants.
    public void testSimplifyWithoutCardinality() {
        Coefficient[] raw = new Coefficient[]{
            new Add(new ConstantNumber(1), new ConstantNumber(2)),
            new Add(new ConstantNumber(0), new ConstantNumber(3)),
            new Add(new ConstantNumber(4), new ConstantNumber(0)),

            new Subtract(new ConstantNumber(5), new ConstantNumber(6)),
            new Subtract(new ConstantNumber(6), new ConstantNumber(5)),
            new Subtract(new ConstantNumber(0), new ConstantNumber(7)),
            new Subtract(new ConstantNumber(8), new ConstantNumber(0)),

            new Multiply(new ConstantNumber(9), new ConstantNumber(10)),
            new Multiply(new ConstantNumber(1), new ConstantNumber(11)),
            new Multiply(new ConstantNumber(12), new ConstantNumber(1)),
            new Multiply(new ConstantNumber(0), new ConstantNumber(13)),
            new Multiply(new ConstantNumber(14), new ConstantNumber(0)),

            new Divide(new ConstantNumber(15), new ConstantNumber(3)),
            new Divide(new ConstantNumber(16), new ConstantNumber(1)),
            new Divide(new ConstantNumber(0), new ConstantNumber(17)),

            new Min(new ConstantNumber(18), new ConstantNumber(19)),
            new Min(new ConstantNumber(19), new ConstantNumber(18)),

            new Max(new ConstantNumber(20), new ConstantNumber(21)),
            new Max(new ConstantNumber(21), new ConstantNumber(20)),

            new Min(
            new Add(
               new Multiply(new ConstantNumber(4), new ConstantNumber(3)),
               new Divide(new ConstantNumber(15), new ConstantNumber(3))
            ),
            new Subtract(
               new ConstantNumber(100),
               new Max(new ConstantNumber(1), new ConstantNumber(2))
            )
         )
        };

        double[] expected = new double[]{
         // Add
            3,
            3,
            4,
         // Subtract
            -1,
         1,
         -7,
         8,
         // Multiply
            90,
         11,
         12,
         0,
         0,
         // Divide
         5,
         16,
         0,
         // Min
         18,
         18,
         // Max
         21,
         21,
         // Complex
         17
        };

        for (int i = 0; i < raw.length; i++) {
            Coefficient simple = raw[i].simplify();
         if (!(simple instanceof ConstantNumber)) {
            fail(String.format("Expecting a ConstantNumber, found a %s.", simple.getClass().getName()));
         }

            double actual = ((ConstantNumber)simple).value;
            assertEquals(
                String.format("Value mismatch on coefficient %d (%s). Expected [%f], found [%f]", i, raw[i], expected[i], actual),
                expected[i],
                actual,
            MathUtils.EPSILON
            );
        }
    }
}
