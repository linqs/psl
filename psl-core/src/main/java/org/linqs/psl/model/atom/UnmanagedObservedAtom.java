/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.database.Partition;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;

/**
 * An observed atom that is not generally managed by an AtomStore
 * (and thus may have more than one global instantiation).
 * This may have been created by a closed-world assumption.
 * An exception to this pattern are ground atoms from functional predicates.
 * These will be constructed as unmanaged, but stored in the AtomStore.
 */
public final class UnmanagedObservedAtom extends ObservedAtom {
    public UnmanagedObservedAtom(Predicate predicate, Constant[] args, float value) {
        super(predicate, args, value, Partition.SPECIAL_UNMANAGED_ID);
    }

    @Override
    public UnmanagedObservedAtom copy() {
        return new UnmanagedObservedAtom(predicate, (Constant[]) arguments, value);
    }

    @Override
    public boolean isManaged() {
        return false;
    }
}
