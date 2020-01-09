/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
package org.linqs.psl.reasoner.term.blocker;

import org.linqs.psl.grounding.AtomRegisterGroundRuleStore;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.arithmetic.UnweightedGroundArithmeticRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Prepares blocks.
 * Typically terms come from ground rules, but here we have structures
 * that are built up around the entire collection of ground rules.
 * A "term" is equivalent to a "block" here.
 *
 * A block is a collection of all random variable atoms (RVAs) that are incident on the same constraint.
 * All the user imposed constraints (not GroundValueConstraint) are either functional or partial functional.
 * Because of the constraints imposed on the model, no RVA can touch multiple constraints,
 * so there are no duplicates in the blocks.
 * In a block, the values of all atoms sum to at most one.
 * If exactlyOne is true, then the values must sum to one exactly.
 * When a block is exactlyOne and one of the atoms is pegged to one, then all the other values
 * will be set to 0 during the generation process.
 *
 * It is possible (and common) to see a block that is actually just a single atom.
 *
 * Restrictions:
 * <ul>
 *     <li>The GroundRuleStore must be a type of AtomRegisterGroundRuleStore.</li>
 *     <li>The TermStore must be a type of ConstraintBlockerTermStore.</li>
 *     <li>The TermStore must be a type of ConstraintBlockerTermStore.</li>
 *     <li>No unweighted logical rules are allowed.</li>
 *     <li>Only functional, partial functional, or GroundValueConstraint constraints are in the model.</li>
 *     <li>All atoms are involved in at most one (partial) function and at most one GroundValueConstraint constraint.</li>
 * </ul>
 */
public class ConstraintBlockerTermGenerator implements TermGenerator<ConstraintBlockerTerm, RandomVariableAtom> {
    @Override
    public int generateTerms(GroundRuleStore ruleStore, TermStore<ConstraintBlockerTerm, RandomVariableAtom> termStore) {
        if (!(ruleStore instanceof AtomRegisterGroundRuleStore)) {
            throw new IllegalArgumentException("AtomRegisterGroundRuleStore required.");
        }

        if (!(termStore instanceof ConstraintBlockerTermStore)) {
            throw new IllegalArgumentException("ConstraintBlockerTermStore required.");
        }

        return generateTermsInternal((AtomRegisterGroundRuleStore)ruleStore, (ConstraintBlockerTermStore)termStore);
    }

    private int generateTermsInternal(AtomRegisterGroundRuleStore ruleStore, ConstraintBlockerTermStore termStore) {
        // Collects constraints.
        Set<UnweightedGroundArithmeticRule> constraintSet = new HashSet<UnweightedGroundArithmeticRule>();
        Map<RandomVariableAtom, GroundValueConstraint> valueConstraintMap = new HashMap<RandomVariableAtom, GroundValueConstraint>();
        buildConstraints(ruleStore, constraintSet, valueConstraintMap);

        Set<RandomVariableAtom> freeRVSet = buildFreeRVSet(ruleStore);

        // Put RandomVariableAtoms in 2d array by block.
        RandomVariableAtom[][] rvBlocks = new RandomVariableAtom[constraintSet.size() + freeRVSet.size()][];

        // If true, exactly one Atom in the RV block must be 1.0. If false, at most one can.
        boolean[] exactlyOne = new boolean[rvBlocks.length];

        // False means that an ObservedAtom or constrained RandomVariableAtom
        // is 1.0, forcing others to 0.0
        boolean varsAreFree;

        // Index of the current block we are working with.
        int blockIndex = 0;

        // RVAs constrained by each functional constraint.
        Set<RandomVariableAtom> constrainedRVSet = new HashSet<RandomVariableAtom>();

        // Process constrained RVs first.
        for (UnweightedGroundArithmeticRule con : constraintSet) {
            constrainedRVSet.clear();
            varsAreFree = true;

            for (GroundAtom atom : con.getAtoms()) {
                if (atom instanceof ObservedAtom && atom.getValue() != 0.0) {
                    varsAreFree = false;
                } else if (atom instanceof RandomVariableAtom) {
                    GroundValueConstraint valueCon = valueConstraintMap.get(atom);
                    if (valueCon != null) {
                        if (valueCon.getConstraintDefinition().getValue() != 0.0) {
                            varsAreFree = false;
                        }
                    } else {
                        constrainedRVSet.add((RandomVariableAtom)atom);
                    }
                }
            }

            if (varsAreFree) {
                rvBlocks[blockIndex] = new RandomVariableAtom[constrainedRVSet.size()];
                int j = 0;
                for (RandomVariableAtom atom : constrainedRVSet) {
                    rvBlocks[blockIndex][j++] = atom;
                }

                exactlyOne[blockIndex] = con.getConstraintDefinition().getComparator().equals(FunctionComparator.EQ) || constrainedRVSet.size() == 0;
            } else {
                rvBlocks[blockIndex] = new RandomVariableAtom[0];
                // Sets to true regardless of constraint type to avoid extra processing steps
                // that would not work on empty blocks
                exactlyOne[blockIndex] = true;

                // Set all the RVs in this block to 0.0 since there is a observed/constrained value.
                for (RandomVariableAtom atom : constrainedRVSet) {
                    atom.setValue(0.0f);
                }
            }

            blockIndex++;
        }

        // Processes free RVs second.
        for (RandomVariableAtom atom : freeRVSet) {
            rvBlocks[blockIndex] = new RandomVariableAtom[] {atom};
            exactlyOne[blockIndex] = false;
            blockIndex++;
        }

        // Collects WeightedGroundRules incident on each block of RandomVariableAtoms.
        WeightedGroundRule[][] incidentGRs = collectIncidentWeightedGroundRules(ruleStore, rvBlocks);

        // Sets all value-constrained atoms.
        for (Map.Entry<RandomVariableAtom, GroundValueConstraint> e : valueConstraintMap.entrySet()) {
            e.getKey().setValue(e.getValue().getConstraintDefinition().getValue());
        }

        termStore.init(ruleStore, rvBlocks, incidentGRs, exactlyOne);
        return rvBlocks.length;
    }

