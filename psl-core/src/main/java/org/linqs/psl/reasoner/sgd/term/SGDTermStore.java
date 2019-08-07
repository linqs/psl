/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.reasoner.term.MemoryTermStore;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.MemoryTermStore;
import org.linqs.psl.util.RandUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A TermStore specifically for SDG terms.
 */
public class SGDTermStore extends MemoryTermStore<SGDObjectiveTerm> {
    private Set<RandomVariableAtom> variables;

    public SGDTermStore() {
        variables = new HashSet<RandomVariableAtom>();
    }

    public int getNumVariables() {
        return variables.size();
    }

    @Override
    public synchronized RandomVariableAtom createLocalVariable(RandomVariableAtom atom) {
        if (variables.contains(atom)) {
            return atom;
        }

        atom.setValue(RandUtils.nextFloat());
        variables.add(atom);

        return atom;
    }

    /**
     * Make sure we allocate the right amount of memory for global variables.
     */
    public void ensureVariableCapacity(int capacity) {
        if (capacity == 0) {
            return;
        }

        if (variables.size() == 0) {
            // If there are no variables, then re-allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashSet<RandomVariableAtom>((int)Math.ceil(capacity / 0.75));
        }
    }

    public Iterable<RandomVariableAtom> getVariables() {
        return Collections.unmodifiableSet(variables);
    }

    @Override
    public void clear() {
        super.clear();

        if (variables != null) {
            variables.clear();
        }
    }

    @Override
    public void close() {
        super.close();
        variables = null;
    }
}
