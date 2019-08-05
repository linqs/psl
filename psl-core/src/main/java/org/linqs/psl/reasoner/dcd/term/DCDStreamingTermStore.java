/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.RandUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A term store that iterates over ground queries directly (obviating the GroundRuleStore).
 * Note that the iterators given by this class are meant to be exhaustd (at least the first time).
 * Remember that this class will internally iterate over an unknown number of groundings.
 * So interrupting the iteration can cause the term count to be incorrect.
 */
public class DCDStreamingTermStore implements DCDTermStore {
    private static final Logger log = LoggerFactory.getLogger(DCDStreamingTermStore.class);

    private List<WeightedLogicalRule> rules;
    private AtomManager atomManager;

    private Set<RandomVariableAtom> variables;

    private boolean initialRound;
    private TermIterator activeIterator;
    private int seenTermCount;

    private DCDTermGenerator termGenerator;

    // TODO(eriq): Disk paging: write cache and page cache.
    // TODO(eriq): Technically, we don't need to page ground rules, only terms.

    public DCDStreamingTermStore(List<Rule> rules, AtomManager atomManager) {
        this.rules = new ArrayList<WeightedLogicalRule>();
        for (Rule rule : rules) {
            if (!rule.isWeighted()) {
                log.warn("DCD does not support hard constraints: " + rule);
                continue;
            }

            if (!((WeightedRule)rule).isSquared()) {
                log.warn("DCD does not support linear rules: " + rule);
                continue;
            }

            // HACK(eriq): This is not actually true,
            //  but I am putting it in place for efficiency reasons.
            if (((WeightedRule)rule).getWeight() < 0.0) {
                log.warn("DCD does not support negative weights: " + rule);
                continue;
            }

            if (rule instanceof AbstractArithmeticRule) {
                log.warn("DCD does not support arithmetic rules: " + rule);
                continue;
            }

            if (!(rule instanceof WeightedLogicalRule)) {
                log.warn("DCD does not support this rule: " + rule);
                continue;
            }

            this.rules.add((WeightedLogicalRule)rule);
        }

        if (rules.size() == 0) {
            throw new IllegalArgumentException("Found no valid rules for DCD.");
        }

        this.atomManager = atomManager;
        termGenerator = new DCDTermGenerator();
        variables = new HashSet<RandomVariableAtom>();

        initialRound = true;
        activeIterator = null;
    }

    public boolean isFirstRound() {
        return initialRound;
    }

