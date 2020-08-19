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
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.streaming.StreamingIterator;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;
import org.linqs.psl.util.IteratorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Variables are kept in memory, but terms are kept on disk.
 */
public abstract class OnlineTermStore<T extends ReasonerTerm> extends StreamingTermStore<T> {
    public OnlineTermStore(List<Rule> rules, AtomManager atomManager,
            HyperplaneTermGenerator<T, GroundAtom> termGenerator) {
        super(rules, atomManager, termGenerator);
    }

    @Override
    public boolean isLoaded() {
        return !(initialRound || ((OnlineAtomManager)atomManager).hasNewAtoms());
    }

    @Override
    protected int estimateVariableCapacity() {
        return atomManager.getCachedRVACount() + atomManager.getCachedObsCount();
    }

    // Note(Charles): This number is unreliable once any online actions are processed.
    //  Because of the nature of streaming storage, we will not know what terms are deleted until we iterate over them.
    @Override
    public long size() {
        return seenTermCount;
    }

    public GroundAtom addAtom(StandardPredicate predicate, Constant[] arguments, float newValue, boolean readPartition) {
        if (((OnlineAtomManager)atomManager).hasAtom(predicate, arguments)) {
            deleteAtom(predicate, arguments);
        }

        GroundAtom atom = null;
        if (readPartition) {
            atom = ((OnlineAtomManager)atomManager).addObservedAtom(predicate, newValue, arguments);
        } else {
            atom = ((OnlineAtomManager)atomManager).addRandomVariableAtom((StandardPredicate) predicate, arguments);
        }

        createLocalVariable(atom);

        return atom;
    }

    public ObservedAtom observeAtom(StandardPredicate predicate, Constant[] arguments, float newValue) {
        QueryAtom atom = new QueryAtom(predicate, arguments);
        if (!variables.containsKey(atom)) {
            // Atom does not exist in current model.
            return null;
        }

        GroundAtom groundAtom = atomManager.getAtom(predicate, arguments);

        if (!(groundAtom instanceof RandomVariableAtom)) {
            // Atom does not exist in current model as random variable.
            return null;
        }

        // Delete the random variable atom from the atom manager.
        ((OnlineAtomManager)atomManager).deleteAtom(predicate, arguments);

        // Create observed atom with same predicates and arguments as the existing random variable atom.
        ObservedAtom observedAtom = ((OnlineAtomManager)atomManager).addObservedAtom(predicate, newValue, arguments);
        variableAtoms[getVariableIndex(observedAtom)] = observedAtom;
        variableValues[getVariableIndex(observedAtom)] = newValue;

        numRandomVariableAtoms--;
        numObservedAtoms++;

        return observedAtom;
    }

    public GroundAtom deleteAtom(StandardPredicate predicate, Constant[] arguments) {
        GroundAtom atom = ((OnlineAtomManager)atomManager).deleteAtom(predicate, arguments);

        if (atom == null) {
            // Atom never existed.
            return null;
        }

        int index = getVariableIndex(atom);
        if (index == -1) {
            // Atom never used in any terms.
            return atom;
        }

        variables.remove(atom);

        variableValues[index] = -1.0f;
        variableAtoms[index] = null;

        if (atom instanceof RandomVariableAtom) {
            numRandomVariableAtoms--;
        } else {
            numObservedAtoms--;
        }

        return atom;
    }

    public synchronized GroundAtom updateAtom(StandardPredicate predicate, Constant[] arguments, float newValue) {
        QueryAtom atom = new QueryAtom(predicate, arguments);
        if (!variables.containsKey(atom)) {
            return null;
        }

        GroundAtom groundAtom = atomManager.getAtom(predicate, arguments);
        variableValues[getVariableIndex(groundAtom)] = newValue;

        return groundAtom;
    }

    public void groundingIterationComplete(long termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        seenTermCount += termCount;

        this.numPages = numPages;
        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        initialRound = false;
        activeIterator = null;
    }

    /**
     * In addition to the typical behavior of setting values for random variable atoms,
     * also set the values for observed atoms.
     */
    @Override
    public void syncAtoms() {
        for (int i = 0; i < totalVariableCount; i++) {
            if (variableAtoms[i] == null) {
                continue;
            }

            if (variableAtoms[i] instanceof RandomVariableAtom) {
                ((RandomVariableAtom)variableAtoms[i]).setValue(variableValues[i]);
            } else {
                ((ObservedAtom)variableAtoms[i])._assumeValue(variableValues[i]);
            }
        }
    }

    @Override
    protected StreamingIterator<T> streamingIterator() {
        activeIterator = super.streamingIterator();

        // If there are new atoms, then we need to iterate through the cache and new groundings.
        if (!initialRound && ((OnlineAtomManager)atomManager).hasNewAtoms()) {
            activeIterator = new StreamingJoinIterator<T>(IteratorUtils.join(activeIterator, getGroundingIterator()));
        }

        return activeIterator;
    }

    /**
     * A thin wrapper around an Iterator to turn it into a StreamingIterator.
     * The internal iterator should call close() on itself when out of items.
     */
    private static class StreamingJoinIterator<E extends ReasonerTerm> implements StreamingIterator<E> {
        private Iterator<E> iterator;

        public StreamingJoinIterator(Iterator<E> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public E next() {
            return iterator.next();
        }

        @Override
        public void remove() {
            iterator.remove();
        }

        @Override
        public void close() {
        }
    }
}
