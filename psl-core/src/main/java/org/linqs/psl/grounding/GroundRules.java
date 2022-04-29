/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.grounding;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utilities for common {@link GroundRule} tasks.
 */
public class GroundRules {
    /**
     * Sums the total weighted incompatibility of an iterable container of
     * {@link WeightedGroundRule GroundCompatibilityRules}.
     *
     * @param groundRules  the GroundCompatibilityRules
     * @return the total weighted incompatibility
     */
    public static double getTotalWeightedIncompatibility(Iterable<WeightedGroundRule> groundRules) {
        double totalInc = 0.0;
        for (WeightedGroundRule groundRule : groundRules) {
            totalInc += groundRule.getIncompatibility() * groundRule.getWeight();
        }
        return totalInc;
    }

    /**
     * Sums the total weighted compatibility (1 - incompatibility) of an iterable
     * container of {@link WeightedGroundRule GroundCompatibilityRules}.
     *
     * WARNING: This method does not account for GroundCompatibilityRules that
     * were not grounded because they are trivially satisfied.
     *
     * @param groundRules  the GroundCompatibilityRules
     * @return the total weighted compatibility
     * @see WeightedGroundRule#getIncompatibility()
     * @see WeightedGroundRule#getWeight()
     */
    public static double getTotalWeightedCompatibility(Iterable<WeightedGroundRule> groundRules) {
        double totalInc = 0.0;
        for (WeightedGroundRule groundRule : groundRules) {
            totalInc += (1 - groundRule.getIncompatibility()) * groundRule.getWeight();
        }
        return totalInc;
    }

    /**
     * Computes the expected total weighted incompatibility of an iterable
     * container of {@link WeightedGroundRule GroundCompatibilityRules}
     * from independently rounding each {@link RandomVariableAtom} to 1.0 or 0.0
     * with probability equal to its current truth value.
     *
     * WARNING: the result of this function is incorrect if the RandomVariableAtoms
     * are subject to any {@link UnweightedGroundRule GroundConstraintRules}.
     *
     * @param groundRules  the GroundCompatibilityRules
     * @return the expected total weighted incompatibility
     * @see WeightedGroundRule#getIncompatibility()
     * @see WeightedGroundRule#getWeight()
     */
    public static double getExpectedTotalWeightedIncompatibility(Iterable<WeightedGroundRule> groundRules) {
        double totalInc = 0.0;
        List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();
        for (WeightedGroundRule groundRule : groundRules) {
            double inc = 0.0;

            // Collect RandomVariableAtoms,
            for (GroundAtom atom : groundRule.getAtoms()) {
                if (atom instanceof RandomVariableAtom) {
                    atoms.add((RandomVariableAtom) atom);
                }
            }

            // Collect truth values,
            float[] truthValues = new float[atoms.size()];
            for (int i = 0; i < truthValues.length; i++) {
                truthValues[i] = atoms.get(i).getValue();
            }

            // Sums over settings.
            for (int i = 0; i < Math.pow(2, atoms.size()); i++) {
                float assignmentProb = 1.0f;

                // Sets assignment and computes probability.
                for (int j = 0; j < atoms.size(); j++) {
                    int assignment = ((i >> j) & 1);
                    atoms.get(j).setValue((float)assignment);
                    assignmentProb *= (assignment == 1) ? truthValues[j] : 1 - truthValues[j];
                }

                inc += assignmentProb * groundRule.getIncompatibility();
            }

            // Restores truth values.
            for (int i = 0; i < atoms.size(); i++) {
                atoms.get(i).setValue(truthValues[i]);
            }

            // Clears atom list,
            atoms.clear();

            // Weights and adds to total,
            inc *= groundRule.getWeight();
            totalInc += inc;
        }
        return totalInc;
    }

    /**
     * Computes the expected weighted compatibility (1 - incompatibility)
     * of a collection of WeightedGroundRules.
     * from independently rounding each {@link RandomVariableAtom} to 1.0 or 0.0
     * with probability equal to its current truth value.
     *
     * WARNING: the result of this function is incorrect if the RandomVariableAtoms
     * are subject to any {@link UnweightedGroundRule GroundConstraintRules}.
     *
     * WARNING: This method does not account for GroundCompatibilityRules that
     * were not grounded because they are trivially satisfied.
     */
    public static double getExpectedTotalWeightedCompatibility(Iterable<WeightedGroundRule> groundRules) {
        double totalInc = 0.0;
        for (WeightedGroundRule groundRule : groundRules) {
            totalInc += getExpectedWeightedCompatibility(groundRule);
        }

        return totalInc;
    }

    /**
     * Computes the expected weighted compatibility (1 - incompatibility)
     * of a WeightedGroundRule.
     * from independently rounding each {@link RandomVariableAtom} to 1.0 or 0.0
     * with probability equal to its current truth value.
     *
     * WARNING: the result of this function is incorrect if the RandomVariableAtoms
     * are subject to any {@link UnweightedGroundRule GroundConstraintRules}.
     *
     * WARNING: This method does not account for GroundCompatibilityRules that
     * were not grounded because they are trivially satisfied.
     */
    public static double getExpectedWeightedCompatibility(WeightedGroundRule groundRule) {
        double inc = 0.0;
        List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

        // Collect RandomVariableAtoms,
        for (GroundAtom atom : groundRule.getAtoms()) {
            if (atom instanceof RandomVariableAtom) {
                atoms.add((RandomVariableAtom) atom);
            }
        }

        // Collect truth values.
        float[] truthValues = new float[atoms.size()];
        for (int i = 0; i < truthValues.length; i++) {
            truthValues[i] = atoms.get(i).getValue();
        }

        // Sum over settings.
        for (int i = 0; i < Math.pow(2, atoms.size()); i++) {
            float assignmentProb = 1.0f;

            // Set assignment and compute probability.
            for (int j = 0; j < atoms.size(); j++) {
                int assignment = ((i >> j) & 1);
                atoms.get(j).setValue((float)assignment);
                assignmentProb *= (assignment == 1) ? truthValues[j] : 1 - truthValues[j];
            }

            inc += assignmentProb * (1 - groundRule.getIncompatibility());
        }

        // Restore truth values,
        for (int i = 0; i < atoms.size(); i++) {
            atoms.get(i).setValue(truthValues[i]);
        }

        // Weight and return.
        return inc * groundRule.getWeight();
    }

    /**
     * Computes the Euclidean norm of the infeasibilities of an iterable container
     * of {@link UnweightedGroundRule GroundConstraintRules}.
     *
     * @param groundRules  the GroundConstraintRules
     * @return the Euclidean norm of the infeasibilities
     * @see UnweightedGroundRule#getInfeasibility()
     */
    public static double getInfeasibilityNorm(Iterable<UnweightedGroundRule> groundRules) {
        double inf, norm = 0.0;
        for (UnweightedGroundRule groundRule : groundRules) {
            inf = groundRule.getInfeasibility();
            norm += inf * inf;
        }
        return Math.sqrt(norm);
    }
}
