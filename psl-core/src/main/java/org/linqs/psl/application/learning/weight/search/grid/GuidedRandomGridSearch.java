/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Randomly search a some locations and then look around those locations.
 */
public class GuidedRandomGridSearch extends RandomGridSearch {
    private static final Logger log = LoggerFactory.getLogger(GuidedRandomGridSearch.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "guidedrandomgridsearch";

    /**
     * The number of locations to initially search.
     */
    public static final String SEED_LOCATIONS_KEY = CONFIG_PREFIX + ".seedlocations";
    public static final int SEED_LOCATIONS_DEFAULT = 25;

    /**
     * The number of initial seed locations to explore based off of whichever ones score the best.
     */
    public static final String EXPLORE_LOCATIONS_KEY = CONFIG_PREFIX + ".explorelocations";
    public static final int EXPLORE_LOCATIONS_DEFAULT = 10;

    private final int maxNumSeedLocations;
    private int numSeedLocations;
    private final int maxNumExploreLocations;
    private int numExploreLocations;
    private Set<String> toExplore;

    public GuidedRandomGridSearch(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public GuidedRandomGridSearch(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        maxNumSeedLocations = Config.getInt(SEED_LOCATIONS_KEY, SEED_LOCATIONS_DEFAULT);
        numSeedLocations = maxNumSeedLocations;
        if (numSeedLocations < 1) {
            throw new IllegalArgumentException("Need at least one location to start the search.");
        }

        maxNumExploreLocations = Config.getInt(EXPLORE_LOCATIONS_KEY, EXPLORE_LOCATIONS_DEFAULT);
        numExploreLocations = maxNumExploreLocations;
        if (numExploreLocations < 1) {
            throw new IllegalArgumentException("Need at least one explore location.");
        }

        // Adjust the number of locations.
        numLocations = Math.min(
                numLocations,
                numSeedLocations + numExploreLocations * (int)(Math.pow(2, mutableRules.size())));

        toExplore = new HashSet<String>(numLocations - numSeedLocations);
    }

    @Override
    protected boolean chooseNextLocation() {
        if (objectives.size() < numSeedLocations) {
            // Seed phase
            do {
                currentLocation = randomConfiguration();
            } while (objectives.containsKey(currentLocation));
        } else {
            // Explore phase

            // Initialize the locations to explore.
            if (objectives.size() == numSeedLocations) {
                // We want only the top locations, so first sort by score.
                List<Map.Entry<String, Double>> locations =
                        new ArrayList<Map.Entry<String, Double>>(objectives.entrySet());

                Collections.sort(locations, new Comparator<Map.Entry<String, Double>>(){
                    @Override
                    public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                        return MathUtils.compare(a.getValue().doubleValue(), b.getValue().doubleValue());
                    }
                });

                for (int i = 0; i < Math.min(numExploreLocations, objectives.size()); i++) {
                    log.trace("Adding neighbors for {}.", locations.get(i));

                    addNeighbors(locations.get(i).getKey());
                }

                // Remove the locations we have already explored.
                toExplore.removeAll(objectives.keySet());

                log.debug("Seed phase complete, starting explore phase with {} locations.", toExplore.size());
            }

            // It is possible to run out of locations early because
            // the seed locations are all close together.
            if (toExplore.size() == 0) {
                return false;
            }

            currentLocation = toExplore.iterator().next();
            toExplore.remove(currentLocation);
        }

        return true;
    }

    /**
     * Add neightbors to toExplore.
     */
    private void addNeighbors(String location) {
        int[] indexes = StringUtils.splitInt(location, DELIM);
        assert(indexes.length == mutableRules.size());

        // Go through each weight and move it up and down by one.
        for (int i = 0; i < mutableRules.size(); i++) {
            if (indexes[i] != possibleWeights.length - 1) {
                indexes[i]++;
                toExplore.add(StringUtils.join(DELIM, indexes));
                indexes[i]--;
            }

            if (indexes[i] != 0) {
                indexes[i]--;
                toExplore.add(StringUtils.join(DELIM, indexes));
                indexes[i]++;
            }
        }
    }

    @Override
    public void setBudget(double budget) {
        super.setBudget(budget);

        numSeedLocations = (int)Math.ceil(budget * maxNumSeedLocations);
        numExploreLocations = (int)Math.ceil(budget * maxNumExploreLocations);

        numLocations = Math.min(
                numLocations,
                numSeedLocations + numExploreLocations * (int)(Math.pow(2, mutableRules.size())));
    }
}
