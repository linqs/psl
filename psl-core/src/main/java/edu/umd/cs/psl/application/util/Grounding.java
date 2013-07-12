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
package edu.umd.cs.psl.application.util;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.kernel.Kernel;

/**
 * Static utilities for common {@link Model}-grounding tasks.
 */
public class Grounding {

	private final static com.google.common.base.Predicate<Kernel> all = new com.google.common.base.Predicate<Kernel>(){
		@Override
		public boolean apply(Kernel el) {	return true; }
	};
	
	/**
	 * Calls {@link Kernel#groundAll(AtomManager, GroundKernelStore)} on
	 * each Kernel in a Model.
	 * 
	 * @param m  the Model with the Kernels to ground
	 * @param atomManager  AtomManager to use for grounding
	 * @param gks  GroundKernelStore to use for grounding
	 */
	public static void groundAll(Model m, AtomManager atomManager, GroundKernelStore gks) {
		groundAll(m,atomManager, gks, all);
	}
	
	/**
	 * Calls {@link Kernel#groundAll(AtomManager, GroundKernelStore)} on
	 * each Kernel in a Model which passes a filter.
	 * 
	 * @param m  the Model with the Kernels to ground
	 * @param atomManager  AtomManager to use for grounding
	 * @param gks  GroundKernelStore to use for grounding
	 * @param filter  filter for Kernels to ground
	 */
	public static void groundAll(Model m, AtomManager atomManager, GroundKernelStore gks,
			com.google.common.base.Predicate<Kernel> filter) {
		for (Kernel k : m.getKernels()) {
			if (filter.apply(k))
				k.groundAll(atomManager, gks);
		}
	}
	
}
