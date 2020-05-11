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

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;

import org.linqs.psl.util.MathUtils;

import org.linqs.psl.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.lang.Math;

/**
 * The base for grid search-like method.
 * The number of ADMM iterations can also be controlled.
 * It is recommended to set ADMM iterations through this class' configuration
 * rather than through ADMM's configuration so you don't globally change the number of iterations.
 */
public abstract class BaseGridSearch extends WeightLearningApplication {
    private static final Logger log = LoggerFactory.getLogger(BaseGridSearch.class);

    /**
     * The delimiter to separate rule weights (and location ids).
     * Note that we cannot use ',' because our configuration infrastructure will try
     * interpret it as a list of strings.
     */
    public static final String DELIM = ":";

    /**
     * The current location we are investigating.
     * The exact representation is up to the implementing child class.
     */
    protected String currentLocation;

    /**
     * The number of actual possible locations.
     * Initially set to 0 and should be set by child constructors.
     */
    protected int maxNumLocations;

    /**
     * The number of locations we will search.
     * Initially set to 0 and should be set by child constructors.
     */
    protected int numLocations;

    /**
     * The dimension of the space we are searching over.
     */
    protected int spaceDimension;

    /**
     * Whether to perform search in log scale
     * */
    protected double logBase;

    /**
     * The base of the log scale we are using
     * */
    protected boolean logScale;

    /**
     * The radius of the sphere that is being optimized over
     * */
    protected double hypersphereRadius;

    /**
     * Whether we will be performing search over hypersphere
     * */
    protected boolean searchHypersphere;

    /**
     * Whether we will be performing search over hypersphere
     * */
    protected boolean searchDirichlet;

    /**
     * Whether we will be performing search over hypersphere
     * */
    protected double dirichletAlpha;

    /**
     * Whether we will be performing search over hypersphere
     * */
    protected double[] dirichletAlphas;

    /**
     * The objectives at each location.
     * The default implementation does not actually need this, but childen may.
     */
    protected Map<String, Double> objectives;

    /**
     * The objectives at each location.
     * The default implementation does not actually need this, but children may.
     */
    protected Map<String, String> exploredConfigurations;

    public BaseGridSearch(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public BaseGridSearch(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        currentLocation = null;

        maxNumLocations = 0;
        numLocations = maxNumLocations;

        objectives = new HashMap<String, Double>();

        exploredConfigurations = new HashMap<String, String>();

        logScale = Options.WLA_SEARCH_LOG_SCALE.getBoolean();
        logBase = Options.WLA_SEARCH_LOG_BASE.getDouble();

        hypersphereRadius = Options.WLA_SEARCH_HYPERSPHERE_RADIUS.getDouble();
        searchHypersphere = Options.WLA_SEARCH_HYPERSPHERE.getBoolean();

        searchDirichlet = Options.WLA_SEARCH_DIRICHLET.getBoolean();
        dirichletAlpha = Options.WLA_SEARCH_DIRICHLET_ALPHA.getDouble();
        dirichletAlphas = new double[mutableRules.size()];

        for (int i = 0; i < mutableRules.size(); i ++) {
            dirichletAlphas[i] = dirichletAlpha;
        }

        spaceDimension = searchHypersphere ? mutableRules.size() - 1 : mutableRules.size();
    }

    @Override
    protected void doLearn() {
        double bestObjective = -1;
        double[] bestWeights = new double[mutableRules.size()];

        double[] weights = new double[mutableRules.size()];

        double[] unitWeightVector = new double[mutableRules.size()];

        String unitConfiguration;

        for (int iteration = 0; iteration < numLocations; iteration++) {
            if (!chooseNextLocation()) {
                log.debug("Stopping search.");
                break;
            }

            log.debug("Iteration {} / {} ({}) -- Inspecting location {}", iteration, numLocations, maxNumLocations, currentLocation);

            // Set the weights for the current round.
            getWeights(weights);
            if (logScale) {
                log.debug("Pre Log scaled weights: {}", weights);
                weights = MathUtils.toLogScale(weights, logBase);
                log.debug("Log scaled weights: {}", weights);
            }

            // Check if we have explored this configuration before
            unitWeightVector = MathUtils.toUnit(weights);
            // Round each weight to 5 decimal places
            for(int i = 0; i < unitWeightVector.length; i ++){
                unitWeightVector[i] = (double)Math.round(unitWeightVector[i] * 100000d) / 100000d;
            }
            unitConfiguration = StringUtils.join(DELIM, unitWeightVector);
            if (exploredConfigurations.containsKey(unitConfiguration)) {
                log.debug("Location: {} \nalready explored via: {} \nSkipping", currentLocation,
                        exploredConfigurations.get(unitConfiguration));
                continue;
            }

            for (int i = 0; i < mutableRules.size(); i++) {
                mutableRules.get(i).setWeight(weights[i]);
            }

            log.trace("Weights: {}", weights);

            // The weights have changed, so we are no longer in an MPE state.
            inMPEState = false;

            double objective = inspectLocation(weights);

            // Log this location.
            objectives.put(currentLocation, new Double(objective));
            exploredConfigurations.put(unitConfiguration, StringUtils.join(DELIM, weights));

            if (iteration == 0 || objective < bestObjective) {
                bestObjective = objective;
                for (int i = 0; i < mutableRules.size(); i++) {
                    bestWeights[i] = weights[i];
                }
            }

            log.debug("Location {} -- objective: {}", currentLocation, objective);
        }

        // Set the final weights.
        for (int i = 0; i < mutableRules.size(); i++) {
            mutableRules.get(i).setWeight(bestWeights[i]);
        }

        // The weights have changed, so we are no longer in an MPE state.
        inMPEState = false;
    }

    /**
     * Inspect the location defined by the given weights and give back its score (lower is better).
     * This method may modify weights if it wants to store a different set of weights than those initially passed in.
     * The rules have already been set with the given weights, they are only passed in so the method
     * has a chance to modify them before the result is stored.
     * This is a prime method for child classes to override.
     * Implementers should make sure to correct (negate) the value that comes back from the Evaluator
     * if lower is better for that evaluator.
     */
    protected double inspectLocation(double[] weights) {
        computeMPEState();

        evaluator.compute(trainingMap);

        return -1.0 * evaluator.getNormalizedRepMetric();
    }

    /**
     * Get the weight configuration at the current location.
     */
    protected abstract void getWeights(double[] weights);

    /**
     * Choose the next location we will search.
     * This method is responsible for setting the currentLocation variable.
     * @return false if the search is to abort.
     */
    protected abstract boolean chooseNextLocation();
}
