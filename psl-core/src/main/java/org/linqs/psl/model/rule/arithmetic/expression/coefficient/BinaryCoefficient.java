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
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.MathUtils;

public abstract class BinaryCoefficient extends Coefficient {
    protected final Coefficient c1;
    protected final Coefficient c2;

    public BinaryCoefficient(Coefficient c1, Coefficient c2) {
        this.c1 = c1;
        this.c2 = c2;
    }

    @Override
    public int hashCode() {
        return HashCode.build(HashCode.build(HashCode.build(this.getClass()), c1), c2);
    }

    @Override
    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }

        BinaryCoefficient otherBinary = (BinaryCoefficient)other;
        return this.c1.equals(otherBinary.c1) && this.c2.equals(otherBinary.c2);
    }
}
