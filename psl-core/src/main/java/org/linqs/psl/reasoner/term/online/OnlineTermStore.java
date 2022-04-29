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
package org.linqs.psl.reasoner.term.online;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.streaming.StreamingIterator;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;
import org.linqs.psl.util.IteratorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A term store that supports online operations.
 *
 * The numPages class variable represents the number of active pages in OnlineTermStores.
 */
public abstract class OnlineTermStore<T extends ReasonerTerm> extends StreamingTermStore<T> {
    private static final Logger log = LoggerFactory.getLogger(OnlineTermStore.class);

    protected List<Integer> activeTermPages;
    protected List<Integer> activeVolatilePages;
    protected Integer nextTermPageIndex;
    protected Integer nextVolatilePageIndex;
    protected Map<Rule, List<Integer>> rulePageMapping;
    protected Map<Rule, Boolean> activatedRules;

    public OnlineTermStore(List<Rule> rules, AtomManager atomManager,
                           HyperplaneTermGenerator<T, GroundAtom> termGenerator) {
        super(rules, atomManager, termGenerator);

        activeTermPages = new ArrayList<Integer>();
        activeVolatilePages = new ArrayList<Integer>();
        rulePageMapping = new HashMap<Rule, List<Integer>>();
        activatedRules = new HashMap<Rule, Boolean>();

        for (Rule rule : rules) {
            activatedRules.put(rule, true);
            rulePageMapping.put(rule, new ArrayList<Integer>());
        }

        nextTermPageIndex = 0;
        nextVolatilePageIndex = 0;
    }

    @Override
    public boolean isLoaded() {
        return !(initialRound || ((OnlineAtomManager)atomManager).hasNewAtoms());
    }

    @Override
    protected int estimateVariableCapacity() {
        return atomManager.getCachedRVACount() + atomManager.getCachedObsCount();
    }

    public synchronized void deleteLocalVariable(GroundAtom atom) {
        int index = getVariableIndex(atom);
        if (index == -1) {
            // Atom never used in any terms.
            return;
        }

        variables.remove(atom);
        variableValues[index] = -1.0f;
        variableAtoms[index] = null;

        if (atom instanceof RandomVariableAtom) {
            numRandomVariableAtoms--;
        } else {
            numObservedAtoms--;
        }
    }

    public synchronized void updateLocalVariable(ObservedAtom atom, float newValue) {
        if (!variables.containsKey(atom)) {
            // Atom does not exist in current model.
            return;
        } else if (variableAtoms[getVariableIndex(atom)] instanceof RandomVariableAtom) {
            numRandomVariableAtoms--;
            numObservedAtoms++;
        }

        variableAtoms[getVariableIndex(atom)] = atom;
        variableValues[getVariableIndex(atom)] = newValue;
    }

    public synchronized void activateRule(Rule rule) {
        if (!rules.contains(rule)) {
            // Rule not in model.
            return;
        }

        List<Integer> rulePages = rulePageMapping.get(rule);
        int activePageIndex = 0;
        for (Integer pageIndex : rulePages) {
            activePageIndex = activeTermPages.indexOf(pageIndex);
            if (activePageIndex == -1) {
                activeTermPages.add(pageIndex);
                numPages++;
            }
        }

        activatedRules.put(rule, true);
    }

    public synchronized void addRule(Rule rule) {
        if (rules.contains(rule)) {
            // Rule already exists in model.
            return;
        }

        // Add new rule to rule set.
        rules.add(rule);
        rulePageMapping.put(rule, new ArrayList<Integer>());
        activatedRules.put(rule, true);

        // Ground new rule.
        this.initialRound = true;
        StreamingIterator<T> groundingIterator = getGroundingIterator(Arrays.asList(rule));
        while (groundingIterator.hasNext()) {
            groundingIterator.next();
        }
    }

    public synchronized void deactivateRule(Rule rule) {
        if (!rules.contains(rule)) {
            // Rule does not exist in model.
            return;
        }

        removeActiveTermPages(rule);
        activatedRules.put(rule, false);
    }

