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
package edu.umd.cs.psl.model.kernel;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.model.NumericUtilities;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.parameters.Parameters;

/**
 * A template for functions that either constrain or measure the compatibility
 * of the truth values of {@link GroundAtom GroundAtoms}.
 * <p>
 * A Kernel is responsible for instantiating {@link GroundKernel GroundKernels}.
 * A Kernel must instantiate only {@link GroundCompatibilityKernel}s or only
 * {@link GroundConstraintKernel}s.
 * 
 * @author Matthias Broecheler <mail@knowledgefrominformation.com>
 * @author Eric Norris <enorris@cs.umd.edu>
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public interface Kernel extends AtomEvent.Listener, Cloneable {

	/**
	 * Adds all missing, potentially unsatisfied {@link GroundKernel GroundKernels}
	 * to a {@link GroundKernelStore} based on an {@link AtomManager}.
	 * <p>
	 * Specifically, will add any GroundKernel templated by this Kernel
	 * that satisfies all the following conditions:
	 * <ul>
	 *   <li>The GroundKernel has incompatibility or infeasibility
	 *   greater than {@link NumericUtilities#strictEpsilon}
	 *   for some assignment of truth values to the {@link RandomVariableAtom}s
	 *   <em>currently persisted</em> in the AtomManager's Database given the truth
	 *   values of the {@link ObservedAtom}s and assuming that any RandomVariableAtom
	 *   not persisted has a truth value of 0.0.</li>
	 *   <li>The GroundKernel is not already in the GroundKernelStore.</li>
	 *   <li>If the Ground Kernel is a {@link GroundCompatibilityKernel}, its
	 *       incompatibility is not constant with respect to the truth values
	 *       of RandomVariableAtoms (including those not persisted in the
	 *       AtomManager's Database).
	 *   </li>
	 * </ul>
	 * <p>
	 * Only GroundKernels which satisfy these conditions should be added.
	 * 
	 * @param atomManager  AtomManager on which to base the grounding
	 * @param gks          store for new GroundKernels
	 * @see GroundCompatibilityKernel#getIncompatibility()
	 * @see GroundConstraintKernel#getInfeasibility()
	 */
	public void groundAll(AtomManager atomManager, GroundKernelStore gks);
	
	/**
	 * Registers this Kernel to listen for the {@link AtomEvent AtomEvents}
	 * it needs to update a {@link GroundKernelStore}.
	 * <p>
	 * Specifically, this Kernel will register for AtomEvents and update the
	 * GroundKernelStore in response to AtomEvents. In response to an AtomEvent
	 * on a {@link RandomVariableAtom}, the GroundKernelStore must contain the
	 * GroundKernels that are functions of it which would have been added via
	 * {@link #groundAll(AtomManager, GroundKernelStore)} given the current state of
	 * the AtomEventFramework's Database and assuming that the RandomVariableAtom
	 * was also persisted in the Database.
	 * 
	 * @param eventFramework  AtomEventFramework to register with
	 * @param gks             GroundKernelStore to update in response to AtomEvents
	 */
	public void registerForAtomEvents(AtomEventFramework eventFramework, GroundKernelStore gks);
	
	/**
	 * Stops updating a {@link GroundKernelStore} in response to AtomEvents from
	 * an {@link AtomEventFramework} and unregisters with that AtomEventFramework
	 * if it no longer needs to listen for AtomEvents from it.
	 * 
	 * @param eventFramework  AtomEventFramework to unregister with
	 * @param gks             GroundKernelStore to stop updating
	 */
	public void unregisterForAtomEvents(AtomEventFramework eventFramework, GroundKernelStore gks);
	
	/**
	 * @return the parameterization of this Kernel
	 */
	public Parameters getParameters();
	
	/**
	 * Sets the Parameters of this Kernel.
	 * 
	 * @param para  the new parameterization
	 */
	public void setParameters(Parameters para);
	
	public Kernel clone() throws CloneNotSupportedException;
	
}
