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

import org.linqs.psl.config.Config;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.MemoryTermStore;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.util.RandUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A general TermStore that handles terms and variables all in memory.
 * Variables are stored in an array along with their values.
 */
public abstract class MemoryVariableTermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements VariableTermStore<T, V> {
    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "memoryvariabletermstore";

    /**
     * Shuffle the terms before each return of iterator().
     */
    public static final String SHUFFLE_KEY = CONFIG_PREFIX + ".shuffle";
    public static final boolean SHUFFLE_DEFAULT = true;

    /**
     * The default size in terms of number of variables.
     */
    public static final String DEFAULT_SIZE_KEY = CONFIG_PREFIX + ".defaultsize";
    public static final int DEFAULT_SIZE_DEFAULT = 1000;

    // Keep an internal store to hold the terms while this class focuses on variables.
    private MemoryTermStore<T> store;

    // Keep track of variable indexes.
    private Map<V, Integer> variables;

    // Matching arrays for variables values and atoms.
    private float[] variableValues;
    private RandomVariableAtom[] variableAtoms;

    private boolean shuffle;
    private int defaultSize;

    public MemoryVariableTermStore() {
        shuffle = Config.getBoolean(SHUFFLE_KEY, SHUFFLE_DEFAULT);
        defaultSize = Config.getInt(DEFAULT_SIZE_KEY, DEFAULT_SIZE_DEFAULT);

        store = new MemoryTermStore<T>();
        ensureVariableCapacity(defaultSize);
    }

    @Override
    public int getVariableIndex(V variable) {
        return variables.get(variable).intValue();
    }

    @Override
    public float[] getVariableValues() {
        return variableValues;
    }

    @Override
    public void syncAtoms() {
        for (int i = 0; i < variables.size(); i++) {
            variableAtoms[i].setValue(variableValues[i]);
        }
    }

    @Override
    public int getNumVariables() {
        return variables.size();
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public synchronized V createLocalVariable(RandomVariableAtom atom) {
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
        variableValues[index] = RandUtils.nextFloat();
        variableAtoms[index] = atom;

        return variable;
    }

    /**
     * Make sure we allocate the right amount of memory for global variables.
     */
    @Override
    public void ensureVariableCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Variable capacity must be non-negative. Got: " + capacity);
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
    public T get(int index) {
        return store.get(index);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void ensureCapacity(int capacity) {
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

    protected abstract V convertAtomToVariable(RandomVariableAtom atom);
}
