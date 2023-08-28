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

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.util.MathUtils;

import java.util.Arrays;

public abstract class ReasonerTerm {
    /**
     * The specific type of term represented by this instance.
     */
    public static enum TermType {
        LinearConstraintTerm,
        LinearLossTerm,
        HingeLossTerm,
        SquaredLinearLossTerm,
        SquaredHingeLossTerm,
    }

    public final TermType termType;

    /**
     * When non-null, this term must be a hard constraint.
     */
    protected FunctionComparator comparator;

    protected Rule rule;
    protected int[] atomIndexes;

    protected short size;
    protected float[] coefficients;
    protected float constant;
    protected boolean squared;
    protected boolean hinge;

    public ReasonerTerm(Hyperplane hyperplane, Rule rule,
                        boolean squared, boolean hinge,
                        FunctionComparator comparator) {
        this.rule = rule;
        this.comparator = comparator;
        this.squared = squared;
        this.hinge = hinge;
        termType = getTermType();

        this.size = (short)hyperplane.size();
        this.coefficients = hyperplane.getCoefficients();
        this.constant = hyperplane.getConstant();

        atomIndexes = new int[size];
        GroundAtom[] atoms = hyperplane.getVariables();
        for (int i = 0; i < size; i++) {
            atomIndexes[i] = atoms[i].getIndex();
        }
    }

    public ReasonerTerm(short size, float[] coefficients, float constant, int[] atomIndexes,
                        Rule rule, boolean squared, boolean hinge, FunctionComparator comparator) {
        this.rule = rule;
        this.comparator = comparator;
        this.squared = squared;
        this.hinge = hinge;
        termType = getTermType();

        this.size = size;
        this.coefficients = Arrays.copyOf(coefficients, size);
        this.constant = constant;
        this.atomIndexes = Arrays.copyOf(atomIndexes, size);
    }

    public abstract ReasonerTerm copy();

    /**
     * Get the specific type of term this instance represents.
     */
    private TermType getTermType() {
        if (comparator != null) {
            return TermType.LinearConstraintTerm;
        } else if (!squared && !hinge) {
            return TermType.LinearLossTerm;
        } else if (!squared && hinge) {
            return TermType.HingeLossTerm;
        } else if (squared && !hinge) {
            return TermType.SquaredLinearLossTerm;
        } else if (squared && hinge) {
            return TermType.SquaredHingeLossTerm;
        }

        throw new IllegalStateException("Unknown term type.");
    }

    /**
     * Get the number of variables in this term.
     */
    public int size() {
        return size;
    }

    /**
     * Get the rule this term was generated from.
     */
    public Rule getRule() {
        return rule;
    }

    /**
     * Get the weight of the rule this term was generated from.
     */
    public float getWeight() {
        if (rule != null && rule.isWeighted()) {
            return ((WeightedRule)rule).getWeight();
        }

        return Float.POSITIVE_INFINITY;
    }

    public boolean isActive() {
        if (rule != null) {
            return rule.isActive();
        }

        return true;
    }

    /**
     * Get the constant defining this term.
     */
    public float getConstant() {
        return constant;
    }

    /**
     * Get the indices of the atoms involved in this reasoner term.
     */
    public int[] getAtomIndexes() {
        return atomIndexes;
    }

    public void setAtomIndexes(int[] atomIndexes) {
        assert (atomIndexes.length == size);
        this.atomIndexes = atomIndexes;
    }

    /**
     * Get the coefficients of the atoms involved in this term.
     * The coefficients are aligned with the atomIndexes array, i.e., the i'th entry in the coefficient array
     * corresponds to the atom with the i'th atomIndex in the atomIndex array.
     */
    public float[] getCoefficients() {
        return coefficients;
    }

    /**
     * Return a boolean indicating if this term represents a hard constraint.
     */
    public boolean isConstraint() {
        return termType.equals(TermType.LinearConstraintTerm);
    }

    /**
     * Evaluate the term's (weighted) value using the given variable values (indexed according to the AtomStore).
     * For constraints, the evaluation is the extended value extension of the constraint.
     */
    public float evaluate(float[] variableValues) {
        float incompatibility = evaluateIncompatibility(variableValues);
        if (isConstraint()) {
            if (incompatibility > 0.0f) {
                return Float.POSITIVE_INFINITY;
            }
            return 0.0f;
        }
        return getWeight() * incompatibility;
    }

