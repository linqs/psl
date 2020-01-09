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
 * weight * coefficients^T * x
 */
public class LinearLossTerm extends ADMMObjectiveTerm {
    private final float[] coefficients;

    /**
     * Caller releases control of |variables| and |coefficients|.
     */
    LinearLossTerm(GroundRule groundRule, Hyperplane<LocalVariable> hyperplane) {
        super(hyperplane, groundRule);

        this.coefficients = hyperplane.getCoefficients();
    }

    @Override
    public void minimize(float stepSize, float[] consensusValues) {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];

            float value = consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize;
            value -= (weight * coefficients[i] / stepSize);

            variable.setValue(value);
        }
    }

    /**
     * weight * coefficients^T * x
     */
    @Override
    public float evaluate() {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        return weight * value;
    }

    @Override
    public float evaluate(float[] consensusValues) {
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * consensusValues[variables[i].getGlobalId()];
        }

        return weight * value;
    }
}
