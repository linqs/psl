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

public class ConstantNumber extends Coefficient {
    protected final float value;

    public ConstantNumber(float value) {
        this.value = value;
    }

    @Override
    public float getValue(Map<SummationVariable, Integer> subs) {
        return value;
    }

    @Override
    public String toString() {
        return Float.toString(value);
    }

    @Override
    public Coefficient simplify() {
        return this;
    }

    @Override
    public int hashCode() {
        // This method is allso sufficient for equals().
        return Float.floatToIntBits(value);
    }
}
