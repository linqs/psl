/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;

import java.util.List;


/**
 * A batch generator that uses the neural model to define batches.
 * Each batch shares the same symbolic potentials, i.e., the same terms and term store,
 * but may have different neural inputs.
 */
public class NeuralBatchGenerator extends BatchGenerator {
    int batchCount;
    int numBatches;

    public NeuralBatchGenerator(InferenceApplication inferenceApplication, SimpleTermStore<? extends ReasonerTerm> fullTermStore, List<DeepPredicate> deepPredicates) {
        super(inferenceApplication, fullTermStore, deepPredicates);

        assert deepPredicates.size() >= 1;

        batchCount = 0;
        numBatches = -1;
    }

    @Override
    public int numBatches() {
        // Warning: This method must be called after at least one epoch has been completed to get the correct value.
        return numBatches;
    }

    @Override
    public int epochStart() {
        DeepPredicate.epochStartAllDeepPredicates();

        batchCount = 0;

        return 0;
    }

    @Override
    public void generateBatchTermStores() {
        batchTermStores.add(fullTermStore.copy());
    }

    @Override
    public void permuteBatchOrdering() {
        // Do nothing.
    }

    @Override
    public int nextBatch() {
        DeepPredicate.nextBatchAllDeepPredicates();

        batchCount++;

        return 0;
    }

    @Override
    public void epochEnd() {
        DeepPredicate.epochEndAllDeepPredicates();

        numBatches = batchCount;
    }

    @Override
    public boolean isEpochComplete() {
        return DeepPredicate.isEpochCompleteAllDeepPredicates();
    }
}
