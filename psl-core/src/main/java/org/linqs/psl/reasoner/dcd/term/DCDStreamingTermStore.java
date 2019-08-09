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
     * Where on disk to write term pages.
     */
    public static final String PAGE_LOCATION_KEY = CONFIG_PREFIX + ".pagelocation";
    public static final String PAGE_LOCATION_DEFAULT = SystemUtils.getTempDir("dcd_cache_pages");

    /**
     * The number of terms in a single page.
     */
    public static final String PAGE_SIZE_KEY = CONFIG_PREFIX + ".pagesize";
    public static final int PAGE_SIZE_DEFAULT = 10000;

    /**
     * Whether to shuffle within a page when it is picked up.
     */
    public static final String SHUFFLE_PAGE_KEY = CONFIG_PREFIX + ".shufflepage";
    public static final boolean SHUFFLE_PAGE_DEFAULT = true;

    /**
     * Whether to pick up pages in a random order.
     */
    public static final String RANDOMIZE_PAGE_ACCESS_KEY = CONFIG_PREFIX + ".randomizepageaccess";
    public static final boolean RANDOMIZE_PAGE_ACCESS_DEFAULT = true;

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
    private boolean shufflePage;
    private boolean randomizePageAccess;

    /**
     * The IO buffer for terms.
     * This buffer is only written on the first iteration,
     * and contains only components of the terms that do not change.
     */
    private ByteBuffer termBuffer;

    /**
     * The IO buffer for lagrange values.
     * These values change every iteration, and need to be updated.
     */
    private ByteBuffer lagrangeBuffer;

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

    /**
     * When we shuffle pages, we need to know how they were shuffled so the lagrange
     * cache can be writtten in the same order.
     * So we will shuffle this list of sequential ints in the same order as the page.
     */
    private List<Integer> shuffleMap;

    public DCDStreamingTermStore(List<Rule> rules, AtomManager atomManager) {
        this.rules = new ArrayList<WeightedLogicalRule>();
        for (Rule rule : rules) {
            if (!rule.isWeighted()) {
                log.warn("DCD does not support hard constraints: " + rule);
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

        termBuffer = null;
        lagrangeBuffer = null;
        pageSize = Config.getInt(PAGE_SIZE_KEY, PAGE_SIZE_DEFAULT);
        pageDir = Config.getString(PAGE_LOCATION_KEY, PAGE_LOCATION_DEFAULT);
        shufflePage = Config.getBoolean(SHUFFLE_PAGE_KEY, SHUFFLE_PAGE_DEFAULT);
        randomizePageAccess = Config.getBoolean(RANDOMIZE_PAGE_ACCESS_KEY, RANDOMIZE_PAGE_ACCESS_DEFAULT);
        SystemUtils.recursiveDelete(pageDir);

        if (pageSize <= 1) {
            throw new IllegalArgumentException("Page size is too small.");
        }

        termCache = new ArrayList<DCDObjectiveTerm>(pageSize);
        termPool = new ArrayList<DCDObjectiveTerm>(pageSize);
        shuffleMap = new ArrayList<Integer>(pageSize);
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

        if (termBuffer != null) {
            termBuffer.clear();
            termBuffer = null;
        }

        if (lagrangeBuffer != null) {
            lagrangeBuffer.clear();
            lagrangeBuffer = null;
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

    /**
     * Iterate over all the terms from grounding/cache.
     * The order of events here is very precise.
     * This stems from needing the write the lagrange values every iteration.
     * We cannot prefetch terms too early, because this may flush the cache.
     * For example if we prefetch the next term in next(),
     * then the term we are about to return may be the last of its page.
     * This means that (pre)fetching the next term will flush the page.
     * So we will have written a stale lagrange value and
     * the retutned term will be converted into another one.
     * To avoid this, we will never prefetch (have two terms at a time)
     * and we will fetch in hasNext().
     *
     * On the first iteration, we will build the term cache up from ground rules and flush to disk.
     * On subsequent iterations, we will fill the term cache from disk and drain it.
     */
    private class TermIterator implements Iterator<DCDObjectiveTerm> {
        private int termCount;
        private int currentRule;
        private int nextPage;
        private int nextCachedTermIndex;

        // The iteratble is kept around for cleanup.
        private QueryResultIterable queryIterable;
        private Iterator<Constant[]> queryResults;

        private DCDObjectiveTerm nextTerm;

        // When we are reading pages from disk (after the initial round),
        // this list will tell us the order to read them in.
        // This may get shuffled depending on configuration.
        private List<Integer> pageAccessOrder;

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

            pageAccessOrder = new ArrayList<Integer>(numPages);
            for (int i = 0; i < numPages; i++) {
                pageAccessOrder.add(i);
            }

            if (randomizePageAccess) {
                RandUtils.shuffle(pageAccessOrder);
            }

            // Note that we cannot pre-fetch.
            nextTerm = null;
        }

        /**
         * Get the next term.
         * It is critical that every call to hasNext be followed by a call to next
         * (as long as hasNext returns true).
         */
        public boolean hasNext() {
            if (nextTerm != null) {
                throw new IllegalStateException("hasNext() was called twice in a row. Call next() directly after hasNext() == true.");
            }

            nextTerm = fetchNextTerm();
            if (nextTerm == null) {
                close();
                return false;
            }

            return true;
        }

        @Override
        public DCDObjectiveTerm next() {
            if (nextTerm == null) {
                throw new IllegalStateException("Called next() when hasNext() == false (or before the first hasNext() call).");
            }

            DCDObjectiveTerm term = nextTerm;
            nextTerm = null;

            return term;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Get the next term from wherever we need to.
         * We will always settle outstanding pages before trying to get the next term.
         */
        private DCDObjectiveTerm fetchNextTerm() {
            if (initialRound && termCache.size() >= pageSize) {
                // Cache is full on the first round, drop it to disk.
                flushCache();
            } else if (!initialRound && nextCachedTermIndex >= termCache.size()) {
                // Cache is exhaused not on the initial round, fill it up.

                // First flush the lagrange cache.
                flushCache();

                fetchPage();
            }

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
                throw new IllegalStateException("Cannot fetch from the cache on the initial round.");
            }

            // Check for no more terms right away.
            if (termCount >= seenTermCount) {
                return null;
            }

            // The page has already been verified, so there must be a term waiting.

            DCDObjectiveTerm term = termCache.get(nextCachedTermIndex);
            nextCachedTermIndex++;
            return term;
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
                    // We are out of ground rules, and therefore out of terms.
                    return null;
                }

                term = termGenerator.createTerm(groundRule, DCDStreamingTermStore.this);
            }

            seenTermCount++;
            termCache.add(term);

            // If we are on the first round/page, set aside the term for reuse.
            if (initialRound && numPages == 0) {
                termPool.add(term);
            }

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
            // Note that the termBuffer should be at maximum size from the initial round.
            termBuffer.clear();
            lagrangeBuffer.clear();

            int termsSize = 0;
            int numTerms = 0;
            int headerSize = (Integer.SIZE / 8) * 2;
            int lagrangesSize = 0;

            int pageIndex = pageAccessOrder.get(nextPage).intValue();

            String termPagePath = getTermPagePath(pageIndex);
            String lagrangePagePath = getLagrangePagePath(pageIndex);

            try (
                    FileInputStream termStream = new FileInputStream(termPagePath);
                    FileInputStream lagrangeStream = new FileInputStream(lagrangePagePath)) {
                // First read the size information.
                termStream.read(termBuffer.array(), 0, headerSize);

                termsSize = termBuffer.getInt();
                numTerms = termBuffer.getInt();
                lagrangesSize = (Float.SIZE / 8) * numTerms;

                // Now read in all the terms and lagrange values.
                termStream.read(termBuffer.array(), headerSize, termsSize);
                lagrangeStream.read(lagrangeBuffer.array(), 0, lagrangesSize);
            } catch (IOException ex) {
                throw new RuntimeException(String.format("Unable to read cache pages: [%s ; %s].", termPagePath, lagrangePagePath), ex);
            }

            // Convert all the terms from binary to objects.
            // Use the terms from the pool.

            for (int i = 0; i < numTerms; i++) {
                DCDObjectiveTerm term = termPool.get(i);
                term.read(termBuffer, lagrangeBuffer, variables);
                termCache.add(term);
            }

            if (shufflePage) {
                shuffleMap.clear();
                for (int i = 0; i < termCache.size(); i++) {
                    shuffleMap.add(i);
                }

                RandUtils.pairedShuffle(termCache, shuffleMap);
            }

            nextPage++;
            nextCachedTermIndex = 0;

            return true;
        }

        private void flushCache() {
            // If is possible to get two flush requests in a row, so check to see if we actually need it.
            if (termCache.size() == 0) {
                return;
            }

            if (initialRound) {
                (new File(pageDir)).mkdirs();

                if (termCache.size() == 0) {
                    return;
                }

                flushLagrangeCache(numPages);
                flushTermCache(numPages);

                numPages++;
            } else if (nextPage > 0) {
                // We will not flush when the next page is 0 (we have not yet fetched the first page).
                flushLagrangeCache(nextPage - 1);
            }

            termCache.clear();
        }

        private void flushTermCache(int pageNumber) {
            // Terms (the static components) are only cached on the initial round.
            // We also don't have to worry about page access randomization or shuffled pages.
            if (!initialRound) {
                return;
            }

            int termsSize = 0;
            for (DCDObjectiveTerm term : termCache) {
                termsSize += term.fixedByteSize();
            }

            // Allocate an extra two ints for the number of terms and size of terms in that page.
            int termBufferSize = termsSize + (Integer.SIZE / 8) * 2;

            if (termBuffer == null) {
                termBuffer = ByteBuffer.allocate((int)(termBufferSize * OVERALLOCATION_RATIO));
            } else if (termBuffer.capacity() < termBufferSize) {
                // Reallocate.
                termBuffer.clear();
                termBuffer = ByteBuffer.allocate((int)(termBufferSize * OVERALLOCATION_RATIO));
            } else {
                termBuffer.clear();
            }

            // First put the size of the terms and number of terms.
            termBuffer.putInt(termsSize);
            termBuffer.putInt(termCache.size());

            // Now put in all the terms.
            for (DCDObjectiveTerm term : termCache) {
                term.writeFixedValues(termBuffer);
            }

            String termPagePath = getTermPagePath(pageNumber);
            try (FileOutputStream stream = new FileOutputStream(termPagePath)) {
                stream.write(termBuffer.array(), 0, termBufferSize);
            } catch (IOException ex) {
                throw new RuntimeException("Unable to write term cache page: " + termPagePath, ex);
            }
        }

        private void flushLagrangeCache(int pageNumber) {
            int lagrangeBufferSize = (Float.SIZE / 8) * termCache.size();

            if (lagrangeBuffer == null) {
                lagrangeBuffer = ByteBuffer.allocate((int)(lagrangeBufferSize * OVERALLOCATION_RATIO));
            } else if (lagrangeBuffer.capacity() < lagrangeBufferSize) {
                // Reallocate.
                lagrangeBuffer.clear();
                lagrangeBuffer = ByteBuffer.allocate((int)(lagrangeBufferSize * OVERALLOCATION_RATIO));
            } else {
                lagrangeBuffer.clear();
            }

            // If this page was picked up from the cache (and not from grounding) and shuffled,
            // then we will need to use the shuffle map to write the lagrange values back in
            // the same order as the terms.
            if (shufflePage && !initialRound) {
                for (int shuffledIndex = 0; shuffledIndex < shuffleMap.size(); shuffledIndex++) {
                    int writeIndex = shuffleMap.get(shuffledIndex);
                    DCDObjectiveTerm term = termCache.get(shuffledIndex);
                    lagrangeBuffer.putFloat(writeIndex * (Float.SIZE / 8), term.getLagrange());
                }
            } else {
                for (DCDObjectiveTerm term : termCache) {
                    lagrangeBuffer.putFloat(term.getLagrange());
                }
            }

            // On non-initial rounds, translate the page access order.
            int pageIndex = pageNumber;
            if (!initialRound) {
                pageIndex = pageAccessOrder.get(pageNumber).intValue();
            }

            String lagrangePagePath = getLagrangePagePath(pageIndex);
            try (FileOutputStream stream = new FileOutputStream(lagrangePagePath)) {
                stream.write(lagrangeBuffer.array(), 0, lagrangeBufferSize);
            } catch (IOException ex) {
                throw new RuntimeException("Unable to write lagrange cache page: " + lagrangePagePath, ex);
            }
        }

        private String getTermPagePath(int index) {
            return Paths.get(pageDir, String.format("%08d_term.page", index)).toString();
        }

        private String getLagrangePagePath(int index) {
            return Paths.get(pageDir, String.format("%08d_lagrange.page", index)).toString();
        }

        public void close() {
            if (activeIterator == null) {
                // Don't double close.
                return;
            }

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
