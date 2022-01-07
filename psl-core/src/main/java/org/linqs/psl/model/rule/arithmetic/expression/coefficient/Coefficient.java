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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.term.Constant;

/**
 * Numeric coefficient in a {@link ArithmeticRuleExpression}.
 *
 * Coefficient and its subclasses are composable to represent complex definitions.
 * Its subclasses are defined as inner classes, because there are
 * a lot of them and they are simple.
 *
 * All coefficients should define a hashCode(), as it will be used in equality checks.
 */
public abstract class Coefficient implements Serializable {
    /**
     * Get the value of a coefficient (which may require a reqursive descent).
     * For performance reasons, instead of passing the full subtitution set to this method,
     * we are only passing the number of substitutions there are.
     * This may need to change in the future,
     * but the cost is too high to justify unless it is necessary.
     */
    public abstract float getValue(Map<SummationVariable, Integer> subs);

    /**
     * Get a simplified version of this Coefficient, the Coefficient itself if it cannot be simplified further.
     */
    public abstract Coefficient simplify();

    public abstract int hashCode();

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        return this.hashCode() == ((Coefficient)other).hashCode();
    }
}
