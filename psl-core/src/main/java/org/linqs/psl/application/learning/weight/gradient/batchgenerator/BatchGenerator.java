/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight.gradient.batchgenerator;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.deep.DeepModelPredicate;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.Reflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * A class for generating batches of data for learning.
 * A batch in this case is a set of terms and corresponding atoms defining a subgraph of the complete factor graph.
 */
public abstract class BatchGenerator {
    private static final Logger log = LoggerFactory.getLogger(BatchGenerator.class);

    protected InferenceApplication inferenceApplication;
    protected SimpleTermStore<? extends ReasonerTerm> fullTermStore;
    protected AtomStore fullTruthAtomStore;
    protected List<DeepPredicate> deepPredicates;

    protected List<SimpleTermStore<? extends ReasonerTerm>> batchTermStores;
    protected List<AtomStore> batchTruthAtomStores;
    protected List<TrainingMap> batchTrainingMaps;
    protected List<List<DeepModelPredicate>> batchDeepModelPredicates;

    protected ArrayList<Integer> batchPermutation;
    protected int currentBatchPermutationIndex;


    public BatchGenerator(InferenceApplication inferenceApplication, SimpleTermStore<? extends ReasonerTerm> fullTermStore,
                          List<DeepPredicate> deepPredicates, AtomStore fullTruthAtomStore) {
        this.inferenceApplication = inferenceApplication;
        this.fullTermStore = fullTermStore;
        this.fullTruthAtomStore = fullTruthAtomStore;
        this.deepPredicates = deepPredicates;

        batchTermStores = new ArrayList<SimpleTermStore<? extends ReasonerTerm>>();
        batchTruthAtomStores = new ArrayList<AtomStore>();
        batchTrainingMaps = new ArrayList<TrainingMap>();
        batchDeepModelPredicates = new ArrayList<List<DeepModelPredicate>>();
        batchPermutation = new ArrayList<Integer>();

        currentBatchPermutationIndex = -1;
    }

    /**
     * Return the number of batch term stores.
     */
    public int numBatchTermStores() {
        return batchTermStores.size();
    }

    public int numBatches() {
        return batchTermStores.size();
    }

    public List<SimpleTermStore<? extends ReasonerTerm>> getBatchTermStores() {
        return batchTermStores;
    }

    public SimpleTermStore<? extends ReasonerTerm> getBatchTermStore(int index) {
        return batchTermStores.get(index);
    }

    public List<List<DeepModelPredicate>> getBatchDeepModelPredicates() {
        return batchDeepModelPredicates;
    }

    public List<DeepModelPredicate> getBatchDeepModelPredicates(int index) {
        return batchDeepModelPredicates.get(index);
    }

    public List<AtomStore> getBatchTruthAtomStores() {
        return batchTruthAtomStores;
    }

    public AtomStore getBatchTruthAtomStore(int index) {
        return batchTruthAtomStores.get(index);
    }

    public List<TrainingMap> getBatchTrainingMaps() {
        return batchTrainingMaps;
    }

    public TrainingMap getBatchTrainingMap(int index) {
        return batchTrainingMaps.get(index);
    }

    public void generateBatches() {
        log.trace("Generating batches.");

        clear();

        generateBatchesInternal();

        // Generate batch training maps.
        for (int i = 0; i < numBatchTermStores(); i++) {
            batchTrainingMaps.add(new TrainingMap(batchTermStores.get(i).getAtomStore(), batchTruthAtomStores.get(i)));
        }

        // Generate batch deep model predicates.
        for (int i = 0; i < numBatchTermStores(); i++) {
            SimpleTermStore<? extends ReasonerTerm> batchTermStore = batchTermStores.get(i);
            batchDeepModelPredicates.add(new ArrayList<DeepModelPredicate>());

            // Copy all deep model predicates.
            for (DeepPredicate deepPredicate : deepPredicates) {
                DeepModelPredicate batchDeepModelPredicate = deepPredicate.getDeepModel().copy();
                batchDeepModelPredicate.setAtomStore(batchTermStore.getAtomStore(), true);
                batchDeepModelPredicates.get(i).add(batchDeepModelPredicate);
            }
        }

        // Generate initial batch permutation.
        for (int i = 0; i < numBatchTermStores(); i++) {
            batchPermutation.add(i);
        }
    }

