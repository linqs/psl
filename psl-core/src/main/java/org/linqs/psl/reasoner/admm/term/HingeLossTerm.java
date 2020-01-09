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

import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

/**
 * ADMMReasoner objective term of the form <br />
 * weight * max(coefficients^T * x - constant, 0)
 *
 * All coefficients must be non-zero.
 */
public class HingeLossTerm extends HyperplaneTerm {
    public HingeLossTerm(GroundRule groundRule, Hyperplane<LocalVariable> hyperplane) {
        super(groundRule, hyperplane);
    }

    @Override
    public void minimize(float stepSize, float[] consensusValues) {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        float total = 0.0f;

        // Minimizes without the linear loss, i.e., solves
        // argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
            total += (coefficients[i] * variable.getValue());
        }

        // If the linear loss is NOT active at the computed point, it is the solution...
        if (total <= constant) {
            return;
        }

        // Else, minimizes with the linear loss, i.e., solves
        // argmin weight * coefficients^T * x + stepSize/2 * \|x - z + y / stepSize \|_2^2
        total = 0.0f;
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];

            // TODO(eriq): We just took this step above. Is ADMM accidentally taking two steps?
            variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
            variable.setValue(variable.getValue() - weight * coefficients[i] / stepSize);

            total += coefficients[i] * variable.getValue();
        }

        // If the linear loss IS active at the computed point, it is the solution...
        if (total >= constant) {
            return;
        }

        // Else, the solution is on the hinge.
        project(stepSize, consensusValues);
    }

    /**
     * weight * max(0.0, coefficients^T * x - constant)
     */
    @Override
    public float evaluate() {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        return weight * Math.max(super.evaluate(), 0.0f);
    }

    @Override
    public float evaluate(float[] consensusValues) {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        return weight * Math.max(super.evaluate(consensusValues), 0.0f);
    }
}
