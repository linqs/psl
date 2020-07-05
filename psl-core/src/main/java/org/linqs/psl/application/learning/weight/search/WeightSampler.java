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

package org.linqs.psl.application.learning.weight.search;

import org.linqs.psl.config.Options;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.RandUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeightSampler {
    private static final Logger log = LoggerFactory.getLogger(WeightSampler.class);

    /**
     * Whether we will be sampling points from a Dirichlet distribution.
     */
    private boolean searchDirichlet;

    /**
     * The alpha parameters for the dirichlet distribution.
     */
    private double[] dirichletAlphas;

    /**
     * The number of weights
     */
    private int numWeights;

    public WeightSampler(int numWeights) {
        this.numWeights = numWeights;

        searchDirichlet = Options.WLA_SEARCH_DIRICHLET.getBoolean();
        double dirichletAlpha = Options.WLA_SEARCH_DIRICHLET_ALPHA.getDouble();
        dirichletAlphas = new double[this.numWeights];

        for (int i = 0; i < this.numWeights; i ++) {
            dirichletAlphas[i] = dirichletAlpha;
        }
    }

    public void getRandomWeights(double[] weights) {
        if (searchDirichlet) {
            getDirichletRandomWeights(weights);
        } else {
            getHypercubeRandomWeights(weights);
        }
    }

    private void getDirichletRandomWeights(double[] weights) {
        double[] dirichletSample = RandUtils.sampleDirichlet(dirichletAlphas);

        MathUtils.toUnit(dirichletSample);

        for (int i = 0; i < numWeights; i++) {
            weights[i] = dirichletSample[i];
        }
    }

    private void getHypercubeRandomWeights(double[] weights) {
        for (int i = 0; i < numWeights; i++) {
            weights[i] = RandUtils.nextDouble();
        }
    }
}
