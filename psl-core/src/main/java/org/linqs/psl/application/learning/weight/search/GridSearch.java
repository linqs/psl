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
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An exhaustive grid search for weights.
 * The weights searched over is set using configuration options.
 * The number of ADMM iterations can also be controlled.
 * It is recommended to set ADMM iterations through this class' configuration
 * rather than through ADMM's configuration so you don't globally change the number of iterations.
 */
public class GridSearch extends WeightLearningApplication {
	private static final Logger log = LoggerFactory.getLogger(GridSearch.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "gridsearch";

	/**
	 * A comma-separated list of possible weights.
	 */
	public static final String POSSIBLE_WEIGHTS_KEY = CONFIG_PREFIX + ".weights";
	public static final String POSSIBLE_WEIGHTS_DEFAULT = "0.001:0.01:0.1:1:10";

	/**
	 * The objective function to use.
	 */
	public static final String OBJECTIVE_KEY = CONFIG_PREFIX + ".objective";
	public static final String OBJECTIVE_DEFAULT = LossObjective.class.getName();

	/**
	 * The max number of ADMM iterations to spend on each location.
	 */
	public static final String ADMM_ITERATIONS_KEY = CONFIG_PREFIX + ".admmiterations";
	public static final int ADMM_ITERATIONS_DEFAULT = 100;

	/**
	 * The delimiter to separate rule weights (and lication ids).
	 * Note that we cannot use ',' because our configuration infrastructure will try
	 * interpret it as a list of strings.
	 */
	public static final String DELIM = ":";

	protected final double[] possibleWeights;
	protected final int admmIterations;

	/**
	 * The current location we are investigating.
	 * The exact representation is up to the implementing subclass.
	 * The default implementation uses a colon-separated lists.
	 */
	protected String currentLocation;

	/**
	 * The size of the actual grid.
	 */
	protected int gridSize;

	/**
	 * The number of locations we will search.
	 */
	protected int numLocations;

	protected ObjectiveFunction objectiveFunction;

	/**
	 * The objectives at each location.
	 * The default implementation does not actually need this, but childen may.
	 */
	protected Map<String, Double> objectives;

	// TODO(eriq): Latent variables?
	public GridSearch(List<Rule> rules, Database rvDB, Database observedDB, ConfigBundle config) {
		super(rules, rvDB, observedDB, false, config);

		possibleWeights = StringUtils.splitDouble(config.getString(POSSIBLE_WEIGHTS_KEY, POSSIBLE_WEIGHTS_DEFAULT), DELIM);
		if (possibleWeights.length == 0) {
			throw new IllegalArgumentException("No weights provided for grid search.");
		}

		objectiveFunction = (ObjectiveFunction)config.getNewObject(OBJECTIVE_KEY, OBJECTIVE_DEFAULT);

		admmIterations = config.getInt(ADMM_ITERATIONS_KEY, ADMM_ITERATIONS_DEFAULT);
		if (admmIterations < 1) {
			throw new IllegalArgumentException("Need at least one iteration for grid search.");
		}

		currentLocation = null;

		gridSize = (int)Math.pow(possibleWeights.length, mutableRules.size());
		numLocations = gridSize;

		objectives = new HashMap<String, Double>();
	}

	@Override
	protected void initGroundModel() {
		super.initGroundModel();

		// If we are dealing with an ADMMReasoner, then set its max iterations.
		if (reasoner instanceof ADMMReasoner) {
			((ADMMReasoner)reasoner).setMaxIter(admmIterations);
		}
	}

	@Override
	protected void doLearn() {
		double bestObjective = -1;
		double[] bestWeights = new double[mutableRules.size()];

		// Computes the observed incompatibilities.
		computeObservedIncompatibility();

		double[] weights = new double[mutableRules.size()];

		for (int iteration = 0; iteration < numLocations; iteration++) {
			chooseNextLocation();
			log.debug("Iteration {} / {} ({}) -- Inspecting location {}", iteration, numLocations, gridSize, currentLocation);

			// Set the weights for the current round.
			getWeights(weights);
			for (int i = 0; i < mutableRules.size(); i++) {
				mutableRules.get(i).setWeight(weights[i]);
			}

			// Reset the RVAs to default values.
			setDefaultRandomVariables();

			// Computes the expected incompatibility.
			computeExpectedIncompatibility();

			double objective = objectiveFunction.compute(mutableRules, observedIncompatibility, expectedIncompatibility, trainingMap);
			objectives.put(currentLocation, new Double(objective));

			if (iteration == 0 || objective < bestObjective) {
				bestObjective = objective;
				for (int i = 0; i < mutableRules.size(); i++) {
					bestWeights[i] = weights[i];
				}
			}

			log.trace("Location {} -- objective: {}", currentLocation, objective);
		}

		// Set the final weights.
		for (int i = 0; i < mutableRules.size(); i++) {
			mutableRules.get(i).setWeight(bestWeights[i]);
		}
	}

	/**
	 * Get the weight configuration at the current location.
	 */
	protected void getWeights(double[] weights) {
		int[] indexes = StringUtils.splitInt(currentLocation, DELIM);
		assert(indexes.length == mutableRules.size());

		for (int i = 0; i < mutableRules.size(); i++) {
			weights[i] = possibleWeights[indexes[i]];
		}
	}

	/**
	 * Choose the next location we will search.
	 * This method is responsible for setting currentLocation.
	 */
	protected void chooseNextLocation() {
		// Start at all zeros.
		if (currentLocation == null) {
			currentLocation = StringUtils.join(new int[mutableRules.size()], DELIM);
			return;
		}

		int[] indexes = StringUtils.splitInt(currentLocation, DELIM);
		assert(indexes.length == mutableRules.size());

		// Start at the last weight and move it.
		// If it rolls over, move the one above it.
		for (int i = mutableRules.size() - 1; i >= 0; i--) {
			indexes[i]++;

			if (indexes[i] == possibleWeights.length) {
				// Rollover and move to the next rule.
				indexes[i] = 0;
			} else {
				// No rollover, stop changing weights.
				break;
			}
		}

		currentLocation = StringUtils.join(indexes, DELIM);
	}
}
