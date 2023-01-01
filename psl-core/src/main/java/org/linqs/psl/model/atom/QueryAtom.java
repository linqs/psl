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

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.util.MathUtils;

import java.util.Map;

/**
 * An Atom that can be used in a query, but does not have a truth value.
 *
 * Arguments to a GetAtom can be a mix of Variables and Constants.
 * In other words, they are not necessarily ground
 * and can be used for matching GroundAtoms in a query.
 */
public class QueryAtom extends Atom {
    public QueryAtom(Predicate predicate, Term... args) {
        super(predicate, args);
    }

    /**
     * Have this GetAtom assume new values.
     * Do not use unless you really know what you are doing.
     * Typical usage would just create a new GetAtom.
     */
    public void assume(Predicate predicate, Term... args) {
        init(false, false, predicate, args);
    }

    public GroundAtom ground(Database database, ResultList res, int resultIndex) {
        return ground(database, res, resultIndex, new Constant[arguments.length], -1.0f);
    }

    /**
     * Ground using the passed in buffer.
     * The buffer cannot be held as member datum or statically for thread-safety.
     * It is up to the caller to make sure the buffer is only used on this thread.
     */
    public GroundAtom ground(Database database, ResultList res, int resultIndex, Constant[] newArgs, float trivialValue) {
        return ground(database, res, resultIndex, newArgs, trivialValue, false);
    }

    public GroundAtom ground(Database database, ResultList res, int resultIndex, Constant[] newArgs, float trivialValue, boolean ignoreUnmanagedAtoms) {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof Variable) {
                newArgs[i] = res.get(resultIndex, (Variable)arguments[i]);
            } else if (arguments[i] instanceof Constant) {
                newArgs[i] = (Constant)arguments[i];
            } else {
                throw new IllegalArgumentException("Unrecognized type of Term.");
            }
        }

        return fetchAtom(database, predicate, newArgs, trivialValue, ignoreUnmanagedAtoms);
    }

    public GroundAtom ground(Database database, Constant[] queryResults, Map<Variable, Integer> projectionMap) {
        return ground(database, queryResults, projectionMap, new Constant[arguments.length], -1.0f);
    }

    public GroundAtom ground(Database database, Constant[] queryResults, Map<Variable, Integer> projectionMap, Constant[] newArgs, float trivialValue) {
        return ground(database, queryResults, projectionMap, newArgs, trivialValue, false);
    }

    public GroundAtom ground(Database database, Constant[] queryResults, Map<Variable, Integer> projectionMap, Constant[] newArgs, float trivialValue, boolean ignoreUnmanagedAtoms) {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof Variable) {
                newArgs[i] = queryResults[projectionMap.get((Variable)arguments[i]).intValue()];
            } else if (arguments[i] instanceof Constant) {
                newArgs[i] = (Constant)arguments[i];
            } else {
                throw new IllegalArgumentException("Unrecognized type of Term.");
            }
        }

        return fetchAtom(database, predicate, newArgs, trivialValue, ignoreUnmanagedAtoms);
    }

    public VariableTypeMap collectVariables(VariableTypeMap varMap) {
        for (int i=0;i<arguments.length;i++) {
            if (arguments[i] instanceof Variable) {
                ConstantType t = predicate.getArgumentType(i);
                varMap.addVariable((Variable)arguments[i], t);
            }
        }

        return varMap;
    }

    private GroundAtom fetchAtom(Database database, Predicate predicate, Constant[] args, float trivialValue, boolean ignoreUnmanagedAtoms) {
        GroundAtom atom = database.getAtomStore().getAtom(predicate, args);

        // Functional atoms always exist (and are unmanaged), shortcut the remaining checks.
        if (predicate instanceof FunctionalPredicate) {
            return atom;
        }

        // Check to ignore unmanaged atoms.
        if (ignoreUnmanagedAtoms && !atom.isManaged()) {
            return null;
        }

        // Check for triviality.
        if ((atom instanceof ObservedAtom) && MathUtils.equals(trivialValue, atom.getValue())) {
            return null;
        }

        return atom;
    }
}
