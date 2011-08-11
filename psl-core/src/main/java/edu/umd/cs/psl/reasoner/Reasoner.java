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

import edu.umd.cs.psl.model.kernel.GroundKernel;

/**
 * Performs probablistic inference over {@link edu.umd.cs.psl.model.atom.Atom Atoms}
 * based on a set of {@link edu.umd.cs.psl.model.kernel.GroundKernel GroundKernels}.
 * 
 * A Reasoner infers the most probable values of atoms. The form of the probability
 * density function that is used is implementation specific.
 */
public interface Reasoner {
	
	/**
	 * Adds a {@link edu.umd.cs.psl.model.kernel.GroundKernel} to the set used
	 * for inference.
	 */
	public void addGroundKernel(GroundKernel gk);
	
	/**
	 * Updates a {@link edu.umd.cs.psl.model.kernel.GroundKernel} in the
	 * set used for inference.
	 *
	 * This method should be called if a {@link edu.umd.cs.psl.model.kernel.GroundKernel}
	 * that has already been added is modified.
	 */
	public void updateGroundKernel(GroundKernel gk);
	
	/**
	 * Removes a {@link edu.umd.cs.psl.model.kernel.GroundKernel} from the set used
	 * for inference.
	 */
	public void removeGroundKernel(GroundKernel gk);
	
	/**
	 * Checks whether a {@link edu.umd.cs.psl.model.kernel.GroundKernel} is in the set
	 * used for inference.
	 */
	public boolean containsGroundKernel(GroundKernel gk);
	
	/**
	 * Infers the most probable values of atoms according to a probability distribution
	 * constructed from the set of {@link edu.umd.cs.psl.model.kernel.GroundKernel GroundKernels}.
	 *
	 * The form of the probability density function is implementation specific.
	 */
	public void mapInference();
	
	/**
	 * Stops any listening for {@link edu.umd.cs.psl.model.atom.Atom} lifecycle events
	 * and releases any resources that have been accquired.
	 *
	 * This method allows the Reasoner to stop maintaining an accurate probability distribution
	 * relative to the current states of Atoms and should only be called when the Reasoner
	 * will no longer be used.
	 */
	public void close();
}

