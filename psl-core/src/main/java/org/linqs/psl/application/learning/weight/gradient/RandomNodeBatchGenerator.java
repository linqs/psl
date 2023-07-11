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
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.util.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class RandomNodeBatchGenerator extends LearningBatchGenerator {
    private static final Logger log = Logger.getLogger(RandomNodeBatchGenerator.class);

    private final int batchSize;
    private final int bfsDepth;

    public RandomNodeBatchGenerator(InferenceApplication inferenceApplication) {
        super(inferenceApplication);

        batchSize = Options.RANDOM_NODE_BATCH_GENERATOR_BATCH_SIZE.getInt();
        bfsDepth = Options.RANDOM_NODE_BATCH_GENERATOR_BFS_DEPTH.getInt();
    }

    @Override
    public void generateBatches() {
        // Clear out any old batches.
        clear();

        // Randomly sample batchSize number of random variable atoms from the full atom store and create a new term store and atom store for each batch.
        ArrayList<GroundAtom> allAtoms = new ArrayList<GroundAtom>(Arrays.asList(Arrays.copyOf(fullTermStore.getAtomStore().getAtoms(), fullTermStore.getAtomStore().size())));
        Collections.shuffle(allAtoms);

        HashSet<GroundAtom> batchSourceAtoms = new HashSet<GroundAtom>();
        HashSet<ReasonerTerm> visitedTerms = new HashSet<ReasonerTerm>();
        HashSet<GroundAtom> visitedAtoms = new HashSet<GroundAtom>();
        int numBatches = 0;
        while (!allAtoms.isEmpty()) {
            AtomStore batchAtomStore = new AtomStore();
            SimpleTermStore<? extends ReasonerTerm> batchTermStore = (SimpleTermStore<? extends ReasonerTerm>)inferenceApplication.createTermStore();
            batchTermStore.setAtomStore(batchAtomStore);
            batchTermStores.add(batchTermStore);

            visitedTerms.clear();
            visitedAtoms.clear();

            // The last batch may be smaller than the rest.
            int currentBatchSize = 0;
            while ((currentBatchSize < batchSize) && !allAtoms.isEmpty()) {
                GroundAtom originalAtom = allAtoms.remove(0);

                // Skip observed atoms and atoms that are not batch sources and atoms that are already batch sources.
                if ((originalAtom instanceof ObservedAtom)
                        || !Boolean.parseBoolean(originalAtom.getPredicate().getPredicateOptions().get("batch-source"))
                        || batchSourceAtoms.contains(originalAtom)) {
                    continue;
                }
                batchSourceAtoms.add(originalAtom);
                currentBatchSize++;

                // Perform a bfs on the factor graph starting from the sampled atom to obtain batch terms.
                HashSet<ReasonerTerm> bfsCurrentDepth = new HashSet<ReasonerTerm>(originalAtom.getTerms());

                // If this is a categorical atom, add all the atoms and terms associated with the category atoms.
                if (originalAtom.getPredicate().getPredicateOptions().containsKey("class-size")) {
                    int classSize = Integer.parseInt(originalAtom.getPredicate().getPredicateOptions().get("class-size"));
                    for (int i = 0; i < classSize; i++) {
                        // Add the atoms and terms associated with the category atoms.
                        for (GroundAtom groundAtom : getClasses(originalAtom, classSize)) {
                            batchSourceAtoms.add(groundAtom);
                            bfsCurrentDepth.addAll(groundAtom.getTerms());
                        }
                    }
                }

                for (int depth = 0; depth < bfsDepth; depth++) {
                    HashSet<ReasonerTerm> bfsNextDepth = new HashSet<ReasonerTerm>();

                    for (ReasonerTerm term : bfsCurrentDepth) {
                        if (visitedTerms.contains(term) || !term.isActive()) {
                            continue;
                        }
                        visitedTerms.add(term);

                        // The copied term with have different atom indexes to align with the new atom store for the batch.
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
                            newAtomIndexes[j] = batchAtomStore.getAtomIndex(newBatchAtom);

                            if (!(atom instanceof ObservedAtom)) {
                                bfsNextDepth.addAll(atom.getTerms());
                            }

                            // If this is a deep predicate, we need to add all the class atoms to the batch.
                            if (newBatchAtom.getPredicate() instanceof DeepPredicate) {
                                for (GroundAtom classAtom : ((DeepPredicate)newBatchAtom.getPredicate()).getDeepModel().getClasses(newBatchAtom)) {
                                    if (visitedAtoms.contains(classAtom)) {
                                        continue;
                                    }
                                    visitedAtoms.add(classAtom);

                                    GroundAtom newBatchClassAtom = classAtom.copy();
                                    newBatchClassAtom.clearTerms();
                                    batchAtomStore.addAtom(newBatchClassAtom);

                                    bfsNextDepth.addAll(classAtom.getTerms());
                                }
                            }
                        }

                        ReasonerTerm newBatchTerm = term.copy();
                        newBatchTerm.setAtomIndexes(newAtomIndexes);
                        batchTermStore.add(newBatchTerm);
                    }

                    bfsCurrentDepth = bfsNextDepth;
                }
            }
            numBatches++;
        }
        log.trace("Batch size: " + batchSize + ", num batches: " + numBatches);
    }
}
