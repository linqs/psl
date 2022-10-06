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
import org.linqs.psl.util.Logger;

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
    private static final Logger log = Logger.getLogger(MemoryVariableTermStore.class);

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

    private boolean variablesExternallyUpdatedFlag;

    public MemoryVariableTermStore() {
        shuffle = Options.MEMORY_VTS_SHUFFLE.getBoolean();
        defaultSize = Options.MEMORY_VTS_DEFAULT_SIZE.getInt();

        store = new MemoryTermStore<T>();
        ensureVariableCapacity(defaultSize);

        modelPredicates = new HashSet<ModelPredicate>();

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
            // TEST
            // predicate.updateAtoms(atomStore);
        }


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

        for (ModelPredicate predicate : modelPredicates) {
            predicate.fit();
        }
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
}
