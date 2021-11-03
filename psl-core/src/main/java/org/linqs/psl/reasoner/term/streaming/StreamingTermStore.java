/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerAtom;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Atoms are kept in memory, but terms are kept on disk.
 */
public abstract class StreamingTermStore<T extends ReasonerTerm> extends TermStore<T, GroundAtom> {
    private static final Logger log = LoggerFactory.getLogger(StreamingTermStore.class);

    public static final int INITIAL_PATH_CACHE_SIZE = 100;

    protected List<Rule> rules;
    protected AtomManager atomManager;

    protected List<String> termPagePaths;
    protected List<String> volatilePagePaths;

    protected boolean initialRound;
    protected StreamingIterator<T> activeIterator;
    protected long seenTermCount;
    protected int numPages;

    protected HyperplaneTermGenerator<T, GroundAtom> termGenerator;

    protected int pageSize;
    protected String pageDir;
    protected boolean shufflePage;
    protected boolean randomizePageAccess;

    protected boolean warnRules;

    /**
     * The IO buffer for terms.
     * This buffer is only written on the first iteration,
     * and contains only components of the terms that do not change.
     */
    protected ByteBuffer termBuffer;

    /**
     * The IO buffer for volatile values.
     * These values change every iteration, and need to be updated.
     */
    protected ByteBuffer volatileBuffer;

    /**
     * Terms in the current page.
     * On the initial round, this is filled from DB and flushed to disk.
     * On subsequent rounds, this is filled from disk.
     */
    protected List<T> termCache;

    /**
     * Terms that we will reuse when we start pulling from the cache.
     * This should be a fill page's worth.
     * After the initial round, terms will bounce between here and the term cache.
     */
    protected List<T> termPool;

    /**
     * When we shuffle pages, we need to know how they were shuffled so the volatile
     * cache can be writtten in the same order.
     * So we will shuffle this list of sequential ints in the same order as the page.
     */
    protected int[] shuffleMap;

    public StreamingTermStore(List<Rule> rules, AtomManager atomManager,
            HyperplaneTermGenerator<T, GroundAtom> termGenerator) {
        pageSize = Options.STREAMING_TS_PAGE_SIZE.getInt();
        pageDir = Options.STREAMING_TS_PAGE_LOCATION.getString();
        shufflePage = Options.STREAMING_TS_SHUFFLE_PAGE.getBoolean();
        randomizePageAccess = Options.STREAMING_TS_RANDOMIZE_PAGE_ACCESS.getBoolean();
        warnRules = Options.STREAMING_TS_WARN_RULES.getBoolean();

        this.rules = new ArrayList<Rule>();
        for (Rule rule : rules) {
            if (supportsRule(rule, warnRules)) {
                this.rules.add(rule);
            }
        }

        if (rules.size() == 0) {
            throw new IllegalArgumentException("Found no valid rules for a streaming term store.");
        }

        this.atomManager = atomManager;
        this.termGenerator = termGenerator;

        ensureAtomCapacity(estimateAtomCapacity());

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        volatilePagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);

        initialRound = true;
        activeIterator = null;
        seenTermCount = 0l;
        numPages = 0;

        termBuffer = null;
        volatileBuffer = null;

        FileUtils.recursiveDelete(pageDir);
        if (pageSize <= 1) {
            throw new IllegalArgumentException("Page size is too small.");
        }

        termCache = new ArrayList<T>(pageSize);
        termPool = new ArrayList<T>(pageSize);
        shuffleMap = new int[pageSize];

