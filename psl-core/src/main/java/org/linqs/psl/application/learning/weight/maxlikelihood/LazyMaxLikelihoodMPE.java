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
package org.linqs.psl.application.learning.weight.maxlikelihood;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Queries;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.AtomEventFramework;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.ReasonerFactory;
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
 * <p>
 * For the gradient of the objective, the expected total incompatibility is
 * computed by finding the MPE state.
 * <p>
 * Note that this class does not support latent variables but will not throw
 * an error if the labelDB does not include a corresponding label for a
 * RandomVariableAtom. All unspecified labels will set to their most probable
 * values conditioned on the observations in distributionDB and the labels in labelDB.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class LazyMaxLikelihoodMPE extends VotedPerceptron {

	private static final Logger log = LoggerFactory.getLogger(AtomEventFramework.class);

	protected AtomEventFramework eventFramework;

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
	}

	@Override
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		termStore = (TermStore)config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
		groundRuleStore = (GroundRuleStore)config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
		termGenerator = (TermGenerator)config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);

		eventFramework = new AtomEventFramework(rvDB, config);

		/* Registers the Model's Rules with the AtomEventFramework */
		for (Rule rule : model.getRules()) {
			rule.registerForAtomEvents(eventFramework, groundRuleStore);
		}

		/* Grounds the model */
		Grounding.groundAll(model, eventFramework, groundRuleStore);
		while (eventFramework.checkToActivate() > 0) {
			eventFramework.workOffJobQueue();
		}
	}

	@Override
	protected double[] computeObservedIncomp() {

		/*
		 * In order to ground out the graphical model with the label truth values,
		 * all labeled atoms with non-zero truth values are activated and constrained.
		 *
		 * Then, a loop is used to extend the network based on MPE inference and
		 * activation events, and then check for new labeled RandomVariableAtoms
		 * to constrain as necessary.
		 */

		Map<RandomVariableAtom, GroundValueConstraint> targetMap = new HashMap<RandomVariableAtom, GroundValueConstraint>();

		/* Activates all non-zero labeled atoms */
		for (StandardPredicate p : observedDB.getRegisteredPredicates()) {
			Set<GroundAtom> labeledAtoms = Queries.getAllAtoms(observedDB, p);
			for (GroundAtom labeledAtom : labeledAtoms) {
				/*
				 * Double checks that it is observed in observedDB and unobserved in rvDB,
				 * since those are the only atoms in observedDB to be considered. Also,
				 * checks if the labeled truth value is greater than 0.0, since activation
				 * would be unnecessary until the corresponding atom in rvDB had a non-zero
				 * truth value. If all three conditions are met, activates and constrains the atom.
				 */
				if (labeledAtom instanceof ObservedAtom && labeledAtom.getValue() > 0.0) {
					GroundAtom correspondingAtom = eventFramework.getAtom(labeledAtom.getPredicate(), labeledAtom.getArguments());
					if (correspondingAtom instanceof RandomVariableAtom) {
						eventFramework.activateAtom((RandomVariableAtom) correspondingAtom);
						GroundValueConstraint con = new GroundValueConstraint((RandomVariableAtom) correspondingAtom, ((ObservedAtom) labeledAtom).getValue());
						targetMap.put((RandomVariableAtom) correspondingAtom, con);
						groundRuleStore.addGroundRule(con);
					}
				}
			}
		}

		boolean continueGrowing;
		/* Maintains a temporary set during collection to avoid concurrent modification errors */
		Set<GroundValueConstraint> toAdd = new HashSet<GroundValueConstraint>();

		log.debug("Beginning to grow labeled network.");
		do {
			continueGrowing = false;

			/* Computes the MPE state and grows graphical model */
			do {
				eventFramework.workOffJobQueue();

				if (changedRuleWeights) {
					termGenerator.updateWeights(groundRuleStore, termStore);
					changedRuleWeights = false;
				}

				// Computes the MPE state.
				reasoner.optimize(termStore);
			}
			while (eventFramework.checkToActivate() > 0);

			/* Collects existing RandomVariableAtoms and pairs them with label constraints */
			for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
				for (Atom a : groundRule.getAtoms()) {
					if (a instanceof RandomVariableAtom) {
						RandomVariableAtom rv = (RandomVariableAtom) a;
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

		}
		while (continueGrowing);
		log.debug("Finished growing labeled network.");

		/* Computes the observed incompatibilities */
		double[] truthIncompatibility = new double[rules.size()];
		for (int i = 0; i < rules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(rules.get(i))) {
				truthIncompatibility[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
			}
		}

		/* Removes label value constraints */
		for (GroundValueConstraint con : targetMap.values()) {
			groundRuleStore.removeGroundRule(con);
		}

		return truthIncompatibility;
	}

	@Override
	protected double[] computeExpectedIncomp() {
		double[] expIncomp = new double[rules.size()];

		/* Computes the MPE state */
		do {
			eventFramework.workOffJobQueue();

			if (changedRuleWeights) {
				termGenerator.updateWeights(groundRuleStore, termStore);
				changedRuleWeights = false;
			}

			// Computes the MPE state,
			reasoner.optimize(termStore);
		}
		while (eventFramework.checkToActivate() > 0);

		/* Computes incompatibility */
		for (int i = 0; i < rules.size(); i++) {
			for (GroundRule groundRule : groundRuleStore.getGroundRules(rules.get(i))) {
				expIncomp[i] += ((WeightedGroundRule) groundRule).getIncompatibility();
			}
		}

		return expIncomp;
	}

	@Override
	protected double[] computeScalingFactor() {
		double[] scalingFactor = new double[rules.size()];

		for (int i = 0; i < rules.size(); i++) {
			Iterator<GroundRule> itr = groundRuleStore.getGroundRules(rules.get(i)).iterator();
			while(itr.hasNext()) {
				itr.next();
				scalingFactor[i]++;
			}

			if (scalingFactor[i] == 0.0)
				scalingFactor[i]++;
		}

		return scalingFactor;
	}

	@Override
	protected void cleanUpGroundModel() {
		/* Unregisters the Model's Rules with the AtomEventFramework */
		for (Rule rule : model.getRules())
			rule.unregisterForAtomEvents(eventFramework, groundRuleStore);
		eventFramework = null;

		super.cleanUpGroundModel();
	}

}
