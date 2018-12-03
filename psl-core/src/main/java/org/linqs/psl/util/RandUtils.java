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
package org.linqs.psl.util;

import org.linqs.psl.config.Config;

import java.util.Random;

/**
 * The canonical source of randomness for all PSL core code.
 * Any code using randomness should use this class.
 */
public final class RandUtils {
    public static final String CONFIG_PREFIX = "random";

    public static final String SEED_KEY = CONFIG_PREFIX + ".seed";
    public static final int SEED_DEFAULT = 4;

    private static Random rng = null;

    // Static only.
    private RandUtils() {}

    private static synchronized void ensureRNG() {
        if (rng != null) {
            return;
        }

        rng = new Random(Config.getInt(SEED_KEY, SEED_DEFAULT));
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

    public static synchronized double nextGaussian() {
        ensureRNG();
        return rng.nextGaussian();
    }

    public static synchronized int nextInt() {
        ensureRNG();
        return rng.nextInt();
    }

    public static synchronized int nextInt(int max) {
        ensureRNG();
        return rng.nextInt(max);
    }

    public static synchronized long nextLong() {
        ensureRNG();
        return rng.nextLong();
    }
}
