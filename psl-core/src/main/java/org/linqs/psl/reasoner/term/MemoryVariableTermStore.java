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

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.model.ModelPredicate;
import org.linqs.psl.model.rule.GroundRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A general TermStore that handles terms and variables all in memory.
 * Variables are stored in an array along with their values.
 */
public abstract class MemoryVariableTermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements AtomTermStore<T, V> {
    private static final Logger log = LoggerFactory.getLogger(MemoryVariableTermStore.class);

    // Keep an internal store to hold the terms while this class focuses on variables.
    private MemoryTermStore<T> store;

    // Keep track of variable indexes.
    private Map<V, Integer> variables;
    private ArrayList<V> variableArrayList;

    // Matching arrays for variables values and atoms.
    // A -1 will be stored if we need to go to the atom for the value.
    private float[] variableValues;
    private RandomVariableAtom[] variableAtoms;

    private boolean shuffle;
    private int defaultSize;

    private Set<ModelPredicate> modelPredicates;

    public MemoryVariableTermStore() {
        shuffle = Options.MEMORY_VTS_SHUFFLE.getBoolean();
        defaultSize = Options.MEMORY_VTS_DEFAULT_SIZE.getInt();

        store = new MemoryTermStore<T>();
        ensureAtomCapacity(defaultSize);

        modelPredicates = new HashSet<ModelPredicate>();
    }

    @Override
    public int getAtomIndex(V variable) {
        return variables.get(variable).intValue();
    }

    @Override
    public float getAtomValue(int index) {
        return variableValues[index];
    }

    @Override
    public float[] getAtomValues() {
        return variableValues;
    }

    @Override
    public void syncAtoms() {
        for (int i = 0; i < variables.size(); i++) {
            variableAtoms[i].setValue(variableValues[i]);
        }
    }

    @Override
    public int getNumAtoms() {
        return variables.size();
    }

    @Override
    public boolean writeIterator() {
        return false;
    }

    @Override
    public synchronized V createLocalAtom(GroundAtom atom) {
        if (atom instanceof RandomVariableAtom){
            return createLocalAtom((RandomVariableAtom) atom) ;
        } else {
            // Memory variable termstore does not keep track of observations
            return null;
        }
    }

    private synchronized V createLocalAtom(RandomVariableAtom atom) {
        V variable = convertAtomToVariable(atom);

        if (variables.containsKey(variable)) {
            return variable;
        }

        // Got a new variable.
        if (variables.size() >= variableAtoms.length) {
            ensureAtomCapacity(variables.size() * 2);
        }

        int index = variables.size();

        variables.put(variable, index);
        variableArrayList.add(index, variable);
        variableValues[index] = atom.getValue();
        variableAtoms[index] = (RandomVariableAtom)atom;

        if (atom.getPredicate() instanceof ModelPredicate) {
            modelPredicates.add((ModelPredicate)atom.getPredicate());
        }

        return variable;
    }

    /**
     * Make sure we allocate the right amount of memory for global variables.
     */
    @Override
    public void ensureAtomCapacity(int capacity) {
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
            variableArrayList = new ArrayList<V>(capacity);
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
    public ArrayList<V> getAtoms() {
        return variableArrayList;
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

        if (variables != null) {
            variables.clear();
        }

        if (modelPredicates != null) {
            modelPredicates.clear();
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
        for (int i = 0; i < variables.size(); i++) {
            if (variableAtoms[i].getPredicate() instanceof ModelPredicate) {
                ModelPredicate predicate = (ModelPredicate)variableAtoms[i].getPredicate();
                variableValues[i] = predicate.getValue(variableAtoms[i]);
                rmse += Math.pow(variableValues[i] - predicate.getLabel(variableAtoms[i]), 2);
                count++;
            }
        }

        if (count != 0) {
            rmse = Math.pow(rmse / count, 0.5);
        }

        log.trace("Batch update of {} model atoms. RMSE: {}", count, rmse);
    }

    private void fitModelAtoms() {
        if (modelPredicates.size() == 0) {
            return;
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.resetLabels();
        }

        int count = 0;
        for (int i = 0; i < variables.size(); i++) {
            if (variableAtoms[i].getPredicate() instanceof ModelPredicate) {
                ((ModelPredicate)variableAtoms[i].getPredicate()).setLabel(variableAtoms[i], variableValues[i]);
                count++;
            }
        }

        for (ModelPredicate predicate : modelPredicates) {
            predicate.fit();
        }

        log.trace("Batch fit of {} model atoms.", count);
    }

    @Override
    public T get(int index) {
        return store.get(index);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void ensureTermCapacity(int capacity) {
        store.ensureTermCapacity(capacity);
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

    protected abstract V convertAtomToVariable(RandomVariableAtom atom);

}
