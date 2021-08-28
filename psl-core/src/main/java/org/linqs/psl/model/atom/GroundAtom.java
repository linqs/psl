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
package org.linqs.psl.model.atom;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.term.ReasonerLocalVariable;

/**
 * An Atom with only {@link Constant GroundTerms} for arguments.
 *
 * A GroundAtom has a truth value.
 */
public abstract class GroundAtom extends Atom implements Comparable<GroundAtom>, FunctionTerm, ReasonerLocalVariable {
    protected float value;

    protected GroundAtom(Predicate predicate, Constant[] args, float value) {
        super(predicate, args);
        this.value = value;
    }

    @Override
    public Constant[] getArguments() {
        return (Constant[])arguments;
    }

    /**
     * @return the truth value of this Atom
     */
    @Override
    public float getValue() {
        return value;
    }

    @Override
    public boolean isLinear() {
        return true;
    }

    public String toStringWithValue() {
        return super.toString() + " = " + getValue();
    }

    public VariableTypeMap collectVariables(VariableTypeMap varMap) {
        // No Variables in GroundAtoms.
        return varMap;
    }

    /**
     * First order by value (descending), the predicate name (natural),
     * and then the arguments (in order).
     */
    @Override
    public int compareTo(GroundAtom other) {
        int comparisonResult = Double.compare(other.value, this.value);
        if (comparisonResult != 0) {
            return comparisonResult;
        }

        int val = this.predicate.getName().compareTo(other.predicate.getName());
        if (val != 0) {
            return val;
        }

        for (int i = 0; i < this.arguments.length; i++) {
            val = this.arguments[i].compareTo(other.arguments[i]);
            if (val != 0) {
                return val;
            }
        }

        return 0;
    }
}
