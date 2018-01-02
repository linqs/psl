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
import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.application.inference.result.FullInferenceResult;
import org.linqs.psl.application.inference.result.memory.MemoryFullInferenceResult;
import org.linqs.psl.application.util.GroundRules;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.config.Factory;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.ReasonerFactory;
import org.linqs.psl.reasoner.admm.ADMMReasonerFactory;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Infers the most-probable explanation (MPE) state of the
 * {@link RandomVariableAtom RandomVariableAtoms} persisted in a {@link Database},
 * according to a {@link Model}, given the Database's {@link ObservedAtom ObservedAtoms}.
 *
 * The set of RandomVariableAtoms is those persisted in the Database when {@link #mpeInference()}
 * is called. This set must contain all RandomVariableAtoms the Model might access.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class MPEInference implements ModelApplication {

	private static final Logger log = LoggerFactory.getLogger(MPEInference.class);

	/**
	 * Prefix of property keys used by this class.
	 *
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "mpeinference";

	/**
	 * The class to use for a reasoner.
	 * Should be compatible with REASONER_KEY.
	 */
	public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
	public static final String REASONER_DEFAULT = "org.linqs.psl.reasoner.admm.ADMMReasoner";

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

	protected Model model;
	protected Database db;
	protected ConfigBundle config;
	protected Reasoner reasoner;
	protected PersistedAtomManager atomManager;

	protected GroundRuleStore groundRuleStore;
	protected TermStore termStore;

	public MPEInference(Model model, Database db, ConfigBundle config) {
		this.model = model;
		this.db = db;
		this.config = config;

		initialize();
	}

	protected void initialize() {
		TermGenerator termGenerator = null;

		try {
			reasoner = (Reasoner)config.getNewObject(REASONER_KEY, REASONER_DEFAULT);
			termStore = (TermStore)config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
			groundRuleStore = (GroundRuleStore)config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
			termGenerator = (TermGenerator)config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);
		} catch (Exception ex) {
			// The caller couldn't handle these exception anyways, convert them to runtime ones.
			throw new RuntimeException("Failed to prepare storage for inference.", ex);
		}

		log.debug("Creating persisted atom mannager.");
		atomManager = new PersistedAtomManager(db);

		log.info("Grounding out model.");
		Grounding.groundAll(model, atomManager, groundRuleStore);

		log.debug("Initializing objective terms for {} ground rules.", groundRuleStore.size());
		termGenerator.generateTerms(groundRuleStore, termStore);

		log.debug("Generated {} objective terms from {} ground rules.", termStore.size(), groundRuleStore.size());
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
		log.info("Beginning inference.");
		reasoner.optimize(termStore);
		log.info("Inference complete. Writing results to Database.");

		// Commits the RandomVariableAtoms back to the Database,
		Set<RandomVariableAtom> atoms = atomManager.getPersistedRVAtoms();
		db.commit(atoms);

		double incompatibility = GroundRules.getTotalWeightedIncompatibility(groundRuleStore.getCompatibilityRules());
		double infeasibility = GroundRules.getInfeasibilityNorm(groundRuleStore.getConstraintRules());

		return new MemoryFullInferenceResult(incompatibility, infeasibility, atoms.size(), groundRuleStore.size());
	}

	public Reasoner getReasoner() {
		return reasoner;
	}

	@Override
	public void close() {
		termStore.close();
		groundRuleStore.close();
		reasoner.close();

		termStore = null;
		groundRuleStore = null;
		reasoner = null;

		model=null;
		db = null;
		config = null;
	}

}
