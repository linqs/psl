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
package org.linqs.psl.application.learning.weight.search.grid;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * An exhaustive grid search for weights.
 * The weights searched over is set using configuration options.
 */
public class GridSearch extends BaseGridSearch {
	private static final Logger log = LoggerFactory.getLogger(GridSearch.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "gridsearch";

	/**
	 * A comma-separated list of possible weights.
	 * These weights should be in some sorted order.
	 */
	public static final String POSSIBLE_WEIGHTS_KEY = CONFIG_PREFIX + ".weights";
	public static final String POSSIBLE_WEIGHTS_DEFAULT = "0.001:0.01:0.1:1:10";

	/**
	 * The delimiter to separate rule weights (and lication ids).
	 * Note that we cannot use ',' because our configuration infrastructure will try
	 * interpret it as a list of strings.
	 */
	public static final String DELIM = ":";

	protected final double[] possibleWeights;

	public GridSearch(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this(model.getRules(), rvDB, observedDB, config);
	}

	public GridSearch(List<Rule> rules, Database rvDB, Database observedDB, ConfigBundle config) {
		super(rules, rvDB, observedDB, config);

		possibleWeights = StringUtils.splitDouble(config.getString(POSSIBLE_WEIGHTS_KEY, POSSIBLE_WEIGHTS_DEFAULT), DELIM);
		if (possibleWeights.length == 0) {
			throw new IllegalArgumentException("No weights provided for grid search.");
		}

		maxNumLocations = (int)Math.pow(possibleWeights.length, mutableRules.size());
		numLocations = maxNumLocations;
	}

	@Override
	protected void getWeights(double[] weights) {
		int[] indexes = StringUtils.splitInt(currentLocation, DELIM);
		assert(indexes.length == mutableRules.size());

		for (int i = 0; i < mutableRules.size(); i++) {
			weights[i] = possibleWeights[indexes[i]];
		}
	}

	@Override
	protected boolean chooseNextLocation() {
		// Start at all zeros.
		if (currentLocation == null) {
			currentLocation = StringUtils.join(new int[mutableRules.size()], DELIM);
			return true;
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
		return true;
	}
}
