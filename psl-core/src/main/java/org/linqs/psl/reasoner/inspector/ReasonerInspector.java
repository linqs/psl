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
package org.linqs.psl.reasoner.inspector;

import org.linqs.psl.reasoner.Reasoner;

/**
 * A ReasonerInspector is an object that may be called by a reasoner periodically during
 * whatever reasoning/optimization it is doing.
 * Iterative reasoners should call its inspector at the end of every iteration.
 * An inspector can also suggest that the reasoner stop iterations.
 */
public interface ReasonerInspector {
	/**
	 * Update the inspector on the status of the reasoner.
	 * @return false if the inspector believes the reasoner should stop now.
	 */
	public boolean update(Reasoner reasoner, ReasonerStatus status);

	/**
	 * A blank slate for reasoners to store their run information.
	 */
	public static interface ReasonerStatus {
	}

	public static class IterativeReasonerStatus implements ReasonerStatus {
		private final int iteration;

		public IterativeReasonerStatus(int iteration) {
			this.iteration = iteration;
		}

		public int getIteration() {
			return iteration;
		}

		@Override
		public String toString() {
			return "Iteration: " + iteration;
		}
	}
}
