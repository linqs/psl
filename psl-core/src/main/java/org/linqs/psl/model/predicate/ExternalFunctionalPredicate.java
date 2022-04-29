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

import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.term.Constant;

/**
 * A FunctionalPredicate which uses an {@link ExternalFunction} to compute truth values.
 */
public class ExternalFunctionalPredicate extends FunctionalPredicate {
    private final ExternalFunction extFun;

    private ExternalFunctionalPredicate(String name, ExternalFunction extFun) {
        super(name, extFun.getArgumentTypes());
        this.extFun = extFun;
    }

    @Override
    public double computeValue(ReadableDatabase db, Constant... args) {
        return extFun.getValue(db, args);
    }

    /**
     * Get the ExternalFunction this predicate uses to compute truth values.
     *
     * @return this predicate's ExternalFunction
     */
    public ExternalFunction getExternalFunction() {
        return extFun;
    }

    public static ExternalFunctionalPredicate get(String name) {
        Predicate predicate = Predicate.get(name);
        if (predicate == null) {
            return null;
        }

        if (!(predicate instanceof ExternalFunctionalPredicate)) {
            throw new ClassCastException("Predicate (" + name + ") is not a ExternalFunctionalPredicate.");
        }

        return (ExternalFunctionalPredicate)predicate;
    }

    /**
     * Get a predicate if one already exists, otherwise create a new one.
     */
    public static ExternalFunctionalPredicate get(String name, ExternalFunction extFun) {
        ExternalFunctionalPredicate predicate = get(name);
        if (predicate == null) {
            return new ExternalFunctionalPredicate(name, extFun);
        }

        if (!predicate.getExternalFunction().equals(extFun)) {
            throw new IllegalArgumentException(
                    "External function mismatch for predicate " + predicate.getName() +
                    " Existing predicate: " + predicate.getExternalFunction() +
                    ", Query Predicate: " + extFun);
        }

        return predicate;
    }
}
