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
package edu.umd.cs.psl.model.atom;

import java.util.Set;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * Event manager for a {@link ModelApplication} which uses {@link AtomEvent AtomEvents}. 
 * <p>
 * There are two main events in the lifecycle of a ground Atom: <em>consideration</em> and
 * <em>activation</em>.
 */
public class AtomEventFramework {
	
	/**
	 * Registers a listener for any events in a set.
	 * 
	 * @param events  set of events for which to listen
	 * @param listener  object to register
	 * @see AtomEvent
	 */
	public void registerAtomEventListener(Set<AtomEvent> events, AtomEvent.Listener listener) {
		
	}
	
	/**
	 * Registers a listener for any events in a set related to {@link Atom Atoms}
	 * with a given Predicate.
	 * 
	 * @param events  set of events for which to listen
	 * @param p  Predicate of Atoms for which to listen for events
	 * @param listener  object to register
	 * @see AtomEvent
	 */
	public void registerAtomEventListener(Set<AtomEvent> events, Predicate p, AtomEvent.Listener listener) {
		
	}
	
	/**
	 * Unregisters a listener for any events in a set.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, AtomEvent.Listener listener) {
		
	}
	
	/**
	 * Unregisters a listener for any events in a set related to {@link Atom Atoms}
	 * with a given Predicate.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param p  Predicate of Atoms for which to stop listening
	 * @param listener  object to unregister
	 * @throws IllegalArgumentException  if listener is not registered to listen for
	 *             a subset of AtomEvents in events for Predicate p
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, Predicate p, AtomEvent.Listener listener) {
		
	}
	
	/**
	 * Activates all (ground) {@link Atom Atoms} which meet this manager's
	 * activation criteria.
	 * 
	 * @return number of Atoms activated
	 * @see AtomManager#workOffJobQueue()
	 */
	public int runActivationStrategy() {
		return 0;
	}
	
	/**
	 * Activates an Atom.
	 * 
	 * @param atom  Atom to activate
	 * @see AtomManager#workOffJobQueue()
	 */
	public void activateAtom(Atom atom) {
		
	}
	
	/**
	 * Processes all pending {@link AtomJob AtomJobs}.
	 * <p>
	 * This method should be called after changing {@link Atom} values, activating
	 * Atoms, opening or closing {@link Predicate Predicates}, or any other
	 * operation which could change the {@link AtomStatus} of an Atom.
	 */
	public void workOffJobQueue() {
		
	}
}
