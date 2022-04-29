/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.util.RandUtils;

import org.junit.Test;

/**
 * Test weight sampler class, which samples weight configurations from various distributions.
 */
public class WeightSamplerTest extends PSLBaseTest {
    /**
     * Test sampling weight configurations from a Dirichlet distribution.
     */
    @Test
    public void testDirichletWeightSampler() {
        int numSamples = 1000;
        double sampleMagnitude = 0.0;
        int[] numWeights = new int[]{ 1, 2, 100 };
        float[][] weightSamples = null;

        WeightSampler weightSampler = null;

        // Set the random seed.
        RandUtils.seed(22);

        // Draw weights from Dirichlet distribution with default alpha parameter.
        Options.WLA_SEARCH_DIRICHLET.set(true);

        // Iterate over possible number of weights.
        for (int dimension : numWeights) {
            weightSampler = new WeightSampler(dimension);
            weightSamples = new float[numSamples][dimension];

            // Draw numSamples random samples from dirichlet distribution.
            for (int i = 0; i < numSamples; i++) {
                weightSampler.getRandomWeights(weightSamples[i]);
            }

            // Assert that the magnitude of each sample is 1.0.
            // This tests that the weight vector is being unitized properly and whether getRandomWeights is assigning
            // values to the weight vectors in place.
            for (int i = 0; i < numSamples; i++) {
                sampleMagnitude = 0.0;
                for (int j = 0; j < dimension; j++) {
                    sampleMagnitude += Math.pow(weightSamples[i][j], 2);
                }
                sampleMagnitude = Math.sqrt(sampleMagnitude);

                assertEquals(1.0, sampleMagnitude, 0.1);
            }
        }
    }

    /**
     * Test sampling weight configurations from a uniform distribution over a unit hypercube.
     */
    @Test
    public void testHypercubeWeightSampler() {
        int numSamples = 1000;
        double sampleMin = 0.0;
        double sampleMax = 0.0;
        double sampleMean = 0.0;
        double sampleVariance = 0.0;
        int[] numWeights = new int[]{ 1, 2, 100 };
        float[][] weightSamples = null;

        WeightSampler weightSampler = null;

        // Set the random seed.
        RandUtils.seed(22);

        // Draw weights from uniform distribution over hypercube.
        Options.WLA_SEARCH_DIRICHLET.set(false);

        // Iterate over possible number of weights.
        for (int dimension: numWeights) {
            weightSampler = new WeightSampler(dimension);
            weightSamples = new float[numSamples][dimension];

            // Draw numSamples random samples from dirichlet distribution.
            for (int i = 0; i < numSamples; i++) {
                weightSampler.getRandomWeights(weightSamples[i]);
            }

            // Calculate statistics of sample.
            for (int i = 0; i < dimension; i++) {
                sampleMean = 0.0;
                sampleVariance = 0.0;
                for (int j = 0; j < numSamples; j++) {
                    sampleMean += weightSamples[j][i] / numSamples;
                }

                for (int j = 0; j < numSamples; j++) {
                    sampleVariance += Math.pow(weightSamples[j][i] - 0.5, 2.0) / (numSamples);
                }

                // Assert that various statistics of the distribution are as expected.
                assertEquals(0.5, sampleMean, 0.1);
                assertEquals(1.0 / 12.0, sampleVariance, 0.1);
            }

            // Assert that the maximum of each sample less than 1.0 and the minimum is greater than 0.
            for (int i = 0; i < numSamples; i++) {
                sampleMax = Float.NEGATIVE_INFINITY;
                sampleMin = Float.POSITIVE_INFINITY;
                for (int j = 0; j < dimension; j++) {
                    if (weightSamples[i][j] > sampleMax) {
                        sampleMax = weightSamples[i][j];
                    }

                    if (weightSamples[i][j] > sampleMin) {
                        sampleMin = weightSamples[i][j];
                    }
                }

                assertTrue(sampleMax <= 1.0);
                assertTrue(sampleMin >= 0.0);
            }
        }
    }
}
