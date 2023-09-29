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
package org.linqs.psl.reasoner.duallcqp.term;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermState;

/**
 * Objective term for a DualBCDReasoner.
 *
 * This term holds both the primal and dual form of the regularized LCQP formulation of MAP inference.
 * There are four types of terms covered by this class:
 * 1) Linear Inequality Constraints:   (0 if coefficients^T * y < constant) or (infinity otherwise)
 * 2) Linear Equality Constraints:   (0 if coefficients^T * y = constant) or (infinity otherwise)
 * 3) Hinge-Loss Terms:          weight * max(0, coefficients^T * y - constant).
 * 4) Squared Hinge-Loss Terms:  weight * [max(0, coefficients^T * y - constant)]^2.
 * If a Linear Inequality Constraint has a greater than or equal to comparator (>) then it is translated to
 * an equivalent less than or equal to constraint.
 *
 * Conceptually, there is a slack variable associated with each loss term.
 * At the optimal solution the slack is equivalent to the dissatisfaction of
 * the loss and does not need to be stored.
 *
 * There are lower bound constraints on all the dual variables
 * except for those corresponding to Linear Equality Constraints.
 */
public class DualLCQPObjectiveTerm extends ReasonerTerm {
    protected static final double regularizationParameter = Options.DUAL_LCQP_REGULARIZATION.getDouble();

    // The dual variables associated with this term and with the lower bound on the slack (if it exists).
    protected double dualVariable;
    protected double slackBoundDualVariable;

    protected boolean isEqualityConstraint;

    public DualLCQPObjectiveTerm(Hyperplane hyperplane, Rule rule,
                                 boolean squared, boolean hinge,
                                 FunctionComparator comparator) {
        super(hyperplane, rule, squared, hinge, comparator);

        dualVariable = 0.0;
        slackBoundDualVariable = 0.0;

        init();
    }

    public DualLCQPObjectiveTerm(short size, float[] coefficients, float constant, int[] atomIndexes,
                                 Rule rule, boolean squared, boolean hinge, FunctionComparator comparator,
                                 double dualVariable, double slackBoundDualVariable) {
        super(size, coefficients, constant, atomIndexes, rule, squared, hinge, comparator);

        this.dualVariable = dualVariable;
        this.slackBoundDualVariable = slackBoundDualVariable;

        init();
    }

    private void init() {
        isEqualityConstraint = (this.comparator != null) && this.comparator.equals(FunctionComparator.EQ);

        if ((this.comparator != null) && this.comparator.equals(FunctionComparator.GTE)) {
            // Put problem in standard form.
            this.comparator = FunctionComparator.LTE;

            for (int i = 0; i < size; i++) {
                coefficients[i] = -coefficients[i];
            }

            constant = -constant;
        }
    }

    @Override
    public DualLCQPObjectiveTerm copy() {
        return new DualLCQPObjectiveTerm(size, coefficients, constant, atomIndexes, rule, squared, hinge, comparator,
                dualVariable, slackBoundDualVariable);
    }

    /**
     * The evaluation of the primal representation of a loss term is regularized.
     * The regularization of the loss term is equivalent to adding a squared hinge-loss term
     * with the same coefficients and constants but with a weight equal to the regularization parameter.
     */
    @Override
    public float evaluate(float[] variableValues) {
        float incompatibility = evaluateIncompatibility(variableValues);

        if (isConstraint()) {
            if (incompatibility > 0.0f) {
                return Float.POSITIVE_INFINITY;
            }
            return 0.0f;
        }

        return (float)(getWeight() * incompatibility + regularizationParameter * evaluateSquaredHingeLoss(variableValues));
    }

    @Override
    public float computeVariablePartial(int varId, float innerPotential) {
        float unregularizedPartial = super.computeVariablePartial(varId, innerPotential);

        if (isConstraint()) {
            return unregularizedPartial;
        }

        return (float)(unregularizedPartial + regularizationParameter * computeSquaredHingeLossPartial(varId, innerPotential));
    }

    public boolean isEqualityConstraint() {
        return isEqualityConstraint;
    }

    public double getDualVariable() {
        return dualVariable;
    }

    public void setDualVariable(double dualVariable) {
        this.dualVariable = dualVariable;
    }

    public double getSlackBoundDualVariable() {
        assert !isConstraint();
        return slackBoundDualVariable;
    }

    public void setSlackBoundDualVariable(double slackBoundDualVariable) {
        assert !isConstraint();
        this.slackBoundDualVariable = slackBoundDualVariable;
    }

    public double computeSelfInnerProduct() {
        double innerProduct = 0.0;

        for (int i = 0; i < size(); i++) {
            innerProduct += coefficients[i] * coefficients[i];
        }

        return innerProduct;
    }

    @Override
    public void loadState(TermState termState) {
        assert termState instanceof DualLCQPObjectiveTermState;
        DualLCQPObjectiveTermState objectiveTermState = (DualLCQPObjectiveTermState)termState;

        dualVariable = objectiveTermState.dualVariable;
        slackBoundDualVariable = objectiveTermState.slackBoundDualVariable;
    }

    @Override
    public TermState saveState() {
        return new DualLCQPObjectiveTermState(dualVariable, slackBoundDualVariable);
    }

    @Override
    public void saveState(TermState termState) {
        assert termState instanceof DualLCQPObjectiveTermState;
        DualLCQPObjectiveTermState objectiveTermState = (DualLCQPObjectiveTermState)termState;

        objectiveTermState.dualVariable = dualVariable;
        objectiveTermState.slackBoundDualVariable = slackBoundDualVariable;
    }

    public static final class DualLCQPObjectiveTermState extends TermState {
        public double dualVariable;
        public double slackBoundDualVariable;

        public DualLCQPObjectiveTermState(double dualVariable, double slackBoundDualVariable) {
            this.dualVariable = dualVariable;
            this.slackBoundDualVariable = slackBoundDualVariable;
        }
    }
}
