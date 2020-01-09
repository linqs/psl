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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.config.Config;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.dcd.DCDReasoner;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.VariableTermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TermGenerator for DCD objective terms.
 */
public class DCDTermGenerator extends HyperplaneTermGenerator<DCDObjectiveTerm, RandomVariableAtom> {
    private static final Logger log = LoggerFactory.getLogger(DCDTermGenerator.class);

    private float c;

    public DCDTermGenerator() {
        c = Config.getFloat(DCDReasoner.C_KEY, DCDReasoner.C_DEFAULT);
    }

    @Override
    public Class<RandomVariableAtom> getLocalVariableType() {
        return RandomVariableAtom.class;
    }

    @Override
    public DCDObjectiveTerm createLossTerm(TermStore <DCDObjectiveTerm, RandomVariableAtom> baseTermStore,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane<RandomVariableAtom> hyperplane) {
        VariableTermStore<DCDObjectiveTerm, RandomVariableAtom> termStore = (VariableTermStore<DCDObjectiveTerm, RandomVariableAtom>)baseTermStore;
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();

        if (isHinge && isSquared) {
            return new DCDObjectiveTerm(termStore, true, hyperplane, weight, c);
        } else if (isHinge && !isSquared) {
            return new DCDObjectiveTerm(termStore, false, hyperplane, weight, c);
        } else if (!isHinge && isSquared) {
            log.warn("DCD does not support squared linear terms: " + groundRule);
            return null;
        } else {
            log.warn("DCD does not support linear terms: " + groundRule);
            return null;
        }
    }

    @Override
    public DCDObjectiveTerm createLinearConstraintTerm(TermStore<DCDObjectiveTerm, RandomVariableAtom> termStore,
            GroundRule groundRule, Hyperplane<RandomVariableAtom> hyperplane, FunctionComparator comparator) {
        log.warn("DCD does not support hard constraints, i.e. " + groundRule);
        return null;
    }
}
