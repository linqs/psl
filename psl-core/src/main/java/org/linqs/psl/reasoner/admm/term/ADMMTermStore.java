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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.memory.MemoryTermStore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A TermStore specifically for ADMM terms.
 * A copy of the involved atoms are made for every term.
 */
public class ADMMTermStore extends MemoryTermStore<ADMMObjectiveTerm, LocalAtom> {
    /**
     * The local atom for each consensus atom (indexed by the consensus atom's index).
     */
    protected List<List<LocalAtom>> localAtoms;

    /**
     * The total number of all local atoms (the sum of the sizes of each list in |localAtoms|).
     */
    protected long numLocalAtoms;

    public ADMMTermStore() {
        super();

        localAtoms = new ArrayList<List<LocalAtom>>();
        ((ArrayList<List<LocalAtom>>)localAtoms).ensureCapacity(Options.MEMORY_VTS_DEFAULT_SIZE.getInt());
        numLocalAtoms = 0;
    }

    @Override
    public void atomsExternallyUpdated() {
        super.atomsExternallyUpdated();
        resetLocalAtoms();
    }


    @Override
    public void ensureAtomCapacity(int capacity) {
        super.ensureAtomCapacity(capacity);
    }

    public synchronized long getNumLocalAtoms() {
        return numLocalAtoms;
    }

    public int getNumConsensusAtoms() {
        return getNumAtoms();
    }

    @Override
    public synchronized LocalAtom createReasonerAtom(GroundAtom groundAtom) {
        if (!(groundAtom instanceof RandomVariableAtom)) {
            throw new IllegalArgumentException("ADMMTermStores do not keep track of observed atoms (" + groundAtom + ").");
        }

        RandomVariableAtom atom = (RandomVariableAtom)groundAtom;
        if (!atomIndexMap.containsKey(atom)) {
            // Got a new atom.

            if (atomIndexMap.size() >= atoms.length) {
                ensureAtomCapacity(atomIndexMap.size() * 2);
            }

            int index = atomIndexMap.size();

            atomIndexMap.put(atom, index);
            atomValues[index] = atom.getValue();
            atoms[index] = atom;
            totalAtomCount++;
            numRandomVariableAtoms++;
        }

        return createReasonerAtomFromAtom(atom);
    }

    protected synchronized LocalAtom createReasonerAtomFromAtom(RandomVariableAtom atom) {
        int consensusId = getAtomIndex(atom);

        // The underlying store should not give us an index that is more than one larger than the current highest.
        assert(consensusId <= localAtoms.size());

        if (consensusId == localAtoms.size()) {
            localAtoms.add(new ArrayList<LocalAtom>());
        }

        LocalAtom localAtom = createLocalAtomInternal(atom, consensusId, (float)atom.getValue());
        localAtoms.get(consensusId).add(localAtom);

        numLocalAtoms++;

        return localAtom;
    }

    protected LocalAtom createLocalAtomInternal(RandomVariableAtom atom, int consensusIndex, float value) {
        return new LocalAtom(consensusIndex, value);
    }

    protected synchronized void resetLocalAtoms() {
        for (int i = 0; i < getNumConsensusAtoms(); i++) {
            float value = getAtomValue(i);
            for (LocalAtom localAtom : localAtoms.get(i)) {
                localAtom.setValue(value);
                localAtom.setLagrange(0.0f);
            }
        }
    }

    /**
     * Get the local atoms.
     */
    public synchronized List<List<LocalAtom>> getLocalAtoms() {
        return localAtoms;
    }

    /**
     * Get the local atoms associated with the consensus atom index by the provided id.
     */
    public synchronized List<LocalAtom> getLocalAtoms(int consensusId) {
        return localAtoms.get(consensusId);
    }

    public float[] getConsensusValues() {
        return getAtomValues();
    }

    @Override
    public Iterator<ADMMObjectiveTerm> noWriteIterator() {
        return iterator();
    }

    @Override
    public void iterationComplete() {
        super.iterationComplete();

        if (getAtomsExternallyUpdatedFlag()) {
            atomsExternallyUpdated();
            resetAtomsExternallyUpdatedFlag();
        }
    }

    public boolean isLoaded() {
        return true;
    }

    @Override
    public void initForOptimization() {
        super.initForOptimization();

        if (getAtomsExternallyUpdatedFlag()) {
            atomsExternallyUpdated();
            resetAtomsExternallyUpdatedFlag();
        }
    }

    @Override
    public synchronized void close() {
        super.close();
        localAtoms = null;
    }

    @Override
    public void reset() {
        super.reset();
        resetLocalAtoms();
    }

    @Override
    public synchronized void clear() {
        super.clear();

        if (localAtoms != null) {
            localAtoms.clear();
        }

        numLocalAtoms = 0;
    }
}
