/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract class for learning the weights of weighted mutableRules from data for a model.
 */
public abstract class WeightLearningApplication implements ModelApplication {
	private static final Logger log = LoggerFactory.getLogger(WeightLearningApplication.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "weightlearning";

	/**
	 * The class to use for inference.
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

	protected ConfigBundle config;
	protected boolean supportsLatentVariables;

	protected Database rvDB;
	protected Database observedDB;

	/**
	 * An atom manager on top of the rvDB.
	 */
	protected PersistedAtomManager atomManager;

	protected List<Rule> allRules;
	protected List<WeightedRule> mutableRules;
	protected TrainingMap trainingMap;

	protected Reasoner reasoner;
	protected GroundRuleStore groundRuleStore;
	protected TermStore termStore;
	protected TermGenerator termGenerator;

	public WeightLearningApplication(List<Rule> rules, Database rvDB, Database observedDB,
			boolean supportsLatentVariables, ConfigBundle config) {
		this.rvDB = rvDB;
		this.observedDB = observedDB;
		this.supportsLatentVariables = supportsLatentVariables;
		this.config = config;

		allRules = new ArrayList<Rule>();
		mutableRules = new ArrayList<WeightedRule>();

		for (Rule rule : rules) {
			allRules.add(rule);

			if (rule instanceof WeightedRule) {
				mutableRules.add((WeightedRule)rule);
			}
		}
	}

	/**
	 * Learns new weights.
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} in the distribution are those
	 * persisted in the random variable Database when this method is called. All
	 * RandomVariableAtoms which the Model might access must be persisted in the Database.
	 * <p>
	 * Each such RandomVariableAtom should have a corresponding {@link ObservedAtom}
	 * in the observed Database, unless the subclass implementation supports latent
	 * variables.
	 */
	public void learn() {
		// Sets up the ground model.
		initGroundModel();

		// Learns new weights.
		doLearn();
	}

	protected abstract void doLearn();

	protected void initGroundModel() {
		try {
			reasoner = (Reasoner)config.getNewObject(REASONER_KEY, REASONER_DEFAULT);
			termStore = (TermStore)config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
			groundRuleStore = (GroundRuleStore)config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
			termGenerator = (TermGenerator)config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);
		} catch (Exception ex) {
			// The caller couldn't handle these exception anyways, convert them to runtime ones.
			throw new RuntimeException("Failed to prepare storage for inference.", ex);
		}

		atomManager = createAtomManager();

		// Ensure all targets from the observed (truth) database
		// exist in the RV database.
		ensureTargets();

		trainingMap = new TrainingMap(atomManager, observedDB);
		if (!supportsLatentVariables && trainingMap.getLatentVariables().size() > 0) {
			Set<RandomVariableAtom> latentVariables = trainingMap.getLatentVariables();
			throw new IllegalArgumentException(String.format(
					"All RandomVariableAtoms must have corresponding ObservedAtoms, found %d latent variables." +
					" Latent variables are not supported by this WeightLearningApplication (%s)." +
					" Example latent variable: [%s].",
					latentVariables.size(),
					this.getClass().getName(),
					latentVariables.iterator().next()));
		}

		log.info("Grounding out model.");
		int groundCount = Grounding.groundAll(allRules, atomManager, groundRuleStore);

		log.debug("Initializing objective terms for {} ground rules.", groundCount);
		@SuppressWarnings("unchecked")
		int termCount = termGenerator.generateTerms(groundRuleStore, termStore);
		log.debug("Generated {} objective terms from {} ground rules.", termCount, groundCount);
	}

	@Override
	public void close() {
		trainingMap = null;
		atomManager = null;

		termStore.close();
		termStore = null;

		groundRuleStore.close();
		groundRuleStore = null;

		reasoner.close();
		reasoner = null;

		rvDB = null;
		observedDB = null;
		config = null;
	}

	/**
	 * Set RandomVariableAtoms with training labels to their observed values.
	 */
	protected void setLabeledRandomVariables() {
		for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getTrainingMap().entrySet()) {
			entry.getKey().setValue(entry.getValue().getValue());
		}
	}

	/**
	 * Set RandomVariableAtoms with training labels to their default values.
	 */
	protected void setDefaultRandomVariables() {
		for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getTrainingMap().entrySet()) {
			for (RandomVariableAtom atom : trainingMap.getTrainingMap().keySet()) {
				atom.setValue(0.0);
			}

			for (RandomVariableAtom atom : trainingMap.getLatentVariables()) {
				atom.setValue(0.0);
			}
		}
	}

	/**
	 * Create an atom manager on top of the RV database.
	 * This allows an opportunity for subclasses to create a special manager.
	 */
	protected PersistedAtomManager createAtomManager() {
		return new PersistedAtomManager(rvDB);
	}

	/**
	 * Make sure that all targets from the observed database exist in the RV database.
	 */
	private void ensureTargets() {
		// Iterate through all of the registered predicates in the observed.
		for (StandardPredicate predicate : observedDB.getRegisteredPredicates()) {
			// Ignore any closed predicates.
			if (observedDB.isClosed(predicate)) {
				continue;
			}

			// Commit the atoms into the RV databse with the default value.
			for (ObservedAtom observedAtom : observedDB.getAllGroundObservedAtoms(predicate)) {
				GroundAtom otherAtom = atomManager.getAtom(observedAtom.getPredicate(), observedAtom.getArguments());

				if (otherAtom instanceof ObservedAtom) {
					continue;
				}

				RandomVariableAtom rvAtom = (RandomVariableAtom)otherAtom;
				rvAtom.setValue(0.0);
			}
		}

		atomManager.commitPersistedAtoms();
	}
}