    @Override
    public Iterator<DCDObjectiveTerm> iterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this DCDTermStore. Exhaust the iterator first.");
        }

        activeIterator = new TermIterator();
        return activeIterator;
    }

    @Override
    public int getNumVariables() {
        return variables.size();
    }

    @Override
    public Iterable<RandomVariableAtom> getVariables() {
        return Collections.unmodifiableSet(variables);
    }

    @Override
    public synchronized RandomVariableAtom createLocalVariable(RandomVariableAtom atom) {
        if (variables.contains(atom)) {
            return atom;
        }

        atom.setValue(RandUtils.nextFloat());
        variables.add(atom);

        return atom;
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
        if (capacity == 0) {
            return;
        }

        if (variables.size() == 0) {
            // If there are no variables, then re-allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashSet<RandomVariableAtom>((int)Math.ceil(capacity / 0.75));
        }
    }

    @Override
    public void clear() {
        initialRound = true;

        if (activeIterator != null) {
            activeIterator.close();
            activeIterator = null;
        }

        if (variables != null) {
            variables.clear();
        }
    }

    @Override
    public void close() {
        clear();

        if (variables != null) {
            variables = null;
        }
    }

    @Override
    public int size() {
        return seenTermCount;
    }

    @Override
    public void add(GroundRule rule, DCDObjectiveTerm term) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DCDObjectiveTerm get(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureCapacity(int capacity) {
        throw new UnsupportedOperationException();
    }

    private class TermIterator implements Iterator<DCDObjectiveTerm> {
        private int currentRule;
        private int nextCachedTermIndex;
        private int nextCachedGroundRuleIndex;

        // The iteratble is kept around for cleanup.
        private QueryResultIterable queryIterable;
        private Iterator<Constant[]> queryResults;

        private DCDObjectiveTerm nextTerm;

        private List<DCDObjectiveTerm> termCache;
        private List<GroundRule> groundRuleCache;

        public TermIterator() {
            activeIterator = this;

            currentRule = -1;
            nextCachedGroundRuleIndex = 0;
            nextCachedTermIndex = 0;

            queryIterable = null;
            queryResults = null;

            // TEST
            termCache = new ArrayList<DCDObjectiveTerm>(1000);
            groundRuleCache = new ArrayList<GroundRule>(1000);

            // This will either get the next term, or throw if there are no terms.
            nextTerm = fetchNextTerm();
        }

        @Override
        public boolean hasNext() {
            return nextTerm != null;
        }

        @Override
        public DCDObjectiveTerm next() {
            if (!hasNext()) {
                throw new IllegalStateException("Called next() when hasNext() == false.");
            }

            DCDObjectiveTerm term = nextTerm;
            nextTerm = fetchNextTerm();

            // If this is the last term, then close up the iterator.
            if (!hasNext()) {
                close();
            }

            return term;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private DCDObjectiveTerm fetchNextTerm() {
            // First check the cache.
            if (nextCachedTermIndex < termCache.size()) {
                DCDObjectiveTerm term = termCache.get(nextCachedTermIndex);
                nextCachedTermIndex++;
                return term;
            }

            termCache.clear();
            nextCachedTermIndex = 0;

            // The cache is expired, generate terms for the next ground rule.

            // Note that it is possible to not get a term from a ground rule.
            DCDObjectiveTerm term = null;
            while (term == null) {
                GroundRule groundRule = fetchNextGroundRule();
                if (groundRule == null) {
                    // We are out of ground, and therefore out of terms.
                    return null;
                }

                term = termGenerator.createTerm(groundRule, DCDStreamingTermStore.this);
            }

            if (initialRound) {
                seenTermCount++;
            }

            return term;
        }

        private GroundRule fetchNextGroundRule() {
            // First check the ground rule cache.
            if (nextCachedGroundRuleIndex < groundRuleCache.size()) {
                GroundRule groundRule = groundRuleCache.get(nextCachedGroundRuleIndex);
                nextCachedGroundRuleIndex++;
                return groundRule;
            }

            groundRuleCache.clear();
            nextCachedGroundRuleIndex = 0;

            // TODO(eriq): See if the query's cache is exhaused.
            //  We can fill until that fetch cache is exhaused and put it in the ground rule cache.

            // Check if there are any more results pending from the query.
            while (queryResults != null && queryResults.hasNext()) {
                Constant[] tuple = queryResults.next();
                GroundRule groundRule = rules.get(currentRule).ground(tuple, queryIterable.getVariableMap(), atomManager);
                if (groundRule != null) {
                    return groundRule;
                }
            }

            currentRule++;
            if (currentRule >= rules.size()) {
                // There are no more rules, we are done.
                return null;
            }

            // Start grounding the next rule.
            queryIterable = atomManager.executeGroundingQuery(rules.get(currentRule).getNegatedDNF().getQueryFormula());
            queryResults = queryIterable.iterator();

            return fetchNextGroundRule();
        }

        public void close() {
            activeIterator = null;
            initialRound = false;

            if (queryIterable != null) {
                queryIterable.close();
                queryIterable = null;
                queryResults = null;
            }

            if (termCache != null) {
                termCache.clear();
                termCache = null;
            }

            if (groundRuleCache != null) {
                groundRuleCache.clear();
                groundRuleCache = null;
            }
        }
    }
}
