/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
package org.linqs.psl.reasoner.gurobi.term;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.LinearExpression;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.util.Logger;

import java.util.Collection;

/**
 * A TermGenerator for Gurobi objective terms.
 */
public class GurobiTermGenerator extends TermGenerator<GurobiObjectiveTerm> {
    private static final Logger log = Logger.getLogger(GurobiTermGenerator.class);

    public GurobiTermGenerator() {
        this(true);
    }

    public GurobiTermGenerator(boolean mergeConstants) {
        super(mergeConstants);
    }

    @Override
    public int createLossTerm(Collection<GurobiObjectiveTerm> newTerms,
                              boolean isHinge, boolean isSquared, GroundRule groundRule, LinearExpression linearExpression) {
        // Interpret all loss terms as hinges.
        // This is safe for Lukasiewicz interpretation of logical rules and the current semantics for arithmetic rules.
        newTerms.add(new GurobiObjectiveTerm(linearExpression, groundRule.getRule(), isSquared, true, null));
        return 1;
    }

    @Override
    public int createLinearConstraintTerm(Collection<GurobiObjectiveTerm> newTerms,
                                          GroundRule groundRule, LinearExpression linearExpression, FunctionComparator comparator) {
        newTerms.add(new GurobiObjectiveTerm(linearExpression, groundRule.getRule(), false, true, comparator));
        return 1;
    }
}
