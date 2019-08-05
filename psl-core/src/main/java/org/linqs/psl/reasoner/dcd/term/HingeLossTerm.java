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
import org.linqs.psl.reasoner.term.Hyperplane;

/**
 * Objective term of the form: weight * max(coeffs^T * x - constant, 0).
 */
public class HingeLossTerm extends DCDObjectiveTerm {
    public HingeLossTerm(float weight, Hyperplane<RandomVariableAtom> hyperplane,
            float c) {
        super(weight, hyperplane, c);
    }

    @Override
    public void minimize(boolean truncateEveryStep) {
        minimize(truncateEveryStep, computeGradient(), adjustedWeight);
    }

    /**
     * weight * max(coeffs^T * x - constant, 0)
     */
    @Override
    public float evaluate() {
        return adjustedWeight * Math.max(super.evaluate(), 0.0f);
    }
}
