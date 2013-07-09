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

import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;

/**
 * Static utilities for common {@link GroundKernel} tasks.
 */
public class GroundKernels {

	/**
	 * Sums the total, weighted incompatibility of an iterable container of
	 * {@link GroundCompatibilityKernel GroundCompatibilityKernels}.
	 * 
	 * @param gks  the GroundCompatibilityKernels
	 * @return the total, weighted incompatibility
	 * @see GroundCompatibilityKernel#getIncompatibility()
	 * @see GroundCompatibilityKernel#getWeight()
	 */
	public static double getTotalWeightedIncompatibility(Iterable<GroundCompatibilityKernel> gks) {
		double totalInc = 0.0;
		for (GroundCompatibilityKernel gk : gks)
			totalInc += gk.getIncompatibility() * gk.getWeight().getWeight();
		return totalInc;
	}
	
	/**
	 * Computes the Euclidean norm of the infeasibilities of an iterable container
	 * of {@link GroundConstraintKernel GroundConstraintKernels}.
	 * 
	 * @param gks  the GroundConstraintKernels
	 * @return the Euclidean norm of the infeasibilities
	 * @see GroundConstraintKernel#getInfeasibility()
	 */
	public static double getInfeasibilityNorm(Iterable<GroundConstraintKernel> gks) {
		double inf, norm = 0.0;
		for (GroundConstraintKernel gk : gks) {
			inf = gk.getInfeasibility();
			norm += inf * inf;
		}
		return Math.sqrt(norm);
	}
	
}
