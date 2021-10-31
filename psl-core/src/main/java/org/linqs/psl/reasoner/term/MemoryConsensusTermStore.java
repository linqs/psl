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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.admm.term.LocalVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * A TermStore specifically for a consensus optimizer.
 * A copy of each involved variable is made for each term.
 */
public abstract class MemoryConsensusTermStore<T extends ReasonerTerm> implements TermStore<T, LocalVariable> {
    /**
     * An internal store to track the terms and consensus variables.
     */
    protected MemoryVariableTermStore<T, RandomVariableAtom> store;

    /**
     * The local variables for each consensus variables (indexed by the consensus variable's index).
     */
    protected List<List<LocalVariable>> localVariables;

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

        localVariables = new ArrayList<List<LocalVariable>>();
        numLocalVariables = 0;
    }

    @Override
    public void ensureTermCapacity(long capacity) {
        store.ensureTermCapacity(capacity);
    }

    @Override
    public void addTerm(GroundRule rule, T term, Hyperplane<? extends ReasonerLocalVariable> hyperplane) {
        store.addTerm(rule, term, hyperplane);
    }

    @Override
    public T getTerm(long index) {
        return store.getTerm(index);
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
        store.ensureVariableCapacity(capacity);
        ((ArrayList)localVariables).ensureCapacity(capacity);
    }

    @Override
    public int getNumAtoms() {
        return store.getNumAtoms();
    }

    @Override
    public int getNumRandomVariables() {
        return store.getNumRandomVariables();
    }

    @Override
    public int getNumObservedVariables() {
        return store.getNumObservedVariables();
    }

    public long getNumLocalVariables() {
        return numLocalVariables;
    }

    public int getNumConsensusVariables() {
        return store.getNumAtoms();
    }

    @Override
    public synchronized LocalVariable createLocalVariable(GroundAtom groundAtom) {
        if (!(groundAtom instanceof RandomVariableAtom)) {
            throw new IllegalArgumentException("MemoryConsensusTermStores do not keep track of observed atoms (" + groundAtom + ").");
        }

        RandomVariableAtom atom = (RandomVariableAtom)groundAtom;

        numLocalVariables++;

        store.createLocalVariable(atom);
        int consensusId = store.getAtomIndex(atom);

        // The underlying store should not give us an index that is more than one larger than the current highest.
        assert(consensusId <= localVariables.size());

        if (consensusId == localVariables.size()) {
            localVariables.add(new ArrayList<LocalVariable>());
        }

        LocalVariable localVariable = createLocalVariableInternal(atom, consensusId, (float)atom.getValue());
        localVariables.get(consensusId).add(localVariable);

        return localVariable;
    }

    protected abstract LocalVariable createLocalVariableInternal(RandomVariableAtom atom, int consensusIndex, float value);

    protected abstract void resetLocalVariables();

    /**
     * Get the local variables associated with the consensus variable index by the provided id.
     */
    public List<LocalVariable> getLocalVariables(int consensusId) {
        return localVariables.get(consensusId);
    }

    @Override
    public GroundAtom[] getAtoms() {
        return store.getAtoms();
    }

    @Override
    public GroundAtom getAtom(int index) {
        return store.getAtom(index);
    }

    @Override
    public int getAtomIndex(GroundAtom atom) {
        return store.getAtomIndex(atom);
    }

    @Override
    public float[] getAtomValues() {
        return store.getAtomValues();
    }

    public float[] getConsensusValues() {
        return store.getAtomValues();
    }

    @Override
    public float getAtomValue(int index) {
        return store.getAtomValue(index);
    }

    @Override
    public void variablesExternallyUpdated() {
        store.variablesExternallyUpdated();
    }

    @Override
    public double syncAtoms() {
        return store.syncAtoms();
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
    public void iterationComplete() {
        store.iterationComplete();

        if (store.getVariablesExternallyUpdatedFlag()) {
            variablesExternallyUpdated();
            store.resetVariablesExternallyUpdatedFlag();
        }
    }

    public boolean isLoaded() {
        return true;
    }

    @Override
    public void initForOptimization() {
        store.initForOptimization();

        if (store.getVariablesExternallyUpdatedFlag()) {
            variablesExternallyUpdated();
            store.resetVariablesExternallyUpdatedFlag();
        }
    }

    @Override
    public long size() {
        return store.size();
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
    public void reset() {
        resetLocalVariables();
        store.reset();
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
}
