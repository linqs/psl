/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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

import java.util.Collection;

/**
 * A base term generator.
 * Children of this will always be provided with linearExpressions for creating terms.
 * The exact interpretation of the linearExpressions is up to the child classes.
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
     * @return the number of terms added to the supplied collection.
     */
    public int createTerm(GroundRule groundRule, Collection<T> newTerms) {
        int count = 0;
        LinearExpression linearExpression = null;

        if (groundRule instanceof WeightedGroundRule) {
            GeneralFunction function = ((WeightedGroundRule)groundRule).getFunctionDefinition(mergeConstants);
            linearExpression = processLinearExpression(function);
            if (linearExpression == null) {
                return 0;
            }

            // Non-negative functions have a hinge.
            count = createLossTerm(newTerms, function.isHinge(), function.isSquared(), groundRule, linearExpression);
        } else if (groundRule instanceof UnweightedGroundRule) {
            ConstraintTerm constraint = ((UnweightedGroundRule)groundRule).getConstraintDefinition(mergeConstants);
            GeneralFunction function = constraint.getFunction();
            linearExpression = processLinearExpression(function);
            if (linearExpression == null) {
                return 0;
            }

            linearExpression.setConstant((float)(constraint.getValue() + linearExpression.getConstant()));
            count = createLinearConstraintTerm(newTerms, groundRule, linearExpression, constraint.getComparator());
        } else {
            throw new IllegalArgumentException("Unsupported ground rule: " + groundRule);
        }

        return count;
    }

    /**
     * Construct a linear expression from a general function.
     * Will return null if the term is trivial and should be abandoned.
     */
    private LinearExpression processLinearExpression(GeneralFunction generalFunction) {
        LinearExpression linearExpression = new LinearExpression(generalFunction.size(), -1.0f * (float)generalFunction.getConstant());

        for (int i = 0; i < generalFunction.size(); i++) {
            float coefficient = (float)generalFunction.getCoefficient(i);
            FunctionTerm term = generalFunction.getTerm(i);

            if ((term instanceof RandomVariableAtom) || (!mergeConstants && term instanceof ObservedAtom)) {
                GroundAtom variable = (GroundAtom)term;

                // Check to see if we have seen this variable before in this linear expression.
                // Note that we are checking for existence in a List (O(n)), but there are usually a small number of
                // variables per linear expression.
                int localIndex = linearExpression.indexOfVariable(variable);
                if (localIndex != -1) {
                    // If the local variable already exists, just add to its coefficient.
                    linearExpression.appendCoefficient(localIndex, coefficient);
                } else {
                    linearExpression.addTerm(variable, coefficient);
                }
            } else if (term.isConstant()) {
                // Subtract because linear expression is stored as coeffs^T * x - constant.
                linearExpression.setConstant(linearExpression.getConstant() - (float)(coefficient * term.getValue()));
            } else {
                throw new IllegalArgumentException("Unexpected summand: " + generalFunction + "[" + i + "] (" + term + ").");
            }
        }

        // This should be caught further up the chain, but we will check for full observed terms.
        if (linearExpression.size() == 0) {
            return null;
        }

        // If the linear expression is wrapped in a hinge,
        // and the constant is greater than the upper bound of the inner product,
        // then the term is trivial.
        if (generalFunction.isHinge()) {
            // Compute the upper bound of the inner product.
            float upperBound = 0.0f;
            for (int i = 0; i < linearExpression.size(); i++) {
                if (linearExpression.getCoefficient(i) > 0.0f) {
                    upperBound += linearExpression.getCoefficient(i);
                }
            }

            if (upperBound <= linearExpression.getConstant()) {
                return null;
            }
        }

        return linearExpression;
    }

    /**
     * Create a term from a ground rule and linearExpression, and add it to the collection of new terms.
     * Non-hinge terms are linear combinations (ala arithmetic rules).
     * Non-squared terms are linear.
     *
     * @return the number of terms added to the supplied collection.
     */
    public abstract int createLossTerm(Collection<T> newTerms,
            boolean isHinge, boolean isSquared, GroundRule groundRule, LinearExpression linearExpression);

    /**
     * Create a hard constraint term, and add it to the collection of new terms.
     *
     * @return the number of terms added to the supplied collection.
     */
    public abstract int createLinearConstraintTerm(Collection<T> newTerms,
                                                   GroundRule groundRule, LinearExpression linearExpression, FunctionComparator comparator);
}
