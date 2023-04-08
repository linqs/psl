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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.util.Logger;

import java.util.Collection;

/**
 * A TermGenerator for SGD objective terms.
 */
public class SGDTermGenerator extends TermGenerator<SGDObjectiveTerm> {
    private static final Logger log = Logger.getLogger(SGDTermGenerator.class);

    private boolean warnOnConstraint;

    public SGDTermGenerator() {
        this(true, true);
    }

    public SGDTermGenerator(boolean mergeConstants, boolean warnOnConstraint) {
        super(mergeConstants);
        this.warnOnConstraint = warnOnConstraint;
    }

    public void setWarnOnConstraint(boolean warn) {
        warnOnConstraint = warn;
    }

    @Override
    public int createLossTerm(Collection<SGDObjectiveTerm> newTerms,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane hyperplane) {
        newTerms.add(new SGDObjectiveTerm(((WeightedGroundRule)groundRule).getRule(), isSquared, isHinge, hyperplane));
        return 1;
    }

    @Override
    public int createLinearConstraintTerm(Collection<SGDObjectiveTerm> newTerms,
            GroundRule groundRule, Hyperplane hyperplane, FunctionComparator comparator) {
        if (warnOnConstraint) {
            log.warn("SGD does not support hard constraints, i.e. " + groundRule);
            warnOnConstraint = false;
        }

        return 0;
    }
}
