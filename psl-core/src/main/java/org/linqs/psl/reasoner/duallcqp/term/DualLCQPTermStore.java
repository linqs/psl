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
package org.linqs.psl.reasoner.duallcqp.term;

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.reasoner.term.TermState;

/**
 * A term store that manages both DualLCQPObjectiveTerms and DualLCQPAtoms.
 */
public class DualLCQPTermStore extends SimpleTermStore<DualLCQPObjectiveTerm> {
    // The array of DualLCQPAtoms is aligned with the atoms in the AtomStore.
    protected DualLCQPAtom[] dualLCQPAtoms;

    public DualLCQPTermStore(AtomStore atomStore) {
        super(atomStore, new DualLCQPTermGenerator());

        dualLCQPAtoms = new DualLCQPAtom[AtomStore.MIN_ALLOCATION];
        for (int i = 0; i < dualLCQPAtoms.length; i++) {
            dualLCQPAtoms[i] = new DualLCQPAtom();
        }
    }

    @Override
    public DualLCQPTermStore copy() {
        DualLCQPTermStore dualLCQPTermStoreCopy = new DualLCQPTermStore(atomStore.copy());

        for (DualLCQPObjectiveTerm term : allTerms) {
            dualLCQPTermStoreCopy.add(term.copy());
        }

        return dualLCQPTermStoreCopy;
    }

    private synchronized void init() {
        if (dualLCQPAtoms == null) {
            dualLCQPAtoms = new DualLCQPAtom[atomStore.size()];
            for (int i = 0; i < dualLCQPAtoms.length; i++) {
                dualLCQPAtoms[i] = new DualLCQPAtom();
            }
        }
    }

    @Override
    public void initForOptimization() {
        super.initForOptimization();

        init();
    }

    @Override
    public synchronized int add(ReasonerTerm term) {
        ensureDualLCQPAtomsCapacity();

        super.add(term);

        assert(term instanceof DualLCQPObjectiveTerm);
        DualLCQPObjectiveTerm newTerm = (DualLCQPObjectiveTerm) term;

        int[] atomIndexes = term.getAtomIndexes();
        float[] coefficients = term.getCoefficients();
        for (int i = 0; i < term.size(); i++) {
            dualLCQPAtoms[atomIndexes[i]].addTerm(newTerm, coefficients[i]);
        }

        return 1;
    }

    public DualLCQPAtom getDualLCQPAtom(int index) {
        return dualLCQPAtoms[index];
    }

    public DualLCQPAtom[] getDualLCQPAtoms() {
        return dualLCQPAtoms;
    }

    private synchronized void ensureDualLCQPAtomsCapacity() {
        if (dualLCQPAtoms == null) {
            init();
            return;
        }

        if (dualLCQPAtoms.length < atomStore.size()) {
            DualLCQPAtom[] newDualLCQPAtoms = new DualLCQPAtom[atomStore.size()];
            System.arraycopy(dualLCQPAtoms, 0, newDualLCQPAtoms, 0, dualLCQPAtoms.length);
            for (int i = dualLCQPAtoms.length; i < newDualLCQPAtoms.length; i++) {
                newDualLCQPAtoms[i] = new DualLCQPAtom();
            }
            dualLCQPAtoms = newDualLCQPAtoms;
        }
    }

    @Override
    public void loadState(TermState[] termStates) {
        for (int i = 0; i < size(); i++) {
            get(i).loadState(termStates[i]);
        }

        for (int i = 0; i < dualLCQPAtoms.length; i++) {
            dualLCQPAtoms[i].loadState(termStates[i + (int)size()]);
        }
    }

    @Override
    public TermState[] saveState() {
        TermState[] termStates = new TermState[(int)size() + dualLCQPAtoms.length];

        for (int i = 0; i < size(); i++) {
            termStates[i] = get(i).saveState();
        }

        for (int i = 0; i < dualLCQPAtoms.length; i++) {
            termStates[i + (int)size()] = dualLCQPAtoms[i].saveState();
        }

        return termStates;
    }

    @Override
    public void saveState(TermState[] termStates) {
        for (int i = 0; i < size(); i++) {
            get(i).saveState(termStates[i]);
        }

        for (int i = 0; i < dualLCQPAtoms.length; i++) {
            dualLCQPAtoms[i].saveState(termStates[i + (int)size()]);
        }
    }

    @Override
    public void clear() {
        super.clear();

        dualLCQPAtoms = null;
    }

    @Override
    public void close() {
        super.close();

        dualLCQPAtoms = null;
    }
}
