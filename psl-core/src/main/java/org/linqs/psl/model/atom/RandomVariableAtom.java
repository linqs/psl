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
package org.linqs.psl.model.atom;

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

/**
 * A {@link GroundAtom} with a truth value which can be modified.
 * <p>
 * A GroundAtom is instantiated as a RandomVariableAtom is BOTH of the following
 * conditions are met:
 * <ul>
 *  <li>it has a {@link StandardPredicate} that is open in the Atom's Database</li>
 *  <li>it is not persisted in one of its Database's read-only Partitions</li>
 * </ul>
 */
public class RandomVariableAtom extends GroundAtom {
    /**
     * Whether this atom is backed by a DataStore.
     */
    private boolean isPersisted;

    /**
     * Whether this atom is in violation of an AtomManager's access policy.
     * Typically an AtomManager (like the PersistedAtomManager) would just throw an exception,
     * but exceptions may have been disabled for performance reasons.
     */
    private boolean isAccessException;

    /**
     * An RVA that mirrors this one during optimization.
     */
    private RandomVariableAtom mirror;

    /**
     * Instantiation of GrondAtoms should typically be left to the Database so it can maintain a cache.
     */
    public RandomVariableAtom(StandardPredicate predicate, Constant[] args, float value) {
        super(predicate, args, value);
        isPersisted = false;
        isAccessException = false;
        mirror = null;
    }

    @Override
    public StandardPredicate getPredicate() {
        return (StandardPredicate)predicate;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    /**
     * Sets the truth value of this Atom.
     */
    public void setValue(float value) {
        this.value = value;
    }

    public void setPersisted(boolean isPersisted) {
        this.isPersisted = isPersisted;
    }

    public boolean getPersisted() {
        return isPersisted;
    }

    public void setAccessException(boolean isAccessException) {
        this.isAccessException = isAccessException;
    }

    public boolean getAccessException() {
        return isAccessException;
    }

    public RandomVariableAtom getMirror() {
        return mirror;
    }

    public void setMirror(RandomVariableAtom mirror) {
        if (this.mirror != null && this.mirror != mirror) {
            throw new IllegalArgumentException(String.format(
                    "Mirror atom for %s already set to %s. Cannot change to %s.",
                    this, this.mirror, mirror));
        }

        this.mirror = mirror;
    }
}
