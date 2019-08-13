/**
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

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.util.RandUtils;

import org.apache.commons.lang.mutable.MutableInt;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Iterate over all the terms from the disk cache.
 * On these non-initial iterations, we will fill the term cache from disk and drain it.
 *
 * This iterator can be constructed as read-only.
 * In this case, pages will not be witten to disk.
 */
public class DCDStreamingCacheIterator implements DCDStreamingIterator {
    private DCDStreamingTermStore parentStore;
    private Map<MutableInt, RandomVariableAtom> variables;
    private int[] shuffleMap;

    private boolean readonly;

    private List<DCDObjectiveTerm> termCache;
    private List<DCDObjectiveTerm> termPool;

    private ByteBuffer termBuffer;
    private ByteBuffer lagrangeBuffer;

    private int currentPage;
    private int nextCachedTermIndex;

    private DCDObjectiveTerm nextTerm;

    private boolean shufflePage;

    // When we are reading pages from disk (after the initial round),
    // this list will tell us the order to read them in.
    // This may get shuffled depending on configuration.
    private List<Integer> pageAccessOrder;

    private boolean closed;

    private int numPages;

    public DCDStreamingCacheIterator(
            DCDStreamingTermStore parentStore, boolean readonly, Map<MutableInt, RandomVariableAtom> variables,
            List<DCDObjectiveTerm> termCache, List<DCDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer lagrangeBuffer,
            boolean shufflePage, int[] shuffleMap, boolean randomizePageAccess,
            int numPages) {
        this.parentStore = parentStore;
        this.variables = variables;
        this.shuffleMap = shuffleMap;

        this.readonly = readonly;

        this.termCache = termCache;
        this.termCache.clear();

        this.termPool = termPool;

        this.termBuffer = termBuffer;
        this.lagrangeBuffer = lagrangeBuffer;

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
        // The cache is exhaused, fill it up.
        if (nextCachedTermIndex >= termCache.size()) {
            // Flush all the lagrange terms.
            flushCache();

            // Check if there is another page, and load it if it exists.
            if (!fetchPage()) {
                // There are no more pages, we are done.
                return null;
            }
        }

        // The page has been verified, so there must be a term waiting.

        DCDObjectiveTerm term = termCache.get(nextCachedTermIndex);
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

        // Prep for the next read.
        // Note that the termBuffer should be at maximum size from the initial round.
        termBuffer.clear();
        lagrangeBuffer.clear();

        int termsSize = 0;
        int numTerms = 0;
        int headerSize = (Integer.SIZE / 8) * 2;
        int lagrangesSize = 0;

        int pageIndex = pageAccessOrder.get(currentPage).intValue();
        String termPagePath = parentStore.getTermPagePath(pageIndex);
        String lagrangePagePath = parentStore.getLagrangePagePath(pageIndex);

        try (
                FileInputStream termStream = new FileInputStream(termPagePath);
                FileInputStream lagrangeStream = new FileInputStream(lagrangePagePath)) {
            // First read the term size information.
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

        MutableInt intBuffer = new MutableInt();
        for (int i = 0; i < numTerms; i++) {
            DCDObjectiveTerm term = termPool.get(i);
            term.read(termBuffer, lagrangeBuffer, variables, intBuffer);
            termCache.add(term);
        }

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
        flushLagrangeCache();
    }

    private void flushLagrangeCache() {
        int lagrangeBufferSize = (Float.SIZE / 8) * termCache.size();

        // The buffer has already grown to maximum size in the initial round,
        // no need to reallocate.
        lagrangeBuffer.clear();

        // If this page was picked up from the cache (and not from grounding) and shuffled,
        // then we will need to use the shuffle map to write the lagrange values back in
        // the same order as the terms.
        if (shufflePage) {
            for (int shuffledIndex = 0; shuffledIndex < termCache.size(); shuffledIndex++) {
                int writeIndex = shuffleMap[shuffledIndex];
                DCDObjectiveTerm term = termCache.get(shuffledIndex);
                lagrangeBuffer.putFloat(writeIndex * (Float.SIZE / 8), term.getLagrange());
            }
        } else {
            for (DCDObjectiveTerm term : termCache) {
                lagrangeBuffer.putFloat(term.getLagrange());
            }
        }

        int pageIndex = pageAccessOrder.get(currentPage).intValue();
        String lagrangePagePath = parentStore.getLagrangePagePath(pageIndex);

        try (FileOutputStream stream = new FileOutputStream(lagrangePagePath)) {
            stream.write(lagrangeBuffer.array(), 0, lagrangeBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write lagrange cache page: " + lagrangePagePath, ex);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        flushCache();

        parentStore.cacheIterationComplete();
    }
}
