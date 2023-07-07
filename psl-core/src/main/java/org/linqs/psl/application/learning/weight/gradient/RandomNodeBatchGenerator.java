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
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;

import java.util.*;

public class RandomNodeBatchGenerator extends LearningBatchGenerator {
    private final int numBatches;
    private final int batchSize;
    private final int bfsDepth;

    public RandomNodeBatchGenerator(InferenceApplication inferenceApplication) {
        super(inferenceApplication);

        numBatches = Options.RANDOM_NODE_BATCH_GENERATOR_NUM_BATCHES.getInt();
        batchSize = (int) Math.ceil(((float) fullTermStore.getAtomStore().getNumRVAtoms()) / numBatches);
        bfsDepth = Options.RANDOM_NODE_BATCH_GENERATOR_BFS_DEPTH.getInt();
    }

    @Override
    public void generateBatches() {
        // Clear out any old batches.
        clear();

        // Randomly sample batchSize number of random variable atoms from the full atom store and create a new term store and atom store for each batch.
        ArrayList<GroundAtom> allAtoms = new ArrayList<GroundAtom>(Arrays.asList(Arrays.copyOf(fullTermStore.getAtomStore().getAtoms(), fullTermStore.getAtomStore().size())));
        Collections.shuffle(allAtoms);

        for (int i = 0; i < numBatches; i++) {
            AtomStore batchAtomStore = new AtomStore();
            SimpleTermStore<? extends ReasonerTerm> batchTermStore = (SimpleTermStore<? extends ReasonerTerm>)inferenceApplication.createTermStore();
            batchTermStore.setAtomStore(batchAtomStore);
            batchTermStores.add(batchTermStore);

            HashSet<ReasonerTerm> visitedTerms = new HashSet<>();
            HashSet<GroundAtom> visitedAtoms = new HashSet<>();

            // The last batch may be smaller than the rest.
            while ((batchAtomStore.size() < batchSize) && !allAtoms.isEmpty()) {
                GroundAtom originalAtom = allAtoms.remove(0);

                if (originalAtom.isFixed()) {
                    continue;
                }

                // Make a copy of the atom so that the batch atom store can be modified without affecting the full atom store.
                RandomVariableAtom newBatchRVAtom = (RandomVariableAtom)originalAtom.copy();
                newBatchRVAtom.clearTerms();

                if (!visitedAtoms.contains(originalAtom)) {
                    batchAtomStore.addAtom(newBatchRVAtom);
                }

                // Perform a bfs on the factor graph starting from the sampled atoms to obtain batch terms.
                ArrayList<ReasonerTerm> bfsCurrentDepthQueue = new ArrayList<ReasonerTerm>(originalAtom.getTerms());
                for (int depth = 0; depth < bfsDepth; depth++) {
                    ArrayList<ReasonerTerm> bfsNextDepthQueue = new ArrayList<ReasonerTerm>();

                    for (ReasonerTerm term : bfsCurrentDepthQueue) {
                        if (visitedTerms.contains(term)) {
                            continue;
                        }

                        visitedTerms.add(term);

                        int[] originalAtomIndexes = term.getAtomIndexes();
                        int[] newAtomIndexes = new int[term.getAtomIndexes().length];
                        for (int j = 0 ; j < term.size(); j ++) {
                            int atomIndex = originalAtomIndexes[j];
                            GroundAtom atom = fullTermStore.getAtomStore().getAtom(atomIndex);

                            if (visitedAtoms.contains(atom)) {
                                newAtomIndexes[j] = batchAtomStore.getAtomIndex(atom);
                                continue;
                            }

                            visitedAtoms.add(atom);

                            GroundAtom newBatchAtom = atom.copy();
                            newBatchAtom.clearTerms();
                            batchAtomStore.addAtom(newBatchAtom);
                            newAtomIndexes[j] = batchAtomStore.getAtomIndex(atom);

                            bfsNextDepthQueue.addAll(atom.getTerms());

                            if (newBatchAtom.getPredicate() instanceof DeepPredicate) {
                                for (GroundAtom classAtom : ((DeepPredicate)newBatchAtom.getPredicate()).getDeepModel().getClasses(newBatchAtom)) {
                                    if (visitedAtoms.contains(classAtom)) {
                                        continue;
                                    }

                                    GroundAtom newBatchClassAtom = classAtom.copy();
                                    newBatchClassAtom.clearTerms();
                                    batchAtomStore.addAtom(newBatchClassAtom);

                                    visitedAtoms.add(classAtom);
                                }
                            }
                        }

                        ReasonerTerm newBatchTerm = term.copy();
                        newBatchTerm.setAtomIndexes(newAtomIndexes);
                        batchTermStore.add(newBatchTerm);
                    }

                    bfsCurrentDepthQueue = bfsNextDepthQueue;
                }
            }
        }
    }
}
