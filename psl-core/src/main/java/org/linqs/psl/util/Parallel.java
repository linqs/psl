/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Utilities to run operations in parallel.
 * The threads will be started up on the first call, and not shut down until the JVM shuts down.
 * Since the thread pool (and CPU) is shared, only one task may be run in parallel at a time.
 */
public final class Parallel {
    private static final Logger log = Logger.getLogger(Parallel.class);

    private static boolean initialized = false;

    // Defer assignment until a request is actually made to let the config get initialized.
    private static int numThreads = -1;

    // Block putting work in to the pool until there are workers ready.
    private static BlockingQueue<Worker<?>> workerQueue;

    // Keep all the workers somewhere we can reference them.
    private static List<Worker<?>> allWorkers;

    private static ExecutorService pool;

    /**
     * Objects that are specific to each thread.
     */
    private static Map<Thread, Map<String, Object>> threadObjects = new ConcurrentHashMap<Thread, Map<String, Object>>();

    // Static only.
    private Parallel() {}

    public synchronized static void close() {
        shutdown();
    }

    public synchronized static int getNumThreads() {
        if (numThreads == -1) {
            numThreads = Options.PARALLEL_NUM_THREADS.getInt();
        }

        return numThreads;
    }

    public static boolean hasThreadObject(String key) {
        if (!threadObjects.containsKey(Thread.currentThread())) {
            threadObjects.put(Thread.currentThread(), new HashMap<String, Object>());
        }

        return threadObjects.get(Thread.currentThread()).containsKey(key);
    }

    public static Object getThreadObject(String key) {
        if (!threadObjects.containsKey(Thread.currentThread())) {
            threadObjects.put(Thread.currentThread(), new HashMap<String, Object>());
        }

        return threadObjects.get(Thread.currentThread()).get(key);
    }

    public static void putThreadObject(String key, Object value) {
        if (!threadObjects.containsKey(Thread.currentThread())) {
            threadObjects.put(Thread.currentThread(), new HashMap<String, Object>());
        }

        threadObjects.get(Thread.currentThread()).put(key, value);
    }

    /**
     * Count and call a worker with each number in [start, end).
     * Inclusive with start, exclusive with end.
     * The caller is trusted to provide appropriate numbers.
     * The long value provided to the worker will be the number also passed as a Long.
     */
    public synchronized static RunTimings count(long start, long end, long increment, Worker<Long> baseWorker) {
        initWorkers(baseWorker, null);
        RunTimings timings = countInternal(start, end, increment);
        cleanupWorkers();

        return timings;
    }

    /**
     * Convenience count() that increments by 1.
     */
    public static RunTimings count(long start, long end, Worker<Long> baseWorker) {
        return count(start, end, 1, baseWorker);
    }

    /**
     * Convenience count() that starts at 0 and increments by 1.
     */
    public static RunTimings count(long end, Worker<Long> baseWorker) {
        return count(0, end, 1, baseWorker);
    }

