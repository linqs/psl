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

import org.linqs.psl.config.Options;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.OnlineTermStore;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.RuntimeStats;
import org.linqs.psl.util.SystemUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Variables are kept in memory, but terms are kept on disk.
 */
public abstract class StreamingTermStore<T extends ReasonerTerm> implements VariableTermStore<T, GroundAtom>, OnlineTermStore<T, GroundAtom> {
    private static final Logger log = LoggerFactory.getLogger(StreamingTermStore.class);

    private static final int INITIAL_PATH_CACHE_SIZE = 100;

    protected List<WeightedRule> rules;
    protected AtomManager atomManager;

    // Keep track of variable and observation indexes.
    protected Map<GroundAtom, Integer> variables;

    // Matching arrays for variables and observations values and atoms.
    private float[] variableValues;
    // TODO (connor): Change variableAtoms to boolean array.
    private GroundAtom[] variableAtoms;
    private boolean[] deletedAtoms;
    private int variableIndex;

    // Buffer to hold new terms
    protected Queue<T> newTermBuffer;

    protected List<String> termPagePaths;
    protected List<String> volatilePagePaths;

    protected boolean initialRound;
    protected StreamingIterator<T> activeIterator;
    protected int seenTermCount;
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

    protected boolean online;

    public StreamingTermStore(List<Rule> rules, AtomManager atomManager,
            HyperplaneTermGenerator<T, GroundAtom> termGenerator) {
        online = Options.ONLINE.getBoolean();
        pageSize = Options.STREAMING_TS_PAGE_SIZE.getInt();
        pageDir = Options.STREAMING_TS_PAGE_LOCATION.getString();
        shufflePage = Options.STREAMING_TS_SHUFFLE_PAGE.getBoolean();
        randomizePageAccess = Options.STREAMING_TS_RANDOMIZE_PAGE_ACCESS.getBoolean();
        warnRules = Options.STREAMING_TS_WARN_RULES.getBoolean();

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

        int atomCapacity = online ? atomManager.getCachedRVACount() + atomManager.getCachedOBSCount() :
                atomManager.getCachedRVACount();
        ensureVariableCapacity(atomCapacity);

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        volatilePagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        newTermBuffer = new LinkedList<T>();

        initialRound = true;
        activeIterator = null;
        numPages = 0;
        variableIndex = 0;

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

    @Override
    public boolean isLoaded() {
        return !initialRound;
    }

    @Override
    public int getNumVariables() {
        return variables.size();
    }

    @Override
    public Iterable<GroundAtom> getVariables() {
        return variables.keySet();
    }

    @Override
    public GroundAtom[] getVariableAtoms(){
        return variableAtoms;
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
        return variables.get(variable).intValue();
    }

    public boolean[] getDeletedAtoms(){
        return deletedAtoms;
    }

    @Override
    public void syncAtoms() {
        for (int i = 0; i < variableIndex; i++) {
            variableAtoms[i].setValue(variableValues[i]);
        }
    }

    @Override
    public synchronized GroundAtom createLocalVariable(GroundAtom atom) {
        if (variables.containsKey(atom)) {
            return atom;
        }

        // Got a new variable.
        if (variableIndex >= variableAtoms.length) {
            ensureVariableCapacity(variableAtoms.length * 2 + 1);
        }

        variables.put(atom, variableIndex);
        variableValues[variableIndex] = atom.getValue();
        variableAtoms[variableIndex] = atom;
        variableIndex++;

        return atom;
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Variable capacity must be non-negative. Got: " + capacity);
        }

        if (variables == null || variables.size() == 0) {
            // If there are no variables, then (re-)allocate the variable storage.
            // The default load factor for Java HashSets is 0.75.
            variables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));

