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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.parameters.Parameters;

/**
 * Allows implementing Kernels to avoid keeping track of which GroundKernelStore
 * to use when handling AtomEvents.
 * 
 * @author Eric Norris <enorris@cs.umd.edu>
 */
public abstract class AbstractKernel implements Kernel {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractKernel.class);
	
	protected SetMultimap<AtomEventFramework, GroundKernelStore> frameworks;
	
	protected AbstractKernel() {
		this.frameworks = HashMultimap.create();
	}
	
	@Override
	public void notifyAtomEvent(AtomEvent event) {
		for (GroundKernelStore gks : frameworks.get(event.getEventFramework()))
			notifyAtomEvent(event, gks);
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework eventFramework,
			GroundKernelStore gks) {
		if (!frameworks.containsKey(eventFramework)) {
			frameworks.put(eventFramework, gks);
			registerForAtomEvents(eventFramework);
		} else if (!frameworks.put(eventFramework, gks)) {
			log.debug("Attempted to register for AtomEventFramework that has" +
					" already been registered.");
		}
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework eventFramework,
			GroundKernelStore gks) {
		if (!frameworks.remove(eventFramework, gks))
			log.debug("Attempted to unregister with AtomEventFramework that is" +
					" not registered.");
		else if (!frameworks.containsKey(eventFramework))
			unregisterForAtomEvents(eventFramework);
	}
	
	
	@Override
	public Kernel clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
	
	@Override
	public Parameters getParameters() {
		return Parameters.NoParameters;
	}
	
	@Override
	public void setParameters(Parameters para) {
		throw new UnsupportedOperationException(this.getClass().getName() + " does not have parameters.");
	}
	
	/**
	 * Handles an AtomEvent using the specified GroundKernelStore.
	 * <p>
	 * Kernels need to have registered to handle this event with an
	 * AtomEventFramework.
	 * @param event		the AtomEvent that occurred
	 * @param gks		the GroundKernelStore to use
	 */
	protected abstract void notifyAtomEvent(AtomEvent event, GroundKernelStore gks);
	
	/**
	 * Registers with a specific AtomEventFramework to handle atom events.
	 * <p>
	 * Subclasses are expected to register for the same AtomEvents and Predicates
	 * at all times. Kernels that do not fit this behavior should not extend
	 * this class.
	 * @param eventFramework	The event framework to register with
	 */
	protected abstract void registerForAtomEvents(AtomEventFramework eventFramework);
	
	/**
	 * Unregisters from a specific AtomEventFrameWork to no longer handle atom events.
	 * <p>
	 * Subclasses are expected to have registered for the same AtomEvents and Predicates
	 * at all times. Kernels that do not fit this behavior should not extend this class.
	 * @param eventFramework	The event framework to unregister from
	 */
	protected abstract void unregisterForAtomEvents(AtomEventFramework eventFramework);
}
