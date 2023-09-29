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
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;

import java.util.List;


/**
 * A trivial BatchGenerator that just creates a single batch containing all the data.
 */
public class ConnectedComponentBatchGenerator extends BatchGenerator {

    private int batchSize;

    public ConnectedComponentBatchGenerator(InferenceApplication inferenceApplication, SimpleTermStore<? extends ReasonerTerm> fullTermStore, List<DeepPredicate> deepPredicates) {
        super(inferenceApplication, fullTermStore, deepPredicates);

        batchSize = Options.WLA_CONNECTED_COMPONENT_BATCH_SIZE.getInt();
    }

    @Override
    public void generateBatchTermStores() {
        AtomStore fullAtomStore = fullTermStore.getAtomStore();

        AtomStore batchAtomStore = new AtomStore();
        SimpleTermStore<? extends ReasonerTerm> batchTermStore = (SimpleTermStore<? extends ReasonerTerm>) inferenceApplication.createTermStore();
        batchTermStore.setAtomStore(batchAtomStore);

        int batchNumComponents = 0;
        for (List<? extends ReasonerTerm> component : fullTermStore.getConnectedComponents().values()) {

            // If we have reached the batch size, add the current batch to the list of batches and start a new batch.
            if (batchNumComponents >= batchSize) {
                batchTermStores.add(batchTermStore);
                batchNumComponents = 0;

                batchAtomStore = new AtomStore();
                batchTermStore = (SimpleTermStore<? extends ReasonerTerm>) inferenceApplication.createTermStore();
                batchTermStore.setAtomStore(batchAtomStore);
            }

            for (ReasonerTerm originalTerm : component) {
                ReasonerTerm batchTerm = originalTerm.copy();

                int[] originalAtomIndexes = originalTerm.getAtomIndexes();
                int[] newAtomIndexes = new int[originalTerm.getAtomIndexes().length];
                for (int i = 0; i < batchTerm.size(); i++) {
                    GroundAtom atom = fullAtomStore.getAtom(originalAtomIndexes[i]);
                    if (!batchAtomStore.hasAtom(atom)) {
                        batchAtomStore.addAtom(atom.copy());
                    }

                    newAtomIndexes[i] = batchAtomStore.getAtomIndex(atom);
                }
                batchTerm.setAtomIndexes(newAtomIndexes);

                batchTermStore.add(batchTerm);
            }

            batchNumComponents++;
        }

        // Add the last batch if it is not empty.
        if (batchNumComponents > 0) {
            batchTermStores.add(batchTermStore);
        }
    }
}
