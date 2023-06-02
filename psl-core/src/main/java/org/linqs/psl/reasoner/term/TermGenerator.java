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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;

import java.util.Collection;

/**
 * A base term generator.
 * Children of this will always be provided with hyperplanes for creating terms.
 * The exact interpretation of the hyperplanes is up to the child classes.
 */
public abstract class TermGenerator<T extends ReasonerTerm> {
    private static final Logger log = Logger.getLogger(TermGenerator.class);

    protected boolean mergeConstants;

    public TermGenerator(boolean mergeConstants) {
        this.mergeConstants = mergeConstants;
    }

    public void setMergeConstants(boolean mergeConstants) {
        this.mergeConstants = mergeConstants;
    }

    public boolean getMergeConstants() {
        return mergeConstants;
    }

    /**
     * Create terms from the ground rule and add it to supplied collections.
     *
     * The supplied collections will not be cleared before use.
     * In most cases only one term will be added,
     * but it is possible for zero or more terms to be added.
     *
     * For each term added, a hyperplane will also be added
     * (usually the same hyperplane, and only if newHyperplanes is not null).
     * The terms own their respective (by index) hyperplane.
     *
     * @return the number of terms added to the supplied collection.
     */
    public int createTerm(GroundRule groundRule, Collection<T> newTerms, Collection<Hyperplane> newHyperplanes) {
        int count = 0;
        Hyperplane hyperplane = null;

        if (groundRule instanceof WeightedGroundRule) {
            GeneralFunction function = ((WeightedGroundRule)groundRule).getFunctionDefinition(mergeConstants);
            hyperplane = processHyperplane(function);
            if (hyperplane == null) {
                return 0;
            }

            // Non-negative functions have a hinge.
            count = createLossTerm(newTerms, function.isNonNegative(), function.isSquared(), groundRule, hyperplane);
        } else if (groundRule instanceof UnweightedGroundRule) {
            ConstraintTerm constraint = ((UnweightedGroundRule)groundRule).getConstraintDefinition(mergeConstants);
            GeneralFunction function = constraint.getFunction();
            hyperplane = processHyperplane(function);
            if (hyperplane == null) {
                return 0;
            }

            hyperplane.setConstant((float)(constraint.getValue() + hyperplane.getConstant()));
            count = createLinearConstraintTerm(newTerms, groundRule, hyperplane, constraint.getComparator());
        } else {
            throw new IllegalArgumentException("Unsupported ground rule: " + groundRule);
        }

        if (newHyperplanes != null) {
            for (int i = 0; i < count; i++) {
                newHyperplanes.add(hyperplane);
            }
        }

        return count;
    }

    /**
     * Construct a hyperplane from a general function.
     * Will return null if the term is trivial and should be abandoned.
     */
    private Hyperplane processHyperplane(GeneralFunction sum) {
        Hyperplane hyperplane = new Hyperplane(sum.size(), -1.0f * (float)sum.getConstant());

        for (int i = 0; i < sum.size(); i++) {
            float coefficient = (float)sum.getCoefficient(i);
            FunctionTerm term = sum.getTerm(i);

            if ((term instanceof RandomVariableAtom) || (!mergeConstants && term instanceof ObservedAtom)) {
                GroundAtom variable = (GroundAtom)term;

                // Check to see if we have seen this variable before in this hyperplane.
                // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                // variables per hyperplane.
                int localIndex = hyperplane.indexOfVariable(variable);
                if (localIndex != -1) {
                    // If this function came from a logical rule
                    // and the sign of the current coefficient and the coefficient of this variable do not match,
                    // then this term is trivial.
                    // Recall that all logical rules are disjunctions with only +1 and -1 as coefficients.
                    // A mismatch in signs for the same variable means that a ground atom appeared twice,
                    // once as a positive atom and once as a negative atom: Foo('a') || !Foo('a').
                    if (sum.isNonNegative() && !MathUtils.signsMatch(hyperplane.getCoefficient(localIndex), coefficient)) {
                        return null;
                    }

                    // If the local variable already exists, just add to its coefficient.
                    hyperplane.appendCoefficient(localIndex, coefficient);
                } else {
                    hyperplane.addTerm(variable, coefficient);
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
     * Create a term from a ground rule and hyperplane, and add it to the collection of new terms.
     * Non-hinge terms are linear combinations (ala arithmetic rules).
     * Non-squared terms are linear.
     *
     * @return the number of terms added to the supplied collection.
     */
    public abstract int createLossTerm(Collection<T> newTerms,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane hyperplane);

    /**
     * Create a hard constraint term, and add it to the collection of new terms.
     *
     * @return the number of terms added to the supplied collection.
     */
    public abstract int createLinearConstraintTerm(Collection<T> newTerms,
            GroundRule groundRule, Hyperplane hyperplane, FunctionComparator comparator);
}
