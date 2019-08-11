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

import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

/**
 * Iterate over all the terms that come up from grounding.
 * On this first iteration, we will build the term cache up from ground rules
 * and flush the terms to disk.
 */
public class DCDStreamingInitialRoundIterator implements DCDStreamingIterator {
    private DCDStreamingTermStore parentStore;
    private DCDTermGenerator termGenerator;
    private AtomManager atomManager;

    private List<WeightedLogicalRule> rules;
    private int currentRule;

    private List<DCDObjectiveTerm> termCache;
    private List<DCDObjectiveTerm> termPool;

    private ByteBuffer termBuffer;
    private ByteBuffer lagrangeBuffer;

    private int termCount;

    // The iteratble is kept around for cleanup.
    private QueryResultIterable queryIterable;
    private Iterator<Constant[]> queryResults;

    private boolean closed;

    private DCDObjectiveTerm nextTerm;

    private int pageSize;
    private int numPages;

    public DCDStreamingInitialRoundIterator(
            DCDStreamingTermStore parentStore, List<WeightedLogicalRule> rules,
            AtomManager atomManager, DCDTermGenerator termGenerator,
            List<DCDObjectiveTerm> termCache, List<DCDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer lagrangeBuffer,
            int pageSize) {
        this.parentStore = parentStore;
        this.termGenerator = termGenerator;
        this.atomManager = atomManager;

        this.rules = rules;
        currentRule = -1;

        this.termCache = termCache;
        this.termCache.clear();

        this.termPool = termPool;
        this.termPool.clear();

        this.termBuffer = termBuffer;
        this.lagrangeBuffer = lagrangeBuffer;

        this.pageSize = pageSize;
        numPages = 0;

        termCount = 0;

        queryIterable = null;
        queryResults = null;

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
        // The cache is full, drop it to disk.
        if (termCache.size() >= pageSize) {
            flushCache();
        }

        DCDObjectiveTerm term = fetchNextTermFromRule();
        if (term != null) {
            termCount++;
        }

        return term;
    }

    private DCDObjectiveTerm fetchNextTermFromRule() {
        // Note that it is possible to not get a term from a ground rule.
        DCDObjectiveTerm term = null;
        while (term == null) {
            GroundRule groundRule = fetchNextGroundRule();
            if (groundRule == null) {
                // We are out of ground rules, and therefore out of terms.
                return null;
            }

            term = termGenerator.createTerm(groundRule, parentStore);
        }

        termCache.add(term);

        // If we are on the first page, set aside the term for reuse.
        if (numPages == 0) {
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

    private void flushCache() {
        // If is possible to get two flush requests in a row, so check to see if we actually need it.
        if (termCache.size() == 0) {
            return;
        }

        flushLagrangeCache();
        flushTermCache();

        // Move on to the next page.
        numPages++;
    }

    private void flushTermCache() {
        // Count the exact size we will need to write.
        int termsSize = 0;
        for (DCDObjectiveTerm term : termCache) {
            termsSize += term.fixedByteSize();
        }

        // Allocate an extra two ints for the number of terms and size of terms in that page.
        int termBufferSize = termsSize + (Integer.SIZE / 8) * 2;

        if (termBuffer == null || termBuffer.capacity() < termBufferSize) {
            termBuffer = ByteBuffer.allocate((int)(termBufferSize * DCDStreamingTermStore.OVERALLOCATION_RATIO));
        }
        termBuffer.clear();

        // First put the size of the terms and number of terms.
        termBuffer.putInt(termsSize);
        termBuffer.putInt(termCache.size());

        // Now put in all the terms.
        for (DCDObjectiveTerm term : termCache) {
            term.writeFixedValues(termBuffer);
        }

        String termPagePath = parentStore.getTermPagePath(numPages);
        try (FileOutputStream stream = new FileOutputStream(termPagePath)) {
            stream.write(termBuffer.array(), 0, termBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write term cache page: " + termPagePath, ex);
        }

        termCache.clear();
    }

    private void flushLagrangeCache() {
        int lagrangeBufferSize = (Float.SIZE / 8) * termCache.size();

        if (lagrangeBuffer == null || lagrangeBuffer.capacity() < lagrangeBufferSize) {
            lagrangeBuffer = ByteBuffer.allocate((int)(lagrangeBufferSize * DCDStreamingTermStore.OVERALLOCATION_RATIO));
        }
        lagrangeBuffer.clear();

        // Put in all the lagrange values.
        for (DCDObjectiveTerm term : termCache) {
            lagrangeBuffer.putFloat(term.getLagrange());
        }

        String lagrangePagePath = parentStore.getLagrangePagePath(numPages);
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

        if (queryIterable != null) {
            queryIterable.close();
            queryIterable = null;
            queryResults = null;
        }

        parentStore.initialIterationComplete(termCount, numPages, termBuffer, lagrangeBuffer);
    }
}
