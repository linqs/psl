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
package org.linqs.psl.application.learning.weight.search;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Hyperband.
 * https://arxiv.org/pdf/1603.06560.pdf
 *
 * TODO(eriq): Think about inital weights.
 *
 * All extending classes should ensure that values for RVAs are set before evaluators are computed.
 */
public class Hyperband extends WeightLearningApplication {
	private static final Logger log = LoggerFactory.getLogger(Hyperband.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "hyperband";

	/**
	 * The evaluation method to use as an objective.
	 */
	public static final String OBJECTIVE_KEY = CONFIG_PREFIX + ".objective";
	public static final String OBJECTIVE_DEFAULT = ContinuousEvaluator.class.getName();

	/**
	 * The proportion of configs that survive each round in a brancket.
	 */
	public static final String SURVIVAL_KEY = CONFIG_PREFIX + ".survival";
	public static final int SURVIVAL_DEFAULT = 3;

	public static final double MAX_BUDGET = 200.0;
	public static final double MIN_BUDGET_PROPORTION = 0.001;

	private final int survival;
	private final Evaluator objectiveFunction;

	private double bestObjective;
	private double[] bestWeights;

	private int highestBracket;
	private double bracketMaxBudget;

	public Hyperband(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this(model.getRules(), rvDB, observedDB, config);
	}

	public Hyperband(List<Rule> rules, Database rvDB, Database observedDB, ConfigBundle config) {
		// TODO(eriq): Latent variables?
		super(rules, rvDB, observedDB, false, config);

		objectiveFunction = (Evaluator)config.getNewObject(OBJECTIVE_KEY, OBJECTIVE_DEFAULT);

		survival = config.getInt(SURVIVAL_KEY, SURVIVAL_DEFAULT);
		if (survival < 1) {
			throw new IllegalArgumentException("Need at least one survival porportion.");
		}

		highestBracket = (int)(Math.floor(Math.log(MAX_BUDGET) / Math.log(survival)));
		bracketMaxBudget = (highestBracket + 1) * MAX_BUDGET;
	}

	@Override
	protected void doLearn() {
		double bestObjective = -1;
		double[] bestWeights = null;

		// Computes the observed incompatibilities.
		computeObservedIncompatibility();

		for (int bracket = highestBracket; bracket >= 0; bracket--) {
			int bracketSize = (int)(Math.ceil((double)bracketMaxBudget * Math.pow(survival, bracket) / MAX_BUDGET / (bracket + 1)));
			double bracketBudget = MAX_BUDGET * Math.pow(survival, -1.0 * bracket);

			log.debug("Bracket {} / {} -- Size: {}, Budget: {}%",
					highestBracket - bracket + 1, highestBracket + 1, bracketSize, String.format("%6.3f", bracketBudget / MAX_BUDGET));

			// Note that each config may get adjusted by internal weight learning methods.
			// (Not in the default behavior, but in child class behavior).
			List<double[]> configs = chooseConfigs(bracketSize);

			for (int round = 0; round <= bracket; round++) {
				int roundSize = (int)(Math.floor(bracketSize * Math.pow(survival, -1.0 * round)));
				double roundBudget = bracketBudget * Math.pow(survival, round) / MAX_BUDGET;
				setBudget(Math.max(MIN_BUDGET_PROPORTION, Math.min(1.0, roundBudget)));

				log.debug("Round {} / {} -- Size: {}, Budget: {}%",
						round + 1, bracket + 1, roundSize, String.format("%6.3f", roundBudget));

				PriorityQueue<RunResult> results = new PriorityQueue<RunResult>();
				for (double[] config : configs) {

					// Set the weights for the current round.
					for (int i = 0; i < mutableRules.size(); i++) {
						mutableRules.get(i).setWeight(config[i]);
					}

					double objective = run(config);
					RunResult result = new RunResult(config, objective);

					results.add(result);

					if (bestWeights == null || objective < bestObjective) {
						bestObjective = objective;
						bestWeights = config;
					}

					log.trace("Objective: {}, Weights: {}", objective, config);
				}

				configs.clear();
				for (int i = 0; i < (int)(Math.floor((double)roundSize / survival)); i++) {
					configs.add(results.poll().weights);
				}
			}
		}

		// Set the final weights.
		for (int i = 0; i < mutableRules.size(); i++) {
			mutableRules.get(i).setWeight(bestWeights[i]);
		}
	}

	private List<double[]> chooseConfigs(int bracketSize) {
		List<double[]> configs = new ArrayList<double[]>(bracketSize);

		for (int i = 0; i < bracketSize; i++) {
			double[] config = new double[mutableRules.size()];

			for (int weightIndex = 0; weightIndex < mutableRules.size(); weightIndex++) {
				// TODO(eriq): Mean, stats
				config[weightIndex] = Math.max(0.0, rand.nextGaussian() + 5.0);
			}

			configs.add(config);
		}

		return configs;
	}

	/**
	 * Run and eval on the given weights using the given budget (ratio of max resources) and give back its score (lower is better).
	 * This method may modify weights if it wants to store a different set of weights than those initially passed in.
	 * The rules have already been set with the given weights, they are only passed in so the method
	 * has a chance to modify them before the result is stored.
	 * This is a prime method for child classes to override.
	 * Implementers should make sure to correct (negate) the value that comes back from the Evaluator
	 * if lower is better for that evaluator.
	 */
	protected double run(double[] weights) {
		// Reset the RVAs to default values.
		setDefaultRandomVariables();

		// Computes the expected incompatibility.
		computeExpectedIncompatibility();

		objectiveFunction.compute(trainingMap);

		double score = objectiveFunction.getRepresentativeMetric();
		score = objectiveFunction.isHigherRepresentativeBetter() ? -1.0 * score : score;

		return score;
	}

	private static class RunResult implements Comparable<RunResult> {
		public double[] weights;
		public double objective;

		public RunResult(double[] weights, double objective) {
			this.weights = weights;
			this.objective = objective;
		}

		@Override
		public int compareTo(RunResult other) {
			return Double.compare(objective, other.objective);
		}
	}
}
