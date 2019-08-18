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
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.reasoner.term.streaming.StreamingIterator;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.SystemUtils;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A term store that iterates over ground queries directly (obviating the GroundRuleStore).
 * Note that the iterators given by this class are meant to be exhaustd (at least the first time).
 * Remember that this class will internally iterate over an unknown number of groundings.
 * So interrupting the iteration can cause the term count to be incorrect.
 */
public class DCDStreamingTermStore implements DCDTermStore, StreamingTermStore<DCDObjectiveTerm> {
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

    /**
     * Warn on rules DCD can't handle.
     */
    public static final String WARN_RULES_KEY = CONFIG_PREFIX + ".warnunsupportedrules";
    public static final boolean WARN_RULES_DEFAULT = true;

    public static final int INITIAL_PATH_CACHE_SIZE = 100;

    private List<WeightedRule> rules;
    private AtomManager atomManager;

    // <Object.hashCode(), RVA>
    // Although the key is mutable, it should NEVER be changed.
    // This is for efficiency when reading in pages.
    private Map<MutableInt, RandomVariableAtom> variables;

    private List<String> termPagePaths;
    private List<String> lagrangePagePaths;

    private boolean initialRound;
    private StreamingIterator<DCDObjectiveTerm> activeIterator;
    private int seenTermCount;
    private int numPages;

    private DCDTermGenerator termGenerator;

    private int pageSize;
    private String pageDir;
    private boolean shufflePage;
    private boolean randomizePageAccess;

    private boolean warnRules;

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
    private int[] shuffleMap;

    public DCDStreamingTermStore(List<Rule> rules, AtomManager atomManager) {
        pageSize = Config.getInt(PAGE_SIZE_KEY, PAGE_SIZE_DEFAULT);
        pageDir = Config.getString(PAGE_LOCATION_KEY, PAGE_LOCATION_DEFAULT);
        shufflePage = Config.getBoolean(SHUFFLE_PAGE_KEY, SHUFFLE_PAGE_DEFAULT);
        randomizePageAccess = Config.getBoolean(RANDOMIZE_PAGE_ACCESS_KEY, RANDOMIZE_PAGE_ACCESS_DEFAULT);
        warnRules = Config.getBoolean(WARN_RULES_KEY, WARN_RULES_DEFAULT);

        Set<Atom> atomSet = new HashSet<Atom>();

        this.rules = new ArrayList<WeightedRule>();
        for (Rule rule : rules) {
            if (!rule.isWeighted()) {
                if (warnRules) {
                    log.warn("DCD does not support hard constraints: " + rule);
                }
                continue;
            }

            // HACK(eriq): This is not actually true,
            //  but I am putting it in place for efficiency reasons.
            if (((WeightedRule)rule).getWeight() < 0.0) {
                if (warnRules) {
                    log.warn("DCD does not support negative weights: " + rule);
                }
                continue;
            }

            if (!rule.supportsIndividualGrounding()) {
                if (warnRules) {
                    log.warn("DCD does not support rules that cannot individually ground (arithmetic rules with summations): " + rule);
                }
                continue;
            }

            // Don't allow explicit priors.
            if (rule instanceof WeightedLogicalRule) {
                atomSet.clear();
                atomSet = ((WeightedLogicalRule)rule).getFormula().getAtoms(atomSet);
                if (atomSet.size() == 1) {
                    if (warnRules) {
                        log.warn("DCD does not support explicit priors: " + rule);
                    }
                    continue;
                }
            } else if (rule instanceof WeightedArithmeticRule) {
                ArithmeticRuleExpression expression = ((WeightedArithmeticRule)rule).getExpression();
                if (expression.looksLikeNegativePrior()) {
                    if (warnRules) {
                        log.warn("DCD does not support explicit priors: " + rule);
                    }
                    continue;
                }
            }

            this.rules.add((WeightedRule)rule);
        }

        if (rules.size() == 0) {
            throw new IllegalArgumentException("Found no valid rules for DCD.");
        }

        this.atomManager = atomManager;
        termGenerator = new DCDTermGenerator();
        variables = new HashMap<MutableInt, RandomVariableAtom>();

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        lagrangePagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);

        initialRound = true;
        activeIterator = null;
        numPages = 0;

        termBuffer = null;
        lagrangeBuffer = null;

        SystemUtils.recursiveDelete(pageDir);
        if (pageSize <= 1) {
            throw new IllegalArgumentException("Page size is too small.");
        }

        termCache = new ArrayList<DCDObjectiveTerm>(pageSize);
        termPool = new ArrayList<DCDObjectiveTerm>(pageSize);
        shuffleMap = new int[pageSize];

        (new File(pageDir)).mkdirs();
    }

    @Override
    public boolean isLoaded() {
        return !initialRound;
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
        MutableInt key = new MutableInt(System.identityHashCode(atom));

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
            variables = new HashMap<MutableInt, RandomVariableAtom>((int)Math.ceil(capacity / 0.75));
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

    public String getTermPagePath(int index) {
        // Make sure the path is built.
        for (int i = termPagePaths.size(); i <= index; i++) {
            termPagePaths.add(Paths.get(pageDir, String.format("%08d_term.page", i)).toString());
        }

        return termPagePaths.get(index);
    }

    public String getLagrangePagePath(int index) {
        // Make sure the path is built.
        for (int i = lagrangePagePaths.size(); i <= index; i++) {
            lagrangePagePaths.add(Paths.get(pageDir, String.format("%08d_lagrange.page", i)).toString());
        }

        return lagrangePagePaths.get(index);
    }

    /**
     * A callback for the initial round iterator.
     * The ByterBuffers are here because of possible reallocation.
     */
    public void initialIterationComplete(int termCount, int numPages, ByteBuffer termBuffer, ByteBuffer lagrangeBuffer) {
        seenTermCount = termCount;
        this.numPages = numPages;
        this.termBuffer = termBuffer;
        this.lagrangeBuffer = lagrangeBuffer;

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
    @Override
    public Iterator<DCDObjectiveTerm> noWriteIterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this DCDTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            throw new IllegalStateException("A full iteration must have already been completed before asking for a read-only iterator.");
        }

        activeIterator = new DCDStreamingCacheIterator(
                this, true, variables, termCache, termPool,
                termBuffer, lagrangeBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);

        return activeIterator;
    }

    @Override
    public Iterator<DCDObjectiveTerm> iterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this DCDTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            activeIterator = new DCDStreamingInitialRoundIterator(
                    this, rules, atomManager, termGenerator,
                    termCache, termPool, termBuffer, lagrangeBuffer, pageSize);
        } else {
            activeIterator = new DCDStreamingCacheIterator(
                    this, false, variables, termCache, termPool,
                    termBuffer, lagrangeBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
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
}
