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

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.List;

/**
 * A random grid search that searches a finite number of locations.
 */
public class RandomGridSearch extends GridSearch {
	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "randomgridsearch";

	/**
	 * The max number of locations to search.
	 */
	public static final String MAX_LOCATIONS_KEY = CONFIG_PREFIX + ".maxlocations";
	public static final int MAX_LOCATIONS_DEFAULT = 150;

	/**
	 * The seed to use for the location choosing RNG.
	 */
	public static final String SEED_KEY = CONFIG_PREFIX + ".seed";
	public static final long SEED_DEFAULT = 4;

   private Random rand;

	public RandomGridSearch(List<Rule> rules, Database rvDB, Database observedDB, ConfigBundle config) {
		super(rules, rvDB, observedDB, config);

		int maxLocations = config.getInt(MAX_LOCATIONS_KEY, MAX_LOCATIONS_DEFAULT);
		if (maxLocations < 1) {
			throw new IllegalArgumentException("Need at least one location for grid search.");
		}
      numLocations = Math.min(numLocations, maxLocations);

		long seed = config.getLong(SEED_KEY, SEED_DEFAULT);
      rand = new Random(seed);
	}

   @Override
	protected void chooseNextLocation() {
      do {
         currentLocation = randomConfiguration();
      } while (objectives.containsKey(currentLocation));
	}

   private String randomConfiguration() {
      int[] indexes = new int[mutableRules.size()];
      for (int i = 0; i < indexes.length; i++) {
         indexes[i] = rand.nextInt(possibleWeights.length);
      }
      return StringUtils.join(indexes, DELIM);
   }
}
