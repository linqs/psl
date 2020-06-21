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
    private static final Logger log  = LoggerFactory.getLogger(WeightSampler.class);

    /**
     * Whether we will be sampling points from a Dirichlet distribution.
     */
    private boolean searchDirichlet;

    /**
     * The dirichlet distribution alpha parameters
     */
    private double[] dirichletAlphas;

    /**
     * The number of weights
     */
    private int nWeights;

    public WeightSampler(int nWeights) {
        this.nWeights = nWeights;

        searchDirichlet = Options.WLA_SEARCH_DIRICHLET.getBoolean();
        double dirichletAlpha = Options.WLA_SEARCH_DIRICHLET_ALPHA.getDouble();
        dirichletAlphas = new double[nWeights];

        for (int i = 0; i < nWeights; i ++) {
            dirichletAlphas[i] = dirichletAlpha;
        }
    }

    public void getRandomWeights(double[] weights) {
        if (searchDirichlet) {
            log.debug("Getting Dirichlet Weights");
            getDirichletRandomWeights(weights);
            log.debug("Dirichlet Weights: {}", weights);
        } else {
            getHyperCubeRandomWeights(weights);
        }
    }

    private void getDirichletRandomWeights(double[] weights) {
        double[] dirichletSample = RandUtils.sampleDirichlet(dirichletAlphas);

        MathUtils.toUnit(dirichletSample);

        for (int i = 0; i < nWeights; i++) {
            // Returns the next pseudorandom, uniformly distributed value between 0 and 1
            weights[i] = dirichletSample[i];
        }
    }

    private void getHyperCubeRandomWeights (double[] weights) {
        for (int i = 0; i < nWeights; i++) {
            // Returns the next pseudorandom, uniformly distributed value between 0 and 1
            weights[i] = RandUtils.nextDouble();
        }
    }

    private void getDirichletRandomWeights(float[] weights) {
        double[] dirichletSample = RandUtils.sampleDirichlet(dirichletAlphas);

        MathUtils.toUnit(dirichletSample);

        for (int i = 0; i < nWeights; i++) {
            // Returns the next pseudorandom, uniformly distributed value between 0 and 1
            weights[i] = (float)dirichletSample[i];
        }
    }

    private void getHyperCubeRandomWeights (float[] weights) {
        for (int i = 0; i < nWeights; i++) {
            // Returns the next pseudorandom, uniformly distributed value between 0 and 1
            weights[i] = RandUtils.nextFloat();
        }
    }

    public void getRandomWeights(float[] weights) {
        if (searchDirichlet) {
            log.debug("Getting Dirichlet Weights");
            getDirichletRandomWeights(weights);
            log.debug("Dirichlet Weights: {}", weights);
        } else {
            getHyperCubeRandomWeights(weights);
        }
    }
}
