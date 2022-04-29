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
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

/**
 * A Predicate with truth values defined by some function.
 */
public abstract class FunctionalPredicate extends Predicate {
    protected FunctionalPredicate(String name, ConstantType[] types) {
        super(name, types, true);
    }

    protected FunctionalPredicate(String name, ConstantType[] types, boolean checkName) {
        super(name, types, checkName);
    }

    /**
     * Computes the truth value of this Predicate with the given arguments.
     *
     * @param db the connection to the database which is running this query
     * @param args the arguments for which the truth value will be computed
     * @return the computed truth value
     */
    public abstract double computeValue(ReadableDatabase db, Constant... args);
}
