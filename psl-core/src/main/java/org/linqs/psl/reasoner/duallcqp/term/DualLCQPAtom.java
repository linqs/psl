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

import org.linqs.psl.reasoner.term.TermState;
import org.linqs.psl.util.MathUtils;

/**
 * A class that represents the primal LCQP atom and
 * the dual terms corresponding to the box constraints on the variables values.
 * Contains statistics needed by DualBCDReasoners for updating the primal and dual variables.
 */
public final class DualLCQPAtom {
    // The message represents the total influence this atom has on the total objective.
    // To be maintained the reasoners must call the update method whenever it updates a dual variable
    // associated with the atom and set variables via the provided setters.
    private double message;

    private double lowerBoundDualVariable;
    private double upperBoundDualVariable;

    public DualLCQPAtom() {
        message = 0.0;

        lowerBoundDualVariable = 0.0;
        upperBoundDualVariable = 0.0;
    }

    public void addTerm(DualLCQPObjectiveTerm term, float coefficient) {
        addToMessage(term.getDualVariable() * coefficient);
    }

    /**
     * Update the DualLCQPAtom's bound dual variables and its message given the change
     * a dual variable of a term the atom is involved in.
     */
    public synchronized void update(double termDualDelta, double coefficient,
                                    double regularizationParameter, double stepSize) {
        double lowerBoundPartial = getLowerBoundPartial(regularizationParameter);
        double upperBoundPartial = getUpperBoundPartial(regularizationParameter);

        addToMessage(termDualDelta * coefficient);

        setLowerBoundDualVariable(Math.max(0.0, lowerBoundDualVariable - stepSize * lowerBoundPartial));
        setUpperBoundDualVariable(Math.max(0.0, upperBoundDualVariable - stepSize * upperBoundPartial));
    }

    private synchronized void addToMessage(double value) {
        message += value;
    }

    public double getMessage() {
        return message;
    }

    public float getPrimal(double regularizationParameter) {
        return (float)(-1.0 * message / (2.0 * regularizationParameter));
    }

    public double getLowerBoundDualVariable() {
        return lowerBoundDualVariable;
    }

    public double getUpperBoundDualVariable() {
        return upperBoundDualVariable;
    }

    public synchronized void setLowerBoundDualVariable(double lowerBoundDualVariable) {
        double dualVariableChange = lowerBoundDualVariable - this.lowerBoundDualVariable;
        addToMessage(-1.0 * dualVariableChange);
        this.lowerBoundDualVariable = lowerBoundDualVariable;
    }

    public synchronized void setUpperBoundDualVariable(double upperBoundDualVariable) {
        double dualVariableChange = upperBoundDualVariable - this.upperBoundDualVariable;
        addToMessage(dualVariableChange);
        this.upperBoundDualVariable = upperBoundDualVariable;
    }

    public double getLowerBoundObjective(double regularizationParameter) {
        return -1.0 * message * lowerBoundDualVariable / (2.0 * regularizationParameter);
    }

    public double getUpperBoundObjective(double regularizationParameter) {
        return message * upperBoundDualVariable / (2.0 * regularizationParameter) + 2.0 * upperBoundDualVariable;
    }

    public double getLowerBoundPartial(double regularizationParameter) {
        double lowerBoundPartial = -1.0 * message / regularizationParameter;
        if (MathUtils.isZero(lowerBoundDualVariable, MathUtils.STRICT_EPSILON) && (lowerBoundPartial > 0.0)) {
            lowerBoundPartial = 0.0;
        }
        return lowerBoundPartial;
    }

    public double getUpperBoundPartial(double regularizationParameter) {
        double upperBoundPartial = message / regularizationParameter + 2.0;
        if (MathUtils.isZero(upperBoundDualVariable, MathUtils.STRICT_EPSILON) && (upperBoundPartial > 0.0)) {
            upperBoundPartial = 0.0;
        }
        return upperBoundPartial;
    }

    public synchronized void loadState(TermState termState) {
        assert termState instanceof DualLCQPAtomState;
        DualLCQPAtomState dualLCQPAtomState = (DualLCQPAtomState)termState;

        message = dualLCQPAtomState.message;

        lowerBoundDualVariable = dualLCQPAtomState.lowerBoundDualVariable;
        upperBoundDualVariable = dualLCQPAtomState.upperBoundDualVariable;
    }

    public TermState saveState() {
        return new DualLCQPAtomState(message, lowerBoundDualVariable, upperBoundDualVariable);
    }

    public void saveState(TermState termState) {
        assert termState instanceof DualLCQPAtomState;
        DualLCQPAtomState dualLCQPAtomState = (DualLCQPAtomState)termState;

        dualLCQPAtomState.message = message;

        dualLCQPAtomState.lowerBoundDualVariable = lowerBoundDualVariable;
        dualLCQPAtomState.upperBoundDualVariable = upperBoundDualVariable;
    }

    public static final class DualLCQPAtomState extends TermState {
        public double message;

        public double lowerBoundDualVariable;
        public double upperBoundDualVariable;

        public DualLCQPAtomState(double message, double lowerBoundDualVariable, double upperBoundDualVariable) {
            this.message = message;

            this.lowerBoundDualVariable = lowerBoundDualVariable;
            this.upperBoundDualVariable = upperBoundDualVariable;
        }
    }
}
