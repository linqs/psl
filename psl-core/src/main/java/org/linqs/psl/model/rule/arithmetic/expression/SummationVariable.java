/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import java.io.Serializable;

/**
 * Special argument to a {@link Predicate} in an {@link SummationAtom}.
 * It is a placeholder for constants that are allowed to vary in a summation.
 * Note that SummationVariable is not a subclass of Variable.
 */
public class SummationVariable implements Serializable, SummationVariableOrTerm {
    private final Variable variable;

    public SummationVariable(String name) {
        variable = new Variable(name);
    }

    public Variable getVariable() {
        return variable;
    }

    @Override
    public String toString() {
        return "+" + variable.toString();
    }

    @Override
    public int hashCode() {
        return variable.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof SummationVariable)) {
            return false;
        }

        if (other == this) {
            return true;
        }

        return getVariable().equals(((SummationVariable)other).getVariable());
    }
}
