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
package org.linqs.psl.util;

import org.linqs.psl.test.PSLBaseTest;

import org.junit.Test;

public class RandUtilsTest extends PSLBaseTest {
    @Test
    public void testNextGamma() {
        int numSamples = 10000;
        double sampleMean = 0.0;
        double sampleVariance = 0.0;
        double expectedMean = 0.0;
        double expectedVariance = 0.0;
        double[] shapes = new double[]{ 0.5, 1.0, 5.0 };
        double[] scales = new double[]{ 0.5, 1.0, 5.0 };
        double[] gammaSamples = new double[numSamples];

        // Set the random seed.
        RandUtils.seed(22);

        // Iterate over possible shape and scale parameters.
        for (double shape : shapes) {
            for (double scale : scales) {
                // Draw numSamples random samples from gamma distribution.
                for (int i = 0; i < numSamples; i++) {
                    gammaSamples[i] = RandUtils.nextGamma(shape, scale);
                }

                // Calculate statistics of sample.
                sampleMean = 0.0;
                sampleVariance = 0.0;
                expectedMean = shape * scale;
                expectedVariance = shape * Math.pow(scale, 2.0);

                for (int i = 0; i < numSamples; i++) {
                    sampleMean += gammaSamples[i] / numSamples;
                }

                for (int i = 0; i < numSamples; i++) {
                    sampleVariance += Math.pow(gammaSamples[i] - expectedMean, 2.0) / (numSamples);
                }

                // Assert that various statistics of the distribution are as expected.
                assertEquals(expectedMean, sampleMean, 0.1);
                assertEquals(expectedVariance, sampleVariance, 1.0);
            }
        }
    }

    @Test
    public void testSampleDirichlet() {
        int numSamples = 1000;
        double sampleSum = 0.0;
        double sampleMean = 0.0;
        double sampleVariance = 0.0;
        double expectedMean = 0.0;
        double expectedVariance = 0.0;
        int[] dimensions = new int[]{ 1, 2, 100 };
        double[] alphas = new double[]{ 0.5, 1.0, 5.0 };
        double[][] dirichletSamples = null;
        double[] dirichletAlphas = null;

        // Set the random seed.
        RandUtils.seed(22);

        // iterate over possible shape and scale parameters.
        for (int dimension: dimensions) {
            for (double alpha: alphas) {
                dirichletSamples = new double[numSamples][dimension];
                dirichletAlphas = new double[dimension];

                // populate dirichletAlphas
                for (int i = 0; i < dimension; i++) {
                    dirichletAlphas[i] = alpha;
                }

                // Draw numSamples random samples from dirichlet distribution.
                for (int i = 0; i < numSamples; i++) {
                    dirichletSamples[i] = RandUtils.sampleDirichlet(dirichletAlphas);
                }

                // Calculate statistics of sample.
                expectedMean = 1.0 / dimension;
                expectedVariance = (1.0 / dimension) * (1.0 - 1.0 / dimension) / (dimension * alpha + 1.0);

                for (int i = 0; i < dimension; i++) {
                    sampleMean = 0.0;
                    sampleVariance = 0.0;
                    for (int j = 0; j < numSamples; j++) {
                        sampleMean += dirichletSamples[j][i] / numSamples;
                    }

                    for (int j = 0; j < numSamples; j++) {
                        sampleVariance += Math.pow(dirichletSamples[j][i] - expectedMean, 2.0) / (numSamples);
                    }

                    // Assert that various statistics of the distribution are as expected.
                    assertEquals(expectedMean, sampleMean, 0.1);
                    assertEquals(expectedVariance, sampleVariance, 0.1);
                }

                // Assert that the sum of each sample adds to 1.0.
                for (int i = 0; i < numSamples; i++) {
                    sampleSum = 0.0;
                    for (int j = 0; j < dimension; j++) {
                        sampleSum += dirichletSamples[i][j];
                    }
                    assertEquals(1.0, sampleSum, 0.1);
                }
            }
        }
    }
}
