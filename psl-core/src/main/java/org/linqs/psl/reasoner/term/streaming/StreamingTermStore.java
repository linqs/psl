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
package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A term store that does not hold terms in memory, but instead keeps terms on disk.
 * Note that the iterators given by this class are meant to be exhausted (at least the first time).
 * Remember that this class will internally iterate over an unknown number of groundings.
 * So interrupting the iteration can cause the term count to be incorrect.
 */
public abstract class StreamingTermStore<T extends StreamingTerm> extends TermStore<T> {
    private static final Logger log = Logger.getLogger(StreamingTermStore.class);

    public static final int INITIAL_PATH_CACHE_SIZE = 100;

    public Database database;

    protected List<Rule> rules;

    protected List<String> termPagePaths;

    protected boolean initialRound;
    protected StreamingIterator<T> activeIterator;
    protected long termCount;
    protected int numPages;

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
     * Terms in the current page.
     * On the initial round, this is filled from the DB and flushed to the disk.
     * On subsequent rounds, this is filled from the disk.
     */
    protected List<T> termCache;

    /**
     * Terms that we will reuse when we start pulling from the cache.
     * This should be a fill page's worth.
     * After the initial round, terms will bounce between here and the term cache.
     */
    protected List<T> termPool;

    /**
     * When we shuffle pages, the order to access the pages in.
     */
    protected int[] shuffleMap;

    public StreamingTermStore(List<Rule> rawRules, Database database, TermGenerator<T> termGenerator) {
        super(database.getAtomStore(), termGenerator);

        this.database = database;

        pageSize = Options.STREAMING_TS_PAGE_SIZE.getInt();
        pageDir = Options.STREAMING_TS_PAGE_LOCATION.getString();
        shufflePage = Options.STREAMING_TS_SHUFFLE_PAGE.getBoolean();
        randomizePageAccess = Options.STREAMING_TS_RANDOMIZE_PAGE_ACCESS.getBoolean();
        warnRules = Options.STREAMING_TS_WARN_RULES.getBoolean();

        rules = new ArrayList<Rule>();
        for (Rule rule : rawRules) {
            if (supportsRule(rule, warnRules)) {
                rules.add(rule);
            }
        }

        if (rules.size() == 0) {
            throw new IllegalArgumentException("Found no valid rules for a streaming term store.");
        }

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);

        initialRound = true;
        activeIterator = null;
        termCount = 0l;
        numPages = 0;

        termBuffer = null;

        termCache = new ArrayList<T>(pageSize);
        termPool = new ArrayList<T>(pageSize);
        shuffleMap = new int[pageSize];

        if (pageSize <= 1) {
            throw new IllegalArgumentException("Page size is too small.");
        }

        FileUtils.recursiveDelete(pageDir);
        FileUtils.mkdir(pageDir);
    }

    @Override
    public int add(ReasonerTerm term) {
        throw new UnsupportedOperationException();
    }

    /**
     * A callback for the non-initial round iterator.
     */
    public void cacheIterationComplete(long termCount) {
        this.termCount = termCount;
        activeIterator = null;
    }

    public Database getDatabase() {
        return database;
    }

    @Override
    public void clear() {
        initialRound = true;
        termCount = 0l;
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

        if (termBuffer != null) {
            termBuffer.clear();
            termBuffer = null;
        }

        if (termCache != null) {
            termCache = null;
        }

        if (termPool != null) {
            termPool = null;
        }
    }

    @Override
    public void ensureCapacity(long capacity) {
        // Streaming always has capacity.
    }

    @Override
    public T get(long index) {
        throw new UnsupportedOperationException();
    }

    public String getTermPagePath(int index) {
        // Make sure the path is built.
        for (int i = termPagePaths.size(); i <= index; i++) {
            // Creating new term page path.
            termPagePaths.add(Paths.get(pageDir, String.format("%08d_term.page", i)).toString());
        }

        return termPagePaths.get(index);
    }

    /**
     * A callback for the initial round iterator.
     * The ByterBuffer is here because of possible reallocation.
     */
    public void groundingIterationComplete(long termCount, int numPages, ByteBuffer termBuffer) {
        this.termCount += termCount;

        this.numPages = numPages;
        this.termBuffer = termBuffer;

        initialRound = false;
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
    public Iterator<T> iterator() {
        return streamingIterator();
    }

    /**
     * Return true if the given term read from the cache is invalid.
     * Under normal streaming circumstances this cannot happen,
     * but children may encounter different situations (like online inference where atoms can be deleted).
     */
    public boolean rejectCacheTerm(T term) {
        return false;
    }

    @Override
    public long size() {
        return termCount;
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

        if (!rule.supportsIndividualGrounding()) {
            if (warnRules) {
                log.warn("Streaming term stores do not support rules that cannot individually ground (arithmetic rules with summations): " + rule);
            }

            return false;
        }

        return true;
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
}
