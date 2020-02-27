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
package org.linqs.psl.application.learning.weight.maxlikelihood;

import org.linqs.psl.application.inference.LazyMPEInference;
import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.LazyAtomManager;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Voted perception algorithm that does not require a ground model of pre-specified dimensionality.
 *
 * Unlike MaxLikelihoodMPE, this will make changes to your RV database.
 * The lazy growing process requires committing the new lazy atoms to the database.
 *
 * For the gradient of the objective, the expected total incompatibility is computed by finding the MPE state.
 * The model is grown using LazyMPEInference.
 */
public class LazyMaxLikelihoodMPE extends VotedPerceptron {
    private static final Logger log = LoggerFactory.getLogger(LazyMaxLikelihoodMPE.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "lazymaxlikelihoodmpe";

    /**
     * Key for int property for the maximum number of rounds of lazy growing.
     */
    public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxgrowrounds";
    public static final int MAX_ROUNDS_DEFAULT = 100;

    private int maxRounds;

    public LazyMaxLikelihoodMPE(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public LazyMaxLikelihoodMPE(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        maxRounds = Config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
    }

    @Override
    protected void computeObservedIncompatibility() {
        // First grow the atom set (which involves computing the MPE state),
        // and then just do everything else normally.
        LazyMPEInference.inference(allRules, reasoner, groundRuleStore, termStore, termGenerator, (LazyAtomManager)atomManager, maxRounds);

        super.computeObservedIncompatibility();
    }

    @Override
    protected PersistedAtomManager createAtomManager() {
        return new LazyAtomManager(rvDB);
    }
}
