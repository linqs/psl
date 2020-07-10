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
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;

/**
 * A term in the objective to be optimized by an ADMMReasoner.
 */
public abstract class ADMMObjectiveTerm implements ReasonerTerm {
    protected final GroundRule groundRule;
    protected final LocalVariable[] variables;
    protected final int size;

    /**
     * Caller releases control of the hyperplane and all members of it.
     */
    public ADMMObjectiveTerm(Hyperplane<LocalVariable> hyperplane, GroundRule groundRule) {
        this.variables = hyperplane.getVariables();
        this.size = hyperplane.size();
        this.groundRule = groundRule;
    }

    public void updateLagrange(float stepSize, float[] consensusValues) {
        // Use index instead of iterator here so we can see clear results in the profiler.
        // http://psy-lob-saw.blogspot.co.uk/2014/12/the-escape-of-arraylistiterator.html
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setLagrange(variable.getLagrange() + stepSize * (variable.getValue() - consensusValues[variable.getGlobalId()]));
        }
    }

    /**
     * Updates x to the solution of <br />
     * argmin f(x) + stepSize / 2 * \|x - z + y / stepSize \|_2^2 <br />
     * for the objective term f(x)
     */
    public abstract void minimize(float stepSize, float[] consensusValues);

    /**
     * Evaluate this potential using the local variables.
     */
    public abstract float evaluate();

    /**
     * Evaluate this potential using the given consensus values.
     */
    public abstract float evaluate(float[] consensusValues);

    /**
     * Get the variables used in this term.
     * The caller should not modify the returned array, and should check size() for a reliable length.
     */
    public LocalVariable[] getVariables() {
        return variables;
    }

    /**
     * Get the number of variables in this term.
     */
    @Override
    public int size() {
        return size;
    }

    public GroundRule getGroundRule() {
        return groundRule;
    }
}
