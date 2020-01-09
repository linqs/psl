/*
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

import org.linqs.psl.config.Config;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.SystemUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class StreamingTermStore<T extends ReasonerTerm> implements VariableTermStore<T, RandomVariableAtom> {
    private static final Logger log = LoggerFactory.getLogger(StreamingTermStore.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "streamingtermstore";

    /**
     * Where on disk to write term pages.
     */
    public static final String PAGE_LOCATION_KEY = CONFIG_PREFIX + ".pagelocation";
    public static final String PAGE_LOCATION_DEFAULT = SystemUtils.getTempDir("streaimg_term_cache_pages");

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

    /**
     * Warn on rules the streaming term store can't handle.
     */
    public static final String WARN_RULES_KEY = CONFIG_PREFIX + ".warnunsupportedrules";
    public static final boolean WARN_RULES_DEFAULT = true;

    public static final int INITIAL_PATH_CACHE_SIZE = 100;

    protected List<WeightedRule> rules;
    protected AtomManager atomManager;

    // Keep track of variable indexes.
    protected Map<RandomVariableAtom, Integer> variables;

    // Matching arrays for variables values and atoms.
    private float[] variableValues;
    private RandomVariableAtom[] variableAtoms;

    protected List<String> termPagePaths;
    protected List<String> volatilePagePaths;

    protected boolean initialRound;
    protected StreamingIterator<T> activeIterator;
    protected int seenTermCount;
    protected int numPages;

    protected HyperplaneTermGenerator<T, RandomVariableAtom> termGenerator;

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
            HyperplaneTermGenerator<T, RandomVariableAtom> termGenerator) {
        pageSize = Config.getInt(PAGE_SIZE_KEY, PAGE_SIZE_DEFAULT);
        pageDir = Config.getString(PAGE_LOCATION_KEY, PAGE_LOCATION_DEFAULT);
        shufflePage = Config.getBoolean(SHUFFLE_PAGE_KEY, SHUFFLE_PAGE_DEFAULT);
        randomizePageAccess = Config.getBoolean(RANDOMIZE_PAGE_ACCESS_KEY, RANDOMIZE_PAGE_ACCESS_DEFAULT);
        warnRules = Config.getBoolean(WARN_RULES_KEY, WARN_RULES_DEFAULT);

        this.rules = new ArrayList<WeightedRule>();
        for (Rule rule : rules) {
            if (!rule.isWeighted()) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support hard constraints: " + rule);
                }
                continue;
            }

            // HACK(eriq): This is not actually true,
            //  but I am putting it in place for efficiency reasons.
            if (((WeightedRule)rule).getWeight() < 0.0) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support negative weights: " + rule);
                }
                continue;
            }

            if (!rule.supportsIndividualGrounding()) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support rules that cannot individually ground (arithmetic rules with summations): " + rule);
                }
                continue;
            }

            if (!supportsRule(rule)) {
                if (warnRules) {
                    log.warn("Rule not supported: " + rule);
                }

                continue;
            }

            this.rules.add((WeightedRule)rule);
        }

        if (rules.size() == 0) {
            throw new IllegalArgumentException("Found no valid rules for a streaming term store.");
        }

        this.atomManager = atomManager;
        this.termGenerator = termGenerator;
        ensureVariableCapacity(atomManager.getCachedRVACount());

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        volatilePagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);

        initialRound = true;
        activeIterator = null;
        numPages = 0;

        termBuffer = null;
        volatileBuffer = null;

        SystemUtils.recursiveDelete(pageDir);
        if (pageSize <= 1) {
            throw new IllegalArgumentException("Page size is too small.");
        }

        termCache = new ArrayList<T>(pageSize);
        termPool = new ArrayList<T>(pageSize);
        shuffleMap = new int[pageSize];

        (new File(pageDir)).mkdirs();
    }

    public boolean isLoaded() {
        return !initialRound;
    }

    public int getNumVariables() {
        return variables.size();
    }

    public Iterable<RandomVariableAtom> getVariables() {
        return variables.keySet();
    }

    @Override
    public float[] getVariableValues() {
        return variableValues;
    }

    @Override
    public int getVariableIndex(RandomVariableAtom variable) {
        return variables.get(variable).intValue();
    }

    @Override
    public void syncAtoms() {
        for (int i = 0; i < variables.size(); i++) {
            variableAtoms[i].setValue(variableValues[i]);
        }
    }

    @Override
    public synchronized RandomVariableAtom createLocalVariable(RandomVariableAtom atom) {
        if (variables.containsKey(atom)) {
            return atom;
        }

        // Got a new variable.

        if (variables.size() >= variableAtoms.length) {
            ensureVariableCapacity(variables.size() * 2);
        }

        int index = variables.size();

        variables.put(atom, index);
        variableValues[index] = RandUtils.nextFloat();
        variableAtoms[index] = atom;

        return atom;
    }

    public void ensureVariableCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Variable capacity must be non-negative. Got: " + capacity);
        }

        if (variables == null || variables.size() == 0) {
            // If there are no variables, then (re-)allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashMap<RandomVariableAtom, Integer>((int)Math.ceil(capacity / 0.75));

            variableValues = new float[capacity];
            variableAtoms = new RandomVariableAtom[capacity];
        } else if (variables.size() < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < variables.size() * 2) {
                capacity = variables.size() * 2;
            }

            // Reallocate and copy over variables.
            Map<RandomVariableAtom, Integer> newVariables = new HashMap<RandomVariableAtom, Integer>((int)Math.ceil(capacity / 0.75));
            newVariables.putAll(variables);
            variables = newVariables;

            variableValues = Arrays.copyOf(variableValues, capacity);
            variableAtoms = Arrays.copyOf(variableAtoms, capacity);
        }
    }

    @Override
    public int size() {
        return seenTermCount;
    }

    @Override
    public void add(GroundRule rule, T term) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureCapacity(int capacity) {
        throw new UnsupportedOperationException();
    }

    public String getTermPagePath(int index) {
        // Make sure the path is built.
        for (int i = termPagePaths.size(); i <= index; i++) {
            termPagePaths.add(Paths.get(pageDir, String.format("%08d_term.page", i)).toString());
        }

        return termPagePaths.get(index);
    }

    public String getVolatilePagePath(int index) {
        // Make sure the path is built.
        for (int i = volatilePagePaths.size(); i <= index; i++) {
            volatilePagePaths.add(Paths.get(pageDir, String.format("%08d_volatile.page", i)).toString());
        }

        return volatilePagePaths.get(index);
    }

    /**
     * A callback for the initial round iterator.
     * The ByterBuffers are here because of possible reallocation.
     */
    public void initialIterationComplete(int termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        seenTermCount = termCount;
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

    @Override
    public Iterator<T> iterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this StreamingTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            activeIterator = getInitialRoundIterator();
        } else {
            activeIterator = getCacheIterator();
        }

        return activeIterator;
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
     * @return true if the rule is supported.
     */
    protected abstract boolean supportsRule(Rule rule);

    /**
     * Get an iterator that will perform grounding queries and write the initial pages to disk.
     */
    protected abstract StreamingIterator<T> getInitialRoundIterator();

    /**
     * Get an iterator that will read and write from disk.
     */
    protected abstract StreamingIterator<T> getCacheIterator();

    /**
     * Get an iterator that will not write to disk.
     */
    protected abstract StreamingIterator<T> getNoWriteIterator();
}
