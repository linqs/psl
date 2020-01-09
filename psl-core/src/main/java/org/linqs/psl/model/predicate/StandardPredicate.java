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
package org.linqs.psl.model.predicate;

import org.linqs.psl.model.term.ConstantType;

/**
 * Predicate of GroundAtoms that can be persisted.
 * Standard predicates cannot have arguments of DeferredFunctionalUniqueID
 * since the underlying storage type is not known.
 */
public class StandardPredicate extends Predicate {
    private boolean isBlock;

    private StandardPredicate(String name, ConstantType[] types) {
        super(name, types);
        isBlock = false;

        for (ConstantType type : types) {
            if (type == ConstantType.DeferredFunctionalUniqueID) {
                throw new IllegalArgumentException(
                        name + " -- DeferredFunctionalUniqueID can only be used with FunctionalPredicates" +
                        " (and should only be used in rare cases.");
            }
        }
    }

    public void setBlock(boolean isBlock) {
        this.isBlock = isBlock;
    }

    public boolean isBlock() {
        return isBlock;
    }

    /**
     * The an existing standard predicate (or null if none with this name exists).
     * If the predicate exists, but is not a StandardPredicate, an exception will be thrown.
     */
    public static StandardPredicate get(String name) {
        Predicate predicate = Predicate.get(name);
        if (predicate == null) {
            return null;
        }

        if (!(predicate instanceof StandardPredicate)) {
            throw new ClassCastException("Predicate (" + name + ") is not a StandardPredicate.");
        }

        return (StandardPredicate)predicate;
    }

    /**
     * Get a predicate if one already exists, othereise create a new one.
     */
    public static StandardPredicate get(String name, ConstantType... types) {
        StandardPredicate predicate = get(name);
        if (predicate == null) {
            return new StandardPredicate(name, types);
        }

        if (predicate.getArity() != types.length) {
            throw new IllegalArgumentException(
                    name + " -- Size mismatch for predicate types. Existing predicate: " +
                    predicate.getArity() + ", Query Predicate: " + types.length);
        }

        for (int i = 0; i < types.length; i++) {
            if (!predicate.getArgumentType(i).equals(types[i])) {
                throw new IllegalArgumentException(
                        name + " -- Type mismatch on " + i + ". Existing predicate: " +
                        predicate.getArgumentType(i) + ", Query Predicate: " + types[i]);
            }
        }

        return predicate;
    }
}
