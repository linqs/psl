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

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.LocalVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * A TermStore specifically for a consensus optimizer.
 * A copy of each involved variable is made for each term.
 */
public abstract class MemoryConsensusTermStore<T extends ReasonerTerm> extends MemoryVariableTermStore<T, LocalVariable> {
    /**
     * The local variables for each consensus variables (indexed by the consensus variable's index).
     */
    protected List<List<LocalVariable>> localVariables;

    /**
     * The total number of all local variables (the sum of the sizes of each list in |localVariables|).
     */
    protected long numLocalVariables;

    public MemoryConsensusTermStore() {
        super();

        localVariables = new ArrayList<List<LocalVariable>>();
        ((ArrayList)localVariables).ensureCapacity(Options.MEMORY_VTS_DEFAULT_SIZE.getInt());
        numLocalVariables = 0;
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
        super.ensureVariableCapacity(capacity);
    }

    public synchronized long getNumLocalVariables() {
        return numLocalVariables;
    }

    public int getNumConsensusVariables() {
        return getNumAtoms();
    }

    @Override
    public synchronized LocalVariable createLocalVariable(GroundAtom groundAtom) {
        if (!(groundAtom instanceof RandomVariableAtom)) {
            throw new IllegalArgumentException("MemoryConsensusTermStores do not keep track of observed atoms (" + groundAtom + ").");
        }

        RandomVariableAtom atom = (RandomVariableAtom)groundAtom;
        if (!variables.containsKey(atom)) {
            // Got a new variable.

            if (variables.size() >= variableAtoms.length) {
                ensureVariableCapacity(variables.size() * 2);
            }

            int index = variables.size();

            variables.put(atom, index);
            variableValues[index] = atom.getValue();
            variableAtoms[index] = atom;
            totalVariableCount++;
            numRandomVariableAtoms++;
        }

        return createVariableFromAtom(atom);
    }

    protected synchronized LocalVariable createVariableFromAtom(RandomVariableAtom atom) {
        int consensusId = getAtomIndex(atom);

        // The underlying store should not give us an index that is more than one larger than the current highest.
        assert(consensusId <= localVariables.size());

        if (consensusId == localVariables.size()) {
            localVariables.add(new ArrayList<LocalVariable>());
        }

        LocalVariable localVariable = createLocalVariableInternal(atom, consensusId, (float)atom.getValue());
        localVariables.get(consensusId).add(localVariable);

        numLocalVariables++;

        return localVariable;
    }

    protected abstract LocalVariable createLocalVariableInternal(RandomVariableAtom atom, int consensusIndex, float value);

    protected abstract void resetLocalVariables();

    /**
     * Get the local variables.
     */
    public synchronized List<List<LocalVariable>> getLocalVariables() {
        return localVariables;
    }

    /**
     * Get the local variables associated with the consensus variable index by the provided id.
     */
    public synchronized List<LocalVariable> getLocalVariables(int consensusId) {
        return localVariables.get(consensusId);
    }

    public float[] getConsensusValues() {
        return getAtomValues();
    }

    @Override
    public Iterator<T> noWriteIterator() {
        return iterator();
    }

    @Override
    public void iterationComplete() {
        super.iterationComplete();

        if (getVariablesExternallyUpdatedFlag()) {
            variablesExternallyUpdated();
            resetVariablesExternallyUpdatedFlag();
        }
    }

    public boolean isLoaded() {
        return true;
    }

    @Override
    public void initForOptimization() {
        super.initForOptimization();

        if (getVariablesExternallyUpdatedFlag()) {
            variablesExternallyUpdated();
            resetVariablesExternallyUpdatedFlag();
        }
    }

    @Override
    public synchronized void close() {
        super.close();
        localVariables = null;
    }

    @Override
    public void reset() {
        super.reset();
        resetLocalVariables();
    }

    @Override
    public synchronized void clear() {
        super.clear();

        if (localVariables != null) {
            localVariables.clear();
        }

        numLocalVariables = 0;
    }
}
