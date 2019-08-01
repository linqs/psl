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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.MathUtils;

/**
 * A term in the objective to be optimized by a DCDReasoner.
 */
public abstract class DCDObjectiveTerm implements ReasonerTerm  {
    protected final GroundRule groundRule;
    protected final float adjustedWeight;

    protected final int size;
    protected final float[] coefficients;
    protected final RandomVariableAtom[] variables;

    protected final float constant;

    protected final boolean truncateEveryStep;

    protected final float qii;

    protected float lagrange;

    public DCDObjectiveTerm(GroundRule groundRule, Hyperplane<RandomVariableAtom> hyperplane,
            float c, boolean truncateEveryStep) {
        size = hyperplane.size();
        coefficients = hyperplane.getCoefficients();
        variables = hyperplane.getVariables();
        constant = hyperplane.getConstant();

        this.groundRule = groundRule;
        adjustedWeight = (float)((WeightedGroundRule)groundRule).getWeight() * c;

        this.truncateEveryStep = truncateEveryStep;

        float tempQii = 0f;
        for (int i = 0; i < size; i++) {
            tempQii += coefficients[i] * coefficients[i];
        }
        qii = tempQii;

        lagrange = 0.0f;
    }

    public float computeGradient() {
        float val = 0.0f;

        for (int i = 0; i < size; i++) {
            val += variables[i].getValue() * coefficients[i];
        }

        return constant - val;
    }

    protected void minimize(float grad, float lim) {
        float pg = grad;
        if (MathUtils.isZero(lagrange)) {
            pg = Math.min(grad, 0.0f);
        }

        if (MathUtils.equals(lim, adjustedWeight) && MathUtils.equals(lagrange, adjustedWeight)) {
            pg = Math.max(grad, 0.0f);
        }

        if (!MathUtils.isZero(pg)) {
            float pa = lagrange;
            lagrange = Math.min(Math.max(lagrange - grad / qii, 0.0f), lim);
            for (int i = 0; i < size; i++) {
                float val = variables[i].getValue() - ((lagrange - pa) * coefficients[i]);
                if (truncateEveryStep) {
                    val = Math.max(Math.min(val, 1.0f), 0.0f);
                }
                variables[i].setValue(val);
            }
        }
    }

    public float evaluate() {
        float value = 0.0f;

        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        return value - constant;
    }

    @Override
    public int size() {
        return size;
    }

    public abstract void minimize();
}
