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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.config.Config;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.sgd.SGDReasoner;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.VariableTermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TermGenerator for SGD objective terms.
 */
public class SGDTermGenerator extends HyperplaneTermGenerator<SGDObjectiveTerm, RandomVariableAtom> {
    private static final Logger log = LoggerFactory.getLogger(SGDTermGenerator.class);

    private float learningRate;

    public SGDTermGenerator() {
        learningRate = Config.getFloat(SGDReasoner.LEARNING_RATE_KEY, SGDReasoner.LEARNING_RATE_DEFAULT);
    }

    @Override
    public Class<RandomVariableAtom> getLocalVariableType() {
        return RandomVariableAtom.class;
    }

    @Override
    public SGDObjectiveTerm createLossTerm(TermStore<SGDObjectiveTerm, RandomVariableAtom> baseTermStore,
            boolean isHinge, boolean isSquared, GroundRule groundRule, Hyperplane<RandomVariableAtom> hyperplane) {
        VariableTermStore<SGDObjectiveTerm, RandomVariableAtom> termStore = (VariableTermStore<SGDObjectiveTerm, RandomVariableAtom>)baseTermStore;
        float weight = (float)((WeightedGroundRule)groundRule).getWeight();
        return new SGDObjectiveTerm(termStore, isSquared, isHinge, hyperplane, weight, learningRate);
    }

    @Override
    public SGDObjectiveTerm createLinearConstraintTerm(TermStore<SGDObjectiveTerm, RandomVariableAtom> termStore,
            GroundRule groundRule, Hyperplane<RandomVariableAtom> hyperplane, FunctionComparator comparator) {
        log.warn("SGD does not support hard constraints, i.e. " + groundRule);
        return null;
    }
}
