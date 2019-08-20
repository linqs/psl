/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A static class that collects runtime stats in the background.
 * The Config class will try to start stats collection every time a config file is loaded.
 * If the config option exists (and is true), then collection will start.
 * Collections can also be started manually through the collect() method.
 */
public final class RuntimeStats {
    private static final Logger log = LoggerFactory.getLogger(RuntimeStats.class);

    public static final String CONFIG_PREFIX = "runtimestats";

    /**
     * Periodically collect stats on the JVM.
     */
    public static final String COLLECT_KEY = CONFIG_PREFIX + ".collect";

    /**
     * The period (in ms) of stats collection.
     */
    public static final String COLLECTION_PERIOD_KEY = CONFIG_PREFIX + ".period";
    public static final long COLLECTION_PERIOD_DEFAULT = 250;

    private static final int MIN = 0;
    private static final int MAX = 1;
    private static final int MEAN = 2;
    private static final int COUNT = 3;
    private static final int STATS_SIZE = 4;

    private static long[] totalMemory = new long[STATS_SIZE];
    private static long[] freeMemory = new long[STATS_SIZE];
    private static long[] usedMemory = new long[STATS_SIZE];
    private static long[] maxMemory = new long[STATS_SIZE];

    private static Runtime runtime = null;
    private static Timer collectionTimer = null;

    // Static only.
    private RuntimeStats() {}

    private static synchronized void init() {
        if (runtime != null) {
            return;
        }

        runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ShutdownHook());
    }

    /**
     * Begin the process of collecting stats.
     * If stats are already being collected or the config is not set, nothing will happen.
     */
    public static synchronized void collect() {
        if (collectionTimer != null) {
            return;
        }

        Object property = Config.getUnloggedProperty(COLLECT_KEY);
        if (property == null || !Boolean.parseBoolean((String)property)) {
            return;
        }

        init();

        long period = Config.getLong(COLLECTION_PERIOD_KEY, COLLECTION_PERIOD_DEFAULT);

        collectionTimer = new Timer(RuntimeStats.class.getName(), true);
        collectionTimer.schedule(new CollectionTask(), 0, period);
    }

    public static synchronized void stopCollection() {
        if (collectionTimer != null) {
            return;
        }

        collectionTimer.purge();
        collectionTimer.cancel();
        collectionTimer = null;
    }

    /**
     * Ouput collected stats.
     */
    public static void logStats() {
        if (runtime == null) {
            return;
        }

        log.info("Total Memory KB -- " + formatStats(totalMemory));
        log.info("Free Memory KB  -- " + formatStats(freeMemory));
        log.info("Used Memory KB  -- " + formatStats(usedMemory));
        log.info("Max Memory KB   -- " + formatStats(maxMemory));
    }

    private static String formatStats(long[] stats) {
        return String.format(
                "Min: %9d, Max: %9d, Mean: %9d, Count: %6d",
                stats[MIN] / 1024, stats[MAX] / 1024, stats[MEAN] / 1024, stats[COUNT]);
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            logStats();
        }
    }

    private static class CollectionTask extends TimerTask {
        @Override
        public void run() {
            long totalMemoryValue = runtime.totalMemory();
            long freeMemoryValue = runtime.freeMemory();
            long usedMemoryValue = Math.max(0, totalMemoryValue - freeMemoryValue);
            long maxMemoryValue = runtime.maxMemory();

            updateStats(totalMemory, totalMemoryValue);
            updateStats(freeMemory, freeMemoryValue);
            updateStats(usedMemory, usedMemoryValue);
            updateStats(maxMemory, maxMemoryValue);
        }

        private void updateStats(long[] stats, long value) {
            if (stats[COUNT] == 0 || value < stats[MIN]) {
                stats[MIN] = value;
            }

            if (stats[COUNT] == 0 || value > stats[MAX]) {
                stats[MAX] = value;
            }

            stats[MEAN] = (stats[MEAN] * stats[COUNT] + value) / (stats[COUNT] + 1);

            stats[COUNT]++;
        }
    }
}
