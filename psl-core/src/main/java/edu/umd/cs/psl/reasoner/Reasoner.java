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
package edu.umd.cs.psl.reasoner;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;

/**
 * A GroundKernelStore that can minimize the total weighted incompatibility
 * of its GroundKernels by optimizing the RandomVariableAtoms.
 */
public interface Reasoner extends GroundKernelStore {
	
	/**
	 * Distribution types supported by Reasoners.
	 * 
	 * A linear distribution does not modify the incompatibility values of
	 * {@link GroundCompatibilityKernel GroundCompatibilityKernels},
	 * and a quadratic distribution squares them.
	 */
	public static enum DistributionType {linear, quadratic};
	
	public DistributionType getDistributionType();
	
	/**
	 * Minimizes the total weighted incompatibility of the this Reasoner's
	 * GroundKernels by optimizing the truth values of the RandomVariableAtoms.
	 * 
	 * @see #getTotalWeightedIncompatibility()
	 */
	public void optimize();
	
	/**
	 * Releases all resources acquired by this Reasoner.
	 */
	public void close();
}

