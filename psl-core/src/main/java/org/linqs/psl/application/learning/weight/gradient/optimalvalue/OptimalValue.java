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
package org.linqs.psl.application.learning.weight.gradient.optimalvalue;

import org.linqs.psl.application.learning.weight.gradient.GradientDescent;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.term.ReasonerTerm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Optimal value-based weight learning losses are functions that depend on the parameters only through
 * the optimal values of various inference subproblems.
 *
 * Optimal value-based losses are typically defined as a function of the latent inference problem.
 * In the latent inference problem RandomVariableAtoms with labels are set to their observed (truth) value
 * and inference is performed over the latent variables only. Default implementations of methods for
 * computing the incompatibility of the latent variable inference problem solution are provided in this class.
 */
public abstract class OptimalValue extends GradientDescent {
    protected float[] latentInferenceIncompatibility;

    public OptimalValue(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        latentInferenceIncompatibility = new float[mutableRules.size()];
    }

    @Override
    protected void postInitGroundModel() {
        super.postInitGroundModel();

        // Set the initial value of atoms to be the current atom value.
        // This ensures that when the inference application is reset before computing the MAP state
        // the atom values that were fixed to their true labels are preserved.
        inference.setInitialValue(InitialValue.ATOM);
    }

    /**
     * A method for computing the incompatibility of rules with atoms values in their current state.
     */
    protected void computeCurrentIncompatibility(float[] incompatibilityArray) {
        // Zero out the incompatibility first.
        Arrays.fill(incompatibilityArray, 0.0f);

        float[] atomValues = inference.getTermStore().getDatabase().getAtomStore().getAtomValues();

        // Sums up the incompatibilities.
        for (int i = 0; i < mutableRules.size(); i++) {
            // Note that this cast should be unnecessary, but Java does not like the TermStore without a generic.
            for (Object rawTerm : inference.getTermStore().getTerms(mutableRules.get(i))) {
                @SuppressWarnings("unchecked")
                ReasonerTerm term = (ReasonerTerm)rawTerm;

                incompatibilityArray[i] += term.evaluateIncompatibility(atomValues);
            }
        }
    }

    /**
     * Compute the latent inference problem solution incompatibility.
     * RandomVariableAtoms with labels are fixed to their observed (truth) value.
     */
    protected void computeLatentInferenceIncompatibility() {
        fixLabeledRandomVariables();

        computeMPEState();
        computeCurrentIncompatibility(latentInferenceIncompatibility);

        unfixLabeledRandomVariables();
    }

    /**
     * Set RandomVariableAtoms with labels to their observed (truth) value.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void fixLabeledRandomVariables() {
        AtomStore atomStore = inference.getTermStore().getDatabase().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            atomStore.getAtoms()[atomIndex] = observedAtom;
            atomStore.getAtomValues()[atomIndex] = observedAtom.getValue();
            randomVariableAtom.setValue(observedAtom.getValue());
        }

        inMPEState = false;
    }

    /**
     * Set RandomVariableAtoms with labels to their unobserved state.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void unfixLabeledRandomVariables() {
        AtomStore atomStore = inference.getTermStore().getDatabase().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            atomStore.getAtoms()[atomIndex] = randomVariableAtom;
        }

        inMPEState = false;
    }
}
