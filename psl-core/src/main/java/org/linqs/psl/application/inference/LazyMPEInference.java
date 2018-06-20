/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.application.inference;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.util.GroundRules;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.LazyAtomManager;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Performs MPE inference (see MPEInference), but does not require all ground atoms to be
 * specified ahead of time.
 * Instead, any target ground atoms that do not exist (lazy atoms) will get temporarily
 * created at the beginning of each inference round and then persisted to the database
 * if its truth value is above some threshold at the end of each inference round.
 * See LazyAtomManager for details on lazy atoms.
 */
public class LazyMPEInference extends InferenceApplication {
	private static final Logger log = LoggerFactory.getLogger(LazyMPEInference.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "lazympeinference";

	/**
	 * Key for int property for the maximum number of rounds of inference.
	 */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";
	public static final int MAX_ROUNDS_DEFAULT = 100;

	protected final int maxRounds;

	protected LazyAtomManager lazyAtomManager;

	public LazyMPEInference(Model model, Database db) {
		super(model, db);
		maxRounds = Config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
	}

	@Override
	protected void completeInitialize() {
		log.debug("Creating lazy atom mannager.");
		lazyAtomManager = new LazyAtomManager(db);

		log.debug("Initial grounding.");
		Grounding.groundAll(model, lazyAtomManager, groundRuleStore);
	}

	@Override
	public void inference() {
		inference(model.getRules(), reasoner, groundRuleStore, termStore, termGenerator, lazyAtomManager, maxRounds);
	}

	/**
	 * Do the full MPE inference process.
	 * We move the implementation to a static method so it can be accessed
	 * from outsude methods.
	 * Unlike MPEInference which just calls the reasoner, this process is more involved.
	 */
	public static void inference(List<Rule> rules, Reasoner reasoner, GroundRuleStore groundRuleStore,
			TermStore termStore, TermGenerator termGenerator, LazyAtomManager lazyAtomManager,
			int maxRounds) {
		// Performs rounds of inference until the ground model stops growing.
		int rounds = 0;
		int numActivated = 0;

		do {
			rounds++;
			log.debug("Starting round {} of inference.", rounds);

			// Regenerate optimization terms.
			termStore.clear();

			log.debug("Initializing objective terms for {} ground rules.", groundRuleStore.size());
			@SuppressWarnings("unchecked")
			int termCount = termGenerator.generateTerms(groundRuleStore, termStore);
			log.debug("Generated {} objective terms from {} ground rules.", termCount, groundRuleStore.size());

			log.info("Beginning inference round {}.", rounds);
			reasoner.optimize(termStore);
			log.info("Inference round {} complete.", rounds);

			numActivated = lazyAtomManager.activateAtoms(rules, groundRuleStore);
			log.debug("Completed round {} and activated {} atoms.", rounds, numActivated);
		} while (numActivated > 0 && rounds < maxRounds);

		// Commits the RandomVariableAtoms back to the Database.
		lazyAtomManager.commitPersistedAtoms();
	}
}
