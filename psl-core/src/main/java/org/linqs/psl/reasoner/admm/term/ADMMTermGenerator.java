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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;

import java.util.Collection;

/**
 * A TermGenerator for ADMM objective terms.
 */
public class ADMMTermGenerator extends HyperplaneTermGenerator<ADMMObjectiveTerm, LocalVariable> {
    public ADMMTermGenerator() {
        this(true);
    }

    public ADMMTermGenerator(boolean mergeConstants) {
        super(mergeConstants);
    }

    @Override
    public Class<LocalVariable> getLocalVariableType() {
        return LocalVariable.class;
    }

    @Override
    public int createLossTerm(Collection<ADMMObjectiveTerm> newTerms, TermStore<ADMMObjectiveTerm, LocalVariable> termStore,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane<LocalVariable> hyperplane) {
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
    public int createLinearConstraintTerm(Collection<ADMMObjectiveTerm> newTerms, TermStore<ADMMObjectiveTerm, LocalVariable> termStore,
            GroundRule groundRule, Hyperplane<LocalVariable> hyperplane, FunctionComparator comparator) {
        newTerms.add(ADMMObjectiveTerm.createLinearConstraintTerm(hyperplane, groundRule.getRule(), comparator));
        if (!addDeterTerms) {
            return 1;
        }

        Rule rawRule = groundRule.getRule();
        if (rawRule == null || !(rawRule instanceof AbstractArithmeticRule)) {
            return 1;
        }

        AbstractArithmeticRule rule = (AbstractArithmeticRule)rawRule;
        if (!rule.getExpression().looksLikeFunctionalConstraint()) {
            return 1;
        }

        if (collectiveDeter) {
            newTerms.add(ADMMObjectiveTerm.createCollectiveDeterTerm(hyperplane, deterWeight, deterEpsilon));
            return 2;
        }

        float activeDeterConstant = deterConstant;
        if (MathUtils.isZero(activeDeterConstant)) {
            // If the provided deter value is zero, then compute one.
            activeDeterConstant = 1.0f / hyperplane.size();
        }

        // Make independent hyperplanes for each variable in the constant.
        for (int i = 0; i < hyperplane.size(); i++) {
            Hyperplane<LocalVariable> independentHyperplane = new Hyperplane<LocalVariable>(
                    new LocalVariable[]{hyperplane.getVariable(i)},
                    new float[]{1.0f},
                    0.0f, 1);

            newTerms.add(ADMMObjectiveTerm.createIndependentDeterTerm(independentHyperplane, deterWeight, activeDeterConstant));
        }

        return 1 + hyperplane.size();
    }
}
