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

import org.linqs.psl.config.Config;
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
import org.linqs.psl.util.SystemUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A term store that iterates over ground queries directly (obviating the GroundRuleStore).
 * Note that the iterators given by this class are meant to be exhaustd (at least the first time).
 * Remember that this class will internally iterate over an unknown number of groundings.
 * So interrupting the iteration can cause the term count to be incorrect.
 */
public class DCDStreamingTermStore implements DCDTermStore {
    private static final Logger log = LoggerFactory.getLogger(DCDStreamingTermStore.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "dcdstreaming";

    /**
     * The number of terms in a single page.
     */
    public static final String PAGE_SIZE_KEY = CONFIG_PREFIX + ".pagesize";
    public static final int PAGE_SIZE_DEFAULT = 10000;

    /**
     * Where on disk to write term pages.
     */
    public static final String PAGE_LOCATION_KEY = CONFIG_PREFIX + ".pagelocation";
    public static final String PAGE_LOCATION_DEFAULT = SystemUtils.getTempDir("term_pages");

    // How much to over-allocate by.
    private static final double OVERALLOCATION_RATIO = 1.25;

    private List<WeightedLogicalRule> rules;
    private AtomManager atomManager;

    // <Object.hashCode(), RVA>
    private Map<Integer, RandomVariableAtom> variables;

    private boolean initialRound;
    private TermIterator activeIterator;
    private int seenTermCount;
    private int numPages;

    private DCDTermGenerator termGenerator;

    private int pageSize;
    private String pageDir;
    private ByteBuffer buffer;

    /**
     * Terms in the current page.
     * On the initial round, this is filled from DB and flushed to disk.
     * On subsequent rounds, this is filled from disk.
     */
    private List<DCDObjectiveTerm> termCache;

    /**
     * Terms that we will reuse when we start pulling from the cache.
     * This should be a fill page's worth.
     * After the initial round, terms will bounce between here and the term cache.
     */
    private List<DCDObjectiveTerm> termPool;

    // TODO(eriq): Shuffle (in-place) pages.
    // TODO(eriq): Shuffle page access.

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
        variables = new HashMap<Integer, RandomVariableAtom>();

        initialRound = true;
        activeIterator = null;
        numPages = 0;

        buffer = null;
        pageSize = Config.getInt(PAGE_SIZE_KEY, PAGE_SIZE_DEFAULT);
        pageDir = Config.getString(PAGE_LOCATION_KEY, PAGE_LOCATION_DEFAULT);
        SystemUtils.recursiveDelete(pageDir);

        termCache = new ArrayList<DCDObjectiveTerm>(pageSize);
        termPool = new ArrayList<DCDObjectiveTerm>(pageSize);
    }

    public boolean isFirstRound() {
        return initialRound;
    }