    private WeightedGroundRule[][] collectIncidentWeightedGroundRules(
            AtomRegisterGroundRuleStore ruleStore, RandomVariableAtom[][] rvBlocks) {
        WeightedGroundRule[][] incidentGRs = new WeightedGroundRule[rvBlocks.length][];

        Set<WeightedGroundRule> incidentGKSet = new HashSet<WeightedGroundRule>();
        for (int blockIndex = 0; blockIndex < rvBlocks.length; blockIndex++) {
            incidentGKSet.clear();
            for (RandomVariableAtom atom : rvBlocks[blockIndex]) {
                for (GroundRule incidentGK : ruleStore.getRegisteredGroundRules(atom)) {
                    if (incidentGK instanceof WeightedGroundRule) {
                        incidentGKSet.add((WeightedGroundRule) incidentGK);
                    }
                }
            }

            incidentGRs[blockIndex] = new WeightedGroundRule[incidentGKSet.size()];
            int j = 0;
            for (WeightedGroundRule incidentGK : incidentGKSet) {
                incidentGRs[blockIndex][j++] = incidentGK;
            }
        }

        return incidentGRs;
    }

    private Set<RandomVariableAtom> buildFreeRVSet(AtomRegisterGroundRuleStore ruleStore) {
        // Collects the free RandomVariableAtoms that remain.
        Set<RandomVariableAtom> freeRVSet = new HashSet<RandomVariableAtom>();
        for (GroundRule groundRule : ruleStore.getGroundRules()) {
            for (GroundAtom atom : groundRule.getAtoms()) {
                if (!(atom instanceof RandomVariableAtom)) {
                    continue;
                }

                int numDomainConstraints = 0;
                int numValueConstraints = 0;

                for (GroundRule incidentGR : ruleStore.getRegisteredGroundRules(atom)) {
                    if (incidentGR instanceof UnweightedGroundArithmeticRule) {
                        numDomainConstraints++;
                    } else if (incidentGR instanceof GroundValueConstraint) {
                        numValueConstraints++;
                    }
                }

                if (numDomainConstraints == 0 && numValueConstraints == 0) {
                    freeRVSet.add(((RandomVariableAtom) atom));
                } else if (numDomainConstraints >= 2 || numValueConstraints >= 2) {
                    throw new IllegalStateException(
                            "RandomVariableAtoms may only participate in one (at-least) 1-of-k" +
                            " and/or GroundValueConstraint.");
                }
            }
        }

        return freeRVSet;
    }

    private void buildConstraints(GroundRuleStore ruleStore,
            Set<UnweightedGroundArithmeticRule> constraintSet, Map<RandomVariableAtom, GroundValueConstraint> valueConstraintMap) {
        for (UnweightedGroundRule groundRule : ruleStore.getConstraintRules()) {
            if (groundRule instanceof GroundValueConstraint) {
                valueConstraintMap.put(((GroundValueConstraint)groundRule).getAtom(), (GroundValueConstraint)groundRule);
                continue;
            }

            if (!(groundRule instanceof UnweightedGroundArithmeticRule)) {
                throw new IllegalStateException(
                        "Unsupported ground rule: [" + groundRule + "]." +
                        " Only categorical (functional) arithmetic constraints are supported.");
            }

            // If the ground rule is an UnweightedGroundArithmeticRule, checks if it
            // is a categorical, i.e., at-least-1-of-k (partial functional) or 1-of-k (functional), constraint.
            UnweightedGroundArithmeticRule gar = (UnweightedGroundArithmeticRule)groundRule;
            boolean categorical = true;

            FunctionComparator comparator = gar.getConstraintDefinition().getComparator();
            double rhsValue = gar.getConstraintDefinition().getValue();

            if (!(
                    // Foo(A, +B) = 1.0 .
                    (comparator == FunctionComparator.EQ && MathUtils.equals(rhsValue, 1.0))
                    // Foo(A, +B) <= 1.0 .
                    || (comparator == FunctionComparator.LTE && MathUtils.equals(rhsValue, 1.0))
                    // -Foo(A, +B) >= -1.0 .
                    || (comparator == FunctionComparator.GTE && MathUtils.equals(rhsValue, -1.0)))) {
                categorical = false;
            } else {
                GeneralFunction sum = gar.getConstraintDefinition().getFunction();
                for (int i = 0; i < sum.size(); i++) {
                    if (Math.abs(sum.getCoefficient(i) - gar.getConstraintDefinition().getValue()) > 1e-8) {
                        categorical = false;
                        break;
                    }
                }
            }

            if (!categorical) {
                throw new IllegalStateException(
                        "Unsupported ground rule: [" + groundRule + "]." +
                        " The only supported constraints are 1-of-k constraints" +
                        " and at-least-1-of-k constraints and value constraints.");
            }

            constraintSet.add(gar);
        }
    }
}
