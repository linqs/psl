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
package org.linqs.psl.application.learning.weight.gradient;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;

import java.util.ArrayList;
import java.util.List;

/**
 * A class for generating batches of data for learning.
 * A batch in this case is a set of atoms and terms defining a subgraph of the complete factor graph.
 */
public abstract class LearningBatchGenerator {
    protected SimpleTermStore<? extends ReasonerTerm> fullTermStore;
    protected List<SimpleTermStore<? extends ReasonerTerm>> batchTermStores;

    protected InferenceApplication inferenceApplication;


    public LearningBatchGenerator(InferenceApplication inferenceApplication) {
        assert inferenceApplication.getTermStore() instanceof SimpleTermStore;
        this.inferenceApplication = inferenceApplication;
        this.fullTermStore = (SimpleTermStore<? extends ReasonerTerm>)inferenceApplication.getTermStore();

        batchTermStores = new ArrayList<SimpleTermStore<? extends ReasonerTerm>>();
    }

    public int getNumBatches() {
        return batchTermStores.size();
    }

    public List<SimpleTermStore<? extends ReasonerTerm>> getBatchTermStores() {
        return batchTermStores;
    }

    public SimpleTermStore<? extends ReasonerTerm> getBatchTermStore(int index) {
        return batchTermStores.get(index);
    }

    public abstract void generateBatches();

    public void clear() {
        for (SimpleTermStore<? extends ReasonerTerm> termStore : batchTermStores) {
            termStore.getAtomStore().close();
            termStore.clear();
        }

        batchTermStores.clear();
    }

    public void close() {
        for (SimpleTermStore<? extends ReasonerTerm> termStore : batchTermStores) {
            termStore.getAtomStore().close();
            termStore.close();
        }
    }
}
