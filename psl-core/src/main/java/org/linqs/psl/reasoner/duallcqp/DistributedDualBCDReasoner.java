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
package org.linqs.psl.reasoner.duallcqp;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.reasoner.duallcqp.term.DualLCQPObjectiveTerm;
import org.linqs.psl.reasoner.duallcqp.term.DualLCQPTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Parallel;

import java.util.List;

/**
 * A distributed variant of the DualBCDReasoner.
 * Note that unlike the DualBCDReasoner, this reasoner does not guarantee an increase in the dual
 * objective at every iteration as the solution to the stepsize subproblem may be inexact.
 * Practically, this means that this reasoner may not converge to the optimal solution in as
 * few iterations as the DualBCDReasoner.
 * However, this reasoner does have a lower per iteration runtime.
 */
public class DistributedDualBCDReasoner extends DualBCDReasoner {
    private static final org.linqs.psl.util.Logger log = Logger.getLogger(DistributedDualBCDReasoner.class);

    private int blockSize;
    private int numTermBlocks;

    public DistributedDualBCDReasoner() {
        super();

        blockSize = -1;
        numTermBlocks = -1;
    }

    @Override
    protected void internalOptimize(DualLCQPTermStore termStore) {
        Parallel.count(numTermBlocks, new BlockUpdateWorker(termStore, blockSize, regularizationParameter));
    }

    @Override
    protected void initForOptimization(TermStore<DualLCQPObjectiveTerm> termStore) {
        super.initForOptimization(termStore);

        blockSize = (int) (termStore.size() / (Parallel.getNumThreads() * 4) + 1);
        numTermBlocks = (int) Math.ceil(termStore.size() / (double)blockSize);
    }

    private static class BlockUpdateWorker extends Parallel.Worker<Long> {
        private final DualLCQPTermStore termStore;
        private final int blockSize;
        private final float regularizationParameter;

        public BlockUpdateWorker(DualLCQPTermStore termStore, int blockSize, float regularizationParameter) {
            super();

            this.termStore = termStore;
            this.blockSize = blockSize;
            this.regularizationParameter = regularizationParameter;
        }

        @Override
        public Object clone() {
            return new BlockUpdateWorker(termStore, blockSize, regularizationParameter);
        }

        @Override
        public void work(long blockIndex, Long ignore) {
            long numTerms = termStore.size();

            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
                int termIndex = (int) (blockIndex * blockSize + innerBlockIndex);

                if (termIndex >= numTerms) {
                    break;
                }

                DualLCQPObjectiveTerm term = termStore.get(termIndex);

                if (!term.isActive()) {
                    continue;
                }

                dualBlockUpdate(term, termStore, regularizationParameter);
            }
        }
    }
}