    private static RunTimings countInternal(long start, long end, long increment) {
        long iterations = 0;
        long parentWaitTimeMS = 0;
        long workerWaitTimeMS = 0;
        long workerWorkTimeMS = 0;

        for (long i = start; i < end; i += increment) {
            Worker<?> worker = null;
            try {
                // Will block if no workers are ready.
                long time = System.currentTimeMillis();
                worker = workerQueue.take();
                parentWaitTimeMS += (System.currentTimeMillis() - time);
                iterations++;
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted waiting for worker (" + i + ").");
            }

            if (worker.getException() != null) {
                throw new RuntimeException("Exception on worker.", worker.getException());
            }

            @SuppressWarnings("unchecked")
            Worker<Long> typedWorker = (Worker<Long>)worker;
            typedWorker.setWork(i, Long.valueOf(i));
            pool.execute(typedWorker);
        }

        for (int i = 0; i < numThreads; i++) {
            try {
                long time = System.currentTimeMillis();
                Worker<?> worker = workerQueue.take();
                parentWaitTimeMS += (System.currentTimeMillis() - time);

                workerWaitTimeMS += worker.getWaitTime();
                workerWorkTimeMS += worker.getWorkTime();

                if (worker.getException() != null) {
                    throw new RuntimeException("Exception on worker.", worker.getException());
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted waiting for worker (" + i + ").");
            }
        }

        return new RunTimings(iterations, parentWaitTimeMS, workerWaitTimeMS, workerWorkTimeMS);
    }

    /**
     * Invoke a worker once for each item.
     * The long value provided to the worker will be the index of the piece of work.
     */
    public synchronized static <T> RunTimings foreach(Iterable<T> work, Worker<T> baseWorker) {
        initWorkers(baseWorker, work);
        RunTimings timings = foreachInternal(work);
        cleanupWorkers();

        return timings;
    }

    public static <T> RunTimings foreach(Iterator<T> work, Worker<T> baseWorker) {
        return foreach(IteratorUtils.newIterable(work), baseWorker);
    }

    private static <T> RunTimings foreachInternal(Iterable<T> work) {
        long iterations = 0;
        long parentWaitTimeMS = 0;
        long workerWaitTimeMS = 0;
        long workerWorkTimeMS = 0;

        long count = 0;
        for (T job : work) {
            Worker<?> worker = null;
            try {
                // Will block if no workers are ready.
                long time = System.currentTimeMillis();
                worker = workerQueue.take();
                parentWaitTimeMS += (System.currentTimeMillis() - time);
                iterations++;
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted waiting for worker (" + count + ").");
            }

            if (worker.getException() != null) {
                throw new RuntimeException("Exception on worker.", worker.getException());
            }

            @SuppressWarnings("unchecked")
            Worker<T> typedWorker = (Worker<T>)worker;
            typedWorker.setWork(count, job);
            pool.execute(typedWorker);

            count++;
        }

        // As workers finish, they will be added to the queue.
        // We can wait for all the workers by emptying out the queue.
        for (int i = 0; i < numThreads; i++) {
            try {
                long time = System.currentTimeMillis();
                Worker<?> worker = workerQueue.take();
                parentWaitTimeMS += (System.currentTimeMillis() - time);

                workerWaitTimeMS += worker.getWaitTime();
                workerWorkTimeMS += worker.getWorkTime();

                if (worker.getException() != null) {
                    throw new RuntimeException("Exception on worker.", worker.getException());
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted waiting for worker (" + i + ").");
            }
        }

        return new RunTimings(iterations, parentWaitTimeMS, workerWaitTimeMS, workerWorkTimeMS);
    }

    /**
     * Invoke a worker once for each batch of items.
     * This is like foreach() but instead of passing a single item,
     * the parent thread will collect several items and pass those items together to the worker.
     * This works well when there are many small computations that need to be done.
     * The long value passed to the worker will be the number of items in the batch.
     */
    public static <T> RunTimings foreachBatch(Iterator<T> work, int batchSize, Worker<List<T>> baseWorker) {
        initWorkers(baseWorker, work);
        RunTimings timings = foreachBatchInternal(work, batchSize);
        cleanupWorkers();

        return timings;
    }

    public synchronized static <T> RunTimings foreachBatch(Iterable<T> work, int batchSize, Worker<List<T>> baseWorker) {
        return foreachBatch(work.iterator(), batchSize, baseWorker);
    }

    private static <T> RunTimings foreachBatchInternal(Iterator<T> work, int batchSize) {
        long iterations = 0;
        long parentWaitTimeMS = 0;
        long workerWaitTimeMS = 0;
        long workerWorkTimeMS = 0;

        List<List<T>> batches = new ArrayList<List<T>>(numThreads);
        for (int i = 0; i < numThreads; i++) {
            batches.add(new ArrayList<T>(batchSize));
        }

        long count = 0;
        while (work.hasNext()) {
            Worker<?> worker = null;
            try {
                // Will block if no workers are ready.
                long time = System.currentTimeMillis();
                worker = workerQueue.take();
                parentWaitTimeMS += (System.currentTimeMillis() - time);
                iterations++;
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted waiting for worker (" + iterations + ").");
            }

            if (worker.getException() != null) {
                throw new RuntimeException("Exception on worker.", worker.getException());
            }

            List<T> batch = batches.get(worker.getID());
            batch.clear();
            long currentBatchSize = 0;

            for (int i = 0; i < batchSize; i++) {
                if (!work.hasNext()) {
                    break;
                }

                batch.add(work.next());
                currentBatchSize++;
                count++;
            }

            @SuppressWarnings("unchecked")
            Worker<List<T>> typedWorker = (Worker<List<T>>)worker;
            typedWorker.setWork(currentBatchSize, batch);
            pool.execute(typedWorker);
        }

        // As workers finish, they will be added to the queue.
        // We can wait for all the workers by emptying out the queue.
        for (int i = 0; i < numThreads; i++) {
            try {
                long time = System.currentTimeMillis();
                Worker<?> worker = workerQueue.take();
                parentWaitTimeMS += (System.currentTimeMillis() - time);

                workerWaitTimeMS += worker.getWaitTime();
                workerWorkTimeMS += worker.getWorkTime();

                if (worker.getException() != null) {
                    throw new RuntimeException("Exception on worker.", worker.getException());
                }
            } catch (InterruptedException ex) {
                throw new RuntimeException("Interrupted waiting for worker (" + i + ").");
            }
        }

        return new RunTimings(count, parentWaitTimeMS, workerWaitTimeMS, workerWorkTimeMS);
    }

    /**
     * Init the thread pool and supporting structures.
     */
    private static synchronized void initPool() {
        if (initialized) {
            return;
        }

        getNumThreads();

        // We can use an unbounded queue (no initial size given) since the parent
        // thread is disciplined when giving out work.
        workerQueue = new ArrayBlockingQueue<Worker<?>>(numThreads);
        allWorkers = new ArrayList<Worker<?>>(numThreads);

        // We will make all the threads daemons, so the JVM shutdown will not be held up.
        pool = Executors.newFixedThreadPool(numThreads, new DaemonThreadFactory());

        // Close the pool only at JVM shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Parallel.shutdown();
            }
        });

