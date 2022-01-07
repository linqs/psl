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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.util.RandUtils;

import java.util.ArrayList;
import java.util.Iterator;

public class MemoryTermStore<T extends ReasonerTerm> implements TermStore<T, GroundAtom> {
    private ArrayList<T> store;

    public MemoryTermStore() {
        this(Options.MEMORY_TS_INITIAL_SIZE.getLong());
    }

    public MemoryTermStore(long initialSize) {
        if (initialSize > Integer.MAX_VALUE) {
            throw new RuntimeException("Initial size (" + initialSize + ") too large for a MemoryTermStore, consider a streaming method.");
        }

        store = new ArrayList<T>((int)initialSize);
    }

    @Override
    public synchronized void add(GroundRule rule, T term, Hyperplane hyperplane) {
        store.add(term);
    }

    @Override
    public void clear() {
        if (store != null) {
            store.clear();
        }
    }

    @Override
    public void reset() {
        // Nothing is required for a MemoryTermStore to reset.
    }

    @Override
    public void close() {
        clear();

        store = null;
    }

    @Override
    public void initForOptimization() {
    }

    @Override
    public void iterationComplete() {
    }

    @Override
    public T get(long index) {
        // This case is safe, since for this to fail either the user is already out of bounds
        // or an exception would have been thrown when the container grew this large.
        return store.get((int)index);
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public void ensureCapacity(long capacity) {
        assert((int)capacity >= 0);

        if (capacity == 0) {
            return;
        }

        store.ensureCapacity((int)capacity);
    }

    @Override
    public Iterator<T> iterator() {
        return store.iterator();
    }

    @Override
    public Iterator<T> noWriteIterator() {
        return iterator();
    }

    @Override
    public GroundAtom createLocalVariable(GroundAtom atom) {
        return atom;
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
    }

    @Override
    public void variablesExternallyUpdated() {
    }

    @Override
    public double syncAtoms() {
        return 0.0;
    }

    public void shuffle() {
        RandUtils.shuffle(store);
    }
}
