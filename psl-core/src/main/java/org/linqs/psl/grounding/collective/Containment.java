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
package org.linqs.psl.grounding.collective;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.BitUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static utilities for computing query containment and coverage.
 */
public class Containment {
    private static final Logger log = LoggerFactory.getLogger(Containment.class);

    // Static only.
    private Containment() {}

    /**
     * Check which rules each candidate contains (can ground for).
     * A large part of checking containment is building a mapping of variables between the two different queries.
     */
    public static void computeContainement(List<Rule> collectiveRules, Collection<CandidateQuery> candidates) {
        Map<CandidateQuery, Map<Variable, Set<VariableInstance>>> candidateVariableUsages = new HashMap<CandidateQuery, Map<Variable, Set<VariableInstance>>>();
        Map<CandidateQuery, List<Variable>> candidateVariables = new HashMap<CandidateQuery, List<Variable>>();

        for (CandidateQuery candidate : candidates) {
            Map<Variable, Set<VariableInstance>> variableUsage = getVariableUsage(candidate.getFormula());
            candidateVariableUsages.put(candidate, variableUsage);
            candidateVariables.put(candidate, new ArrayList<Variable>(variableUsage.keySet()));
        }

        for (Rule rule : collectiveRules) {
            // Use the query formula for variable mapping, but the full rule for variable usage.
            Formula ruleFormula = rule.getRewritableGroundingFormula();

            Map<Variable, Set<VariableInstance>> ruleVariableUsage = getVariableUsage(rule);
            List<Variable> ruleVariables = new ArrayList<Variable>(ruleVariableUsage.keySet());

            for (CandidateQuery candidate : candidates) {
                if (candidate.getVariableMapping(rule) != null) {
                    continue;
                }

                Map<Variable, Variable> variableMap = computeVariableMapping(
                        candidate.getFormula(), candidateVariableUsages.get(candidate), candidateVariables.get(candidate),
                        ruleFormula, ruleVariableUsage, ruleVariables);
                if (variableMap == null) {
                    continue;
                }

                candidate.getCoveredVariableMappings().put(rule, variableMap);
            }
        }
    }

    /**
     * Check for a containment variable mapping from |superFormula| to |subFormula|.
     * A mapping implies that |superFormula| contains |subFormula|.
     * The variable lists may have their order changed, but the contents will remain consistent.
     */
    private static Map<Variable, Variable> computeVariableMapping(
            Formula superFormula, Map<Variable, Set<VariableInstance>> superVariableUsage, List<Variable> superVariables,
            Formula subFormula, Map<Variable, Set<VariableInstance>> subVariableUsage, List<Variable> subVariables) {
        assert(superVariables.size() < 64);  // Assert size because we are using bitsets.

        if (superVariables.size() != subVariables.size()) {
            return null;
        }

        // Naively check for a variable mapping.
        // The more instances a variable is used, the more restricted it is.
        // To establish a mapping between two variables, the super must have a less restricted version of a sub's variable.

        Map<Variable, Variable> mapping = new HashMap<Variable, Variable>();

        // A bitset tracking the used sub variables.
        long usedVariables = 0l;

        for (int superStartIndex = 0; superStartIndex < superVariables.size(); superStartIndex++) {
            mapping.clear();
            usedVariables = 0l;

            for (int superIndexOffset = 0; superIndexOffset < superVariables.size(); superIndexOffset++) {
                int superIndex = (superStartIndex + superIndexOffset) % superVariables.size();
                boolean foundSubVariable = false;

                for (int subIndex = 0; subIndex < subVariables.size(); subIndex++) {
                    // Check of the sub variable is already in use.
                    if (BitUtils.getBit(usedVariables, subIndex)) {
                        continue;
                    }

                    Set<VariableInstance> superVariableInstances = superVariableUsage.get(superVariables.get(superIndex));
                    Set<VariableInstance> subVariableInstances = subVariableUsage.get(subVariables.get(subIndex));

                    if (subVariableInstances.containsAll(superVariableInstances)) {
                        mapping.put(superVariables.get(superIndex), subVariables.get(subIndex));
                        usedVariables = BitUtils.setBit(usedVariables, subIndex, true);
                        foundSubVariable = true;
                        break;
                    }
                }

                // Check if this super variable found a match.
                if (!foundSubVariable) {
                    break;
                }
            }

            // Check if we found a full mapping.
            if (mapping.size() == superVariables.size()) {
                return mapping;
            }
        }

        return null;
    }

    /**
     * Get all instances of variables being used in a rule.
     * Rules have some special cases that formula's don't.
     * Most significantly, we will not be using the query formula to check variable usage, we will be using the full formula.
     */
    private static Map<Variable, Set<VariableInstance>> getVariableUsage(Rule rule) {
        Set<Atom> atoms = new HashSet<Atom>();
        rule.getCoreAtoms(atoms);

        if (atoms.size() == 1) {
            return getVariableUsage(atoms.iterator().next());
        }

        Formula formula = new Conjunction(atoms.toArray(new Formula[0]));
        return getVariableUsage(formula);
    }

    /**
     * Get all instances of variables being used in a formula.
     */
    private static Map<Variable, Set<VariableInstance>> getVariableUsage(Formula formula) {
        Set<Atom> tempAtoms = new HashSet<Atom>();
        StringBuilder atomModifier = new StringBuilder();

        Map<Variable, Set<VariableInstance>> variableUsage = new HashMap<Variable, Set<VariableInstance>>();

        formula.getAtoms(tempAtoms);
        for (Atom atom : tempAtoms) {
            Term[] arguments = atom.getArguments();

            // First, check if this atom has any constants.
            // If it does, then this atom needs to be marked, since it is more restrictive than an atom without any constants.
            atomModifier.setLength(0);
            for (int i = 0; i < arguments.length; i++) {
                if (!(arguments[i] instanceof Constant)) {
                    continue;
                }

                if (atomModifier.length() != 0) {
                    atomModifier.append(",");
                }

                atomModifier.append("" + i + ":" + arguments[i]);
            }

            // Now, note all the variables.
            for (int i = 0; i < arguments.length; i++) {
                if (!(arguments[i] instanceof Variable)) {
                    if (!(arguments[i] instanceof Constant)) {
                        throw new RuntimeException(String.format(
                                "Expecting only variables and constants, found: %s (%s).",
                                arguments[i], arguments[i].getClass()));
                    }

                    continue;
                }

                Variable variable = (Variable)arguments[i];
                if (!variableUsage.containsKey(variable)) {
                    variableUsage.put(variable, new HashSet<VariableInstance>());
                }

                variableUsage.get(variable).add(new VariableInstance(atom.getPredicate(), i, atomModifier.toString()));
            }
        }

        return variableUsage;
    }

    private static class VariableInstance {
        public final String predicate;
        public final int position;
        public final String atomModifier;

        private final int hashCode;

        public VariableInstance(Predicate predicate, int position, String atomModifier) {
            this.predicate = predicate.getName();
            this.position = position;
            this.atomModifier = atomModifier;

            hashCode = toString().hashCode();
        }

        @Override
        public String toString() {
            if (atomModifier.length() == 0) {
                return predicate + "::" + position;
            }

            return predicate + "(" + atomModifier + ")::" + position;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || other.getClass() != this.getClass() || other.hashCode() != this.hashCode()) {
                return false;
            }

            VariableInstance otherInstance = (VariableInstance)other;
            return this.position == otherInstance.position
                    && this.predicate.equals(otherInstance.predicate)
                    && this.atomModifier.equals(otherInstance.atomModifier);
        }
    }
}