        initialized = true;
    }

    /**
     * Always the first thing called when setting up to run a task in parallel.
     */
    private static <T> void initWorkers(Worker<T> baseWorker, Object source) {
        initPool();

        workerQueue.clear();
        allWorkers.clear();

        for (int i = 0; i < numThreads; i++) {
            Worker<T> worker = null;

            // The base worker goes in last so we won't call copy() after init().
            if (i == numThreads - 1) {
                worker = baseWorker;
            } else {
                worker = baseWorker.copy();
            }

            worker.init(i, source);

            allWorkers.add(worker);
            workerQueue.add(worker);
        }
    }

    private static void cleanupWorkers() {
        for (Worker<?> worker : allWorkers) {
            worker.close();
        }

        allWorkers.clear();
        workerQueue.clear();
    }

    private static synchronized void shutdown() {
        if (!initialized) {
            return;
        }

        cleanupWorkers();

        try {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            // Do nothing, we are shutting down anyways.
        }
        pool = null;

        threadObjects.clear();

        workerQueue.clear();
        workerQueue = null;

        allWorkers.clear();
        allWorkers = null;

        numThreads = -1;
        initialized = false;
    }

    /**
     * Signal that a worker is done and ready for more work.
     */
    private static void freeWorker(Worker<?> worker) {
        workerQueue.add(worker);
    }

    /**
     * Extend this class for any work.
     * Default implmentation are provided for all non-abstract, non-final methods.
     */
    public static abstract class Worker<T> implements Runnable, Cloneable {
        protected int id;
        protected Object source;

        private long value;
        private long waitTimeMS;
        private long workTimeMS;
        private T item;
        private Exception exception;

        public Worker() {
            this.id = -1;
            this.source = null;
            this.value = -1;
            this.waitTimeMS = 0;
            this.workTimeMS = 0;
            this.item = null;
            this.exception = null;
        }

        /**
         * Cleanup anything.
         * Called after all work has been complete and it is time to clean up.
         */
        public void close() {
            id = -1;
            source = null;
        }

        /**
         * Make a deep copy of this worker.
         * Called when the manager is getting the correct number of workers ready.
         */
        @SuppressWarnings("unchecked")
        public Worker<T> copy() {
            try {
                return (Worker<T>)this.clone();
            } catch (CloneNotSupportedException ex) {
                throw new RuntimeException("Either implement copy(), or support clone() for Workers.", ex);
            }
        }

        /**
         * Called before any work is given.
         * The id will be unique to this worker for this batch of work.
         * The id is guarenteed to be in [0, numThreads).
         */
        public void init(int id, Object source) {
            this.id = id;
            this.source = source;
        }

        public int getID() {
            return id;
        }

        public void clearException() {
            exception = null;
        }

        public Exception getException() {
            return exception;
        }

        public long getWaitTime() {
            return waitTimeMS;
        }

        public long getWorkTime() {
            return workTimeMS;
        }

        @Override
        public final void run() {
            try {
                if (value == -1) {
                    log.warn("Called run() without first calling setWork().");
                    return;
                }

                long time = System.currentTimeMillis();
                work(value, item);
                workTimeMS += (System.currentTimeMillis() - time);
            } catch (Exception ex) {
                log.warn("Caught exception on worker: {}", id);
                exception = ex;
            } finally {
                value = -1;
                item = null;

                long time = System.currentTimeMillis();
                freeWorker(this);
                waitTimeMS += (System.currentTimeMillis() - time);
            }
        }

        public final void setWork(long value, T item) {
            this.value = value;
            this.item = item;
        }

        /**
         * Do the actual work.
         * The semantics of value is set by the parallel process calling controllering the workers.
         * For example, foreach() uses value as an index.
         * The only requirement is that it be non-negative.
         */
        public abstract void work(long value, T item);
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        private ThreadFactory defaultThreadFactory;

        public DaemonThreadFactory() {
            this.defaultThreadFactory = Executors.defaultThreadFactory();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = defaultThreadFactory.newThread(r);
            thread.setDaemon(true);
            return thread;
        }
    }

    public static class RunTimings {
        public final long iterations;
        public final long parentWaitTimeMS;
        public final long workerWaitTimeMS;
        public final long workerWorkTimeMS;

        public RunTimings(long iterations, long parentWaitTimeMS, long workerWaitTimeMS, long workerWorkTimeMS) {
            this.iterations = iterations;
            this.parentWaitTimeMS = parentWaitTimeMS;
            this.workerWaitTimeMS = workerWaitTimeMS;
            this.workerWorkTimeMS = workerWorkTimeMS;
        }

        public String toString() {
            return String.format("Iterations: %d, Parent Wait Time: %d, Worker Wait Time: %d, Worker Work Time: %d",
                    iterations, parentWaitTimeMS, workerWaitTimeMS, workerWorkTimeMS);
        }
    }
}
