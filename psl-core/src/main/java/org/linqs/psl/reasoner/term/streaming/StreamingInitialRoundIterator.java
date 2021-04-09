/**
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

import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerTerm;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Iterate over all the terms that come up from grounding.
 * On this first iteration, we will build the term cache up from ground rules
 * and flush the terms to disk.
 */
public abstract class StreamingInitialRoundIterator<T extends ReasonerTerm> implements StreamingIterator<T> {
    // How much to over-allocate by.
    public static final double OVERALLOCATION_RATIO = 1.25;

    protected StreamingTermStore<T> parentStore;
    protected HyperplaneTermGenerator<T, RandomVariableAtom> termGenerator;
    protected AtomManager atomManager;

    protected List<WeightedRule> rules;
    protected int currentRule;

    // Because arithmetic rules can create multiple groundings per query result,
    // we have to keep track of doubles and make sure they get returned.
    protected List<GroundRule> pendingGroundRules;

    protected List<T> termCache;
    protected List<T> termPool;

    // Since a ground rule can produce multiple terms,
    // we need to keep multiple terms until they are officially passed out the iterator.
    private List<T> newTerms;

    protected ByteBuffer termBuffer;
    protected ByteBuffer volatileBuffer;

    protected long termCount;

    // The iteratble is kept around for cleanup.
    protected QueryResultIterable queryIterable;
    protected Iterator<Constant[]> queryResults;

    protected boolean closed;

    protected T nextTerm;

    protected int pageSize;
    protected int numPages;

    public StreamingInitialRoundIterator(
            StreamingTermStore<T> parentStore, List<WeightedRule> rules,
            AtomManager atomManager, HyperplaneTermGenerator<T, RandomVariableAtom> termGenerator,
            List<T> termCache, List<T> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            int pageSize) {
        this.parentStore = parentStore;
        this.termGenerator = termGenerator;
        this.atomManager = atomManager;

        this.rules = rules;
        currentRule = -1;

        pendingGroundRules = new ArrayList<GroundRule>();

        this.termCache = termCache;
        this.termCache.clear();

        this.termPool = termPool;
        this.termPool.clear();

        newTerms = new ArrayList<T>();

        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

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
    public T next() {
        if (nextTerm == null) {
            throw new IllegalStateException("Called next() when hasNext() == false (or before the first hasNext() call).");
        }

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
        // The cache is full, drop it to disk.
        if (termCache.size() >= pageSize) {
            flushCache();
        }

        // If there are no terms already waiting, then fetch the next one(s).
        if (newTerms.size() == 0) {
            fetchNextTermFromRule();
        }

        // Pick off the next waiting term.
        T term = null;
        if (newTerms.size() > 0) {
            term = newTerms.remove(0);

            termCache.add(term);
            termCount++;
        }

        // If the size of this page is ever larger than the size of the pool, then set aside the term for reuse.
        if (term != null && termCache.size() > termPool.size()) {
            termPool.add(term);
        }

        return term;
    }

    /**
     * Go through the next ground rules and look for terms.
     * The results will be loaded into |newTerms|.
     * |newTerms| will be cleared and may be empty after the call if no more terms are available.
     */
    private void fetchNextTermFromRule() {
        newTerms.clear();

        // Note that it is possible to not get any terms from a ground rule.
        while (newTerms.size() == 0) {
            GroundRule groundRule = fetchNextGroundRule();
            if (groundRule == null) {
                // We are out of ground rules, and therefore out of terms.
                return;
            }

            termGenerator.createTerm(groundRule, parentStore, newTerms, null);
        }
    }

    private GroundRule fetchNextGroundRule() {
        // First check for ground rules still pending from the last query tuple.
        while (pendingGroundRules.size() > 0) {
            GroundRule groundRule = pendingGroundRules.remove(pendingGroundRules.size() - 1);
            if (groundRule != null) {
                return groundRule;
            }
        }

        // Check if there are any more results pending from the query.
        while (queryResults != null && queryResults.hasNext()) {
            Constant[] tuple = queryResults.next();
            rules.get(currentRule).ground(tuple, queryIterable.getVariableMap(), atomManager, pendingGroundRules);

            while (pendingGroundRules.size() > 0) {
                GroundRule groundRule = pendingGroundRules.remove(pendingGroundRules.size() - 1);
                if (groundRule != null) {
                    return groundRule;
                }
            }
        }

        currentRule++;
        if (currentRule >= rules.size()) {
            // There are no more rules, we are done.
            return null;
        }

        // Start grounding the next rule.
        queryIterable = ((RDBMSDatabase)atomManager.getDatabase()).executeQueryIterator(rules.get(currentRule).getGroundingQuery(atomManager));
        queryResults = queryIterable.iterator();

        return fetchNextGroundRule();
    }

    private void flushCache() {
        // If is possible to get two flush requests in a row, so check to see if we actually need it.
        if (termCache.size() == 0) {
            return;
        }

        String termPagePath = parentStore.getTermPagePath(numPages);
        String volatilePagePath = parentStore.getVolatilePagePath(numPages);

        writeFullPage(termPagePath, volatilePagePath);

        // Move on to the next page.
        numPages++;
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

        parentStore.initialIterationComplete(termCount, numPages, termBuffer, volatileBuffer);
    }

    /**
     * Write a full page (including any volatile page that the child may use).
     * This is responsible for creating/reallocating both the term buffer and volatile buffer.
     */
    protected abstract void writeFullPage(String termPagePath, String volatilePagePath);
}
