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
import org.linqs.psl.model.predicate.model.ModelPredicate;
import org.linqs.psl.model.rule.GroundRule;

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
 * A general TermStore that handles terms and variables all in memory.
 * Variables are stored in an array along with their values.
 */
public abstract class MemoryVariableTermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> extends TermStore<T, V> {
    private static final Logger log = LoggerFactory.getLogger(MemoryVariableTermStore.class);

    // Keep an internal store to hold the terms while this class focuses on variables.
    private ArrayList<T> store;

    private boolean shuffle;

    private Set<ModelPredicate> modelPredicates;

    // Mirror variables need to track what terms they are involved in.
    // Because they are observed during MAP inference (and unobserved during supporting model fitting),
    // their values are integrated into the constants of terms.
    private Map<RandomVariableAtom, List<MirrorTermCoefficient>> mirrorVariables;

    private boolean variablesExternallyUpdatedFlag;

    public MemoryVariableTermStore() {
        super();

        shuffle = Options.MEMORY_VTS_SHUFFLE.getBoolean();

        store =  new ArrayList<T>((int)Options.MEMORY_TS_INITIAL_SIZE.getLong());

        modelPredicates = new HashSet<ModelPredicate>();
        mirrorVariables = new HashMap<RandomVariableAtom, List<MirrorTermCoefficient>>();

        variablesExternallyUpdatedFlag = false;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public synchronized V createLocalVariable(GroundAtom groundAtom) {
        if (!(groundAtom instanceof RandomVariableAtom)) {
            throw new IllegalArgumentException("MemoryVariableTermStores do not keep track of observed atoms (" + groundAtom + ").");
        }

        RandomVariableAtom atom = (RandomVariableAtom)groundAtom;
        if (variables.containsKey(atom)) {
            return  createVariableFromAtom(atom);
        }

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

        return createVariableFromAtom(atom);
    }

    private synchronized void createMirrorVariable(RandomVariableAtom atom, float coefficient, T term) {
        if (atom.getPredicate() instanceof ModelPredicate) {
            modelPredicates.add((ModelPredicate)atom.getPredicate());
        }

        if (!mirrorVariables.containsKey(atom)) {
            mirrorVariables.put(atom, new ArrayList<MirrorTermCoefficient>());
        }

        mirrorVariables.get(atom).add(new MirrorTermCoefficient(term, coefficient));
    }

    @Override
    public void variablesExternallyUpdated() {
        variablesExternallyUpdatedFlag = true;
    }

    /**
     * Check of the variables were updated externally.
     */
    public boolean getVariablesExternallyUpdatedFlag() {
        return variablesExternallyUpdatedFlag;
    }

    /**
     * Clear the flag for variables being updated externally.
     */
    public void resetVariablesExternallyUpdatedFlag() {
        variablesExternallyUpdatedFlag = false;
    }

    @Override
    public synchronized void addTerm(GroundRule rule, T term, Hyperplane<? extends ReasonerLocalVariable> hyperplane) {
        store.add(term);

        if (hyperplane.getIntegratedRVAs() != null) {
            for (Hyperplane.IntegratedRVA integratedRVA : hyperplane.getIntegratedRVAs()) {
                createMirrorVariable(integratedRVA.atom, integratedRVA.coefficient, term);
            }
        }
    }

    @Override
    public void clear() {
        super.clear();

        if (store != null) {
            store.clear();
        }

        if (modelPredicates != null) {
            modelPredicates.clear();
        }

        if (mirrorVariables != null) {
            mirrorVariables.clear();
        }
    }

    @Override
    public void close() {
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

    private void updateModelAtoms() {
        if (modelPredicates.size() == 0) {
            return;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.runModel();
        }

        double rmse = 0.0;

        int count = 0;
        for (RandomVariableAtom mirrorAtom : mirrorVariables.keySet()) {
            if (!(mirrorAtom.getPredicate() instanceof ModelPredicate)) {
                continue;
            }

            ModelPredicate predicate = (ModelPredicate)mirrorAtom.getPredicate();

            float oldValue = mirrorAtom.getValue();
            float newValue = predicate.getValue(mirrorAtom);
            mirrorAtom.setValue(newValue);

            for (MirrorTermCoefficient pair : mirrorVariables.get(mirrorAtom)) {
                pair.term.adjustConstant(pair.coefficient * oldValue, pair.coefficient * newValue);
            }

            rmse += Math.pow(newValue - predicate.getLabel(mirrorAtom), 2);
            count++;
        }

        if (count != 0) {
            rmse = Math.pow(rmse / count, 0.5);
        }

        log.trace("Batch update of {} model atoms. RMSE: {}", count, rmse);
        variablesExternallyUpdated();
    }

    private void initialFitModelAtoms() {
        for (ModelPredicate predicate : modelPredicates) {
            predicate.initialFit();
        }
    }

    private void fitModelAtoms() {
        if (modelPredicates.size() == 0) {
            return;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.resetLabels();
        }

        int count = 0;
        for (RandomVariableAtom mirrorAtom : mirrorVariables.keySet()) {
            if (!(mirrorAtom.getPredicate() instanceof ModelPredicate)) {
                continue;
            }

            // Get the value from the mirrored pair.
            // The conversion path looks like: RVA -> Mirror RVA -> Mirror Index -> Mirror Value
            float labelValue = variableValues[variables.get(mirrorAtom.getMirror()).intValue()];

            ((ModelPredicate)mirrorAtom.getPredicate()).setLabel(mirrorAtom, labelValue);
            count++;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.fit();
        }

        log.trace("Batch fit of {} model atoms.", count);
    }

    @Override
    public T getTerm(long index) {
        return store.get((int)index);
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public void ensureTermCapacity(long capacity) {
        assert((int)capacity >= 0);

        if (capacity == 0) {
            return;
        }

        store.ensureCapacity((int)capacity);
    }

    @Override
    public Iterator<T> iterator() {
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
     * Get the variable (V) representation of the atom.
     * This should be lightweight and may be called multiple times per atom.
     */
    protected abstract V createVariableFromAtom(RandomVariableAtom atom );

    /**
     * A term and coefficient (for that term) associated with a mirror variable.
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
