/**
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.grounding.LazyGrounding;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerTerm;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Iterate over all the terms that come up from grounding.
 * On this iteration, we will build the term cache up from ground rules.
 * There may or may not be existing term pages stored on disk.
 */
// TODO (Charles): Currently we always start writing to new pages and do not fill up partially filled term pages.
public abstract class StreamingGroundingIterator<T extends ReasonerTerm> implements StreamingIterator<T> {
    // How much to over-allocate by.
    public static final double OVERALLOCATION_RATIO = 1.25;

    protected StreamingTermStore<T> parentStore;
    protected HyperplaneTermGenerator<T, GroundAtom> termGenerator;
    protected AtomManager atomManager;

    protected List<? extends Rule> rules;
    protected int currentRule;

    // Because arithmetic rules can create multiple groundings per query result,
    // we have to keep track of doubles and make sure they get returned.
    protected List<GroundRule> pendingGroundRules;

    protected List<T> termCache;
    protected List<T> termPool;

    protected ByteBuffer termBuffer;
    protected ByteBuffer volatileBuffer;

    protected long newTermCount;

    // Predicates that are currently being used for grounding.
    private Set<StandardPredicate> onlinePredicates;

    // The iterable is kept around for cleanup.
    protected QueryResultIterable queryIterable;
    protected Iterator<Constant[]> queryResults;

    protected boolean closed;

    protected T nextTerm;

    protected int pageSize;
    protected int numPages;

    public StreamingGroundingIterator(
            StreamingTermStore<T> parentStore, List<? extends Rule> rules,
            AtomManager atomManager, HyperplaneTermGenerator<T, GroundAtom> termGenerator,
            List<T> termCache, List<T> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            int pageSize, int numPages) {
        this.parentStore = parentStore;
        this.termGenerator = termGenerator;
        this.atomManager = atomManager;

        this.rules = rules;
        currentRule = -1;

        pendingGroundRules = new ArrayList<GroundRule>();

        this.termCache = termCache;
        this.termCache.clear();

        this.termPool = termPool;
        if (this.parentStore.isInitialRound()) {
            // Initial round, clear termPool.
            this.termPool.clear();
        }

        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        onlinePredicates = new HashSet<StandardPredicate>();

        if (!this.parentStore.isInitialRound()) {
            // Not initial round, ready the new atoms in the atom manager for partial grounding.
            Set<GroundAtom> obsAtomCache = ((OnlineAtomManager)atomManager).flushNewObservedAtoms();
            Set<GroundAtom> rvAtomCache = ((OnlineAtomManager)atomManager).flushNewRandomVariableAtoms();

            onlinePredicates.addAll(LazyGrounding.getLazyPredicates(obsAtomCache));
            onlinePredicates.addAll(LazyGrounding.getLazyPredicates(rvAtomCache));

            atomManager.getDatabase().commit(obsAtomCache, Partition.SPECIAL_READ_ID);
            atomManager.getDatabase().commit(rvAtomCache, Partition.SPECIAL_WRITE_ID);

            // Only ground the rules for which there is a lazy target.
            this.rules = new ArrayList<>(LazyGrounding.getLazyRules(this.rules, onlinePredicates));
        }

        this.pageSize = pageSize;
        this.numPages = numPages;

        newTermCount = 0;

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

        T term = fetchNextTermFromRule();
        if (term != null) {
            newTermCount++;
        }

        return term;
    }

    private T fetchNextTermFromRule() {
        // Note that it is possible to not get a term from a ground rule.
        T term = null;
        while (term == null) {
            GroundRule groundRule = fetchNextGroundRule();
            if (groundRule == null) {
                // We are out of ground rules, and therefore out of terms.
                return null;
            }

            term = termGenerator.createTerm(groundRule, parentStore);
        }

        termCache.add(term);

        // Fill up termPool for reuse.
        if (termCache.size() > termPool.size()) {
            termPool.add(term);
        }

        return term;
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

            Boolean validRule = true;
            while (pendingGroundRules.size() > 0) {
                GroundRule groundRule = pendingGroundRules.remove(pendingGroundRules.size() - 1);
                // Validate rule.
                if (groundRule == null) {
                    validRule = false;
                } else if (!parentStore.isInitialRound()) {
                    for (GroundAtom atom : groundRule.getAtoms()) {
                        // We do not want to create new atoms when partial grounding.
                        if (!parentStore.isCachedAtom(atom)) {
                            validRule = false;
                            break;
                        }
                    }
                }

                if (validRule) {
                    return groundRule;
                } else {
                    validRule = true;
                }
            }
        }

        currentRule++;
        if (currentRule >= rules.size()) {
            // There are no more rules, we are done.
            return null;
        }

        // Start grounding the next rule.
        if (!parentStore.isInitialRound()) {
            do {
                queryIterable = getLazyGroundingIterable();
            } while((queryIterable == null) && (currentRule < rules.size()));
        } else {
            queryIterable = getFullGroundingIterable();
        }

        if (queryIterable != null) {
            queryResults = queryIterable.iterator();
            return fetchNextGroundRule();
        } else {
            return null;
        }
    }

    private QueryResultIterable getFullGroundingIterable() {
        return ((RDBMSDatabase)atomManager.getDatabase()).executeQueryIterator(rules.get(currentRule).getGroundingQuery(atomManager));
    }

    private QueryResultIterable getLazyGroundingIterable() {
        QueryResultIterable queryResultIterable = null;

        while (currentRule < rules.size()) {
            // Find a rule that supports grounding query rewriting.
            if (!rules.get(currentRule).supportsGroundingQueryRewriting()) {
                currentRule++;
            }

            queryResultIterable = LazyGrounding.getLazyGroundingResults(rules.get(currentRule), onlinePredicates, atomManager.getDatabase());

            // Check if there were any grounding results from query.
            if (queryResultIterable != null) {
                break;
            } else {
                currentRule++;
            }
        }

        return queryResultIterable;
    }

    private void flushCache() {
        // It is possible to get two flush requests in a row, so check to see if we actually need it.
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

        // Move all the new atoms out of the lazy partition and into the write partition.
        if (!parentStore.isInitialRound()) {
            for (StandardPredicate onlinePredicate : onlinePredicates) {
                atomManager.getDatabase().moveToPartition(onlinePredicate, Partition.SPECIAL_WRITE_ID,
                        atomManager.getDatabase().getWritePartition().getID());
                atomManager.getDatabase().moveToPartition(onlinePredicate, Partition.SPECIAL_READ_ID,
                        ((OnlineAtomManager)atomManager).getOnlineReadPartition());
            }
        }

        parentStore.groundingIterationComplete(newTermCount, numPages, termBuffer, volatileBuffer);
    }

    /**
     * Write a full page (including any volatile page that the child may use).
     * This is responsible for creating/reallocating both the term buffer and volatile buffer.
     */
    protected abstract void writeFullPage(String termPagePath, String volatilePagePath);
}
