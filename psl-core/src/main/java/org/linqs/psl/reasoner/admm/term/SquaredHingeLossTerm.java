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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.term.Hyperplane;

/**
 * ADMMReasoner objective term of the form <br />
 * weight * [max(coefficients^T * x - constant, 0)]^2
 */
public class SquaredHingeLossTerm extends SquaredHyperplaneTerm {
    public SquaredHingeLossTerm(GroundRule groundRule, Hyperplane<LocalVariable> hyperplane) {
        super(groundRule, hyperplane);
    }

    /**
     * weight * [max(coefficients^T * x - constant, 0.0)]^2
     */
    @Override
    public float evaluate() {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        return weight * (float)Math.pow(Math.max(0.0f, super.evaluate()), 2);
    }

    @Override
    public float evaluate(float[] consensusValues) {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        return weight * (float)Math.pow(Math.max(0.0f, super.evaluate(consensusValues)), 2);
    }

    @Override
    public void minimize(float stepSize, float[] consensusValues) {
        // Initializes scratch data.
        float total = 0.0f;

        // Minimizes without the quadratic loss, i.e., solves
        // argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
            total += coefficients[i] * variable.getValue();
        }

        // If the quadratic loss is NOT active at the computed point, it is the solution...
        if (total <= constant) {
            return;
        }

        // Else, minimizes with the quadratic loss, i.e., solves
        // argmin weight * (coefficients^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
        minWeightedSquaredHyperplane(stepSize, consensusValues);
    }
}
