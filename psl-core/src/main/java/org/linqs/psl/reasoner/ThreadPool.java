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
package org.linqs.psl.reasoner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ThreadPool {
	private final ExecutorService pool;

	public ThreadPool() {
		this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new DaemonThreadFactory());
	}

	public Future<?> submit(Runnable task) {
		return pool.submit(task);
	}

	/**
	 * Submit no new tasks and wait for all tasks to complete.
	 */
	public void shutdownAndWait() {
		pool.shutdown();

		// Timeout does not matter since we will keep waiting until all threads are done.
		while (true) {
			try {
				if (pool.awaitTermination(60, TimeUnit.SECONDS)) {
					break;
				}
			} catch (InterruptedException ex) {
				throw new RuntimeException("Interrupted waiting for pool to shutdown.", ex);
			}
		}
	}

	private class DaemonThreadFactory implements ThreadFactory {

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
