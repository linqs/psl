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
package org.linqs.psl.application.learning.weight.em;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;

import java.util.List;

/**
 * EM algorithm which fits a point distribution to the single most probable
 * assignment of truth values to the latent variables during the E-step.
 */
public class HardEM extends ExpectationMaximization  {
    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "hardem";

    /**
     * Key for Boolean property that indicates whether to use AdaGrad subgradient
     * scaling, the adaptive subgradient algorithm of
     * John Duchi, Elad Hazan, Yoram Singer (JMLR 2010).
     *
     * If TRUE, will override other step scheduling options (but not scaling).
     */
    public static final String ADAGRAD_KEY = CONFIG_PREFIX + ".adagrad";
    public static final boolean ADAGRAD_DEFAULT = false;

    public static final double MIN_SCALING_FACTOR = 1e-8;

    private final boolean useAdaGrad;

    public HardEM(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public HardEM(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);
        useAdaGrad = Config.getBoolean(ADAGRAD_KEY, ADAGRAD_DEFAULT);
    }

    @Override
    protected double[] computeScalingFactor() {
        if (!useAdaGrad) {
            return super.computeScalingFactor();
        }

        double [] scalingFactor = new double[mutableRules.size()];

        // Accumulate gradient
        // TODO(eriq): The old math here was pretty suspect.
        //  I cleaned what the code actually did, but I think that could have been bugged.
        //  (Resulting in a a bugged cleaned version.)
        for (int i = 0; i < mutableRules.size(); i++) {
            double weight = mutableRules.get(i).getWeight();
            double gradient = (
                    expectedIncompatibility[i] - observedIncompatibility[i]
                    - l2Regularization * weight
                    - l1Regularization);

            scalingFactor[i] = Math.max(MIN_SCALING_FACTOR, Math.abs(gradient));
        }

        return scalingFactor;
    }
}
