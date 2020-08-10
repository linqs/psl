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
    protected boolean[] deletedAtoms;

    public OnlineTermStore(List<Rule> rules, AtomManager atomManager,
            HyperplaneTermGenerator<T, GroundAtom> termGenerator) {
        super(rules, atomManager, termGenerator);
    }

    @Override
    public boolean isLoaded() {
        return !(initialRound || ((OnlineAtomManager)atomManager).hasNewAtoms());
    }

    @Override
    public synchronized GroundAtom createLocalVariable(GroundAtom atom) {
        atom = super.createLocalVariable(atom);
        deletedAtoms[nextVariableIndex - 1] = false;
        return atom;
    }

    @Override
    protected int estimateVariableCapacity() {
        return atomManager.getCachedRVACount() + atomManager.getCachedObsCount();
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
        super.ensureVariableCapacity(capacity);

        if (deletedAtoms == null) {
            deletedAtoms = new boolean[variableAtoms.length];
        } else if (deletedAtoms.length != variableAtoms.length) {
            // A resize happened in the super call.
            deletedAtoms = Arrays.copyOf(deletedAtoms, variableAtoms.length);
        }
    }

    // Note(Charles): This number is unreliable once any online actions are processed.
    //  Becaure of the nature of streaming storage, we will not know what terms are deleted until we iterate over them.
    @Override
    public long size() {
        return seenTermCount;
    }

    public void addAtom(StandardPredicate predicate, Constant[] arguments, float newValue, boolean readPartition) {
        if (atomManager.getDatabase().hasCachedAtom(new QueryAtom(predicate, arguments))) {
            deleteAtom(predicate, arguments);
        }

        GroundAtom atom = null;
        if (readPartition) {
            atom = ((OnlineAtomManager)atomManager).addObservedAtom(predicate, newValue, arguments);
        } else {
            atom = ((OnlineAtomManager)atomManager).addRandomVariableAtom((StandardPredicate) predicate, arguments);
        }

        createLocalVariable(atom);
    }

    public void deleteAtom(StandardPredicate predicate, Constant[] arguments) {
        GroundAtom atom = atomManager.getAtom(predicate, arguments);
        if (atom == null) {
            return;
        }

        ((OnlineAtomManager)atomManager).deleteAtom(atom);

        int index = getVariableIndex(atom);
        if (index == -1) {
            return;
        }

        variables.remove(atom);

        variableValues[index] = -1.0f;
        variableAtoms[index] = null;
        deletedAtoms[index] = true;

        if (atom instanceof RandomVariableAtom) {
            numRandomVariableAtoms--;
        } else {
            numObservedAtoms--;
        }
    }

    public synchronized void updateAtom(StandardPredicate predicate, Constant[] arguments, float newValue) {
        QueryAtom atom = new QueryAtom(predicate, arguments);
        if (!variables.containsKey(atom)) {
            return;
        }

        GroundAtom groundAtom = atomManager.getAtom(predicate, arguments);

        variableValues[getVariableIndex(groundAtom)] = newValue;
        groundAtom.setValue(newValue);
    }

    public void groundingIterationComplete(long termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        seenTermCount += termCount;

        this.numPages = numPages;
        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        initialRound = false;
        activeIterator = null;
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
     * The internal interator should call close() on itself when out of items.
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
