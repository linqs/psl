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
package edu.umd.cs.psl.application.groundkernelstore;

import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;

/**
 * Container for a set of {@link GroundKernel GroundKernels}.
 * <p>
 * Since this container uses set semantics, no two GroundKernels that are equal
 * can be stored in it. If a {@link Kernel} wants to add another GroundKernel
 * that does the same thing over the same GroundAtoms, then it should retrieve
 * the original GroundKernel, modify it, and call {@link #changedGroundKernel(GroundKernel)}.
 */
public interface GroundKernelStore {

	/**
	 * Adds a GroundKernel to this store.
	 * 
	 * @param gk  the GroundKernel to add
	 * @throws IllegalArgumentException  if gk is already in this store
	 */
	public void addGroundKernel(GroundKernel gk);
	
	/**
	 * Notifies this store that a GroundKernel was changed.
	 * <p>
	 * Any component that modifies a GroundKernel in this store should call
	 * this method.
	 * 
	 * @param gk  the changed GroundKernel
	 * @throws IllegalArgumentException  if gk is not in this store
	 */
	public void changedGroundKernel(GroundKernel gk);
	
	/**
	 * Notifies this store that a {@link GroundCompatibilityKernel}'s weight
	 * was changed.
	 * <p>
	 * This method should be called whenever the weight of a GroundCompatibilityKernel
	 * in this store is changed, or the weight of its parent {@link CompatibilityKernel}
	 * is changed (and the GroundCompatibilityKernel's weight is still tied to it).
	 * <p>
	 * It is not necessary to also call {@link #changedGroundKernel(GroundKernel)}
	 * if only the weight was changed.
	 * 
	 * @param gk  the ground kernel with a changed weight
	 */
	public void changedGroundKernelWeight(GroundCompatibilityKernel gk);
	
	/**
	 * Equivalent to calling {@link #changedGroundKernelWeight(GroundCompatibilityKernel)}
	 * for all GroundCompatibilityKernels.
	 */
	public void changedGroundKernelWeights();
	
	/**
	 * Removes a GroundKernel from this store.
	 * 
	 * @param gk  the GroundKernel to remove
	 * @throws IllegalArgumentException  if gk is not in this store
	 */
	public void removeGroundKernel(GroundKernel gk);
	
	/**
	 * Checks whether a GroundKernel is in this store.
	 * 
	 * @param gk  the GroundKernel to check
	 * @return TRUE if gk is in this store
	 */
	public boolean containsGroundKernel(GroundKernel gk);
	
	/**
	 * Retrieves the GroundKernel equal to a given one from this store.
	 *  
	 * @param gk  the GroundKernel to match
	 * @return the GroundKernel in this store, or NULL if not present
	 * @see #changedGroundKernel(GroundKernel)
	 */
	public GroundKernel getGroundKernel(GroundKernel gk);
	
	/**
	 * @return every GroundKernel in this store
	 */
	public Iterable<GroundKernel> getGroundKernels();
	
	/**
	 * @return every {@link GroundCompatibilityKernel} in this store
	 */
	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels();
	
	/**
	 * @return every {@link GroundConstraintKernel} in this store
	 */
	public Iterable<GroundConstraintKernel> getConstraintKernels();
	
	/**
	 * Returns every GroundKernel that was instantiated by a given Kernel.
	 * 
	 * @param k  the Kernel of the GroundKernels to return
	 * @return the Kernel's GroundKernels
	 */
	public Iterable<GroundKernel> getGroundKernels(Kernel k);
	
	/**
	 * @return the number of GroundKernels in this store
	 */
	public int size();
	
}
