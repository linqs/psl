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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.TermGenerator;

import java.util.Collection;

/**
 * A TermGenerator for ADMM objective terms.
 */
public class ADMMTermGenerator extends TermGenerator<ADMMObjectiveTerm> {
    public ADMMTermGenerator() {
        this(true);
    }

    public ADMMTermGenerator(boolean mergeConstants) {
        super(mergeConstants);
    }

    @Override
    public int createLossTerm(Collection<ADMMObjectiveTerm> newTerms,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane hyperplane) {
        if (isHinge && isSquared) {
            newTerms.add(ADMMObjectiveTerm.createSquaredHingeLossTerm(hyperplane, groundRule.getRule()));
        } else if (isHinge && !isSquared) {
            newTerms.add(ADMMObjectiveTerm.createHingeLossTerm(hyperplane, groundRule.getRule()));
        } else if (!isHinge && isSquared) {
            newTerms.add(ADMMObjectiveTerm.createSquaredLinearLossTerm(hyperplane, groundRule.getRule()));
        } else {
            newTerms.add(ADMMObjectiveTerm.createLinearLossTerm(hyperplane, groundRule.getRule()));
        }

        return 1;
    }

    @Override
    public int createLinearConstraintTerm(Collection<ADMMObjectiveTerm> newTerms,
            GroundRule groundRule, Hyperplane hyperplane, FunctionComparator comparator) {
        newTerms.add(ADMMObjectiveTerm.createLinearConstraintTerm(hyperplane, groundRule.getRule(), comparator));
        return 1;
    }
}
