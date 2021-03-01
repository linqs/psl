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

import org.linqs.psl.reasoner.term.ReasonerLocalVariable;

/**
 * The local context of a variable.
 * A local variable keeps track of what global (consensus) variable it is associated with.
 * Note that local variables are hashed and equated by the global variable they track.
 */
public class LocalVariable implements ReasonerLocalVariable {
    private final int globalId;
    private float value;
    private float lagrange;
    private boolean fixed;

    /**
     * In the context of ADMM, local variables should be initialized with the initial value of the
     * global variable they are tracking.
     */
    public LocalVariable(int globalId, float value, boolean fixed) {
        this.value = value;
        this.globalId = globalId;
        this.fixed = fixed;

        lagrange = 0;
    }

    public int getGlobalId() {
        return globalId;
    }

    public float getLagrange() {
        return lagrange;
    }

    public float getValue() {
        return value;
    }

    /**
     * Check if this variable is considered fixed for the purpose of optimization.
     * These variables are meant to be manually set outside of optimization.
     */
    public boolean isFixed() {
        return fixed;
    }

    public void setLagrange(float lagrange) {
        setLagrange(lagrange, false);
    }

    public void setLagrange(float lagrange, boolean overrideFixed) {
        if (!fixed || overrideFixed) {
            this.lagrange = lagrange;
        }
    }

    public void setValue(float value) {
        setValue(value, false);
    }

    public void setValue(float value, boolean overrideFixed) {
        if (!fixed || overrideFixed) {
            this.value = value;
        }
    }

    /**
     * Hash by global identifier.
     */
    @Override
    public int hashCode() {
        return globalId;
    }

    public boolean equals(Object other) {
        if (other == null || !(other instanceof LocalVariable)) {
            return false;
        }

        return this.globalId == ((LocalVariable)other).globalId;
    }

    public String toString() {
        return String.format("(%f, %f, %s)", value, lagrange, fixed);
    }
}