    @Override
    public Iterator<DCDObjectiveTerm> iterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this DCDTermStore. Exhaust the iterator first.");
        }

        return new TermIterator();
    }

    @Override
    public int getNumVariables() {
        return variables.size();
    }

    @Override
    public Iterable<RandomVariableAtom> getVariables() {
        return variables.values();
    }

    @Override
    public synchronized RandomVariableAtom createLocalVariable(RandomVariableAtom atom) {
        int key = System.identityHashCode(atom);

        if (variables.containsKey(key)) {
            return atom;
        }

        atom.setValue(RandUtils.nextFloat());
        variables.put(key, atom);

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
            variables = new HashMap<Integer, RandomVariableAtom>((int)Math.ceil(capacity / 0.75));
        }
    }

    @Override
    public void clear() {
        initialRound = true;
        numPages = 0;

        if (activeIterator != null) {
            activeIterator.close();
            activeIterator = null;
        }

        if (variables != null) {
            variables.clear();
        }

        if (termCache != null) {
            termCache.clear();
        }

        if (termPool != null) {
            termPool.clear();
        }

        SystemUtils.recursiveDelete(pageDir);
    }

    @Override
    public void close() {
        clear();

        if (variables != null) {
            variables = null;
        }

        if (buffer != null) {
            buffer.clear();
            buffer = null;
        }

        if (termCache != null) {
            termCache = null;
        }

        if (termPool != null) {
            termPool = null;
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
        private int termCount;
        private int currentRule;
        private int nextPage;
        private int nextCachedTermIndex;

        // The iteratble is kept around for cleanup.
        private QueryResultIterable queryIterable;
        private Iterator<Constant[]> queryResults;

        private DCDObjectiveTerm nextTerm;

        public TermIterator() {
            // Note that activeIterator is specifically set here in case there are no terms.
            // (We cannot wait for construction to assign it.)
            activeIterator = this;

            termCount = 0;
            currentRule = -1;
            nextPage = 0;
            nextCachedTermIndex = 0;

            queryIterable = null;
            queryResults = null;

            termCache.clear();

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
            DCDObjectiveTerm term;
            if (initialRound) {
                term = fetchNextTermFromRule();
            } else {
                term = fetchNextTermFromCache();
            }

            if (term != null) {
                termCount++;
            }

            return term;
        }

        private DCDObjectiveTerm fetchNextTermFromCache() {
            if (initialRound) {
                throw new IllegalStateException("Cannot only fetch from the cache on the initial round.");
            }

            // Check for no more terms right away.
            if (termCount >= seenTermCount) {
                return null;
            }

            // First check the existing page.
            // Note that because the last term was checked for earlier,
            // this will even work on the last page.
            if (nextCachedTermIndex < termCache.size()) {
                DCDObjectiveTerm term = termCache.get(nextCachedTermIndex);
                nextCachedTermIndex++;
                return term;
            }

            // This page is up, fetch the next page.
            if (!fetchPage()) {
                // There are no more pages.
                return null;
            }

            return fetchNextTermFromCache();
        }

        private DCDObjectiveTerm fetchNextTermFromRule() {
            if (!initialRound) {
                throw new IllegalStateException("Can only fetch a term from a rule (the DB) on the initial round.");
            }

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

            seenTermCount++;
            addToCache(term);

            return term;
        }

        private GroundRule fetchNextGroundRule() {
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

        private void addToCache(DCDObjectiveTerm term) {
            termCache.add(term);

            // On the first round and page, also set aside these terms for reuse.
            if (numPages == 0 && initialRound) {
                termPool.add(term);
            }

            if (termCache.size() >= pageSize) {
                flushCache();
            }
        }

        /**
         * Fetch the next page.
         * @return true if the next page was fetched and loaded.
         */
        private boolean fetchPage() {
            // Clear the existing page cache.
            termCache.clear();

            if (nextPage >= numPages) {
                // Out of pages.
                return false;
            }

            // Prep for the next read.
            buffer.clear();

            int termsSize = 0;
            int numTerms = 0;

            // Note that the buffer should be at maximum size from the initial round.
            String pagePath = getPagePath(nextPage);
            try (FileInputStream stream = new FileInputStream(pagePath)) {
                // First read the size information.
                stream.read(buffer.array(), 0, Integer.SIZE * 2);

                termsSize = buffer.getInt();
                numTerms = buffer.getInt();

                // Now read in all the terms.
                stream.read(buffer.array(), Integer.SIZE * 2, termsSize);
            } catch (IOException ex) {
                throw new RuntimeException("Unable to read cache page: " + pagePath, ex);
            }

            // Convert all the terms from binart to objects.
            // Use the terms from the pool.

            for (int i = 0; i < numTerms; i++) {
                DCDObjectiveTerm term = termPool.get(i);
                term.read(buffer, variables);
                termCache.add(term);
            }

            nextPage++;
            nextCachedTermIndex = 0;

            return true;
        }

        private void flushCache() {
            if (!initialRound || termCache.size() == 0) {
                return;
            }

            (new File(pageDir)).mkdirs();

            // Allocate an extra two int for the number of terms and size of terms in that page.
            int size = Integer.SIZE * 2;
            for (DCDObjectiveTerm term : termCache) {
                size += term.byteSize();
            }

            if (buffer == null) {
                buffer = ByteBuffer.allocate((int)(size * OVERALLOCATION_RATIO));
            } else if (buffer.capacity() < size) {
                // Reallocate.
                buffer.clear();
                buffer = ByteBuffer.allocate((int)(size * OVERALLOCATION_RATIO));
            }

            // First put the size of the terms and number of terms.
            buffer.putInt(size - Integer.SIZE * 2);
            buffer.putInt(termCache.size());

            // Now put in all the terms.
            for (DCDObjectiveTerm term : termCache) {
                term.write(buffer);
            }

            String pagePath = getPagePath(numPages);
            try (FileOutputStream stream = new FileOutputStream(pagePath)) {
                stream.write(buffer.array(), 0, size);
            } catch (IOException ex) {
                throw new RuntimeException("Unable to write cache page: " + pagePath, ex);
            }

            termCache.clear();
            numPages++;
        }

        private String getPagePath(int index) {
            return Paths.get(pageDir, String.format("%08d.page", index)).toString();
        }

        public void close() {
            flushCache();

            activeIterator = null;
            initialRound = false;

            if (queryIterable != null) {
                queryIterable.close();
                queryIterable = null;
                queryResults = null;
            }
        }
    }
}
