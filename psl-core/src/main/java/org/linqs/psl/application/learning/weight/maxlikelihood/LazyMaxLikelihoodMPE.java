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
package org.linqs.psl.application.learning.weight.maxlikelihood;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Queries;
import org.linqs.psl.database.atom.LazyAtomManager;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
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
import java.util.Map;
import java.util.Set;

/**
 * Voted perceptron algorithm that does not require a ground model of pre-specified
 * dimensionality.
 *
 * For the gradient of the objective, the expected total incompatibility is
 * computed by finding the MPE state.
 *
 * Note that this class does not support latent variables but will not throw
 * an error if the labelDB does not include a corresponding label for a
 * RandomVariableAtom. All unspecified labels will set to their most probable
 * values conditioned on the observations in distributionDB and the labels in labelDB.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class LazyMaxLikelihoodMPE extends VotedPerceptron {
	private static final Logger log = LoggerFactory.getLogger(LazyMaxLikelihoodMPE.class);

	private LazyAtomManager lazyAtomManager;

	/**
	 * Constructs a new weight learner.
	 *
	 * @param model  the model for which to learn weights
	 * @param distributionDB  a Database containing all atoms for the ground distribution
	 * @param labelDB  a Database containing labels for the unknowns in the distribution
	 * @param config  configuration bundle
	 */
	public LazyMaxLikelihoodMPE(Model model, Database distributionDB, Database labelDB, ConfigBundle config) {
		super(model, distributionDB, labelDB, config);

		// VotedPerceptron will try to access the training map
		// (which doesn't exist in this application) if this is true.
		augmentLoss = false;
	}

	@Override
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

		lazyAtomManager = new LazyAtomManager(rvDB, config);

		log.debug("Initial grounding.");
		Grounding.groundAll(model, lazyAtomManager, groundRuleStore);

		log.debug("Initializing objective terms for {} ground rules.", groundRuleStore.size());
		termGenerator.generateTerms(groundRuleStore, termStore);
		log.debug("Generated {} objective terms from {} ground rules.", termStore.size(), groundRuleStore.size());
	}

	@Override
	protected double[] computeObservedIncomp() {
		Map<RandomVariableAtom, GroundValueConstraint> targetMap = activateLabeledAtoms();

		boolean continueGrowing = false;

		// Maintains a temporary set during collection to avoid concurrent modification errors.
		Set<GroundValueConstraint> toAdd = new HashSet<GroundValueConstraint>();

		// Use a loop to extend the network based on MPE inference and
		// activation events, and then check for new labeled RandomVariableAtoms
		// to constrain as necessary.
		log.debug("Beginning to grow labeled network.");
		do {
			continueGrowing = false;

			computeMPEState();

			// Collects existing RandomVariableAtoms and pairs them with label constraints.
			for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
				for (Atom atom : groundRule.getAtoms()) {
					if (atom instanceof RandomVariableAtom) {
						RandomVariableAtom rv = (RandomVariableAtom) atom;
						if (!targetMap.containsKey(rv)) {
							Atom possibleLabel = observedDB.getAtom(rv.getPredicate(), rv.getArguments());
							if (possibleLabel instanceof ObservedAtom) {
								GroundValueConstraint con = new GroundValueConstraint(rv, ((ObservedAtom) possibleLabel).getValue());
								targetMap.put(rv, con);
								toAdd.add(con);
								continueGrowing = true;
							}
						}
					}
				}
			}

			// Adds new constraints to the ground rule store.
			for (GroundValueConstraint con : toAdd) {
				groundRuleStore.addGroundRule(con);
			}
			toAdd.clear();
		} while (continueGrowing);
		log.debug("Finished growing labeled network.");

		// Computes the observed incompatibilities.
		double[] truthIncompatibility = new double[mutableRules.size()];
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				truthIncompatibility[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
			}
		}

		// Removes label value constraints.
		for (GroundValueConstraint con : targetMap.values()) {
			groundRuleStore.removeGroundRule(con);
		}

		return truthIncompatibility;
	}

	@Override
	protected double[] computeExpectedIncomp() {
		double[] expIncomp = new double[mutableRules.size()];

		computeMPEState();

		// Computes incompatibility.
		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				expIncomp[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
			}
		}

		return expIncomp;
	}

	@Override
	protected double[] computeScalingFactor() {
		double[] scalingFactor = new double[mutableRules.size()];

		for (int i = 0; i < mutableRules.size(); i++) {
			for (GroundRule rule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				scalingFactor[i]++;
			}

			if (scalingFactor[i] == 0.0) {
				scalingFactor[i]++;
			}
		}

		return scalingFactor;
	}

	// TODO(eriq): This really seems like it should have a max number of iterations like LazyMPEInference.
	private void computeMPEState() {
		int iteration = 1;

		do {
			// Activate any relevant lazy atoms.
			int numActivated = lazyAtomManager.activateAtoms(model, groundRuleStore);
			log.debug("Iteration {} -- Activated {} atoms.", iteration, numActivated);

			if (changedRuleWeights) {
				termGenerator.updateWeights(groundRuleStore, termStore);
				changedRuleWeights = false;
			}

			if (numActivated > 0) {
				// Regenerate optimization terms.
				termStore.clear();

				log.debug("Iteration {} -- Initializing objective terms for {} ground rules.",
						iteration, groundRuleStore.size());
				termGenerator.generateTerms(groundRuleStore, termStore);
				log.debug("Iteration {} -- Generated {} objective terms from {} ground rules.",
						iteration, termStore.size(), groundRuleStore.size());
			}

			log.debug("Iteration {} -- Begining inference.");
			reasoner.optimize(termStore);
			log.debug("Iteration {} -- Inference complete.");
		} while (lazyAtomManager.countActivatableAtoms() > 0);
	}

	/**
	 * In order to ground out the graphical model with the label truth values,
	 * all labeled atoms with non-zero truth values are activated and constrained.
	 */
	private Map<RandomVariableAtom, GroundValueConstraint> activateLabeledAtoms() {
		Map<RandomVariableAtom, GroundValueConstraint> targetMap = new HashMap<RandomVariableAtom, GroundValueConstraint>();

		Set<RandomVariableAtom> toActivate = new HashSet<RandomVariableAtom>();

		// Collect all non-zero labeled atoms to activate.
		for (StandardPredicate predicate : observedDB.getRegisteredPredicates()) {
			for (GroundAtom labeledAtom : Queries.getAllAtoms(observedDB, predicate)) {
				// Double check that it is observed in observedDB and unobserved in rvDB,
				// since those are the only atoms in observedDB to be considered.
				// Also check if the labeled truth value is greater than 0.0,
				// since activation would be unnecessary until the corresponding atom in rvDB had a non-zero
				// truth value. If all three conditions are met, activate and constrain the atom.
				if (labeledAtom instanceof ObservedAtom && labeledAtom.getValue() > 0.0) {
					GroundAtom correspondingAtom = lazyAtomManager.getAtom(labeledAtom.getPredicate(), labeledAtom.getArguments());
					if (correspondingAtom instanceof RandomVariableAtom) {
						RandomVariableAtom rvAtom = (RandomVariableAtom)correspondingAtom;
						toActivate.add(rvAtom);

						GroundValueConstraint con = new GroundValueConstraint(rvAtom, ((ObservedAtom)labeledAtom).getValue());
						targetMap.put(rvAtom, con);
						groundRuleStore.addGroundRule(con);
					}
				}
			}
		}

		lazyAtomManager.activateAtoms(toActivate, model, groundRuleStore);

		return targetMap;
	}
}
