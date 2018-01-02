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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Utilities to run operations in parallel.
 *
 * TODO(eriq): Implement a foreach with iterators.
 */
public final class Parallel {
	// TODO(eriq): Replace with config option once we have global config.
	public static final int NUM_THREADS;

	// Block putting work intot he pool until there are workers ready.
	private static BlockingQueue<Worker> workerQueue;

	// Keep all the workers somewhere we can reference them.
	private static List<Worker> allWorkers;

	private static ExecutorService pool;

	// Init
	static {
		NUM_THREADS = Runtime.getRuntime().availableProcessors();
		workerQueue = new LinkedBlockingQueue<Worker>(NUM_THREADS);
		allWorkers = new ArrayList<Worker>(NUM_THREADS);
		// We will make all the threads daemons, so the JVM shutdown will not be held up.
		pool = Executors.newFixedThreadPool(NUM_THREADS, new DaemonThreadFactory());

		// Close the pool only at JVM shutdown.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Parallel.shutdown();
			}
		});
	}

	// Static only.
	private Parallel() {}

	/**
	 * Count and call a worker with each number in [start, end).
	 * Inclusive with start, exclusive with end.
	 * The caller is trusted to provide appropriate numbers.
	 */
	public synchronized static <T> void count(int start, int end, int increment, Worker<T> baseWorker) {
		initWorkers(baseWorker);
		countInternal(start, end, increment, baseWorker);
		cleanupWorkers();
	}

	/**
	 * Convenience count() that increments by 1.
	 */
	public static <T> void count(int start, int end, Worker<T> baseWorker) {
		count(start, end, 1, baseWorker);
	}

	/**
	 * Convenience count() that starts at 0 and increments by 1.
	 */
	public static <T> void count(int end, Worker<T> baseWorker) {
		count(0, end, 1, baseWorker);
	}

	private static <T> void countInternal(int start, int end, int increment, Worker<T> baseWorker) {
		for (int i = start; i < end; i += increment) {
			// Will block if no workers are ready.
			Worker worker = null;
			try {
				worker = workerQueue.take();
			} catch (InterruptedException ex) {
				throw new RuntimeException("Interrupted waiting for worker (" + i + ").");
			}

			worker.setWork(i, new Integer(i));
			pool.execute(worker);
		}

		// As workers finish, they will be added to the queue.
		// We can wait for all the workers by emptying out the queue.
		for (int i = 0; i < NUM_THREADS; i++) {
			try {
				workerQueue.take();
			} catch (InterruptedException ex) {
				throw new RuntimeException("Interrupted waiting for worker (" + i + ").");
			}
		}
	}

	private static void initWorkers(Worker baseWorker) {
		workerQueue.clear();
		allWorkers.clear();

		for (int i = 0; i < NUM_THREADS; i++) {
			Worker worker = null;

			// The base worker goes in last so we won't call copy() after init().
			if (i == NUM_THREADS - 1) {
				worker = baseWorker;
			} else {
				worker = baseWorker.copy();
			}

			worker.init(i);

			allWorkers.add(worker);
			workerQueue.add(worker);
		}
	}

	private static void cleanupWorkers() {
		for (Worker worker : allWorkers) {
			worker.close();
		}

		allWorkers.clear();
		workerQueue.clear();
	}

	 private static void shutdown() {
		cleanupWorkers();

		try {
			pool.shutdownNow();
			pool.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			// Do nothing, we are shutting down anyways.
		}

		workerQueue = null;
		allWorkers = null;
		pool = null;
	 }

	/**
	 * Signal that a worker is done and ready for more work.
	 */
	private static void freeWorker(Worker worker) {
		workerQueue.add(worker);
	}

	/**
	 * Extend this class for any work.
	 * Default implmentation are provided for all non-abstract, non-final methods.
	 */
	public static abstract class Worker<T> implements Runnable {
		protected int id;

		private int index;
		private T item;

		public Worker() {
			this.id = -1;
			this.index = -1;
			this.item = null;
		}

		/**
		 * Cleanup anything.
		 * Called after all work has been complete and it is time to clean up.
		 */
		public void close() {}

		/**
		 * Make a deep copy of this worker.
		 * Called when the manager is getting the correct number of workers ready.
		 */
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
		 */
		public void init(int id) {
			this.id = id;
		}

		@Override
		public final void run() {
			if (index == -1) {
				throw new IllegalStateException("Called run() without first calling setWork().");
			}

			try {
				work(index, item);
			} finally {
				index = -1;
				item = null;

				freeWorker(this);
			}
		}

		public final void setWork(int index, T item) {
			this.index = index;
			this.item = item;
		}

		/**
		 * Do the actual work.
		 * The index is the item's index in the collection.
		 */
		public abstract void work(int index, T item);
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
}
