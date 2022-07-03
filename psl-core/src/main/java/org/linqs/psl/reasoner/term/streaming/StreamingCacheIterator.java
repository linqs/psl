/**
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
package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.RandUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Iterate over all the terms from the disk cache.
 * On these non-initial iterations, we will fill the term cache from disk and drain it.
 *
 * This iterator can be constructed as read-only.
 * In this case, pages will not be written to disk.
 */
public abstract class StreamingCacheIterator<T extends ReasonerTerm> implements StreamingIterator<T> {
    protected StreamingTermStore<T> parentStore;
    protected int[] shuffleMap;

    protected boolean readonly;

    protected List<T> termCache;
    protected List<T> termPool;

    protected ByteBuffer termBuffer;
    protected ByteBuffer volatileBuffer;

    protected long termCount;

    protected int currentPage;
    protected int nextCachedTermIndex;

    protected T nextTerm;

    protected boolean shufflePage;

    // When we are reading pages from disk (after the initial round),
    // this list will tell us the order to read them in.
    // This may get shuffled depending on configuration.
    protected List<Integer> pageAccessOrder;

    protected boolean closed;

    protected int numPages;

    public StreamingCacheIterator(
            StreamingTermStore<T> parentStore, boolean readonly,
            List<T> termCache, List<T> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            boolean shufflePage, int[] shuffleMap, boolean randomizePageAccess,
            int numPages) {
        this.parentStore = parentStore;
        this.shuffleMap = shuffleMap;

        this.readonly = readonly;

        this.termCache = termCache;
        this.termCache.clear();

        this.termPool = termPool;

        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        termCount = 0l;

        nextCachedTermIndex = 0;

        currentPage = -1;
        this.numPages = numPages;

        this.shufflePage = shufflePage;

        pageAccessOrder = new ArrayList<Integer>(numPages);
        for (int i = 0; i < numPages; i++) {
            pageAccessOrder.add(i);
        }

        if (randomizePageAccess) {
            RandUtils.shuffle(pageAccessOrder);
        }

        closed = false;

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

        if (closed) {
            return false;
        }

        do {
            nextTerm = fetchNextTerm();
            if (nextTerm == null) {
                close();
                return false;
            }
        } while (parentStore.rejectCacheTerm(nextTerm));

        return true;
    }

    @Override
    public T next() {
        if (nextTerm == null) {
            throw new IllegalStateException("Called next() when hasNext() == false (or before the first hasNext() call).");
        }

        termCount++;
        T term = nextTerm;
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
    private T fetchNextTerm() {
        // The cache is exhausted, fill it up.
        if (nextCachedTermIndex >= termCache.size()) {
            // Flush all the volatile terms.
            flushCache();

            // Check if there is another page, and load it if it exists.
            if (!fetchPage()) {
                // There are no more pages, we are done.
                return null;
            }
        }

        // The page has been verified, so there must be a term waiting.

        T term = termCache.get(nextCachedTermIndex);
        nextCachedTermIndex++;
        return term;
    }

    /**
     * Fetch the next page.
     * @return true if the next page was fetched and loaded (false when there are no more pages).
     */
    private boolean fetchPage() {
        // Clear the existing page cache.
        termCache.clear();

        currentPage++;
        nextCachedTermIndex = 0;

        if (currentPage >= numPages) {
            // Out of pages.
            return false;
        }

        int pageIndex = pageAccessOrder.get(currentPage).intValue();
        String termPagePath = parentStore.getTermPagePath(pageIndex);
        String volatilePagePath = parentStore.getVolatilePagePath(pageIndex);

        // Prep for the next read.
        // Note that the termBuffer should be at maximum size from the initial round.
        termBuffer.clear();
        volatileBuffer.clear();

        readPage(termPagePath, volatilePagePath);

        if (shufflePage) {
            // Remember that the shuffle map may be larger than the term cache (for not full pages).
            for (int i = 0; i < termCache.size(); i++) {
                shuffleMap[i] = i;
            }

            RandUtils.pairedShuffleIndexes(termCache, shuffleMap);
        }

        return true;
    }

    private void flushCache() {
        // We will never do any writes if the iterator is read-only.
        if (readonly) {
            return;
        }

        // We don't need to flush if there is nothing to flush.
        if (termCache.size() == 0) {
            return;
        }

        // We will clear the termCache when we fetch a new page, not on flush.
        flushVolatileCache();
    }

    private void flushVolatileCache() {
        // The buffer has already grown to maximum size in the initial round,
        // no need to reallocate.
        volatileBuffer.clear();

        int pageIndex = pageAccessOrder.get(currentPage).intValue();
        String volatilePagePath = parentStore.getVolatilePagePath(pageIndex);

        writeVolatilePage(volatilePagePath);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        flushCache();

        // All the terms have been iterated over and the volatile buffer has been flushed,
        // the term cache is now invalid.
        termCache.clear();

        parentStore.cacheIterationComplete(termCount);
    }

    /**
     * Read a page and fill the termCache using freed terms from the termPool.
     * The child is responsible for all IO, but shuffling will be handled by the parent.
     */
    protected abstract void readPage(String termPagePath, String volatilePagePath);

    /**
     * Write a cache page to disk.
     * Unlike readPage, the child is responsible for undoing any shuffling via shuffleMap.
     */
    protected abstract void writeVolatilePage(String volatilePagePath);
}
