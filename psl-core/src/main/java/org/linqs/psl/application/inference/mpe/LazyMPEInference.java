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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.LazyAtomManager;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.GroundRules;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Performs MPE inference (see MPEInference), but does not require all ground atoms to be
 * specified ahead of time.
 * Instead, any target ground atoms that do not exist (lazy atoms) will get temporarily
 * created at the beginning of each inference round and then persisted to the database
 * if its truth value is above some threshold at the end of each inference round.
 * See LazyAtomManager for details on lazy atoms.
 */
public class LazyMPEInference extends MPEInference {
    private static final Logger log = LoggerFactory.getLogger(LazyMPEInference.class);

    protected final int maxRounds;

    public LazyMPEInference(List<Rule> rules, Database db) {
        super(rules, db);
        maxRounds = Options.LAZY_INFERENCE_MAX_ROUNDS.getInt();
    }

    @Override
    protected void completeInitialize() {
        log.debug("Initial grounding.");
        Grounding.groundAll(rules, atomManager, groundRuleStore);
        log.debug("Initial grounding complete.");
    }

    @Override
    protected PersistedAtomManager createAtomManager(Database db) {
        return new LazyAtomManager(db);
    }

    @Override
    protected double internalInference(List<Evaluator> evaluators, TrainingMap trainingMap, Set<StandardPredicate> evaluationPredicates) {
        LazyAtomManager lazyAtomManager = (LazyAtomManager)atomManager;
        // Performs rounds of inference until the ground model stops growing.
        int rounds = 0;
        int numActivated = 0;
        double objective = 0.0;

        do {
            rounds++;
            log.debug("Starting round {} of inference.", rounds);

            // Regenerate optimization terms.
            termStore.clear();

            log.debug("Initializing objective terms for {} ground rules.", groundRuleStore.size());
            termStore.ensureVariableCapacity(lazyAtomManager.getCachedRVACount());
            @SuppressWarnings("unchecked")
            long termCount = termGenerator.generateTerms(groundRuleStore, termStore);
            log.debug("Generated {} objective terms from {} ground rules.", termCount, groundRuleStore.size());

            log.info("Beginning inference round {}.", rounds);
            objective = reasoner.optimize(termStore, evaluators, trainingMap, evaluationPredicates);
            log.info("Inference round {} complete.", rounds);

            numActivated = lazyAtomManager.activateAtoms(rules, groundRuleStore);
            log.debug("Completed round {} and activated {} atoms.", rounds, numActivated);
        } while (numActivated > 0 && rounds < maxRounds);

        return objective;
    }
}
