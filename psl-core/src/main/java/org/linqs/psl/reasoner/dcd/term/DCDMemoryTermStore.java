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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.MemoryTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.RandUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A TermStore specifically for DCD terms.
 */
public class DCDMemoryTermStore implements DCDTermStore {
    // Keep an internal store to hold the terms while this class focus on variables.
    private TermStore<DCDObjectiveTerm, RandomVariableAtom> store;

    private Set<RandomVariableAtom> variables;

    public DCDMemoryTermStore() {
        this(new MemoryTermStore<DCDObjectiveTerm>());
    }

    public DCDMemoryTermStore(TermStore<DCDObjectiveTerm, RandomVariableAtom> store) {
        this.store = store;
        variables = new HashSet<RandomVariableAtom>();
    }

    @Override
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
    @Override
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

    @Override
    public Iterable<RandomVariableAtom> getVariables() {
        return Collections.unmodifiableSet(variables);
    }

    @Override
    public void add(GroundRule rule, DCDObjectiveTerm term) {
        store.add(rule, term);
    }

    @Override
    public void clear() {
        if (store != null) {
            store.clear();
        }

        if (variables != null) {
            variables.clear();
        }
    }

    @Override
    public void close() {
        clear();

        if (store != null) {
            store.close();
            store = null;
        }

        variables = null;
    }

    @Override
    public DCDObjectiveTerm get(int index) {
        return store.get(index);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void ensureCapacity(int capacity) {
        store.ensureCapacity(capacity);
    }

    @Override
    public Iterator<DCDObjectiveTerm> iterator() {
        return store.iterator();
    }
}
