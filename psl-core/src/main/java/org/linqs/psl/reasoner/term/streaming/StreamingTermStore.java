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
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Variables are kept in memory, but terms are kept on disk.
 */
public abstract class StreamingTermStore<T extends ReasonerTerm> implements VariableTermStore<T, GroundAtom> {
    private static final Logger log = LoggerFactory.getLogger(StreamingTermStore.class);

    public static final int INITIAL_PATH_CACHE_SIZE = 100;

    protected List<Rule> rules;
    protected AtomManager atomManager;

    // Keep track of variable indexes.
    protected Map<GroundAtom, Integer> variables;

    // The count of all seen variables (dead and alive).
    // Since children we may delete variables, we need a variable specifically for the next index.
    // (Otherwise, we could just use the size of the map as the next index.)
    protected int totalVariableCount;

    // Matching arrays for variables values and atoms.
    // If the atom is null, then it has been deleted by a child.
    protected float[] variableValues;
    protected GroundAtom[] variableAtoms;

    protected int numRandomVariableAtoms;
    protected int numObservedAtoms;

    protected List<String> termPagePaths;
    protected List<String> volatilePagePaths;

    protected boolean initialRound;
    protected StreamingIterator<T> activeIterator;
    protected long termCount;
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

        ensureVariableCapacity(estimateVariableCapacity());
        numRandomVariableAtoms = 0;
        numObservedAtoms = 0;

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        volatilePagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);

        initialRound = true;
        activeIterator = null;
        termCount = 0l;
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
    public int getNumVariables() {
        return totalVariableCount;
    }

    @Override
    public int getNumRandomVariables() {
        return numRandomVariableAtoms;
    }

    @Override
    public int getNumObservedVariables() {
        return numObservedAtoms;
    }

    @Override
    public Iterable<GroundAtom> getVariables() {
        return variables.keySet();
    }

    @Override
    public float[] getVariableValues() {
        return variableValues;
    }

    @Override
    public float getVariableValue(int index) {
        return variableValues[index];
    }

    @Override
    public int getVariableIndex(GroundAtom variable) {
        Integer index = variables.get(variable);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    @Override
    public GroundAtom[] getVariableAtoms() {
        return variableAtoms;
    }

    @Override
    public double syncAtoms() {
        double movement = 0.0;

        for (int i = 0; i < totalVariableCount; i++) {
            if (variableAtoms[i] == null) {
                continue;
            }

            if (variableAtoms[i] instanceof RandomVariableAtom) {
                movement += Math.pow(variableAtoms[i].getValue() - variableValues[i], 2);
                ((RandomVariableAtom)variableAtoms[i]).setValue(variableValues[i]);
            }
        }

        return Math.sqrt(movement);
    }

    @Override
    public synchronized GroundAtom createLocalVariable(GroundAtom atom) {
        if (variables.containsKey(atom)) {
            return atom;
        }

        // Got a new variable.

        if (totalVariableCount >= variableAtoms.length) {
            ensureVariableCapacity(totalVariableCount * 2);
        }

        variables.put(atom, totalVariableCount);
        variableValues[totalVariableCount] = atom.getValue();
        variableAtoms[totalVariableCount] = atom;
        totalVariableCount++;

        if (atom instanceof RandomVariableAtom) {
            numRandomVariableAtoms++;
        } else {
            numObservedAtoms++;
        }

        return atom;
    }

    /**
     * Estimate how many total variables this term store will need to track.
     */
    protected int estimateVariableCapacity() {
        return atomManager.getCachedRVACount();
    }

    public void ensureVariableCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Variable capacity must be non-negative. Got: " + capacity);
        }

        if (variables == null || totalVariableCount == 0) {
            // If there are no variables, then (re-)allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            totalVariableCount = 0;

            variableValues = new float[capacity];
            variableAtoms = new GroundAtom[capacity];
        } else if (totalVariableCount < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < totalVariableCount * 2) {
                capacity = totalVariableCount * 2;
            }

            // Reallocate and copy over variables.
            Map<GroundAtom, Integer> newVariables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            newVariables.putAll(variables);
            variables = newVariables;

            variableValues = Arrays.copyOf(variableValues, capacity);
            variableAtoms = Arrays.copyOf(variableAtoms, capacity);
        }
    }

    @Override
    public long size() {
        return termCount;
    }

    @Override
    public void add(GroundRule rule, T term, Hyperplane hyperplane) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(long index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureCapacity(long capacity) {
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

    public String getVolatilePagePath(int index) {
        // Make sure the path is built.
        for (int i = volatilePagePaths.size(); i <= index; i++) {
            // Creating new volatile page path.
            volatilePagePaths.add(Paths.get(pageDir, String.format("%08d_volatile.page", i)).toString());
        }

        return volatilePagePaths.get(index);
    }

    /**
     * A callback for the initial round iterator.
     * The ByterBuffers are here because of possible reallocation.
     */
    public void groundingIterationComplete(long termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        this.termCount += termCount;

        this.numPages = numPages;
        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        initialRound = false;
        activeIterator = null;
    }

    /**
     * A callback for the non-initial round iterator.
     */
    public void cacheIterationComplete(long termCount) {
        this.termCount = termCount;
        activeIterator = null;
    }

    /**
     * Get an iterator that goes over all the terms for only reading.
     * Before this method can be called, a full iteration must have already been done.
     * (The cache will need to have been built.)
     */
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
    public void clear() {
        initialRound = true;
        termCount = 0l;
        numPages = 0;

        numRandomVariableAtoms = 0;
        numObservedAtoms = 0;

        if (activeIterator != null) {
            activeIterator.close();
            activeIterator = null;
        }

        if (variables != null) {
            variables.clear();
            totalVariableCount = 0;
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
    public void reset() {
        for (int i = 0; i < totalVariableCount; i++) {
            if (variableAtoms[i] == null) {
                continue;
            }

            variableValues[i] = variableAtoms[i].getValue();
        }
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

    @Override
    public void initForOptimization() {
    }

    @Override
    public void iterationComplete() {
    }

    @Override
    public void variablesExternallyUpdated() {
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
}
