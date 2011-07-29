/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.evaluation.process.local;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import edu.umd.cs.psl.evaluation.process.*;

public class LocalProcessMonitor implements ProcessMonitor {

	private final ConcurrentMap<Integer,RunningProcess> processes;
	
	private final AtomicInteger processID;
	
	public LocalProcessMonitor() {
		processes = new ConcurrentHashMap<Integer,RunningProcess>();
		processID = new AtomicInteger(0);
	}

	@Override
	public RunningProcess newProcess() {
		int id = processID.incrementAndGet();
		RunningProcess proc = new LocalProcess(id);
		assert !processes.containsKey(id);
		processes.put(id, proc);
		return proc;
	}

	@Override
	public RunningProcess startProcess() {
		RunningProcess p = newProcess();
		p.start();
		return p;
	}

	@Override
	public Iterable<? extends ProcessView> viewAllProcesses() {
		return processes.values();
	}

	@Override
	public ProcessView viewProcess(int id) {
		return processes.get(id);
	}
	

	@Override
	public void clearTerminatedProcesses() {
		Iterator<Map.Entry<Integer,RunningProcess>> iter = processes.entrySet().iterator();
		while (iter.hasNext()) {
			RunningProcess proc = iter.next().getValue();
			if (proc.isTerminated()) {
				iter.remove();
			}
		}
		
	}
	
	
	private static final LocalProcessMonitor singleton = new LocalProcessMonitor();
	
	public static final ProcessMonitor get() {
		return singleton;
	}
	
	
}
