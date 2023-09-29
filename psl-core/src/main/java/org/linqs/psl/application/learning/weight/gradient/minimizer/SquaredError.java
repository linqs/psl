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
package org.linqs.psl.application.learning.weight.gradient.minimizer;

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;

import java.util.List;
import java.util.Map;

/**
 * Learns parameters for a model by minimizing the squared error loss function
 * using the minimizer-based learning framework.
 */
public class SquaredError extends Minimizer {
    public SquaredError(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                        Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);
    }

    @Override
    protected float computeSupervisedLoss() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        float supervisedLoss = 0.0f;
        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            if (atomIndex == -1) {
                // This atom is not in the current batch.
                continue;
            }

            supervisedLoss += Math.pow(proxRuleObservedAtoms[rvAtomIndexToProxRuleIndex.get(atomIndex)].getValue() - observedAtom.getValue(), 2.0f);
        }

        return supervisedLoss;
    }

    @Override
    protected void addSupervisedProxRuleObservedAtomValueGradient() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            if (atomIndex == -1) {
                // This atom is not in the current batch.
                continue;
            }

            int proxRuleIndex = rvAtomIndexToProxRuleIndex.get(atomIndex);
            proxRuleObservedAtomValueGradient[proxRuleIndex] += 2.0f * (proxRuleObservedAtoms[proxRuleIndex].getValue() - observedAtom.getValue());
        }
    }
}