    public synchronized void deleteRule(Rule rule) {
        if (!rules.contains(rule)) {
            // Rule does not exist in model.
            return;
        }

        removeActiveTermPages(rule);
        rules.remove(rule);
        activatedRules.remove(rule);
        rulePageMapping.remove(rule);
    }

    private void removeActiveTermPages(Rule rule) {
        List<Integer> rulePages = rulePageMapping.get(rule);
        int activePageIndex = 0;
        for (Integer pageIndex : rulePages) {
            activePageIndex = activeTermPages.indexOf(pageIndex);
            if (activePageIndex != -1) {
                activeTermPages.remove(activePageIndex);
                numPages--;
            }
        }
    }

    public abstract StreamingIterator<T> getGroundingIterator(List<Rule> rules);

    /**
     * In addition to the typical behavior of setting values for random variable atoms,
     * also set the values for observed atoms.
     * Returns movement in the random variables.
     */
    @Override
    public double syncAtoms() {
        double movement = 0.0;
        for (int i = 0; i < totalVariableCount; i++) {
            if (variableAtoms[i] == null) {
                continue;
            }

            if (variableAtoms[i] instanceof RandomVariableAtom) {
                movement += Math.pow(variableAtoms[i].getValue() - variableValues[i], 2);
                ((RandomVariableAtom)variableAtoms[i]).setValue(variableValues[i]);
            } else {
                ((ObservedAtom)variableAtoms[i])._assumeValue(variableValues[i]);
            }
        }

        return Math.sqrt(movement);
    }

    @Override
    public String getTermPagePath(int index) {
        buildActivePagePath(index);
        return termPagePaths.get(activeTermPages.get(index));
    }

    @Override
    public String getVolatilePagePath(int index) {
        // Make sure the path is built.
        // This implementation gets the index active term page.
        for (int i = activeVolatilePages.size(); i <= index; i++) {
            volatilePagePaths.add(Paths.get(pageDir, String.format("%08d_volatile.page", nextVolatilePageIndex)).toString());
            activeVolatilePages.add(nextVolatilePageIndex);
            nextVolatilePageIndex++;
        }

        return volatilePagePaths.get(activeVolatilePages.get(index));
    }

    public void addRuleMapping(Rule rule, int pageIndex) {
        buildActivePagePath(pageIndex);
        rulePageMapping.get(rule).add(activeTermPages.get(pageIndex));
    }

    private void buildActivePagePath(int index) {
        // Make sure the path is built.
        // This implementation gets the index of the next active term page.
        for (int i = activeTermPages.size(); i <= index; i++) {
            termPagePaths.add(Paths.get(pageDir, String.format("%08d_term.page", nextTermPageIndex)).toString());
            activeTermPages.add(nextTermPageIndex);
            nextTermPageIndex++;
        }
    }

    @Override
    public void groundingIterationComplete(long termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        super.groundingIterationComplete(termCount, numPages, termBuffer, volatileBuffer);

        // Deactivate any new pages corresponding to deactivated rules.
        for (Rule rule : rules) {
            if (activatedRules.get(rule)) {
                continue;
            }

            List<Integer> rulePages = rulePageMapping.get(rule);
            for (Integer pageIndex : rulePages) {
                int activePageIndex = activeTermPages.indexOf(pageIndex);
                if (activePageIndex == -1) {
                    continue;
                }

                activeTermPages.remove(activePageIndex);
                this.numPages--;
            }
        }
    }

    private void joinIterationComplete() {
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
     * The internal iterator should call close() on itself when out of items.
     */
    private class StreamingJoinIterator<E extends ReasonerTerm> implements StreamingIterator<E> {
        private Iterator<E> iterator;

        public StreamingJoinIterator(Iterator<E> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = iterator.hasNext();

            if (!hasNext) {
                close();
            }

            return hasNext;
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
            joinIterationComplete();
        }
    }
}
