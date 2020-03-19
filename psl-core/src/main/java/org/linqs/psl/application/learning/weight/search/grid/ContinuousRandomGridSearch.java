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

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.RandUtils;

import java.util.List;

/**
 * A grid search that just randomly samples from a continuous grid [0, 1).
 */
public class ContinuousRandomGridSearch extends BaseGridSearch {
    public static final int SCALE_FACTOR = 10;

    private double baseWeight;
    private double variance;

    private int scaleOrder;
    private int currentScale;

    public ContinuousRandomGridSearch(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public ContinuousRandomGridSearch(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        scaleOrder = Options.WLA_CRGS_SCALE_ORDERS.getInt();
        currentScale = 0;

        numLocations = Options.WLA_CRGS_MAX_LOCATIONS.getInt();
        baseWeight = Options.WLA_CRGS_BASE_WEIGHT.getDouble();
        variance = Options.WLA_CRGS_VARIANCE.getDouble();
    }

    @Override
    protected void getWeights(double[] weights) {
        if (currentScale == 0) {
            // Random choice.
            for (int i = 0; i < mutableRules.size(); i++) {
                // Rand give Gaussian with mean = 0.0 and variance = 1.0.
                weights[i] = RandUtils.nextDouble() * Math.sqrt(variance) + baseWeight;
            }
        } else {
            // Scale current by SCALE_FACTOR.
            for (int i = 0; i < mutableRules.size(); i++) {
                // Rand give Gaussian with mean = 0.0 and variance = 1.0.
                weights[i] *= 10;
            }
        }

        currentScale++;
        if (currentScale > scaleOrder) {
            currentScale = 0;
        }
    }

    @Override
    protected boolean chooseNextLocation() {
        currentLocation = "" + objectives.size();
        return true;
    }
}
