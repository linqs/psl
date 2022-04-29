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
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.model.ModelPredicate;
import org.linqs.psl.model.rule.GroundRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
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
public abstract class MemoryVariableTermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements VariableTermStore<T, V> {
    private static final Logger log = LoggerFactory.getLogger(MemoryVariableTermStore.class);

    // Keep an internal store to hold the terms while this class focuses on variables.
    private MemoryTermStore<T> store;

    // Keep track of variable indexes.
    private Map<V, Integer> variables;

    // Matching arrays for variables values and atoms.
    // A -1 will be stored if we need to go to the atom for the value.
    private float[] variableValues;
    private RandomVariableAtom[] variableAtoms;

    private boolean shuffle;
    private int defaultSize;

    private Set<ModelPredicate> modelPredicates;

    // Mirror variables need to track what terms they are involved in.
    // Because they are observed during MAP inference (and unobserved during supporting model fitting),
    // their values are integrated into the constants of terms.
    private Map<RandomVariableAtom, List<MirrorTermCoefficient>> mirrorVariables;

    private boolean variablesExternallyUpdatedFlag;

    public MemoryVariableTermStore() {
        shuffle = Options.MEMORY_VTS_SHUFFLE.getBoolean();
        defaultSize = Options.MEMORY_VTS_DEFAULT_SIZE.getInt();

        store = new MemoryTermStore<T>();
        ensureVariableCapacity(defaultSize);

        modelPredicates = new HashSet<ModelPredicate>();
        mirrorVariables = new HashMap<RandomVariableAtom, List<MirrorTermCoefficient>>();

        variablesExternallyUpdatedFlag = false;
    }

    @Override
    public int getVariableIndex(V variable) {
        return variables.get(variable).intValue();
    }

    @Override
    public float getVariableValue(int index) {
        return variableValues[index];
    }

    @Override
    public float[] getVariableValues() {
        return variableValues;
    }

    @Override
    public double syncAtoms() {
        double movement = 0.0;

        for (int i = 0; i < variables.size(); i++) {
            movement += Math.pow(variableAtoms[i].getValue() - variableValues[i], 2);
            variableAtoms[i].setValue(variableValues[i]);
        }

        return Math.sqrt(movement);
    }

    @Override
    public GroundAtom[] getVariableAtoms() {
        return variableAtoms;
    }

    @Override
    public int getNumVariables() {
        return variables.size();
    }

    @Override
    public int getNumRandomVariables() {
        return getNumVariables();
    }

    @Override
    public int getNumObservedVariables() {
        return 0;
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
        V variable = convertAtomToVariable(atom);
        if (variables.containsKey(variable)) {
            return variable;
        }

        // Got a new variable.

        if (variables.size() >= variableAtoms.length) {
            ensureVariableCapacity(variables.size() * 2);
        }

        int index = variables.size();

        variables.put(variable, index);
        variableValues[index] = atom.getValue();
        variableAtoms[index] = atom;

        return variable;
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
        store.variablesExternallyUpdated();
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

    /**
     * Make sure we allocate the right amount of memory for global variables.
     */
    @Override
    public void ensureVariableCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Variable capacity must be non-negative. Got: " + capacity);
        }

        if (capacity == 0) {
            return;
        }

        if (variables == null || variables.size() == 0) {
            // If there are no variables, then (re-)allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashMap<V, Integer>((int)Math.ceil(capacity / 0.75));

            variableValues = new float[capacity];
            variableAtoms = new RandomVariableAtom[capacity];
        } else if (variables.size() < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < variables.size() * 2) {
                capacity = variables.size() * 2;
            }

            // Reallocate and copy over variables.
            Map<V, Integer> newVariables = new HashMap<V, Integer>((int)Math.ceil(capacity / 0.75));
            newVariables.putAll(variables);
            variables = newVariables;

            variableValues = Arrays.copyOf(variableValues, capacity);
            variableAtoms = Arrays.copyOf(variableAtoms, capacity);
        }
    }

    @Override
    public Iterable<V> getVariables() {
        return variables.keySet();
    }

    @Override
    public void add(GroundRule rule, T term, Hyperplane hyperplane) {
        store.add(rule, term, hyperplane);

        if (hyperplane.getIntegratedRVAs() != null) {
            for (Object object : hyperplane.getIntegratedRVAs()) {
                // HACK(eriq): There is some strange typing issue here that I do not understand.
                //  Java thinks that the elements in hyperplane.getIntegratedRVAs() are Objects,
                //  and therefore will not implicitly cast to Hyperplane.IntegratedRVA.
                Hyperplane.IntegratedRVA integratedRVA = (Hyperplane.IntegratedRVA)object;
                createMirrorVariable(integratedRVA.atom, integratedRVA.coefficient, term);
            }
        }
    }

    @Override
    public void clear() {
        if (store != null) {
            store.clear();
        }

        if (variables != null) {
            variables.clear();
        }

        if (modelPredicates != null) {
            modelPredicates.clear();
        }

        if (mirrorVariables != null) {
            mirrorVariables.clear();
        }

        variableValues = null;
        variableAtoms = null;
    }

    @Override
    public void reset() {
        for (int i = 0; i < variables.size(); i++) {
            variableValues[i] = variableAtoms[i].getValue();
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
    public void initForOptimization() {
        initialFitModelAtoms();
        updateModelAtoms();
    }

    @Override
    public void iterationComplete() {
        fitModelAtoms();
        updateModelAtoms();
    }

    public RandomVariableAtom getAtom(int index) {
        return variableAtoms[index];
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
            // The conversion path looks like: RVA -> Mirror RVA -> Mirror V -> Mirror Index -> Mirror Value
            float labelValue = variableValues[variables.get(convertAtomToVariable(mirrorAtom.getMirror())).intValue()];

            ((ModelPredicate)mirrorAtom.getPredicate()).setLabel(mirrorAtom, labelValue);
            count++;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.fit();
        }

        log.trace("Batch fit of {} model atoms.", count);
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
    public Iterator<T> iterator() {
        if (shuffle) {
            store.shuffle();
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
    protected abstract V convertAtomToVariable(RandomVariableAtom atom);

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
