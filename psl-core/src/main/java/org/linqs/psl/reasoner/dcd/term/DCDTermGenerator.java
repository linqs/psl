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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.VariableTermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * A TermGenerator for DCD objective terms.
 */
public class DCDTermGenerator extends HyperplaneTermGenerator<DCDObjectiveTerm, GroundAtom> {
    private static final Logger log = LoggerFactory.getLogger(DCDTermGenerator.class);

    private float c;

    public DCDTermGenerator() {
        this(true);
    }

    public DCDTermGenerator(boolean mergeConstants) {
        super(mergeConstants);
        c = Options.DCD_C.getFloat();
    }

    @Override
    public Class<GroundAtom> getLocalVariableType() {
        return GroundAtom.class;
    }

    @Override
    public int createLossTerm(Collection<DCDObjectiveTerm> newTerms, TermStore <DCDObjectiveTerm, GroundAtom> baseTermStore,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane<GroundAtom> hyperplane) {
        VariableTermStore<DCDObjectiveTerm, GroundAtom> termStore = (VariableTermStore<DCDObjectiveTerm, GroundAtom>)baseTermStore;

        if (isHinge && isSquared) {
            newTerms.add(new DCDObjectiveTerm(termStore, ((WeightedGroundRule)groundRule).getRule(), true, hyperplane, c));
            return 1;
        } else if (isHinge && !isSquared) {
            newTerms.add(new DCDObjectiveTerm(termStore, ((WeightedGroundRule)groundRule).getRule(), false, hyperplane, c));
            return 1;
        } else if (!isHinge && isSquared) {
            log.warn("DCD does not support squared linear terms: " + groundRule);
            return 0;
        } else {
            log.warn("DCD does not support linear terms: " + groundRule);
            return 0;
        }
    }

    @Override
    public int createLinearConstraintTerm(Collection<DCDObjectiveTerm> newTerms, TermStore<DCDObjectiveTerm, GroundAtom> termStore,
            GroundRule groundRule, Hyperplane<GroundAtom> hyperplane, FunctionComparator comparator) {
        log.warn("DCD does not support hard constraints, i.e. " + groundRule);
        return 0;
    }
}
