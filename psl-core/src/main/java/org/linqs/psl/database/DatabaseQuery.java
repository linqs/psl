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
package org.linqs.psl.database;

import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A query to select groundings from a {@link Database}.
 * Groundings that match the query are returned in the form of a {@link ResultList}.
 *
 * The Formula is given upon initialization and is fixed.
 * It must be a Conjunction of Atoms or a single Atom.
 * Any {@link Variable} in the Formula must be used in an Atom with a StandardPredicate.
 * (Then it can be used in others as well.)
 * The query will return any grounding such that each GroundAtom
 * with a StandardPredicate in the ground Formula is persisted in the
 * Database and each GroundAtom with a FunctionalPredicate
 * in the ground Formula has a non-zero truth value (regardless of whether
 * it is instantiated in memory).
 */
public class DatabaseQuery {
    private final Formula formula;
    private final boolean distinct;
    private final Set<Variable> ignoreVariables;

    public DatabaseQuery(Formula formula) {
        this(formula, true);
    }

    public DatabaseQuery(Formula formula, boolean distinct) {
        this(formula, distinct, new HashSet<Variable>());
    }

    public DatabaseQuery(Formula formula, boolean distinct, Set<Variable> ignoreVariables) {
        this.formula = formula;
        this.distinct = distinct;
        this.ignoreVariables = ignoreVariables;

        validate(formula);
    }

    public Formula getFormula() {
        return formula;
    }

    public boolean getDistinct() {
        return distinct;
    }

    public Set<Variable> getIgnoreVariables() {
        return ignoreVariables;
    }

    public static void validate(Formula formula) {
        FormulaAnalysis analysis = new FormulaAnalysis(formula);
        if (analysis.getNumDNFClauses() > 1 || analysis.getDNFClause(0).getNegLiterals().size() > 0) {
            throw new IllegalArgumentException("Illegal query formula. " +
                    "Must be a conjunction of atoms or a single atom. " +
                    "Formula: " + formula);
        }

        Set<Variable> unboundVariables = analysis.getDNFClause(0).getUnboundVariables();
        if (unboundVariables.size() > 0) {
            Variable[] sortedVariables = unboundVariables.toArray(new Variable[unboundVariables.size()]);
            Arrays.sort(sortedVariables);

            throw new IllegalArgumentException(
                    "Any variable used in a negated (non-functional) predicate must also participate" +
                    " in a positive (non-functional) predicate." +
                    " The following variables do not meet this requirement: [" + StringUtils.join(", ", sortedVariables) + "]."
            );
        }
    }
}
