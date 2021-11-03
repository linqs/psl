/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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

import org.linqs.psl.reasoner.term.ReasonerAtom;

/**
 * A local copy of a GroundAtom.
 * A LocalAtom keeps track of what GroundAtom (consensus atom) it is associated with.
 * Note that LocalAtoms are hashed and equated by the global atom they track.
 */
public class LocalAtom implements ReasonerAtom {
    private final int globalId;
    private float value;
    private float lagrange;

    /**
     * LocalAtoms should be initialized with the initial value of the ground atom they are tracking.
     */
    public LocalAtom(int globalId, float value) {
        this.value = value;
        this.globalId = globalId;

        lagrange = 0;
    }

    public int getGlobalId() {
        return globalId;
    }

    public float getLagrange() {
        return lagrange;
    }

    @Override
    public float getValue() {
        return value;
    }

    public void setLagrange(float lagrange) {
        this.lagrange = lagrange;
    }

    public void setValue(float value) {
        this.value = value;
    }

    /**
     * Hash by global identifier.
     */
    @Override
    public int hashCode() {
        return globalId;
    }

    public boolean equals(Object other) {
        if (other == null || !(other instanceof LocalAtom)) {
            return false;
        }

        return this.globalId == ((LocalAtom)other).globalId;
    }

    public String toString() {
        return String.format("(%f, %f)", value, lagrange);
    }
}
