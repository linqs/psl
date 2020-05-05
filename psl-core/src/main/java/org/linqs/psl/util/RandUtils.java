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
package org.linqs.psl.util;

import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.linqs.psl.config.Options;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;

/**
 * The canonical source of randomness for all PSL core code.
 * Any code using randomness should use this class.
 * The seed can be set through config before asking for any random numbers,
 * or through the seed() method.
 *
 * If parallel randomness is required, then use this RNG to seed the per-thread RNGs.
 */
public final class RandUtils {
    private static final Logger log = LoggerFactory.getLogger(RandUtils.class);

    private static Random rng = null;
    private static Random rng_gaussian = null;
    private static RandomGenerator rng_gamma = null;

    private static GammaDistribution gammaDist = null;

    // Static only.
    private RandUtils() {}

    private static synchronized void ensureRNG() {
        if (rng != null && rng_gaussian != null && rng_gamma != null) {
            return;
        }

        if (rng == null) {
            long seed = Options.RANDOM_SEED.getInt();
            log.info("Using random seed: " + seed);
            rng = new Random(seed);
        }

        if (rng_gaussian == null) {
            long seed = Options.RANDOM_SEED.getInt();
            log.info("Using random seed: " + seed);
            rng_gaussian = new Random(seed);
        }

        if (rng_gamma == null) {
            long seed = Options.RANDOM_SEED.getInt();
            log.info("Using random seed: " + seed);
            rng_gamma = new JDKRandomGenerator();
            rng_gamma.setSeed(seed);
        }
    }

    public static synchronized void seed(int seed) {
        ensureRNG();
        rng.setSeed(seed);
    }

    public static synchronized boolean nextBoolean() {
        ensureRNG();
        return rng.nextBoolean();
    }

    public static synchronized double nextDouble() {
        ensureRNG();
        return rng.nextDouble();
    }

    public static synchronized float nextFloat() {
        ensureRNG();
        return rng.nextFloat();
    }

    public static synchronized float nextFloat(float min, float max) {
        ensureRNG();

        if (min >= max) {
            throw new IllegalArgumentException(String.format(
                    "Min (%f) must be strictly less than max (%f).", min, max));
        }

        return rng.nextFloat() * (max - min) + min;
    }

    public static synchronized double nextGaussian() {
        ensureRNG();
        return rng.nextGaussian();
    }

    /**
     * Get an int in the range of a signed int.
     */
    public static synchronized int nextInt() {
        ensureRNG();
        return rng.nextInt();
    }

    /**
     * Get an int in the range [0, max).
     */
    public static synchronized int nextInt(int max) {
        ensureRNG();
        return rng.nextInt(max);
    }

    public static synchronized long nextLong() {
        ensureRNG();
        return rng.nextLong();
    }

    public static synchronized void shuffle(List<?> list) {
        ensureRNG();
        Collections.shuffle(list, rng);
    }

    /**
     * Shuffle multiple lists, but keep the elements that share indexes together.
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static synchronized void pairedShuffle(List... lists) {
        ensureRNG();

        if (lists.length == 0) {
            return;
        }

        for (List list : lists) {
            if (list.size() != lists[0].size()) {
                throw new IllegalArgumentException(String.format(
                        "Lists must all have a matching size, found %d and %d.",
                        list.size(), lists[0].size()));
            }
        }

        for (int i = lists[0].size() - 1; i >= 0; i--) {
            int swapIndex = nextInt(i + 1);

            for (List list : lists) {
                Object temp = list.get(i);
                list.set(i, list.get(swapIndex));
                list.set(swapIndex, temp);
            }
        }
    }

    /**
     * A version of pairedShuffle() optimized for very specific list types.
     * This is used in a high-throughput piece of code and needs to be more optimized
     * than the general variant.
     * The indexes (array) may be larger than list.
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T> void pairedShuffleIndexes(List<T> list, int[] indexes) {
        ensureRNG();

        if (list.size() > indexes.length) {
            throw new IllegalArgumentException(String.format(
                    "List size (%d) must be greater than or equal to array size (%d).",
                    list.size(), indexes.length));
        }

        T temp = null;
        int tempIndex = -1;

        for (int i = list.size() - 1; i >= 0; i--) {
            int swapIndex = nextInt(i + 1);

            temp = list.get(i);
            list.set(i, list.get(swapIndex));
            list.set(swapIndex, temp);

            tempIndex = indexes[i];
            indexes[i] = indexes[swapIndex];
            indexes[swapIndex] = indexes[i];
        }
    }

    /**
     * Sample from gridded points on the surface of a hypersphere with a radius 1.
     * The grid is defined by
     */
    public static synchronized double[] sampleGriddedHypersphereSurface (int dimension, double gridStepSize) {
        ensureRNG();

        double[] vector = sampleHypersphereSurface(dimension, 1.0);

        for (int i = 0; i < vector.length; i ++) {
            vector[i] = Math.round(vector[i] / gridStepSize) * gridStepSize;
        }

        vector = MathUtils.toUnit(vector);

        return vector;
    }

    /**
     * Sample from the surface of a hypersphere with a radius of 1
     */
    public static synchronized double[] sampleUnitHypersphereSurface (int dimension) {
        ensureRNG();

        return sampleHypersphereSurface (dimension, 1.0);
    }

    /**
     * Sample from the surface of a hypersphere with a certain radius
     */
    public static synchronized double[] sampleHypersphereSurface (int dimension, double radius) {
        ensureRNG();

        double[] vector = new double[dimension];

        for (int i = 0; i < dimension; i++) {
            // Returns Gaussian with mean = 0.0 and variance = 1.0
            vector[i] = rng_gaussian.nextGaussian();
        }

        vector = MathUtils.toMagnitude(vector, radius);

        return vector;
    }

    /**
     * Sample from a dirichlet with the provided parameters
     */
    public static synchronized double[] sampleDirichlet (double[] alphas) {
        ensureRNG();

        double [] gammaSamples = new double[alphas.length];
        double gammaSampleSum = 0;
        double [] dirichletSample = new double[alphas.length];

        for(int i = 0; i < alphas.length; i++){
            gammaSamples[i] = sampleGamma(alphas[i],1);
            gammaSampleSum = gammaSampleSum + gammaSamples[i];
        }

        for(int i = 0; i < alphas.length; i++){
            dirichletSample[i] = gammaSamples[i] / gammaSampleSum ;
        }

        return dirichletSample;
    }


    /**
     * Sample from a gamma with the provided parameters
     */
    public static synchronized double sampleGamma (double shape, double scale) {
        ensureRNG();

        if (gammaDist == null) {
            gammaDist = new GammaDistribution(shape, scale);
        }

        if (gammaDist.getShape() != shape || gammaDist.getScale() != scale) {
            gammaDist = new GammaDistribution(rng_gamma, shape, scale);
        }

        return gammaDist.sample(1)[0];
    }

}