    /**
     * Evaluate the term's incompatibility using the given variable values (indexed according to the AtomStore).
     */
    public float evaluateIncompatibility(float[] variableValues) {
        switch (termType) {
            case LinearConstraintTerm:
                return evaluateConstraint(variableValues);
            case LinearLossTerm:
                return evaluateLinearLoss(variableValues);
            case HingeLossTerm:
                return evaluateHingeLoss(variableValues);
            case SquaredLinearLossTerm:
                return evaluateSquaredLinearLoss(variableValues);
            case SquaredHingeLossTerm:
                return evaluateSquaredHingeLoss(variableValues);
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    /**
     * Evaluate to zero if the constraint is satisfied, infinity otherwise.
     * if (coefficients^T * y [comparator] constant) { return 0.0 }
     * else { return infinity }
     */
    protected float evaluateConstraint(float[] variableValues) {
        float value = computeInnerPotential(variableValues);

        if (comparator.equals(FunctionComparator.EQ)) {
            if (MathUtils.isZero(value, MathUtils.RELAXED_EPSILON)) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else if (comparator.equals(FunctionComparator.LTE)) {
            if (value <= MathUtils.RELAXED_EPSILON) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else if (comparator.equals(FunctionComparator.GTE)) {
            if (value >= -MathUtils.RELAXED_EPSILON) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else {
            throw new IllegalStateException("Unknown comparison function.");
        }
    }

    /**
     * coefficients^T * y
     */
    protected float evaluateLinearLoss(float[] variableValues) {
        return computeInnerPotential(variableValues);
    }

    /**
     * max(0.0, coefficients^T * y - constant)
     */
    protected float evaluateHingeLoss(float[] variableValues) {
        return Math.max(0.0f, computeInnerPotential(variableValues));
    }

    /**
     * (coefficients^T * y - constant)^2
     */
    protected float evaluateSquaredLinearLoss(float[] variableValues) {
        return (float)Math.pow(computeInnerPotential(variableValues), 2.0f);
    }

    /**
     * [max(0, coefficients^T * y - constant)]^2
     */
    public float evaluateSquaredHingeLoss(float[] variableValues) {
        return (float)Math.pow(Math.max(0.0f, computeInnerPotential(variableValues)), 2.0f);
    }

    /**
     * coefficients^T * y - constant
     */
    public float computeInnerPotential(float[] variableValues) {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variableValues[atomIndexes[i]];
        }

        return value - constant;
    }

    /**
     * Compute the partial derivative of the (weighted) potential.
     */
    public float computeVariablePartial(int varId, float innerPotential) {
        switch (termType) {
            case LinearConstraintTerm:
                return getWeight() * computeLinearConstraintPartial(varId);
            case LinearLossTerm:
                return getWeight() * computeLinearLossPartial(varId);
            case HingeLossTerm:
                return getWeight() * computeHingeLossPartial(varId, innerPotential);
            case SquaredLinearLossTerm:
                return getWeight() * computeSquaredLinearLossPartial(varId, innerPotential);
            case SquaredHingeLossTerm:
                return getWeight() * computeSquaredHingeLossPartial(varId, innerPotential);
            default:
                throw new IllegalStateException("Unknown term type.");
        }
    }

    protected float computeLinearConstraintPartial(int varId) {
        return coefficients[varId];
    }

    protected float computeLinearLossPartial(int varId) {
        return coefficients[varId];
    }

    protected float computeHingeLossPartial(int varId, float innerPotential) {
        if (innerPotential <= 0.0f) {
            return 0.0f;
        }

        return coefficients[varId];
    }

    protected float computeSquaredLinearLossPartial(int varId, float innerPotential) {
        return 2.0f * innerPotential * coefficients[varId];
    }

    protected float computeSquaredHingeLossPartial(int varId, float innerPotential) {
        if (innerPotential <= 0.0f) {
            return 0.0f;
        }

        return 2.0f * innerPotential * coefficients[varId];
    }

    /**
     * Load the provided state of the term.
     * By default, reasoner terms hold no state information.
     */
    public void loadState(TermState termState) {
        // Pass.
    }

    /**
     * Save the current state of the term.
     * By default, reasoner terms hold no state information.
     */
    public TermState saveState() {
        return new TermState();
    }

    /**
     * Save the current state of the term using the provide TermState object.
     * By default, reasoner terms hold no state information.
     */
    public void saveState(TermState termState) {
        // Pass.
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(AtomStore atomStore) {
        // weight * [max(coeffs^T * x - constant, 0.0)]^2

        StringBuilder builder = new StringBuilder();

        builder.append(getWeight());
        builder.append(" * ");

        if (hinge) {
            builder.append("max(0.0, ");
        } else {
            builder.append("(");
        }

        for (int i = 0; i < size; i++) {
            builder.append("(");
            builder.append(coefficients[i]);

            if (atomStore == null) {
                builder.append(" * <index:");
                builder.append(atomIndexes[i]);
                builder.append(">)");
            } else {
                builder.append(" * ");
                builder.append(atomStore.getAtomValue(atomIndexes[i]));
                builder.append(")");
            }

            if (i != size - 1) {
                builder.append(" + ");
            }
        }

        builder.append(" - ");
        builder.append(constant);

        builder.append(")");

        if (squared) {
            builder.append(" ^2");
        }

        return builder.toString();
    }
}
