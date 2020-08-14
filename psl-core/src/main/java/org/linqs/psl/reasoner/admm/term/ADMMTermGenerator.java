/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

/**
 * A TermGenerator for ADMM objective terms.
 */
public class ADMMTermGenerator extends HyperplaneTermGenerator<ADMMObjectiveTerm, LocalVariable> {
    public ADMMTermGenerator() {
        super();
    }

    @Override
    public Class<LocalVariable> getLocalVariableType() {
        return LocalVariable.class;
    }

    @Override
    public ADMMObjectiveTerm createLossTerm(TermStore<ADMMObjectiveTerm, LocalVariable> termStore,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane<LocalVariable> hyperplane) {
        if (isHinge && isSquared) {
            return ADMMObjectiveTerm.createSquaredHingeLossTerm(hyperplane, groundRule);
        } else if (isHinge && !isSquared) {
            return ADMMObjectiveTerm.createHingeLossTerm(hyperplane, groundRule);
        } else if (!isHinge && isSquared) {
            return ADMMObjectiveTerm.createSquaredLinearLossTerm(hyperplane, groundRule);
        } else {
            return ADMMObjectiveTerm.createLinearLossTerm(hyperplane, groundRule);
        }
    }

    @Override
    public ADMMObjectiveTerm createLinearConstraintTerm(TermStore<ADMMObjectiveTerm, LocalVariable> termStore,
            GroundRule groundRule, Hyperplane<LocalVariable> hyperplane, FunctionComparator comparator) {
        return ADMMObjectiveTerm.createLinearConstraintTerm(hyperplane, groundRule, comparator);
    }
}
