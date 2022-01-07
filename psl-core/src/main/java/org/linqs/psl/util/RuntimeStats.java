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

import org.linqs.psl.config.Options;

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

    private static MeanStats totalMemory = new MeanStats();
    private static MeanStats freeMemory = new MeanStats();
    private static MeanStats usedMemory = new MeanStats();
    private static MeanStats maxMemory = new MeanStats();

    private static AccumulatingStats reads = new AccumulatingStats();
    private static AccumulatingStats writes = new AccumulatingStats();

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

        Object property = Options.RUNTIME_STATS_COLLECT.getUnlogged();
        if (property == null || !Boolean.parseBoolean((String)property)) {
            return;
        }

        init();

        long period = Options.RUNTIME_COLLECTION_PERIOD.getLong();

        collectionTimer = new Timer(RuntimeStats.class.getName(), true);
        collectionTimer.schedule(new CollectionTask(), 0, period);
    }

    public static synchronized void stopCollection() {
        if (collectionTimer == null) {
            return;
        }

        collectionTimer.purge();
        collectionTimer.cancel();
        collectionTimer = null;
    }

    /**
     * Tell the RuntimeStats about an io operation.
     * Since we can't just monitor all IO like memory, we rely on self reporting.
     */
    public static synchronized void logDiskRead(long bytes) {
        reads.add(bytes);
    }

    /**
     * Tell the RuntimeStats about an io operation.
     * Since we can't just monitor all IO like memory, we rely on self reporting.
     */
    public static synchronized void logDiskWrite(long bytes) {
        writes.add(bytes);
    }

    /**
     * Ouput collected stats.
     */
    public static void outputStats() {
        if (runtime == null) {
            return;
        }

        log.info("Total Memory (bytes) -- " + totalMemory);
        log.info("Free Memory (bytes)  -- " + freeMemory);
        log.info("Used Memory (bytes)  -- " + usedMemory);
        log.info("Max Memory (bytes)   -- " + maxMemory);
        log.info("IO Reads (bytes)     -- " + reads);
        log.info("IO Writes (bytes)    -- " + writes);
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            outputStats();
        }
    }

    private static class CollectionTask extends TimerTask {
        @Override
        public void run() {
            long totalMemoryValue = runtime.totalMemory();
            long freeMemoryValue = runtime.freeMemory();
            long usedMemoryValue = Math.max(0, totalMemoryValue - freeMemoryValue);
            long maxMemoryValue = runtime.maxMemory();

            totalMemory.add(totalMemoryValue);
            freeMemory.add(freeMemoryValue);
            usedMemory.add(usedMemoryValue);
            maxMemory.add(maxMemoryValue);
        }
    }

    private static class MeanStats {
        private long min;
        private long max;
        private long mean;
        private long count;

        public MeanStats() {
            min = 0;
            max = 0;
            mean = 0;
            count = 0;
        }

        public void add(long value) {
            if (count == 0 || value < min) {
                min = value;
            }

            if (count == 0 || value > max) {
                max = value;
            }

            mean = (mean * count + value) / (count + 1);
            count++;
        }

        @Override
        public String toString() {
            return String.format(
                    "Min: %12d, Max: %12d, Mean: %12d, Count: %12d",
                    min, max, mean, count);
        }
    }

    private static class AccumulatingStats extends MeanStats {
        private long total;

        public AccumulatingStats() {
            super();
            total = 0;
        }

        @Override
        public void add(long value) {
            super.add(value);
            total += value;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s, Total: %12d",
                    super.toString(), total);
        }
    }
}
