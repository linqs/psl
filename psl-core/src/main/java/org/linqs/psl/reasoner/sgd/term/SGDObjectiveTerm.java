/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;

/**
 * A term in the objective to be optimized by an ADMMReasoner.
 */
public class SGDObjectiveTerm implements ReasonerTerm  {
    private boolean squared;
    private float weight;

    private float constant;
    private float learningRate;

    private short size;
    private float[] coefficients;
    private RandomVariableAtom[] variables;

    public SGDObjectiveTerm(boolean squared, Hyperplane<RandomVariableAtom> hyperplane,
            float weight, float learningRate) {
        this.squared = squared;
        this.weight = weight;

        this.learningRate = learningRate;

        size = (short)hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        variables = hyperplane.getVariables();
        constant = hyperplane.getConstant();
    }

    @Override
    public int size() {
        return size;
    }

    public float evaluate() {
        if (squared) {
            // weight * [max(0.0, coeffs^T * x - constant)]^2
            return weight * (float)Math.pow(Math.max(0.0f, dot()), 2);
        } else {
            // weight * max(0.0, coeffs^T * x - constant)
            return weight * Math.max(0.0f, dot());
        }
    }

    public void minimize(int iteration) {
        for (int i = 0 ; i < size; i++) {
            float dot = dot();
            float gradient = 0.0f;

            if (dot >= 0.0f) {
                gradient = computeGradient(iteration, i, dot);
            }

            variables[i].setValue(Math.max(0.0f, Math.min(1.0f, variables[i].getValue() - gradient)));
        }
    }

    private float computeGradient(int iteration, int varId, float dot) {
        if (squared) {
            return weight * (learningRate / iteration) * 2.0f * dot * coefficients[varId];
        } else {
            return weight * (learningRate / iteration) * coefficients[varId];
        }
    }

    private float dot() {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        return value - constant;
    }
}
