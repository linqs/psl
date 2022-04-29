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
package org.linqs.psl.model.predicate;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;

/**
 * Commonly used FunctionalPredicates that get special treatment in PSL.
 * These predicates are not backed by physical data (hence the extension of FunctionalPredicate),
 * and they are only worked with at the grounding-query level.
 * This means that they will never need to be actually evaluated in Java and never actually make it into a ground rule.
 *
 * A GroundingOnlyPredicate should be preferred over a user-made FunctionalPredicate
 * or ExternalFunctionalPredicate because they can be evaluated more efficiently
 * (via the translation to SQL).
 *
 * The names of GroundingOnlyPredicates begin with '#'.
 */
public abstract class GroundingOnlyPredicate extends FunctionalPredicate {
    private GroundingOnlyPredicate(String name, ConstantType[] types) {
        super(name, types, false);
    }

    /**
     * True if arguments are equal.
     */
    public static final GroundingOnlyPredicate Equal
        = new GroundingOnlyPredicate("#Equal",
                new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

        @Override
        public double computeValue(ReadableDatabase db, Constant... args) {
            checkArguments(getName(), args);
            return (args[0].equals(args[1])) ? 1.0 : 0.0;
        }
    };

    /**
     * True if arguments are not equal.
     */
    public static final GroundingOnlyPredicate NotEqual
        = new GroundingOnlyPredicate("#NotEqual",
                new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

        @Override
        public double computeValue(ReadableDatabase db, Constant... args) {
            checkArguments(getName(), args);
            return (!args[0].equals(args[1])) ? 1.0 : 0.0;
        }
    };

    /**
     * True if the first argument is less than the second.
     * Used to ground only one of a symmetric pair of ground rules.
     */
    public static final GroundingOnlyPredicate NonSymmetric
        = new GroundingOnlyPredicate("#NonSymmetric",
                new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

        @Override
        public double computeValue(ReadableDatabase db, Constant... args) {
            checkArguments(getName(), args);
            return (args[0].compareTo(args[1]) < 0) ? 1.0 : 0.0;
        }
    };

    private static final void checkArguments(String functionName, Constant[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException(functionName + " expects two arguments, got " + args.length + ".");
        }

        if (!(args[0] instanceof UniqueIntID || args[0] instanceof UniqueStringID) ||
             !(args[1] instanceof UniqueIntID || args[1] instanceof UniqueStringID)) {
            throw new IllegalArgumentException(
                    String.format("%s expects both arguments to be a Unique*ID. Instead, got: (%s, %s).",
                    functionName, args[0].getClass().getName(), args[1].getClass().getName()));
        }

        if (args[0].getClass() != args[1].getClass()) {
            throw new IllegalArgumentException(
                    String.format("%s expects both arguments to be Unique*IDs of the same type. Instead, got: (%s, %s).",
                    functionName, args[0].getClass().getName(), args[1].getClass().getName()));
        }
    }
}
