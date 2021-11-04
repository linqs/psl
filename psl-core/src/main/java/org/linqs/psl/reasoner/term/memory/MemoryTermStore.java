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
package org.linqs.psl.reasoner.term.memory;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.model.ModelPredicate;
import org.linqs.psl.model.rule.GroundRule;

import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerAtom;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A general TermStore that handles terms and atoms all in memory.
 * Atoms are stored in an array along with their values.
 */
public abstract class MemoryTermStore<T extends ReasonerTerm, V extends ReasonerAtom> extends TermStore<T, V> {
    private static final Logger log = LoggerFactory.getLogger(MemoryTermStore.class);

    // The data structure containing all of the ReasonerTerms.
    private ArrayList<T> store;

    private boolean shuffle;

    private final Set<ModelPredicate> modelPredicates;

    // Mirror variables need to track what terms they are involved in.
    // Because they are observed during MAP inference (and unobserved during supporting model fitting),
    // their values are integrated into the constants of terms.
    private final Map<RandomVariableAtom, List<MirrorTermCoefficient>> mirrorAtoms;

    private boolean atomsExternallyUpdatedFlag;

    public MemoryTermStore() {
        super();

        shuffle = Options.MEMORY_VTS_SHUFFLE.getBoolean();

        store =  new ArrayList<T>((int)Options.MEMORY_TS_INITIAL_SIZE.getLong());

        modelPredicates = new HashSet<ModelPredicate>();
        mirrorAtoms = new HashMap<RandomVariableAtom, List<MirrorTermCoefficient>>();

        atomsExternallyUpdatedFlag = false;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public synchronized V createReasonerAtom(GroundAtom groundAtom) {
        if (!(groundAtom instanceof RandomVariableAtom)) {
            throw new IllegalArgumentException("MemoryVariableTermStores do not keep track of observed atoms (" + groundAtom + ").");
        }

        RandomVariableAtom atom = (RandomVariableAtom)groundAtom;
        if (atomIndexMap.containsKey(atom)) {
            return  createReasonerAtomFromAtom(atom);
        }

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

        return createReasonerAtomFromAtom(atom);
    }

    private synchronized void createMirrorAtom(RandomVariableAtom atom, float coefficient, T term) {
        if (atom.getPredicate() instanceof ModelPredicate) {
            modelPredicates.add((ModelPredicate)atom.getPredicate());
        }

        if (!mirrorAtoms.containsKey(atom)) {
            mirrorAtoms.put(atom, new ArrayList<MirrorTermCoefficient>());
        }

        mirrorAtoms.get(atom).add(new MirrorTermCoefficient(term, coefficient));
    }

    @Override
    public void atomsExternallyUpdated() {
        atomsExternallyUpdatedFlag = true;
    }

    /**
     * Check if the atoms were updated externally.
     */
    public boolean getAtomsExternallyUpdatedFlag() {
        return atomsExternallyUpdatedFlag;
    }

    /**
     * Clear the flag for atoms being updated externally.
     */
    public void resetAtomsExternallyUpdatedFlag() {
        atomsExternallyUpdatedFlag = false;
    }

    @Override
    public synchronized void addTerm(GroundRule rule, T term, Hyperplane<? extends ReasonerAtom> hyperplane) {
        store.add(term);

        if (hyperplane.getIntegratedRVAs() != null) {
            for (Hyperplane.IntegratedRVA integratedRVA : hyperplane.getIntegratedRVAs()) {
                createMirrorAtom(integratedRVA.atom, integratedRVA.coefficient, term);
            }
        }
    }

    @Override
    public synchronized void clear() {
        super.clear();

        if (store != null) {
            store.clear();
        }

        if (modelPredicates != null) {
            modelPredicates.clear();
        }

        if (mirrorAtoms != null) {
            mirrorAtoms.clear();
        }
    }

    @Override
    public synchronized void close() {
        super.close();

        if (store != null) {
            store = null;
        }
    }

    @Override
    public void initForOptimization() {
        initialFitModelAtoms();
        updateModelAtoms();
    }

    @Override
    public void iterationComplete() {
        fitModelAtoms();
        updateModelAtoms();
    }

    private synchronized void updateModelAtoms() {
        if (modelPredicates.size() == 0) {
            return;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.runModel();
        }

        double rmse = 0.0;

        int count = 0;
        for (Map.Entry<RandomVariableAtom, List<MirrorTermCoefficient>> mirrorAtomEntry : mirrorAtoms.entrySet()) {
            RandomVariableAtom mirrorAtom = mirrorAtomEntry.getKey();
            List<MirrorTermCoefficient> mirrorAtomCoefficients = mirrorAtomEntry.getValue();

            if (!(mirrorAtom.getPredicate() instanceof ModelPredicate)) {
                continue;
            }

            ModelPredicate predicate = (ModelPredicate)mirrorAtom.getPredicate();

            float oldValue = mirrorAtom.getValue();
            float newValue = predicate.getValue(mirrorAtom);
            mirrorAtom.setValue(newValue);

            for (MirrorTermCoefficient pair : mirrorAtomCoefficients) {
                pair.term.adjustConstant(pair.coefficient * oldValue, pair.coefficient * newValue);
            }

            rmse += Math.pow(newValue - predicate.getLabel(mirrorAtom), 2);
            count++;
        }

        if (count != 0) {
            rmse = Math.pow(rmse / count, 0.5);
        }

        log.trace("Batch update of {} model atoms. RMSE: {}", count, rmse);
        atomsExternallyUpdated();
    }

    private synchronized void initialFitModelAtoms() {
        for (ModelPredicate predicate : modelPredicates) {
            predicate.initialFit();
        }
    }

    private synchronized void fitModelAtoms() {
        if (modelPredicates.size() == 0) {
            return;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.resetLabels();
        }

        int count = 0;
        for (RandomVariableAtom mirrorAtom : mirrorAtoms.keySet()) {
            if (!(mirrorAtom.getPredicate() instanceof ModelPredicate)) {
                continue;
            }

            // Get the value from the mirrored pair.
            // The conversion path looks like: RVA -> Mirror RVA -> Mirror Index -> Mirror Value
            float labelValue = atomValues[atomIndexMap.get(mirrorAtom.getMirror()).intValue()];

            ((ModelPredicate)mirrorAtom.getPredicate()).setLabel(mirrorAtom, labelValue);
            count++;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.fit();
        }

        log.trace("Batch fit of {} model atoms.", count);
    }

    @Override
    public synchronized List<T> getTerms() {
        return store;
    }

    @Override
    public synchronized T getTerm(long index) {
        return store.get((int)index);
    }

    @Override
    public synchronized long size() {
        return store.size();
    }

    @Override
    public synchronized void ensureTermCapacity(long capacity) {
        assert((int)capacity >= 0);

        if (capacity == 0) {
            return;
        }

        store.ensureCapacity((int)capacity);
    }

    @Override
    public synchronized Iterator<T> iterator() {
        if (shuffle) {
            RandUtils.shuffle(store);
        }

        return store.iterator();
    }

    @Override
    public Iterator<T> noWriteIterator() {
        return iterator();
    }

    /**
     * Get the ReasonerAtom (V) representation of the atom.
     * This should be lightweight and may be called multiple times per atom.
     */
    protected abstract V createReasonerAtomFromAtom(RandomVariableAtom atom );

    /**
     * A term and coefficient (for that term) associated with a mirror atom.
     */
    private class MirrorTermCoefficient {
        public T term;
        public float coefficient;

        public MirrorTermCoefficient(T term, float coefficient) {
            this.term = term;
            this.coefficient = coefficient;
        }
    }
}
