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
package org.linqs.psl.reasoner.term.online;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.OnlineHyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Variables are kept in memory, but terms are kept on disk.
 */
public abstract class OnlineStreamingTermStore<T extends ReasonerTerm> extends StreamingTermStore<T> {
    private static final Logger log = LoggerFactory.getLogger(OnlineStreamingTermStore.class);

    public static final int INITIAL_NEW_TERM_BUFFER_SIZE = 1000;

    // Keep track of observed indexes.
    protected Map<ObservedAtom, Integer> observeds;

    // Matching arrays for observed values and atoms.
    private float[] observedValues;
    private ObservedAtom[] observedAtoms;

    // Buffer to hold new terms
    protected List<T> newTermBuffer;

    protected OnlineStreamingIterator<T> activeIterator;

    protected OnlineHyperplaneTermGenerator<T, RandomVariableAtom> termGenerator;

    public OnlineStreamingTermStore(List<Rule> rules, AtomManager atomManager,
                                    OnlineHyperplaneTermGenerator<T, RandomVariableAtom> termGenerator) {
        super(rules, atomManager, null);

        this.termGenerator = termGenerator;
        ensureObservedCapacity(atomManager.getCachedObservedCount());
        newTermBuffer = new ArrayList<T>(INITIAL_NEW_TERM_BUFFER_SIZE);
    }

    public Iterable<ObservedAtom> getObserveds() { return observeds.keySet(); }

    public float[] getObservedValues() { return observedValues; }

    public float getObservedValue(int index) {
        return observedValues[index];
    }

    public int getObservedIndex(ObservedAtom observed) {
        return observeds.get(observed).intValue();
    }

    public synchronized ObservedAtom createLocalObserved(ObservedAtom atom) {
        if (observeds.containsKey(atom)) {
            return atom;
        }

        // Got a new variable.

        if (observeds.size() >= observedAtoms.length) {
            ensureObservedCapacity(observeds.size() * 2);
        }

        int index = observeds.size();

        observeds.put(atom, index);
        observedValues[index] = atom.getValue();
        observedAtoms[index] = atom;

        return atom;
    }

    public void ensureObservedCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Observed capacity must be non-negative. Got: " + capacity);
        }

        if (observeds == null || observeds.size() == 0) {
            // If there are no variables, then (re-)allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            observeds = new HashMap<ObservedAtom, Integer>((int)Math.ceil(capacity / 0.75));

            observedValues = new float[capacity];
            observedAtoms = new ObservedAtom[capacity];
        } else if (observeds.size() < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < observeds.size() * 2) {
                capacity = observeds.size() * 2;
            }

            // Reallocate and copy over variables.
            Map<ObservedAtom, Integer> newObservations = new HashMap<>((int)Math.ceil(capacity / 0.75));
            newObservations.putAll(observeds);
            observeds = newObservations;

            observedValues = Arrays.copyOf(observedValues, capacity);
            observedAtoms = Arrays.copyOf(observedAtoms, capacity);
        }
    }

    @Override
    public void add(GroundRule rule, T term) {
        this.add(term);
    }

    public void add(T term) {
        //Currently a hack, newTermBuffer should check if there is room
        seenTermCount = seenTermCount + 1;
        newTermBuffer.add(term);
    }

    /**
     * Get an iterator that will perform grounding queries and write the initial pages to disk.
     */
    protected abstract OnlineStreamingIterator<T> getInitialRoundIterator();

    /**
     * Get an iterator that will read and write from disk.
     */
    protected abstract OnlineStreamingIterator<T> getCacheIterator();

    /**
     * Get an iterator that will not write to disk.
     */
    protected abstract OnlineStreamingIterator<T> getNoWriteIterator();
}
