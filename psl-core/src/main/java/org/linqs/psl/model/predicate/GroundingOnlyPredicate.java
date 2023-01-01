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
package org.linqs.psl.model.predicate;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;

import java.util.Map;

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
     * Compute the value, possibly using a lookup map for a variable's constant.
     * This style of check will often be used in grounding processes to validate constants before ground rule instantiation.
     */
    public float computeValue(Atom atom, Map<Variable, Integer> variableMap, Constant[] constants) {
        assert(this == atom.getPredicate());

        Term[] arguments = atom.getArguments();
        assert(arguments.length == 2);

        Constant lhs = null;
        Constant rhs = null;

        if (arguments[0] instanceof Constant) {
            lhs = (Constant)arguments[0];
        } else if (arguments[0] instanceof Variable) {
            lhs = constants[variableMap.get((Variable)arguments[0]).intValue()];
        } else {
            throw new RuntimeException("Expecting Constant or Variable, but found: " + arguments[0].getClass());
        }

        if (arguments[1] instanceof Constant) {
            rhs = (Constant)arguments[1];
        } else if (arguments[1] instanceof Variable) {
            rhs = constants[variableMap.get((Variable)arguments[1]).intValue()];
        } else {
            throw new RuntimeException("Expecting Constant or Variable, but found: " + arguments[1].getClass());
        }

        return computeValue(lhs, rhs);
    }

    /**
     * A method of computing the value that is more direct (and less robust) than the FunctionalPredicate way.
     */
    public abstract float computeValue(Constant a, Constant b);

    /**
     * True if arguments are equal.
     */
    public static final GroundingOnlyPredicate Equal
        = new GroundingOnlyPredicate("#Equal",
                new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

        @Override
        public float computeValue(Database db, Constant... args) {
            checkArguments(getName(), args);
            return computeValue(args[0], args[1]);
        }

        @Override
        public float computeValue(Constant a, Constant b) {
            return a.equals(b) ? 1.0f : 0.0f;
        }
    };

    /**
     * True if arguments are not equal.
     */
    public static final GroundingOnlyPredicate NotEqual
        = new GroundingOnlyPredicate("#NotEqual",
                new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

        @Override
        public float computeValue(Database db, Constant... args) {
            checkArguments(getName(), args);
            return computeValue(args[0], args[1]);
        }

        @Override
        public float computeValue(Constant a, Constant b) {
            return !a.equals(b) ? 1.0f : 0.0f;
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
        public float computeValue(Database db, Constant... args) {
            checkArguments(getName(), args);
            return computeValue(args[0], args[1]);
        }

        @Override
        public float computeValue(Constant a, Constant b) {
            return (a.compareTo(b) < 0) ? 1.0f : 0.0f;
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
