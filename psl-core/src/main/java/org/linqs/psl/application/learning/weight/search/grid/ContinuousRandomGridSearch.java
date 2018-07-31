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

import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.RandUtils;

import java.util.List;

/**
 * A grid search that just randomly samples from a continuous grid [0, 1).
 */
public class ContinuousRandomGridSearch extends BaseGridSearch {
	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "continuousrandomgridsearch";

	/**
	 * The max number of locations to search.
	 */
	public static final String MAX_LOCATIONS_KEY = CONFIG_PREFIX + ".maxlocations";
	public static final int MAX_LOCATIONS_DEFAULT = 250;

	// TODO(eriq): Config
	public static final double BASE_WEIGHT = 0.20;
	public static final double VARIANCE = 0.10;

	/**
	 * Means for the Gaussian's that we will sample rule weights from.
	 */
	private double[] weightMeans;

	public ContinuousRandomGridSearch(Model model, Database rvDB, Database observedDB) {
		this(model.getRules(), rvDB, observedDB);
	}

	public ContinuousRandomGridSearch(List<Rule> rules, Database rvDB, Database observedDB) {
		super(rules, rvDB, observedDB);

		numLocations = Config.getInt(MAX_LOCATIONS_KEY, MAX_LOCATIONS_DEFAULT);
		weightMeans = null;
	}

	@Override
	protected void postInitGroundModel() {
		computeWeightMeans();
	}

	@Override
	protected void getWeights(double[] weights) {
		for (int i = 0; i < mutableRules.size(); i++) {
			// Rand give Gaussian with mean = 0.0 and variance = 1.0.
			weights[i] = RandUtils.nextDouble() * Math.sqrt(VARIANCE) + weightMeans[i];
		}
	}

	@Override
	protected boolean chooseNextLocation() {
		currentLocation = "" + objectives.size();
		return true;
	}

	/**
	 * For each rule, compute what we will use as the mean in our sampling Gaussian.
	 * Get the average compatability for each rule, set the smallest to the baseline,
	 * and scale all others by that smallest.
	 */
	private void computeWeightMeans() {
		// Set all the weights to 1.0 to get a baseline on the number of satisfied ground rules.
		for (WeightedRule rule : mutableRules) {
			rule.setWeight(1.0);
		}

		inMPEState = false;
		computeMPEState();

		weightMeans = new double[mutableRules.size()];

		double smallestCompatability = 1.0;

		// We will let the mean be the proportion of ground rules that are violated.
		for (int i = 0; i < mutableRules.size(); i++) {
			int count = 0;
			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
				if (!(groundRule instanceof WeightedGroundRule)) {
					continue;
				}

				count++;
				weightMeans[i] += (1.0 - ((WeightedGroundRule)groundRule).getIncompatibility());
			}

			if (count == 0) {
				weightMeans[i] = 0.0;
			} else {
				weightMeans[i] /= count;
			}

			if (weightMeans[i] < smallestCompatability) {
				smallestCompatability = weightMeans[i];
			}
		}

		for (int i = 0; i < mutableRules.size(); i++) {
			weightMeans[i] = BASE_WEIGHT * weightMeans[i] / smallestCompatability;
		}
	}
}
