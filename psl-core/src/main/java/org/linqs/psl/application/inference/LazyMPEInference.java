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

import java.util.Observable;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.application.inference.result.FullInferenceResult;
import org.linqs.psl.application.inference.result.memory.MemoryFullInferenceResult;
import org.linqs.psl.application.util.GroundRules;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.config.Factory;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.LazyAtomManager;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.ReasonerFactory;
import org.linqs.psl.reasoner.admm.ADMMReasonerFactory;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs MPE inference (see MPEInference), but does not require all ground atoms to be
 * specified ahead of time.
 * Instead, any target ground atoms that do not exist (lazy atoms) will get temporarily
 * created at the beginning of each inference round and then persisted to the database
 * if its truth value is above some threshold at the end of each inference round.
 * See LazyAtomManager for details on lazy atoms.
 */
public class LazyMPEInference implements ModelApplication {
	private static final Logger log = LoggerFactory.getLogger(LazyMPEInference.class);

	/**
	 * Prefix of property keys used by this class.
	 *
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "lazympeinference";

	/**
	 * Key for {@link Factory} or String property.
	 *
	 * Should be set to a {@link ReasonerFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link Reasoner}.
	 */
	public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
	/**
	 * Default value for REASONER_KEY.
	 *
	 * Value is instance of {@link ADMMReasonerFactory}.
	 */
	public static final ReasonerFactory REASONER_DEFAULT = new ADMMReasonerFactory();

	/**
	 * The class to use for ground rule storage.
	 */
	public static final String GROUND_RULE_STORE_KEY = CONFIG_PREFIX + ".groundrulestore";
	public static final String GROUND_RULE_STORE_DEFAULT = "org.linqs.psl.application.groundrulestore.MemoryGroundRuleStore";

	/**
	 * The class to use for term storage.
	 * Should be compatible with REASONER_KEY.
	 */
	public static final String TERM_STORE_KEY = CONFIG_PREFIX + ".termstore";
	public static final String TERM_STORE_DEFAULT = "org.linqs.psl.reasoner.admm.term.ADMMTermStore";

	/**
	 * The class to use for term generator.
	 * Should be compatible with REASONER_KEY and TERM_STORE_KEY.
	 */
	public static final String TERM_GENERATOR_KEY = CONFIG_PREFIX + ".termgenerator";
	public static final String TERM_GENERATOR_DEFAULT = "org.linqs.psl.reasoner.admm.term.ADMMTermGenerator";

	/**
	 * Key for int property for the maximum number of rounds of inference.
	 */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";

	/**
	 * Default value for MAX_ROUNDS_KEY property.
	 */
	public static final int MAX_ROUNDS_DEFAULT = 100;

	protected Model model;
	protected Database db;
	protected ConfigBundle config;
	protected final int maxRounds;

	protected Reasoner reasoner;
	protected GroundRuleStore groundRuleStore;
	protected TermStore termStore;
	protected TermGenerator termGenerator;
	protected LazyAtomManager lazyAtomManager;

	public LazyMPEInference(Model model, Database db, ConfigBundle config) {
		this.model = model;
		this.db = db;
		this.config = config;
		maxRounds = config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);

		initialize();
	}

	private void initialize() {
		try {
			reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
			termStore = (TermStore)config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
			groundRuleStore = (GroundRuleStore)config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
			termGenerator = (TermGenerator)config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);
		} catch (Exception ex) {
			// The caller couldn't handle these exception anyways, convert them to runtime ones.
			throw new RuntimeException("Failed to prepare storage for inference.", ex);
		}

		lazyAtomManager = new LazyAtomManager(db, config);

		log.debug("Initial grounding.");
		Grounding.groundAll(model, lazyAtomManager, groundRuleStore);
	}

	/**
	 * Minimizes the total weighted incompatibility of the {@link GroundAtom GroundAtoms}
	 * in the Database according to the Model and commits the updated truth
	 * values back to the Database.
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} to be inferred are those
	 * persisted in the Database when this method is called. All RandomVariableAtoms
	 * which the Model might access must be persisted in the Database.
	 *
	 * @return inference results
	 */
	public FullInferenceResult mpeInference() {
		// Performs rounds of inference until the ground model stops growing.
		int rounds = 0;
		int numActivated = 0;

		do {
			rounds++;
			log.debug("Starting round {} of inference.", rounds);

			// Regenerate optimization terms.
			termStore.clear();

			log.debug("Initializing objective terms for {} ground rules.", groundRuleStore.size());
			termGenerator.generateTerms(groundRuleStore, termStore);
			log.debug("Generated {} objective terms from {} ground rules.", termStore.size(), groundRuleStore.size());

			log.info("Beginning inference round {}.", rounds);
			reasoner.optimize(termStore);
			log.info("Inference round {} complete.", rounds);

			// Only activates if there is another round.
			if (rounds < maxRounds) {
				numActivated = lazyAtomManager.activateAtoms(model, groundRuleStore);
			}
			log.debug("Completed round {} and activated {} atoms.", rounds, numActivated);
		} while (numActivated > 0 && rounds < maxRounds);

		// Commits the RandomVariableAtoms back to the Database.
		lazyAtomManager.commitPersistedAtoms();

		double incompatibility = GroundRules.getTotalWeightedIncompatibility(groundRuleStore.getCompatibilityRules());
		double infeasibility = GroundRules.getInfeasibilityNorm(groundRuleStore.getConstraintRules());

		return new MemoryFullInferenceResult(incompatibility, infeasibility,
				lazyAtomManager.getPersistedRVAtoms().size(), groundRuleStore.size());
	}

	@Override
	public void close() {
		termStore.close();
		groundRuleStore.close();
		reasoner.close();

		termStore = null;
		groundRuleStore = null;
		reasoner = null;

		model = null;
		db = null;
		config = null;
	}

	public GroundRuleStore getGroundRuleStore() {
		return groundRuleStore;
	}
}
