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

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;

/**
 * A {@link GroundAtom} with a fixed truth value.
 * <p>
 * Circumstances that cause a GroundAtom to be instantiated as an ObservedAtom include
 * <ul>
 *  <li>its Predicate is a StandardPredicate and closed in the Atom's Database</li>
 *  <li>its Predicate is a FunctionalPredicate</li>
 *  <li>its Predicate is a StandardPredicate and it is persisted in one of its
 *  Database's read-only Partitions</li>
 * </ul>
 * Other reasons may exist for specific Database implementations.
 */
public class ObservedAtom extends GroundAtom {
    /**
     * Instantiation of GroundAtoms should typically be left to the Database so it can maintain a cache.
     */
    public ObservedAtom(Predicate predicate, Constant[] args, float value) {
        super(predicate, args, value);
    }

    /**
     * This method should only be used in VERY specific situations and with a considerable amount of preparation.
     * This method sets the truth value of the atom.
     * However, in most circumstances observed atoms have a fixed value.
     */
    public void _assumeValue(float newValue) {
        value = newValue;
    }

    @Override
    public boolean isConstant() {
        return true;
    }
}
