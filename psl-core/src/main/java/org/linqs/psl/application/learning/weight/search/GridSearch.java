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
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.admm.ADMMReasoner;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grid search for weights.
 *
 * TODO(eriq): More comments once this is more worked out.
 */
public class GridSearch extends WeightLearningApplication {
	private static final Logger log = LoggerFactory.getLogger(GridSearch.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "gridsearch";

	// TEST
	// private static final double[] POSSIBLE_WEIGHTS = new double[]{0.001, 0.01, 0.1, 1, 10};
	private static final double[] POSSIBLE_WEIGHTS = new double[]{0.01, 1, 10};

	// TODO(eriq): config
	private static final int ADMM_ITERATIONS = 100;

	private static final char DELIM = ':';

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

	/**
	 * The losses at each location.
	 * The default implementation does not actually need this, but childen may.
	 */
	private Map<String, Double> losses;

	// TODO(eriq): Latent variables?
	public GridSearch(List<Rule> rules, Database rvDB, Database observedDB, ConfigBundle config) {
		super(rules, rvDB, observedDB, false, config);

		currentLocation = null;

		gridSize = (int)Math.pow(POSSIBLE_WEIGHTS.length, mutableRules.size());
		numLocations = gridSize;

		losses = new HashMap<String, Double>();
	}

	@Override
	protected void initGroundModel() {
		super.initGroundModel();

		// If we are dealing with an ADMMReasoner, then set its max iterations.
		if (reasoner instanceof ADMMReasoner) {
			((ADMMReasoner)reasoner).setMaxIter(ADMM_ITERATIONS);
		}
	}

	@Override
	protected void doLearn() {
		double bestLoss = -1;
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

			double loss = computeLoss();
			losses.put(currentLocation, new Double(loss));

			if (iteration == 0 || loss < bestLoss) {
				bestLoss = loss;
				for (int i = 0; i < mutableRules.size(); i++) {
					bestWeights[i] = weights[i];
				}
			}

			log.trace("Location {} -- loss: {}", currentLocation, loss);
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
		String[] parts = currentLocation.split("" + DELIM);
		assert(parts.length == mutableRules.size());

		for (int i = 0; i < mutableRules.size(); i++) {
			weights[i] = POSSIBLE_WEIGHTS[Integer.parseInt(parts[i])];
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

		String[] parts = currentLocation.split("" + DELIM);
		assert(parts.length == mutableRules.size());

		int[] indexes = new int[mutableRules.size()];
		for (int i = 0; i < mutableRules.size(); i++) {
			indexes[i] = Integer.parseInt(parts[i]);
		}

		// Start at the last weight and move it.
		// If it rolls over, move the one above it.
		for (int i = mutableRules.size() - 1; i >= 0; i--) {
			indexes[i]++;

			if (indexes[i] == POSSIBLE_WEIGHTS.length) {
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
