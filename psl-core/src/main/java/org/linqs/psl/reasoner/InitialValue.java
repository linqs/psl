/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.reasoner;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.util.RandUtils;

/**
 * An enum that represents the initial value a variable should take.
 * ZERO: take a zero value.
 * RANDOM: take a uniform random value in [0, 1].
 * ATOM: take the value of the atom representing this variable.
 */
public enum InitialValue {
    ZERO,
    ONE,
    HALF,
    RANDOM,
    ATOM;

    /**
     * Get the value that this enum represents.
     */
    public float getVariableValue(GroundAtom atom) {
        switch (this) {
            case ZERO:
                return 0.0f;
            case ONE:
                return 1.0f;
            case HALF:
                return 0.5f;
            case RANDOM:
                return RandUtils.nextFloat();
            case ATOM:
                return atom.getValue();
            default:
                throw new IllegalStateException("Unknown initial value state: " + this);
        }
    }
}
