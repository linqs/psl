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

    /**
     * The base weight of a rule.
     * The exact use of this value depends on UNIFORM_BASE.
     * This will either be used as the mean of the Gaussian from which a weight will be sampled,
     * or as the smallest weight that all rules will be started from.
     */
    public static final String BASE_WEIGHT_KEY = CONFIG_PREFIX + ".baseweight";
    public static final double BASE_WEIGHT_DEFAULT = 0.40;

    /**
     * The variance used when sampling the weights from a Gaussian.
     */
    public static final String VARIANCE_KEY = CONFIG_PREFIX + ".variance";
    public static final double VARIANCE_DEFAULT = 0.20;

    /**
     * If true, then use the same base weight as the Gaussian's mean when sampling the weight.
     * Otherwise, use different base weights depending on the inital satisfaction of each rule.
     */
    public static final String UNIFORM_BASE_KEY = CONFIG_PREFIX + ".uniformbase";
    public static final boolean UNIFORM_BASE_DEFAULT = true;

    /**
     * Means for the Gaussian's that we will sample rule weights from.
     */
    private double[] weightMeans;

    private double baseWeight;
    private double variance;
    private boolean uniformBase;

    public ContinuousRandomGridSearch(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public ContinuousRandomGridSearch(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        numLocations = Config.getInt(MAX_LOCATIONS_KEY, MAX_LOCATIONS_DEFAULT);
        baseWeight = Config.getDouble(BASE_WEIGHT_KEY, BASE_WEIGHT_DEFAULT);
        variance = Config.getDouble(VARIANCE_KEY, VARIANCE_DEFAULT);
        uniformBase = Config.getBoolean(UNIFORM_BASE_KEY, UNIFORM_BASE_DEFAULT);

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
            weights[i] = RandUtils.nextDouble() * Math.sqrt(variance) + weightMeans[i];
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
        weightMeans = new double[mutableRules.size()];

        if (uniformBase) {
            for (int i = 0; i < mutableRules.size(); i++) {
                weightMeans[i] = baseWeight;
            }

            return;
        }

        // Set all the weights to 1.0 to get a baseline on the number of satisfied ground rules.
        for (WeightedRule rule : mutableRules) {
            rule.setWeight(1.0);
        }

        inMPEState = false;
        computeMPEState();

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
            weightMeans[i] = baseWeight * weightMeans[i] / smallestCompatability;
        }
    }
}
