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
 * A trivial BatchGenerator that just creates a single batch containing all the data.
 */
public class FullBatchGenerator extends BatchGenerator {

    public FullBatchGenerator(InferenceApplication inferenceApplication, SimpleTermStore<? extends ReasonerTerm> fullTermStore, List<DeepPredicate> deepPredicates) {
        super(inferenceApplication, fullTermStore, deepPredicates);
    }

    @Override
    public void generateBatchTermStores() {
        batchTermStores.add(fullTermStore.copy());
    }
}
