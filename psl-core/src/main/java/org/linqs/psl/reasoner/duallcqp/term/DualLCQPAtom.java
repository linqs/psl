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
    private float message;

    private float lowerBoundDualVariable;
    private float upperBoundDualVariable;

    public DualLCQPAtom() {
        message = 0.0f;

        lowerBoundDualVariable = 0.0f;
        upperBoundDualVariable = 0.0f;
    }

    public void addTerm(DualLCQPObjectiveTerm term, float coefficient) {
        addToMessage(term.getDualVariable() * coefficient);
    }

    /**
     * Update the DualLCQPAtom's bound dual variables and its message given the change
     * a dual variable of a term the atom is involved in.
     */
    public synchronized void update(float termDualDelta, float coefficient,
                                    float regularizationParameter, float stepSize) {
        float lowerBoundPartial = getLowerBoundPartial(regularizationParameter);
        float upperBoundPartial = getUpperBoundPartial(regularizationParameter);

        addToMessage(termDualDelta * coefficient);

        setLowerBoundDualVariable(Math.max(0.0f, lowerBoundDualVariable - stepSize * lowerBoundPartial));
        setUpperBoundDualVariable(Math.max(0.0f, upperBoundDualVariable - stepSize * upperBoundPartial));
    }

    private synchronized void addToMessage(float value) {
        message += value;
    }

    public float getMessage() {
        return message;
    }

    public float getPrimal(float regularizationParameter) {
        return -1.0f * message / (2.0f * regularizationParameter);
    }

    public float getLowerBoundDualVariable() {
        return lowerBoundDualVariable;
    }

    public float getUpperBoundDualVariable() {
        return upperBoundDualVariable;
    }

    public synchronized void setLowerBoundDualVariable(float lowerBoundDualVariable) {
        float dualVariableChange = lowerBoundDualVariable - this.lowerBoundDualVariable;
        addToMessage(-1.0f * dualVariableChange);
        this.lowerBoundDualVariable = lowerBoundDualVariable;
    }

    public synchronized void setUpperBoundDualVariable(float upperBoundDualVariable) {
        float dualVariableChange = upperBoundDualVariable - this.upperBoundDualVariable;
        addToMessage(dualVariableChange);
        this.upperBoundDualVariable = upperBoundDualVariable;
    }

    public float getLowerBoundObjective(float regularizationParameter) {
        return -1.0f * message * lowerBoundDualVariable / (2.0f * regularizationParameter);
    }

    public float getUpperBoundObjective(float regularizationParameter) {
        return message * upperBoundDualVariable / (2.0f * regularizationParameter) + 2.0f * upperBoundDualVariable;
    }

    public float getLowerBoundPartial(float regularizationParameter) {
        float lowerBoundPartial = -1.0f * message / regularizationParameter;
        if (MathUtils.isZero(lowerBoundDualVariable, MathUtils.STRICT_EPSILON) && lowerBoundPartial > 0.0f) {
            lowerBoundPartial = 0.0f;
        }
        return lowerBoundPartial;
    }

    public float getUpperBoundPartial(float regularizationParameter) {
        float upperBoundPartial = message / regularizationParameter + 2.0f;
        if (MathUtils.isZero(upperBoundDualVariable, MathUtils.STRICT_EPSILON) && upperBoundPartial > 0.0f) {
            upperBoundPartial = 0.0f;
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
        public float message;

        public float lowerBoundDualVariable;
        public float upperBoundDualVariable;

        public DualLCQPAtomState(float message, float lowerBoundDualVariable, float upperBoundDualVariable) {
            this.message = message;

            this.lowerBoundDualVariable = lowerBoundDualVariable;
            this.upperBoundDualVariable = upperBoundDualVariable;
        }
    }
}
