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
package org.linqs.psl.model.rule;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.DeepPredicate;

/**
 * A weight for a rule.
 * A weight is a constant value and can be associated with a GroundAtom.
 * The value of the weight is the constant value multiplied by the value of the GroundAtom.
 */
public class Weight {
    private float constantValue;
    private Atom atom;

    public Weight(float constantValue) {
        this.constantValue = constantValue;
        this.atom = null;
    }

    public Weight(float constantValue, Atom atom) {
        this.constantValue = constantValue;
        this.atom = atom;
    }

    /**
     * Returns the weight's value
     */
    public float getValue() {
        if (atom != null) {
            if (!(atom instanceof GroundAtom)) {
                throw new IllegalStateException("Called getValue() on weight with atom: " + atom + " before grounding. Atom must be a GroundAtom before it can be used in a Weight.");
            }

            if (!((GroundAtom) atom).isFixed()) {
                throw new IllegalStateException("Called getValue() on weight with non-fixed atom: " + atom + ". Atoms must be fixed (deep or observed) if they are used as weights.");
            }

            return constantValue * ((GroundAtom)atom).getValue();
        } else {
            return constantValue;
        }
    }

    public void setConstantValue(float value) {
        this.constantValue = value;
    }

    public float getConstantValue() {
        return constantValue;
    }

    public void setAtom(Atom atom) {
        this.atom = atom;
    }

    public Atom getAtom() {
        return atom;
    }

    /**
     * Returns whether the term is constant or if it is a function of an atom.
     */
    public boolean isConstant() {
        return atom == null;
    }

    public String toString() {
        if (atom != null) {
            return constantValue + " * " + atom;
        } else {
            return Float.toString(constantValue);
        }
    }
}