        FileUtils.mkdir(pageDir);
    }

    @Override
    public void ensureTermCapacity(long capacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addTerm(GroundRule rule, T term, Hyperplane<? extends ReasonerAtom> hyperplane) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> getTerms() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T getTerm(long index) {
        throw new UnsupportedOperationException();
    }

    /**
     * Estimate how many total atoms this term store will need to track.
     */
    protected int estimateAtomCapacity() {
        return atomManager.getCachedRVACount();
    }

    @Override
    public synchronized GroundAtom createReasonerAtom(GroundAtom atom) {
        if (atomIndexMap.containsKey(atom)) {
            return atom;
        }

        // Got a new atom.

        if (totalAtomCount >= atoms.length) {
            ensureAtomCapacity(totalAtomCount * 2);
        }

        atomIndexMap.put(atom, totalAtomCount);
        atomValues[totalAtomCount] = atom.getValue();
        atoms[totalAtomCount] = atom;
        totalAtomCount++;

        if (atom instanceof RandomVariableAtom) {
            numRandomVariableAtoms++;
        } else {
            numObservedAtoms++;
        }

        return atom;
    }

    @Override
    public void atomsExternallyUpdated() {
    }

    /**
     * Get an iterator that will perform grounding queries and write pages to disk.
     * By default, this is only called for the initial round of iteration,
     * but children may override call this in different situations (like online inference).
     */
    protected abstract StreamingIterator<T> getGroundingIterator();

    /**
     * Get an iterator that will read and write from disk.
     */
    protected abstract StreamingIterator<T> getCacheIterator();

    /**
     * Get an iterator that will not write to disk.
     */
    protected abstract StreamingIterator<T> getNoWriteIterator();

    /**
     * Get an iterator that goes over all the terms for only reading.
     * Before this method can be called, a full iteration must have already been done.
     * (The cache will need to have been built.)
     */
    @Override
    public Iterator<T> noWriteIterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this StreamingTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            throw new IllegalStateException("A full iteration must have already been completed before asking for a read-only iterator.");
        }

        activeIterator = getNoWriteIterator();

        return activeIterator;
    }

    /**
     * Provide the iterator that will be used in iterator(), along with any additional setup.
     * This method is used instead of just iterator() for type safety in child classes.
     */
    protected StreamingIterator<T> streamingIterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this StreamingTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            activeIterator = getGroundingIterator();
        } else {
            activeIterator = getCacheIterator();
        }

        return activeIterator;
    }

    @Override
    public Iterator<T> iterator() {
        return streamingIterator();
    }

    @Override
    public void iterationComplete() {
    }

    /**
     * A callback for the initial round iterator.
     * The ByterBuffers are here because of possible reallocation.
     */
    public void groundingIterationComplete(long termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        seenTermCount += termCount;

        this.numPages = numPages;
        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        initialRound = false;
        activeIterator = null;
    }

    /**
     * A callback for the non-initial round iterator.
     */
    public void cacheIterationComplete() {
        activeIterator = null;
    }

    /**
     * Whether this is the first round of iteration.
     * During the initial round, not all terms will be loaded into the cache
     * (they will need to be grounded).
     * If false, users can specifically call noWriteIterator() for faster iteration.
     */
    public boolean isInitialRound() {
        return initialRound;
    }

    @Override
    public boolean isLoaded() {
        return !initialRound;
    }

    @Override
    public void initForOptimization() {
    }

    @Override
    public long size() {
        return seenTermCount;
    }

    public String getTermPagePath(int index) {
        // Make sure the path is built.
        for (int i = termPagePaths.size(); i <= index; i++) {
            // Creating new term page path.
            termPagePaths.add(Paths.get(pageDir, String.format("%08d_term.page", i)).toString());
        }

        return termPagePaths.get(index);
    }

    public String getVolatilePagePath(int index) {
        // Make sure the path is built.
        for (int i = volatilePagePaths.size(); i <= index; i++) {
            // Creating new volatile page path.
            volatilePagePaths.add(Paths.get(pageDir, String.format("%08d_volatile.page", i)).toString());
        }

        return volatilePagePaths.get(index);
    }

    @Override
    public void clear() {
        super.clear();

        initialRound = true;
        seenTermCount = 0l;
        numPages = 0;

        if (activeIterator != null) {
            activeIterator.close();
            activeIterator = null;
        }

        if (termCache != null) {
            termCache.clear();
        }

        if (termPool != null) {
            termPool.clear();
        }

        FileUtils.recursiveDelete(pageDir);
    }

    @Override
    public void close() {
        super.close();

        clear();

        if (termBuffer != null) {
            termBuffer.clear();
            termBuffer = null;
        }

        if (volatileBuffer != null) {
            volatileBuffer.clear();
            volatileBuffer = null;
        }

        if (termCache != null) {
            termCache = null;
        }

        if (termPool != null) {
            termPool = null;
        }
    }

    /**
     * Check if this term store supports this rule.
     * If the rule is not supported and |warnRules| is true, then a warning should be logged.
     * @return true if the rule is supported.
     */
    protected boolean supportsRule(Rule rule, boolean warnRules) {
        if (!rule.isWeighted()) {
            if (warnRules) {
                log.warn("Streaming term stores do not support hard constraints: " + rule);
            }

            return false;
        }

        // HACK(eriq): This is not actually true, but I am putting it in place for efficiency reasons.
        if (((WeightedRule)rule).getWeight() < 0.0) {
            if (warnRules) {
                log.warn("Streaming term stores do not support negative weights: " + rule);
            }

            return false;
        }

        if (!rule.supportsIndividualGrounding()) {
            if (warnRules) {
                log.warn("Streaming term stores do not support rules that cannot individually ground (arithmetic rules with summations): " + rule);
            }

            return false;
        }

        return true;
    }

    /**
     * Return true if the given term read from the cache is invalid.
     * Under normal streaming circumstances this cannot happen,
     * but children may encounter different situations (like online inference where atoms can be deleted).
     */
    public boolean rejectCacheTerm(T term) {
        return false;
    }
}
