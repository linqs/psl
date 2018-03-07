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

import org.linqs.psl.application.learning.weight.search.objective.LossObjective;
import org.linqs.psl.application.learning.weight.search.objective.ObjectiveFunction;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
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
 */
public class Hyperband extends WeightLearningApplication {
	private static final Logger log = LoggerFactory.getLogger(Hyperband.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "hyperband";

	/**
	 * The max number of ADMM iterations.
	 */
	public static final String ADMM_ITERATIONS_KEY = CONFIG_PREFIX + ".admmiterations";
	public static final int ADMM_ITERATIONS_DEFAULT = 500;

	/**
	 * The objective function to use.
	 */
	public static final String OBJECTIVE_KEY = CONFIG_PREFIX + ".objective";
	public static final String OBJECTIVE_DEFAULT = LossObjective.class.getName();

	// TEST
	/**
	 * A comma-separated list of possible weights.
	 * These weights should be in some sorted order.
	 */
	public static final String POSSIBLE_WEIGHTS_KEY = CONFIG_PREFIX + ".weights";
	public static final String POSSIBLE_WEIGHTS_DEFAULT = "0.001:0.01:0.1:1:10";

	/**
	 * The proportion of configs that survive each round in a brancket.
	 */
	public static final String SURVIVAL_KEY = CONFIG_PREFIX + ".survival";
	public static final int SURVIVAL_DEFAULT = 3;

	/**
	 * The delimiter to separate rule weights (and lication ids).
	 * Note that we cannot use ',' because our configuration infrastructure will try
	 * interpret it as a list of strings.
	 */
	public static final String DELIM = ":";

	private final int survival;
	private final int maxADMMIterations;
	private final ObjectiveFunction objectiveFunction;

	private double bestObjective;
	private double[] bestWeights;

	private int numBrackets;
	private int maxBudget;

	// TEST
	private Random rand;

	public Hyperband(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this(model.getRules(), rvDB, observedDB, config);
	}

	public Hyperband(List<Rule> rules, Database rvDB, Database observedDB, ConfigBundle config) {
		// TODO(eriq): Latent variables?
		super(rules, rvDB, observedDB, false, config);

		maxADMMIterations = config.getInt(ADMM_ITERATIONS_KEY, ADMM_ITERATIONS_DEFAULT);
		if (maxADMMIterations < 1) {
			throw new IllegalArgumentException("Need at least one iteration for grid search.");
		}

		objectiveFunction = (ObjectiveFunction)config.getNewObject(OBJECTIVE_KEY, OBJECTIVE_DEFAULT);

		/* TEST
		possibleWeights = StringUtils.splitDouble(config.getString(POSSIBLE_WEIGHTS_KEY, POSSIBLE_WEIGHTS_DEFAULT), DELIM);
		if (possibleWeights.length == 0) {
			throw new IllegalArgumentException("No weights provided for grid search.");
		}
		*/

		survival = config.getInt(SURVIVAL_KEY, SURVIVAL_DEFAULT);
		if (survival < 1) {
			throw new IllegalArgumentException("Need at least one survival porportion.");
		}

		numBrackets = (int)(Math.floor(Math.log(maxADMMIterations) / Math.log(survival)));
		maxBudget = (numBrackets + 1) * maxADMMIterations;

		// TODO(eriq): Seed
		rand = new Random(4);
	}

	@Override
	protected void postInitGroundModel() {
		// If we are dealing with an ADMMReasoner, then set its max iterations.
		if (!(reasoner instanceof ADMMReasoner)) {
			throw new IllegalArgumentException("Hyperband requires an ADMMReasoner.");
		}
	}

	@Override
	protected void doLearn() {
		double bestObjective = -1;
		double[] bestWeights = null;

		// Computes the observed incompatibilities.
		computeObservedIncompatibility();

		for (int bracket = numBrackets; bracket >= 0; bracket--) {
			int bracketSize = (int)(Math.ceil((double)maxBudget * Math.pow(survival, bracket) / maxADMMIterations / (bracket + 1)));
			double bracketBudget = maxADMMIterations * Math.pow(survival, -1.0 * bracket);

			log.debug("Bracket {} / {}", numBrackets - bracket, numBrackets);
			log.trace("  Size: {}, Budget: {}", bracketSize, bracketBudget);

			List<double[]> configs = chooseConfigs(bracketSize);

			for (int round = 0; round <= bracket; round++) {
				int roundSize = (int)(Math.floor(bracketSize * Math.pow(survival, -1.0 * round)));
				double roundBudget = bracketBudget * Math.pow(survival, round);

				log.debug("Round {} / {}", round, bracket);
				log.trace("  Size: {}, Budget: {}", roundSize, roundBudget);

				// TODO(eriq): Allocation
				PriorityQueue<RunResult> results = new PriorityQueue<RunResult>();
				for (double[] config : configs) {
					double objective = run(config, Math.min(maxADMMIterations, (int)Math.ceil(roundBudget)));
					RunResult result = new RunResult(config, objective);

					results.add(result);

					if (bestWeights == null || objective < bestObjective) {
						bestObjective = objective;
						bestWeights = config;
					}

					log.trace("	Objective: {}, Weights: {}", objective, config);
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
		// TEST(eriq): Allocations
		List<double[]> configs = new ArrayList<double[]>(bracketSize);

		for (int i = 0; i < bracketSize; i++) {
			double[] config = new double[mutableRules.size()];

			for (int weightIndex = 0; weightIndex < mutableRules.size(); weightIndex++) {
				// TEST(eriq): Mean, stats
				config[weightIndex] = rand.nextGaussian() * 10.0 + 5.0;
			}

			configs.add(config);
		}

		return configs;
	}

	private double run(double[] weights, int admmIterations) {
		// Reset the RVAs to default values.
		setDefaultRandomVariables();

		((ADMMReasoner)reasoner).setMaxIter(admmIterations);

		// Computes the expected incompatibility.
		computeExpectedIncompatibility();

		return objectiveFunction.compute(mutableRules, observedIncompatibility, expectedIncompatibility, trainingMap);
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
