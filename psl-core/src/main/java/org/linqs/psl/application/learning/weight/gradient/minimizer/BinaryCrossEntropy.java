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
import org.linqs.psl.util.MathUtils;

import java.util.List;
import java.util.Map;

/**
 * Learns parameters for a model by minimizing the binary cross entropy loss function using
 * the minimizer-based learning framework.
 */
public class BinaryCrossEntropy extends Minimizer {
    public BinaryCrossEntropy(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);
    }

    @Override
    protected float computeSupervisedLoss() {
        AtomStore atomStore = inference.getDatabase().getAtomStore();

        float supervisedLoss = 0.0f;
        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);

            supervisedLoss += -1.0f * (observedAtom.getValue() * Math.log(Math.max(proxRuleObservedAtoms[atomIndex].getValue(), MathUtils.RELAXED_EPSILON_FLOAT))
                    + (1.0f - observedAtom.getValue()) * Math.log(Math.max(1.0f - proxRuleObservedAtoms[atomIndex].getValue(), MathUtils.RELAXED_EPSILON_FLOAT)));
        }

        return supervisedLoss;
    }

    @Override
    protected void addSupervisedProxRuleObservedAtomValueGradient() {
        AtomStore atomStore = inference.getDatabase().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            proxRuleObservedAtomValueGradient[atomIndex] += -1.0f * (observedAtom.getValue() / Math.max(proxRuleObservedAtoms[atomIndex].getValue(), MathUtils.RELAXED_EPSILON_FLOAT)
                    + (1.0f - observedAtom.getValue()) / Math.max(1.0f - proxRuleObservedAtoms[atomIndex].getValue(), MathUtils.RELAXED_EPSILON_FLOAT));
        }
    }
}