            variableValues = new float[capacity];
            variableAtoms = new GroundAtom[capacity];
            deletedAtoms = new boolean[capacity];
        } else if (variableAtoms.length < capacity) {
            // Don't bother with small reallocations, if we are reallocating make a lot of room.
            if (capacity < variableAtoms.length * 2) {
                capacity = variableAtoms.length * 2;
            }

            // Reallocate and copy over variables.
            Map<GroundAtom, Integer> newVariables = new HashMap<GroundAtom, Integer>((int)Math.ceil(capacity / 0.75));
            newVariables.putAll(variables);
            variables = newVariables;

            variableValues = Arrays.copyOf(variableValues, capacity);
            variableAtoms = Arrays.copyOf(variableAtoms, capacity);
            deletedAtoms = Arrays.copyOf(deletedAtoms, capacity);
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

    @Override
    public synchronized void addTerm(T term) { return; }

    @Override
    public boolean deletedTerm(T term) { return false; }

    @Override
    public boolean updateTerm(T term) {
        return false;
    }

    @Override
    public void addAtom(Predicate predicate, Constant[] arguments, float newValue, boolean readPartition) {
        if (atomManager.getDatabase().hasCachedAtom(new QueryAtom(predicate, arguments))) {
            deleteAtom(predicate, arguments);
        }

        if (readPartition) {
            ((OnlineAtomManager)atomManager).addObservedAtom(predicate, newValue, arguments);
        } else {
            ((OnlineAtomManager)atomManager).addRandomVariableAtom((StandardPredicate) predicate, arguments);
        }
    }

    @Override
    public void deleteAtom(Predicate predicate, Constant[] arguments) {
        GroundAtom atom = atomManager.getAtom(predicate, arguments);
        if (variables.containsKey(atom)) {
            deletedAtoms[getVariableIndex(atom)] = true;
            variables.remove(atom);
        }

        if (atomManager.getDatabase().hasCachedAtom(new QueryAtom(predicate, arguments))) {
            atomManager.getDatabase().deleteAtom(atom);
        }
    }

    @Override
    public synchronized void updateAtom(Predicate predicate, Constant[] arguments, float newValue){
        // add the atom and newValue to the updates map for cache iterator
        GroundAtom atom = atomManager.getAtom(predicate, arguments);
        if (variables.containsKey(atom)) {
            variableValues[getVariableIndex(atom)] = newValue;
            variableAtoms[getVariableIndex(atom)].setValue(newValue);
        }
    }

    @Override
    public void rewriteLastPage() {
        if (newTermBuffer.size() <= 0){
            return;
        }

        termBuffer.clear();
        termCache.clear();

        readPage(getTermPagePath(numPages - 1), getVolatilePagePath(numPages - 1));
        while (true) {
            while (termCache.size() < pageSize && newTermBuffer.size() > 0) {
                if (numPages == 1) {
                    termPool.add(newTermBuffer.peek());
                }
                termCache.add(newTermBuffer.remove());
            }
            writeFullPage(getTermPagePath(numPages - 1), getVolatilePagePath(numPages - 1));

            if (newTermBuffer.size() <= 0) {
                break;
            }

            numPages++;
        }
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
    public synchronized void cacheIterationComplete() {
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
    public synchronized Iterator<T> iterator() {
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
    public void reset() {
        for (int i = 0; i < variableIndex; i++) {
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

    // TODO (Connor): Make these functions inside SGDStreamingCacheIterator Static.
    private void readPage(String termPagePath, String volatilePagePath) {
        int termsSize = 0;
        int numTerms = 0;
        int headerSize = (Integer.SIZE / 8) * 2;

        try (FileInputStream termStream = new FileInputStream(termPagePath)) {
            // First read the term size information.
            termStream.read(termBuffer.array(), 0, headerSize);

            termsSize = termBuffer.getInt();
            numTerms = termBuffer.getInt();

            // Now read in all the terms.
            termStream.read(termBuffer.array(), headerSize, termsSize);
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unable to read cache page: [%s].", termPagePath), ex);
        }

        // Log io.
        RuntimeStats.logDiskRead(headerSize + termsSize);

        // Convert all the terms from binary to objects.
        // Use the terms from the pool.
        for (int i = 0; i < numTerms; i++) {
            SGDObjectiveTerm term = (SGDObjectiveTerm)termPool.get(i);
            term.read(termBuffer, volatileBuffer);
            termCache.add((T)term);
        }
    }

    private void writeFullPage(String termPagePath, String volatilePagePath) {
        flushTermCache(termPagePath);

        termCache.clear();

        // SGD doesn't use a volatile buffer.
        if (volatileBuffer == null) {
            volatileBuffer = ByteBuffer.allocate(0);
        }
    }

    private void flushTermCache(String termPagePath) {
        // Count the exact size we will need to write.
        int termsSize = 0;
        double overallocation_ratio = 1.25;
        for (T term : termCache) {
            termsSize += ((SGDObjectiveTerm)term).fixedByteSize();
        }

        // Allocate an extra two ints for the number of terms and size of terms in that page.
        int termBufferSize = termsSize + (Integer.SIZE / 8) * 2;

        if (termBuffer == null || termBuffer.capacity() < termBufferSize) {
            termBuffer = ByteBuffer.allocate((int)(termBufferSize * overallocation_ratio));
        }
        termBuffer.clear();

        // First put the size of the terms and number of terms.
        termBuffer.putInt(termsSize);
        termBuffer.putInt(termCache.size());

        // Now put in all the terms.
        for (T term : termCache) {
            ((SGDObjectiveTerm)term).writeFixedValues(termBuffer);
        }

        try (FileOutputStream stream = new FileOutputStream(termPagePath)) {
            stream.write(termBuffer.array(), 0, termBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write term cache page: " + termPagePath, ex);
        }

        // Log io.
        RuntimeStats.logDiskWrite(termBufferSize);
    }
}
