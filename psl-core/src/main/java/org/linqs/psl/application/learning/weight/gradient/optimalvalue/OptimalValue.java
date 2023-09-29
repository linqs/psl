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
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.SimpleTermStore;
import org.linqs.psl.reasoner.term.TermState;
import org.linqs.psl.util.Logger;

import java.util.ArrayList;
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
    private static final Logger log = Logger.getLogger(GradientDescent.class);

    protected float[] latentInferenceIncompatibility;

    protected TermState[] latentInferenceTermState;
    protected float[] latentInferenceAtomValueState;
    protected List<TermState[]> batchLatentInferenceTermStates;
    protected List<float[]> batchLatentInferenceAtomValueStates;

    protected float[] rvLatentAtomGradient;
    protected float[] deepLatentAtomGradient;

    public OptimalValue(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase,
                        Database validationTargetDatabase, Database validationTruthDatabase, boolean runValidation) {
        super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, runValidation);

        latentInferenceIncompatibility = new float[mutableRules.size()];

        latentInferenceTermState = null;
        latentInferenceAtomValueState = null;
        batchLatentInferenceTermStates = new ArrayList<TermState[]>();
        batchLatentInferenceAtomValueStates = new ArrayList<float[]>();

        rvLatentAtomGradient = null;
        deepLatentAtomGradient = null;
    }

    @Override
    protected void initializeBatchWarmStarts() {
        super.initializeBatchWarmStarts();

        for (int i = 0; i < batchGenerator.getNumBatches(); i++) {
            SimpleTermStore<? extends ReasonerTerm> batchTermStore = batchGenerator.getBatchTermStore(i);
            batchLatentInferenceTermStates.add(batchTermStore.saveState());
            batchLatentInferenceAtomValueStates.add(Arrays.copyOf(batchTermStore.getAtomStore().getAtomValues(), batchTermStore.getAtomStore().getAtomValues().length));
        }
    }

    @Override
    protected void initializeGradients() {
        super.initializeGradients();

        rvLatentAtomGradient = new float[trainFullMAPAtomValueState.length];
        deepLatentAtomGradient = new float[trainFullMAPAtomValueState.length];
    }

    @Override
    protected void setBatch(int batch) {
        super.setBatch(batch);

        latentInferenceTermState = batchLatentInferenceTermStates.get(batch);
        latentInferenceAtomValueState = batchLatentInferenceAtomValueStates.get(batch);
    }

    /**
     * Compute the latent inference problem solution incompatibility.
     * RandomVariableAtoms with labels are fixed to their observed (truth) value.
     */
    protected void computeLatentInferenceIncompatibility() {
        fixLabeledRandomVariables();

        log.trace("Running Latent Inference.");
        computeMAPStateWithWarmStart(trainInferenceApplication, latentInferenceTermState, latentInferenceAtomValueState);
        inTrainingMAPState = true;

        computeCurrentIncompatibility(latentInferenceIncompatibility);
        trainInferenceApplication.getReasoner().computeOptimalValueGradient(trainInferenceApplication.getTermStore(), rvLatentAtomGradient, deepLatentAtomGradient);

        unfixLabeledRandomVariables();
    }

    /**
     * Set RandomVariableAtoms with labels to their observed (truth) value.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void fixLabeledRandomVariables() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();
            ObservedAtom observedAtom = entry.getValue();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            if (atomIndex == -1) {
                // This atom is not in the current batch.
                continue;
            }

            observedAtom.setIndex(atomIndex);
            randomVariableAtom.setIndex(atomIndex);

            atomStore.getAtoms()[atomIndex] = observedAtom;
            atomStore.getAtomValues()[atomIndex] = observedAtom.getValue();
            latentInferenceAtomValueState[atomIndex] = observedAtom.getValue();
            randomVariableAtom.setValue(observedAtom.getValue());
        }

        inTrainingMAPState = false;
    }

    /**
     * Set RandomVariableAtoms with labels to their unobserved state.
     * This method relies on random variable atoms and observed atoms
     * with the same predicates and arguments having the same hash.
     */
    protected void unfixLabeledRandomVariables() {
        AtomStore atomStore = trainInferenceApplication.getTermStore().getAtomStore();

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry: trainingMap.getLabelMap().entrySet()) {
            RandomVariableAtom randomVariableAtom = entry.getKey();

            int atomIndex = atomStore.getAtomIndex(randomVariableAtom);
            if (atomIndex == -1) {
                // This atom is not in the current batch.
                continue;
            }

            randomVariableAtom.setIndex(atomIndex);

            atomStore.getAtoms()[atomIndex] = randomVariableAtom;
        }

        inTrainingMAPState = false;
    }
}
