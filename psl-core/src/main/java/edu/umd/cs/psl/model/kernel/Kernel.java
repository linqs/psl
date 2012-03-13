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
package edu.umd.cs.psl.model.kernel;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.parameters.Parameters;

/**
 * A template for a function that either constrains or measures
 * the compatibility of the values of {@link Atom Atoms}.
 * 
 * A Kernel is responsible for creating {@link GroundKernel GroundKernels} both
 * upon initial grounding and when triggered by changes to individual Atoms.
 * Kernels must register their GroundKernels with the corresponding
 * {@link ModelApplication ModelApplications}.
 * 
 * @author Matthias Broecheler <mail@knowledgefrominformation.com>
 */
public interface Kernel extends AtomEvent.Listener, Cloneable {

	/**
	 * Registers this Kernel to listen for the {@link AtomEvent AtomEvents}
	 * it needs to create {@link GroundKernel GroundKernels} .
	 * 
	 * @param manager  the AtomManager to register with
	 */
	public void registerForAtomEvents(AtomManager manager);
	
	/**
	 * Unregisters this Kernel from listening for {@link AtomEvent AtomEvents}
	 * with an AtomManager
	 * 
	 * @param manager  the AtomManager to unregister with
	 */
	public void unregisterForAtomEvents(AtomManager manager);
	
	public void groundAll(ModelApplication app);
	
	public Parameters getParameters();
	
	public void setParameters(Parameters para);
	
	public boolean isCompatibilityKernel();
	
	public Kernel clone() throws CloneNotSupportedException;
	
}
