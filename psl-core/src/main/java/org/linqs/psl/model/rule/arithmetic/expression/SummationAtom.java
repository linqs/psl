/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.model.rule.arithmetic.expression;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Term;

import java.io.Serializable;
import java.util.Arrays;

/**
 * A variant of an {@link Atom} that can additionally take {@link SummationVariable SummationVariables}
 * as arguments.
 *
 * SummationAtoms can be used in an {@link ArithmeticRuleExpression}.
 *
 * Note that SummationAtom is not a subclass of Atom.
 *
 * @author Stephen Bach
 */
public class SummationAtom implements Serializable, SummationAtomOrAtom {
    protected final Predicate predicate;
    protected final SummationVariableOrTerm[] args;

    public SummationAtom(Predicate predicate, SummationVariableOrTerm[] args) {
        this.predicate = predicate;
        this.args = args;

        checkSchema();
    }

    /**
     * Verifies that this atom has valid arguments.
     *
     * @throws IllegalArgumentException if the number of arguments doesn't match the
     *  number of arguments of the predicate
     * @throws IllegalArgumentException if any argument is null
     */
    protected void checkSchema() {
        if (predicate.getArity() != args.length) {
            throw new IllegalArgumentException("Length of Schema does not match the number of args.");
        }

        for (SummationVariableOrTerm arg : args) {
            if (arg == null) {
                throw new IllegalArgumentException("Arguments must not be null.");
            }
        }
    }

    public QueryAtom getQueryAtom() {
        Term[] queryAtomArgs = new Term[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Term) {
                queryAtomArgs[i] = (Term)args[i];
            } else {
                queryAtomArgs[i] = ((SummationVariable)args[i]).getVariable();
            }
        }
        return new QueryAtom(predicate, queryAtomArgs);
    }

    /**
     * Returns the predicate associated with this SummationAtom.
     */
    public Predicate getPredicate() {
        return predicate;
    }

    /**
     * Returns the number of arguments to the associated Predicate.
     */
    public int getArity() {
        return predicate.getArity();
    }

    /**
     * Returns the arguments associated with this SummationAtom.
     */
    public SummationVariableOrTerm[] getArguments() {
        return args;
    }

    public int getNumSummationVariables() {
        int count = 0;

        for (SummationVariableOrTerm arg : args) {
            if (arg instanceof SummationVariable) {
                count++;
            }
        }

        return count;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(predicate.getName());
        s.append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                s.append(", ");
            }
            s.append(args[i]);
        }
        s.append(")");
        return s.toString();
    }

    @Override
    public int hashCode() {
        return getQueryAtom().hashCode();
    }

    @Override
    public boolean equals(Object oth) {
        if (oth==this) return true;
        if (oth==null || !(oth instanceof SummationAtom)) return false;

        if (!predicate.equals(((SummationAtom) oth).predicate)) {
            return false;
        }

        if (args.length != ((SummationAtom) oth).args.length) {
            return false;
        }

        for (int i = 0; i < args.length; i++) {
            if (!args[i].equals(((SummationAtom) oth).args[i])) {
                return false;
            }
        }

        return true;
    }
}
