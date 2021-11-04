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
package org.linqs.psl.model.formula;

import org.linqs.psl.util.HashCode;

public class Conjunction extends AbstractBranchFormula<Conjunction> {
    public Conjunction(Formula... f) {
        super(f);

        hashcode = HashCode.build(hashcode, "&");
    }

    @Override
    public Formula getDNF() {
        // Get the DNF for all components of the conjunction and flatten them.
        Formula[] components = new Formula[length()];
        for (int i = 0; i < formulas.length; i++) {
            components[i] = formulas[i].getDNF().flatten();
        }

        // Take an extra step to merge conjunctions.
        // We already flattened each individual component, but not across components.
        components = ((Conjunction)(new Conjunction(components)).flatten()).formulas;

        // Distribute any disjunctions over the conjunctions.
        // We will favor clarity over performance for this code since we usually do not
        // have very large (> 10) rules and this is error-prone code.

        // Find the first disjunction.
        int disjunctionIndex = -1;
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof Disjunction) {
                disjunctionIndex = i;
                break;
            }
        }

        // If there is no disjunction, then we are already in DNF (since each component is in DNF).
        if (disjunctionIndex == -1) {
            return new Conjunction(components);
        }

        // Distribute the components of the first disjunction (at disjunctionIndex)
        // amongst all the other components of the conjunction.
        // ie. form a disjunction where each component is a conjunction all of the
        // components of the original conjunction (without the first disjunction) and
        // a single component from the disjunction.
        // A ^ B ^ (X v Y) -> (A ^ B ^ X) v (A ^ B ^ Y)

        // Note that we only need to deal with the first disjunction since we will break the conjunction into a
        // disjunction and call getDNF() on each component of the disjunction.
        // We will just need to make sure we flatten at the end.
        Disjunction firstDisjunction = (Disjunction)components[disjunctionIndex];

        Formula[] disjunctionComponents = new Formula[firstDisjunction.length()];

        for (int disjunctionComponentIndex = 0; disjunctionComponentIndex < disjunctionComponents.length; disjunctionComponentIndex++) {
            // Note that the size of each conjunction will actually be the same as the original conjunction.
            // (Removed the first disjunction, but added a component from that disjunction.)
            Formula[] conjunctionComponents = new Formula[components.length];
            for (int i = 0; i < components.length; i++) {
                if (i == disjunctionIndex) {
                    conjunctionComponents[i] = firstDisjunction.get(disjunctionComponentIndex);
                } else {
                    conjunctionComponents[i] = components[i];
                }
            }

            disjunctionComponents[disjunctionComponentIndex] = new Conjunction(conjunctionComponents).getDNF();
        }

        return new Disjunction(disjunctionComponents).flatten();
    }

    @Override
    protected String separatorString() {
        return "&";
    }
}
