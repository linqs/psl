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
package org.linqs.psl.application.learning.weight.em;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.TrainingMapAtomManager;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract superclass for implementations of the expectation-maximization
 * algorithm for learning with latent variables.
 * <p>
 * This class extends {@link VotedPerceptron}, which is used during the M-step
 * to update the weights.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public abstract class ExpectationMaximization extends VotedPerceptron {

	private static final Logger log = LoggerFactory.getLogger(ExpectationMaximization.class);

	/**
	 * Prefix of property keys used by this class.
	 *
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "em";

	/**
	 * Key for positive int property for the number of iterations of expectation
	 * maximization to perform
	 */
	public static final String ITER_KEY = CONFIG_PREFIX + ".iterations";
	/** Default value for ITER_KEY property */
	public static final int ITER_DEFAULT = 10;

	/**
	 * Key for Boolean property that indicates whether to reset step-size schedule
	 * for each EM round. If TRUE, schedule will be {@link VotedPerceptron#STEP_SIZE_KEY}
	 * at start of each round. If FALSE, schedule will smoothly decrease across rounds,
	 * i.e., the schedule will be 1/ (round number * num steps + step number).
	 *
	 * This property has no effect if {@link VotedPerceptron#STEP_SCHEDULE_KEY} is false.
	 */
	public static final String RESET_SCHEDULE_KEY = CONFIG_PREFIX + ".resetschedule";
	/** Default value for STORE_WEIGHTS_KEY */
	public static final boolean RESET_SCHEDULE_DEFAULT = true;

	/**
	 * Key for Boolean property that indicates whether to store weights along entire optimization path
	 */
	public static final String STORE_WEIGHTS_KEY = CONFIG_PREFIX + ".storeweights";
	/** Default value for STORE_WEIGHTS_KEY */
	public static final boolean STORE_WEIGHTS_DEFAULT = false;

	/**
	 * Key for positive double property for the minimum absolute change in weights
	 * such that EM is considered converged
	 */
	public static final String TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
	/** Default value for TOLERANCE_KEY property */
	public static final double TOLERANCE_DEFAULT = 1e-3;

	protected final int iterations;
	protected final double tolerance;
	protected final boolean resetSchedule;

	private int round;

	protected final boolean storeWeights;
	protected ArrayList<Map<WeightedRule, Double>> storedWeights;


	/**
	 * A reasoner for inferring the latent variables conditioned on
	 * the observations and labels
	 */
	protected Reasoner latentVariableReasoner;
	protected GroundRuleStore latentGroundRuleStore;
	protected TermStore latentTermStore;
	protected TermGenerator latentTermGenerator;

	public ExpectationMaximization(Model model, Database rvDB,
			Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);

		iterations = config.getInt(ITER_KEY, ITER_DEFAULT);

		tolerance = config.getDouble(TOLERANCE_KEY, TOLERANCE_DEFAULT);

		resetSchedule = config.getBoolean(RESET_SCHEDULE_KEY, RESET_SCHEDULE_DEFAULT);

		storeWeights = config.getBoolean(STORE_WEIGHTS_KEY, STORE_WEIGHTS_DEFAULT);
		if (storeWeights)
			storedWeights = new ArrayList<Map<WeightedRule, Double>>();
	}

	@Override
	protected void doLearn() {
		double[] weights = new double[mutableRules.size()];
		for (int i = 0; i < weights.length; i++)
			weights[i] = mutableRules.get(i).getWeight();
		double [] avgWeights = new double[mutableRules.size()];

		round = 0;
		while (round++ < iterations) {
			log.debug("Beginning EM round {} of {}", round, iterations);
			/* E-step */
			minimizeKLDivergence();
			/* M-step */
			super.doLearn();

			double change = 0;
			for (int i = 0; i < mutableRules.size(); i++) {
				change += Math.pow(weights[i] - mutableRules.get(i).getWeight(), 2);
				weights[i] = mutableRules.get(i).getWeight();

				avgWeights[i] = (1 - (1.0 / (double) round)) * avgWeights[i] + (1.0 / (double) round) * weights[i];
			}

			if (storeWeights) {
				Map<WeightedRule,Double> weightMap = new HashMap<WeightedRule, Double>();
				for (int i = 0; i < mutableRules.size(); i++) {
					double weight = (averageSteps) ? avgWeights[i] : weights[i];
					if (weight > 0.0)
						weightMap.put(mutableRules.get(i), weight);
				}
				storedWeights.add(weightMap);
			}

			double loss = getLoss();
			double regularizer = computeRegularizer();
			double objective = loss + regularizer;

			change = Math.sqrt(change);
			if (change <= tolerance) {
				log.info("EM converged with m-step norm {} in {} rounds. Loss: " + loss, change, round);
				break;
			} else
				log.info("Finished EM round {} with m-step norm {}. Loss: " + loss + ", regularizer: " + regularizer + ", objective: " + objective, round, change);
		}

		if (averageSteps) {
			for (int i = 0; i < mutableRules.size(); i++) {
				mutableRules.get(i).setWeight(avgWeights[i]);
			}
		}
	}

	abstract protected void minimizeKLDivergence();

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

		trainingMap = new TrainingMapAtomManager(rvDB, observedDB);

		Grounding.groundAll(model, trainingMap, groundRuleStore);
		termGenerator.generateTerms(groundRuleStore, termStore);

		// The latentVariableReasoner should not be closed until close(),
		// so that calls to inferLatentVariables() still work.
		if (latentVariableReasoner != null) {
			latentVariableReasoner.close();
		}

		try {
			latentVariableReasoner = (Reasoner)config.getNewObject(REASONER_KEY, REASONER_DEFAULT);
			latentTermStore = (TermStore)config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
			latentGroundRuleStore = (GroundRuleStore)config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
			latentTermGenerator = (TermGenerator)config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);
		} catch (Exception ex) {
			// The caller couldn't handle these exception anyways, convert them to runtime ones.
			throw new RuntimeException("Failed to prepare storage for latent inference.", ex);
		}

		Grounding.groundAll(model, trainingMap, latentGroundRuleStore);
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			latentGroundRuleStore.addGroundRule(new GroundValueConstraint(e.getKey(), e.getValue().getValue()));
		}
		latentTermGenerator.generateTerms(latentGroundRuleStore, latentTermStore);
	}

	/**
	 * Infers the most probable assignment to the latent variables conditioned
	 * on the observations and labeled unknowns using the most recently learned
	 * model.
	 *
	 * The atoms with corresponding labels will be set to their label values.
	 *
	 * @throws IllegalStateException  if no model has been learned
	 */
	public void inferLatentVariables() {
		if (latentVariableReasoner == null) {
			throw new IllegalStateException("A model must have been learned " +
					"before latent variables can be inferred.");
		}

		/*
		 * Infers most probable assignment latent variables
		 *
		 * (Calling updateWeights() might be unnecessary, but this is
		 * the easiest way to be sure the terms are updated.)
		 */
		latentTermGenerator.updateWeights(latentGroundRuleStore, latentTermStore);
		latentVariableReasoner.optimize(latentTermStore);
	}

	@Override
	protected double getStepSize(int iter) {
		if (scheduleStepSize && !resetSchedule) {
			return stepSize / (double) ((round-1) * numSteps + iter + 1);
		} else {
			return super.getStepSize(iter);
		}
	}

	public ArrayList<Map<WeightedRule, Double>> getStoredWeights() {
		return (storeWeights)? storedWeights : null;
	}

	@Override
	public void close() {
		super.close();
		if (latentVariableReasoner != null) {
			latentTermStore.close();
			latentTermStore = null;

			latentGroundRuleStore.close();
			latentGroundRuleStore = null;

			latentVariableReasoner.close();
			latentVariableReasoner = null;
		}
	}
}
