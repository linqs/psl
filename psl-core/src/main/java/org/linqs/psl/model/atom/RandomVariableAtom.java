/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

/**
 * A GroundAtom with a truth value which can be modified.
 */
public class RandomVariableAtom extends GroundAtom {
    /**
     * Instantiation of GrondAtoms should typically be left to the Database so it can maintain a cache.
     */
    public RandomVariableAtom(StandardPredicate predicate, Constant[] args, float value, short partition) {
        super(predicate, args, value, partition);
        if (!(predicate instanceof DeepPredicate)) {
            this.fixed = false;
        }
    }

    @Override
    public RandomVariableAtom copy() {
        return new RandomVariableAtom((StandardPredicate)predicate, (Constant[]) arguments, value, partition);
    }

    @Override
    public StandardPredicate getPredicate() {
        return (StandardPredicate)predicate;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    /**
     * Sets the truth value of this Atom.
     */
    public void setValue(float value) {
        this.value = value;
    }
}
