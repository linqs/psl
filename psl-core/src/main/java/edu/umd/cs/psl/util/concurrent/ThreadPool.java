/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class ThreadPool {

	private static ThreadPool instance = null;
	
	private final ExecutorService pool;
	
	private ThreadPool() {
		this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new DaemonThreadFactory());
	}
	
	public static ThreadPool getPool() {
		if (instance == null)
			instance = new ThreadPool();
		
		return instance;
	}
	
	public Future<?> submit(Runnable task) {
		return pool.submit(task);
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
