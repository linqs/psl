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
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract class for learning the weights of weighted mutableRules from data for a model.
 * All non-abstract children should have a constructor that takes:
 * (List<Rule>, Database (rv), Database (observed), ConfigBundle).
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

	/**
	 * Corresponds 1-1 with mutableRules.
	 */
	protected double[] observedIncompatibility;
	protected double[] expectedIncompatibility;

	protected TrainingMap trainingMap;

	protected Reasoner reasoner;
	protected GroundRuleStore groundRuleStore;
	protected GroundRuleStore latentGroundRuleStore;
	protected TermGenerator termGenerator;
	protected TermStore termStore;
	protected TermStore latentTermStore;

	private boolean groundModelInit;

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

		observedIncompatibility = new double[mutableRules.size()];
		expectedIncompatibility = new double[mutableRules.size()];

		groundModelInit = false;
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

		if (supportsLatentVariables) {
			initLatentGroundModel();
		}

		// Learns new weights.
		doLearn();
	}

	protected abstract void doLearn();

	/**
	 * Pass in all the ground model infrastructure.
	 * The caller should be careful calling this method instead of the other variant.
	 */
	public void initGroundModel(Reasoner reasoner, GroundRuleStore groundRuleStore,
			TermStore termStore, TermGenerator termGenerator,
			PersistedAtomManager atomManager, TrainingMap trainingMap) {
		if (groundModelInit) {
			return;
		}

		this.reasoner = reasoner;
		this.groundRuleStore = groundRuleStore;
		this.termStore = termStore;
		this.termGenerator = termGenerator;
		this.atomManager = atomManager;
		this.trainingMap = trainingMap;

		groundModelInit = true;
	}

	/**
	 * Initialize all the infrastructure dealing with the ground model.
	 */
	protected void initGroundModel() {
		if (groundModelInit) {
			return;
		}

		reasoner = (Reasoner)config.getNewObject(REASONER_KEY, REASONER_DEFAULT);
		groundRuleStore = (GroundRuleStore)config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
		termStore = (TermStore)config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
		termGenerator = (TermGenerator)config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);

		atomManager = createAtomManager();

		// Ensure all targets from the observed (truth) database
		// exist in the RV database.
		ensureTargets();

		trainingMap = new TrainingMap(atomManager, observedDB, false);
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

		groundModelInit = true;
	}

	/**
	 * The same as initGroundModel, but for latent variables.
	 * Must be called after initGroundModel().
	 * Sets up a rule/term store stack meant for latent variables.
	 * The reasoner and TermGenerator can be reused (as they don't hold state).
	 * All non-latent variables (from the training map) will be pegged to their truth values.
	 */
	protected void initLatentGroundModel() {
		latentGroundRuleStore = (GroundRuleStore)config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
		latentTermStore = (TermStore)config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);

		log.info("Grounding out latent model.");
		int groundCount = Grounding.groundAll(allRules, atomManager, latentGroundRuleStore);

		// Add in some constraints to peg the values of the non-latent variables.
		for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getTrainingMap().entrySet()) {
			latentGroundRuleStore.addGroundRule(new GroundValueConstraint(entry.getKey(), entry.getValue().getValue()));
		}
		groundCount += trainingMap.getTrainingMap().size();

		log.debug("Initializing latent objective terms for {} ground rules.", groundCount);
		@SuppressWarnings("unchecked")
		int termCount = termGenerator.generateTerms(latentGroundRuleStore, latentTermStore);
		log.debug("Generated {} latent objective terms from {} ground rules.", termCount, groundCount);
	}

	@SuppressWarnings("unchecked")
	protected void computeMPEState() {
		termGenerator.updateWeights(groundRuleStore, termStore);
		reasoner.optimize(termStore);
	}

	@SuppressWarnings("unchecked")
	protected void computeLatentMPEState() {
		termGenerator.updateWeights(latentGroundRuleStore, latentTermStore);
		reasoner.optimize(latentTermStore);
	}

	/**
	 * Compute the incompatibility in the model using the labels (truth values) from the observed (truth) database.
	 * This method is responsible for filling the observedIncompatibility member variable.
	 * This may call setLabeledRandomVariables() and not reset any ground atoms to their original value.
	 *
	 * The default implementation just calls setLabeledRandomVariables() and sums the incompatibility for each rule.
	 */
	protected void computeObservedIncompatibility() {
		setLabeledRandomVariables();

		// Zero out the observed incompatibility first.
		for (int i = 0; i < observedIncompatibility.length; i++) {
			observedIncompatibility[i] = 0.0;
		}

		// Sums up the incompatibilities.
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				observedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
			}
		}
	}

	/**
	 * Compute the incompatibility in the model.
	 * This method is responsible for filling the expectedIncompatibility member variable.
	 *
	 * The default implementation is the total incompatibility in the MPE state.
	 * IE, just calls computeMPEState() and then sums the incompatibility for each rule.
	 */
	protected void computeExpectedIncompatibility() {
		computeMPEState();

		// Zero out the expected incompatibility first.
		for (int i = 0; i < expectedIncompatibility.length; i++) {
			expectedIncompatibility[i] = 0.0;
		}

		// Sums up the incompatibilities.
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				expectedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
			}
		}
	}

	/**
	 * Internal method for computing the loss at the current point before taking a step.
	 * Child methods may override.
	 *
	 * The default implementation just sums the product of the difference between the expected and observed incompatibility.
	 *
	 * @return current learning loss
	 */
	public double computeLoss() {
		double loss = 0.0;
		for (int i = 0; i < mutableRules.size(); i++) {
			loss += mutableRules.get(i).getWeight() * (observedIncompatibility[i] - expectedIncompatibility[i]);
		}

		return loss;
	}

	@Override
	public void close() {
		if (groundRuleStore != null) {
			groundRuleStore.close();
			groundRuleStore = null;
		}

		if (latentGroundRuleStore != null) {
			latentGroundRuleStore.close();
			latentGroundRuleStore = null;
		}

		if (termStore != null) {
			termStore.close();
			termStore = null;
		}

		if (latentTermStore != null) {
			latentTermStore.close();
			latentTermStore = null;
		}

		if (reasoner != null) {
			reasoner.close();
			reasoner = null;
		}

		termGenerator = null;
		trainingMap = null;
		atomManager = null;
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
		for (RandomVariableAtom atom : trainingMap.getTrainingMap().keySet()) {
			atom.setValue(0.0);
		}

		for (RandomVariableAtom atom : trainingMap.getLatentVariables()) {
			atom.setValue(0.0);
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

	/**
	 * Construct a weight learning application given the data.
	 * Look for a constructor like: (List<Rule>, Database (rv), Database (observed), ConfigBundle).
	 */
	public static WeightLearningApplication getWLA(String className, List<Rule> rules,
			Database randomVariableDatabase, Database observedTruthDatabase, ConfigBundle config) {
		Class<? extends WeightLearningApplication> classObject = null;
		try {
			@SuppressWarnings("unchecked")
			Class<? extends WeightLearningApplication> uncheckedClassObject = (Class<? extends WeightLearningApplication>)Class.forName(className);
			classObject = uncheckedClassObject;
		} catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException("Could not find class: " + className, ex);
		}

		Constructor<? extends WeightLearningApplication> constructor = null;
		try {
			constructor = classObject.getConstructor(List.class, Database.class, Database.class, ConfigBundle.class);
		} catch (NoSuchMethodException ex) {
			throw new IllegalArgumentException("No sutible constructor found for weight learner: " + className + ".", ex);
		}

		WeightLearningApplication wla = null;
		try {
			wla = constructor.newInstance(rules, randomVariableDatabase, observedTruthDatabase, config);
		} catch (InstantiationException ex) {
			throw new RuntimeException("Unable to instantiate weight learner (" + className + ")", ex);
		} catch (IllegalAccessException ex) {
			throw new RuntimeException("Insufficient access to constructor for " + className, ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Error thrown while constructing " + className, ex);
		}

		return wla;
	}
}
