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

import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;

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

    public GroundAtom ground(AtomManager atomManager, ResultList res, int resultIndex) {
        return ground(atomManager, res, resultIndex, new Constant[arguments.length]);
    }

    /**
     * Ground using the passed in buffer.
     * The buffer cannot be held as member datum or statically for thread-safety.
     * It is up to the caller to make sure the buffer is only used on this thread.
     */
    public GroundAtom ground(AtomManager atomManager, ResultList res, int resultIndex, Constant[] newArgs) {
        return ground(atomManager, res, resultIndex, newArgs, false);
    }

    public GroundAtom ground(AtomManager atomManager, ResultList res, int resultIndex, Constant[] newArgs, boolean checkDBCache) {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof Variable) {
                newArgs[i] = res.get(resultIndex, (Variable)arguments[i]);
            } else if (arguments[i] instanceof Constant) {
                newArgs[i] = (Constant)arguments[i];
            } else {
                throw new IllegalArgumentException("Unrecognized type of Term.");
            }
        }

        if (checkDBCache) {
            if (!atomManager.getDatabase().hasCachedAtom((StandardPredicate)predicate, newArgs)) {
                return null;
            }
        }

        return atomManager.getAtom(predicate, newArgs);
    }

    public GroundAtom ground(AtomManager atomManager, Constant[] queryResults, Map<Variable, Integer> projectionMap) {
        return ground(atomManager, queryResults, projectionMap, new Constant[arguments.length]);
    }

    public GroundAtom ground(AtomManager atomManager, Constant[] queryResults, Map<Variable, Integer> projectionMap, Constant[] newArgs) {
        return ground(atomManager, queryResults, projectionMap, newArgs, false);
    }

    public GroundAtom ground(AtomManager atomManager, Constant[] queryResults, Map<Variable, Integer> projectionMap, Constant[] newArgs, boolean checkDBCache) {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof Variable) {
                newArgs[i] = queryResults[projectionMap.get((Variable)arguments[i]).intValue()];
            } else if (arguments[i] instanceof Constant) {
                newArgs[i] = (Constant)arguments[i];
            } else {
                throw new IllegalArgumentException("Unrecognized type of Term.");
            }
        }

        if (checkDBCache) {
            if (!atomManager.getDatabase().hasCachedAtom((StandardPredicate)predicate, newArgs)) {
                return null;
            }
        }

        return atomManager.getAtom(predicate, newArgs);
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
}
