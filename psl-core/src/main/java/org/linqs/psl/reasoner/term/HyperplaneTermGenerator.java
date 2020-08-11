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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.config.Options;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * A base term generator for terms that come from hyperplanes.
 */
public abstract class HyperplaneTermGenerator<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements TermGenerator<T, V> {
    private static final Logger log = LoggerFactory.getLogger(HyperplaneTermGenerator.class);

    private boolean mergeConstants;
    private boolean invertNegativeWeight;

    public HyperplaneTermGenerator(boolean mergeConstants) {
        this.mergeConstants = mergeConstants;
        invertNegativeWeight = Options.HYPERPLANE_TG_INVERT_NEGATIVE_WEIGHTS.getBoolean();
    }

    @Override
    public long generateTerms(GroundRuleStore ruleStore, final TermStore<T, V> termStore) {
        long initialSize = termStore.size();
        termStore.ensureCapacity(initialSize + ruleStore.size());

        Set<WeightedRule> rules = new HashSet<WeightedRule>();
        for (GroundRule rule : ruleStore.getGroundRules()) {
            if (rule instanceof WeightedGroundRule) {
                rules.add((WeightedRule)rule.getRule());
            }
        }

        for (WeightedRule rule : rules) {
            if (rule.getWeight() < 0.0) {
                log.warn("Found a rule with a negative weight, but config says not to invert it... skipping: " + rule);
            }
        }

        Parallel.foreach(ruleStore.getGroundRules(), new Parallel.Worker<GroundRule>() {
            @Override
            public void work(long index, GroundRule rule) {
                boolean negativeWeight =
                        rule instanceof WeightedGroundRule
                        && ((WeightedGroundRule)rule).getWeight() < 0.0;

                if (negativeWeight) {
                    // Skip
                    if (!invertNegativeWeight) {
                        return;
                    }

                    // Negate (weight and expression) rules that have a negative weight.
                    for (GroundRule negatedRule : rule.negate()) {
                        T term = createTerm(negatedRule, termStore);
                        if (term != null && term.size() > 0) {
                            termStore.add(rule, term);
                        }
                    }
                } else {
                    T term = createTerm(rule, termStore);
                    if (term != null && term.size() > 0) {
                        termStore.add(rule, term);
                    }
                }
            }
        });

        return termStore.size() - initialSize;
    }

    @Override
    public T createTerm(GroundRule groundRule, TermStore<T, V> termStore) {
        if (groundRule instanceof WeightedGroundRule) {
            GeneralFunction function = ((WeightedGroundRule)groundRule).getFunctionDefinition(mergeConstants);
            Hyperplane<V> hyperplane = processHyperplane(function, termStore);
            if (hyperplane == null) {
                return null;
            }

            // Non-negative functions have a hinge.
            return createLossTerm(termStore, function.isNonNegative(), function.isSquared(), groundRule, hyperplane);
        } else if (groundRule instanceof UnweightedGroundRule) {
            ConstraintTerm constraint = ((UnweightedGroundRule)groundRule).getConstraintDefinition(mergeConstants);
            GeneralFunction function = constraint.getFunction();
            Hyperplane<V> hyperplane = processHyperplane(function, termStore);
            if (hyperplane == null) {
                return null;
            }

            hyperplane.setConstant((float)(constraint.getValue() + hyperplane.getConstant()));
            return createLinearConstraintTerm(termStore, groundRule, hyperplane, constraint.getComparator());
        } else {
            throw new IllegalArgumentException("Unsupported ground rule: " + groundRule);
        }
    }

    /**
     * Construct a hyperplane from a general function.
     * Will return null if the term is trivial and should be abandoned.
     */
    private Hyperplane<V> processHyperplane(GeneralFunction sum, TermStore<T, V> termStore) {
        Hyperplane<V> hyperplane = new Hyperplane<V>(getLocalVariableType(), sum.size(), -1.0f * (float)sum.getConstant());

        for (int i = 0; i < sum.size(); i++) {
            float coefficient = (float)sum.getCoefficient(i);
            FunctionTerm term = sum.getTerm(i);

            if ((term instanceof RandomVariableAtom) || (!mergeConstants && term instanceof ObservedAtom)) {
                V localVariable = termStore.createLocalVariable((GroundAtom)term);
                if (localVariable == null) {
                    throw new RuntimeException("Could not get local term for summand: " + sum + "[" + i + "] (" + term + ").");
                }

                // Check to see if we have seen this localVariable before in this hyperplane.
                // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                // variables per hyperplane.
                int localIndex = hyperplane.indexOfVariable(localVariable);
                if (localIndex != -1) {
                    // If this function came from a logical rule
                    // and the sign of the current coefficient and the coefficient of this localVariable do not match,
                    // then this term is trivial.
                    // Recall that all logical rules are disjunctions with only +1 and -1 as coefficients.
                    // A mismatch in signs for the same localVariable means that a ground atom appeared twice,
                    // once as a positive atom and once as a negative atom: Foo('a') || !Foo('a').
                    if (sum.isNonNegative() && !MathUtils.signsMatch(hyperplane.getCoefficient(localIndex), coefficient)) {
                        return null;
                    }

                    // If the local localVariable already exists, just add to its coefficient.
                    hyperplane.appendCoefficient(localIndex, coefficient);
                } else {
                    hyperplane.addTerm(localVariable, coefficient);
                }
            } else if (term.isConstant()) {
                // Subtract because hyperplane is stored as coeffs^T * x = constant.
                hyperplane.setConstant(hyperplane.getConstant() - (float)(coefficient * term.getValue()));
            } else {
                throw new IllegalArgumentException("Unexpected summand: " + sum + "[" + i + "] (" + term + ").");
            }
        }

        // This should be caught further up the chain, but we will check for full observed terms.
        if (hyperplane.size() == 0) {
            return null;
        }

        return hyperplane;
    }

    /**
     * Get the class object for the local vairable type.
     * This is for type safety when creating hyperplanes.
     */
    public abstract Class<V> getLocalVariableType();

    /**
     * Create a term from a ground rule and hyperplane.
     * Non-hinge terms are linear combinations (ala arithmetic rules).
     * Non-squared terms are linear.
     */
    public abstract T createLossTerm(TermStore<T, V> termStore, boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane<V> hyperplane);

    /**
     * Create a hard constraint term,
     */
    public abstract T createLinearConstraintTerm(TermStore<T, V> termStore, GroundRule groundRule, Hyperplane<V> hyperplane, FunctionComparator comparator);
}
