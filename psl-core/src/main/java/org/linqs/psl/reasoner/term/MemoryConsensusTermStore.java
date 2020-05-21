/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * A TermStore specifically for a consensus optimizer.
 * A copy of each involved variable is made for each term.
 */
public abstract class MemoryConsensusTermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements TermStore<T, V> {
    /**
     * An internal store to track the terms and consensus variables.
     */
    protected MemoryVariableTermStore<T, RandomVariableAtom> store;

    /**
     * The local variables for each consensus variables (indexed by the consensus variable's index).
     */
    protected List<List<V>> localVariables;

    /**
     * The total number of all local variables (the sum of the sizes of each list in |localVariables|).
     */
    protected long numLocalVariables;

    public MemoryConsensusTermStore() {
        this.store = new MemoryVariableTermStore<T, RandomVariableAtom>() {
            protected RandomVariableAtom convertAtomToVariable(RandomVariableAtom atom) {
                return atom;
            }
        };

        localVariables = new ArrayList<List<V>>();
        numLocalVariables = 0;
    }

    @Override
    public synchronized V createLocalVariable(RandomVariableAtom atom) {
        numLocalVariables++;

        store.createLocalVariable(atom);
        int consensusId = store.getVariableIndex(atom);

        // The underlying store should not give us an index that is more than one larger than the current highest.
        assert(consensusId <= localVariables.size());

        if (consensusId == localVariables.size()) {
            localVariables.add(new ArrayList<V>());
        }

        V localVariable = createLocalVariableInternal(consensusId, (float)atom.getValue());
        localVariables.get(consensusId).add(localVariable);

        return localVariable;
    }

    public long getNumLocalVariables() {
        return numLocalVariables;
    }

    public int getNumConsensusVariables() {
        return store.getNumVariables();
    }

    public List<V> getLocalVariables(int consensusId) {
        return localVariables.get(consensusId);
    }

    public float[] getConsensusValues() {
        return store.getVariableValues();
    }

    public void syncAtoms() {
        store.syncAtoms();
    }

    @Override
    public void add(GroundRule rule, T term) {
        store.add(rule, term);
    }

    @Override
    public void clear() {
        if (store != null) {
            store.clear();
        }

        if (localVariables != null) {
            localVariables.clear();
        }

        numLocalVariables = 0;
    }

    @Override
    public void reset() {
        resetLocalVariables();
        store.reset();
    }

    @Override
    public void close() {
        clear();

        if (store != null) {
            store.close();
            store = null;
        }

        localVariables = null;
    }

    @Override
    public void initForOptimization() {
        store.initForOptimization();
    }

    @Override
    public void iterationComplete() {
        store.iterationComplete();
    }

    @Override
    public T get(long index) {
        return store.get(index);
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public void ensureCapacity(long capacity) {
        store.ensureCapacity(capacity);
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
        store.ensureVariableCapacity(capacity);
        ((ArrayList)localVariables).ensureCapacity(capacity);
    }

    @Override
    public void variablesExternallyUpdated() {
        store.variablesExternallyUpdated();
    }

    @Override
    public Iterator<T> iterator() {
        return store.iterator();
    }

    @Override
    public Iterator<T> noWriteIterator() {
        return iterator();
    }

    protected abstract V createLocalVariableInternal(int consensusIndex, float value);

    protected abstract void resetLocalVariables();
}
