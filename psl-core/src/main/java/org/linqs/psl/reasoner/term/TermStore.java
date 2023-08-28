/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Parallel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A place to store terms that are to be optimized.
 */
public abstract class TermStore<T extends ReasonerTerm> implements Iterable<T> {
    protected AtomStore atomStore;
    protected TermGenerator<T> termGenerator;

    private final String threadResourceKey;

    public TermStore(AtomStore atomStore, TermGenerator<T> termGenerator) {
        this.atomStore = atomStore;
        this.termGenerator = termGenerator;
        threadResourceKey = "termstore::objectid::" + System.identityHashCode(this);
    }

    /**
     * An add that will always be called to add new terms.
     * This may be called in parallel, it is up to implementing classes to guarantee thread safety.
     */
    public abstract int add(ReasonerTerm term);

    /**
     * Remove any existing terms and prepare for a new set.
     */
    public abstract void clear();

    /**
     * Ensure that the underlying structures can have the required term capacity.
     * This is more of a hint to the store about how much memory will be used.
     * This is best called on an empty store so it can prepare.
     */
    public abstract void ensureCapacity(long capacity);

    public abstract T get(long index);

    public abstract Iterator<T> iterator();

    public abstract long size();

    public AtomStore getAtomStore() {
        return atomStore;
    }

    public void setAtomStore(AtomStore newAtomStore) {
        // Update atom indexes to align with the new atom store.
        for (ReasonerTerm term : this) {
            int[] termAtomIndexes = term.getAtomIndexes();
            for (int j = 0; j < term.size(); j++) {
                termAtomIndexes[j] = newAtomStore.getAtomIndex(atomStore.getAtom(termAtomIndexes[j]));
            }
        }

        this.atomStore = newAtomStore;
    }

    public TermGenerator<T> getTermGenerator() {
        return termGenerator;
    }

    public void setTermGenerator(TermGenerator<T> termGenerator) {
        this.termGenerator = termGenerator;
    }

    /**
     * Convert the ground rule into terms and add it to this term store.
     * Return the number of terms added.
     * Note that this may be called in parallel.
     */
    public int add(GroundRule groundRule) {
        // Get the grounding resources for this thread,
        if (!Parallel.hasThreadObject(threadResourceKey)) {
            Parallel.putThreadObject(threadResourceKey, new ThreadResources());
        }
        @SuppressWarnings("unchecked")
        ThreadResources resources = (ThreadResources)Parallel.getThreadObject(threadResourceKey);

        resources.newTerms.clear();
        resources.newHyperplane.clear();

        termGenerator.createTerm(groundRule, resources.newTerms, resources.newHyperplane);

        int count = 0;
        for (int i = 0; i < resources.newTerms.size(); i++) {
            count += add(resources.newTerms.get(i));
        }

        resources.newTerms.clear();
        resources.newHyperplane.clear();

        return count;
    }

    /**
     * Reset all atoms and terms.
     * Atom values are used to reset variables.
     * Does NOT clear().
     */
    public void reset() {
        atomStore.resetValues();
    }

    /**
     * Sync all the atom values into atoms.
     */
    public double sync() {
        return atomStore.sync();
    }

    /**
     * Close down the term store, it will not be used any more.
     */
    public void close() {
        clear();

        termGenerator = null;
        atomStore = null;
    }

    /**
     * A notification by the Reasoner that optimization is about to begin.
     * TermStores may use this as a chance to finalize data structures.
     */
    public void initForOptimization() {
    }

    /**
     * Load the provided state of the terms.
     */
    public void loadState(TermState[] termStates) {
        for (int i = 0; i < termStates.length; i++) {
            get(i).loadState(termStates[i]);
        }
    }

    /**
     * Create and return an array of the current term states.
     */
    public TermState[] saveState() {
        TermState[] termStates = new TermState[(int)size()];

        for (int i = 0; i < size(); i++) {
            termStates[i] = get(i).saveState();
        }

        return termStates;
    }

    /**
     * Populate the provided array of term states with the current state of the terms.
     */
    public void saveState(TermState[] termStates) {
        for (int i = 0; i < size(); i++) {
            get(i).saveState(termStates[i]);
        }
    }

    /**
     * Get all the terms associated with this rule.
     */
    public Iterable<T> getTerms(Rule rule) {
        final Rule finalRule = rule;

        return IteratorUtils.filter(this, new IteratorUtils.FilterFunction<T>() {
            public boolean keep(T term) {
                return finalRule.equals(term.getRule());
            }
        });
    }

    /**
     * Get all the atoms associated with each variable from the AtomStore.
     */
    public GroundAtom[] getVariableAtoms() {
        return atomStore.getAtoms();
    }

    /**
     * Get all the variable (atom) values from the AtomStore.
     */
    public float[] getVariableValues() {
        return atomStore.getAtomValues();
    }

    /**
     * Get the total number of all (obs/unobs) variables.
     */
    public int getNumVariables() {
        return atomStore.size();
    }

    /**
     * Get a count of all the types of atoms.
     */
    public AtomCount getVariableCounts() {
        AtomCount count = new AtomCount();

        for (GroundAtom atom : atomStore) {
            if (atom instanceof ObservedAtom) {
                count.observed++;
            } else {
                count.unobserved++;
            }
        }

        return count;
    }

    /**
     * Get the number of terms associated with the given rule.
     */
    public long count(Rule rule) {
        int count = 0;

        for (T term : getTerms(rule)) {
            count++;
        }

        return count;
    }

    private class ThreadResources {
        public List<T> newTerms;
        public List<Hyperplane> newHyperplane;

        public ThreadResources() {
            newTerms = new ArrayList<T>();
            newHyperplane = new ArrayList<Hyperplane>();
        }
    }

    public class AtomCount {
        public int observed;
        public int unobserved;

        public AtomCount() {
            observed = 0;
            unobserved = 0;
        }

        @Override
        public String toString() {
            return String.format("%d unobserved and %d observed", unobserved, observed);
        }
    }
}