    protected abstract void generateBatchesInternal();

    /**
     * Permute the order of the batches.
     */
    public void permuteBatchOrdering() {
        RandUtils.shuffle(batchPermutation);
    }

    /**
     * Return true if the epoch is complete, i.e., there is a next batch, false otherwise.
     */
    public boolean isEpochComplete() {
        return currentBatchPermutationIndex > batchPermutation.size() - 1;
    }

    public int epochStart() {
        DeepPredicate.epochStartAllDeepPredicates();

        currentBatchPermutationIndex++;
        return batchPermutation.get(currentBatchPermutationIndex);
    }

    public void epochEnd() {
        DeepPredicate.epochEndAllDeepPredicates();

        currentBatchPermutationIndex = -1;
    }

    /**
     * Return the index of the next batch.
     */
    public int nextBatch() {
        DeepPredicate.nextBatchAllDeepPredicates();

        currentBatchPermutationIndex++;
        if (currentBatchPermutationIndex >= batchPermutation.size()) {
            return -1;
        }
        return batchPermutation.get(currentBatchPermutationIndex);
    }

    public void clear() {
        for (SimpleTermStore<? extends ReasonerTerm> termStore : batchTermStores) {
            termStore.getAtomStore().close();
            termStore.clear();
        }
        batchTermStores.clear();

        for (List<DeepModelPredicate> deepModelPredicates : batchDeepModelPredicates) {
            deepModelPredicates.clear();
        }
        batchDeepModelPredicates.clear();

        batchPermutation.clear();
    }

    public void close() {
        for (SimpleTermStore<? extends ReasonerTerm> termStore : batchTermStores) {
            termStore.getAtomStore().close();
            termStore.close();
        }
        batchTermStores.clear();

        for (List<DeepModelPredicate> deepModelPredicates : batchDeepModelPredicates) {
            deepModelPredicates.clear();
        }
        batchDeepModelPredicates.clear();

        batchPermutation.clear();
    }

    /**
     * Construct a batch generator.
     * Look for a constructor like: (InferenceApplication inferenceApplication, SimpleTermStore<? extends ReasonerTerm> fullTermStore, List<DeepPredicate> deepPredicates).
     */
    public static BatchGenerator getBatchGenerator(String name, InferenceApplication inferenceApplication,
                                                   SimpleTermStore<? extends ReasonerTerm> fullTermStore,
                                                   List<DeepPredicate> deepPredicates, AtomStore fullTruthAtomStore) {
        String className = Reflection.resolveClassName(name);
        if (className == null) {
            throw new IllegalArgumentException("Could not find class: " + name);
        }

        Class<? extends BatchGenerator> classObject = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends BatchGenerator> uncheckedClassObject = (Class<? extends BatchGenerator>)Class.forName(className);
            classObject = uncheckedClassObject;
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Could not find class: " + className, ex);
        }

        Constructor<? extends BatchGenerator> constructor = null;
        try {
            constructor = classObject.getConstructor(InferenceApplication.class, SimpleTermStore.class, List.class, AtomStore.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("No suitable constructor found for batch generator: " + className + ".", ex);
        }

        BatchGenerator batchGenerator = null;
        try {
            batchGenerator = constructor.newInstance(inferenceApplication, fullTermStore, deepPredicates, fullTruthAtomStore);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Unable to instantiate weight learner (" + className + ")", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Insufficient access to constructor for " + className, ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Error thrown while constructing " + className, ex);
        }

        return batchGenerator;
    }
}
