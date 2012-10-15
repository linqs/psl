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

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * AtomManager with support for {@link AtomEvent AtomEvents}. 
 * <p>
 * An AtomEventFramework handles two types of events: <em>consideration</em> and
 * <em>activation</em>. Any GroundAtom is considered when it is loaded into memory
 * and added to its {@link Database}'s {@link AtomCache}. Further, a RandomVariableAtom
 * is activated when its truth value is at or above a threshold for the first time
 * since being loaded into memory (including being initialized above that threshold).
 * <p>
 * {@link AtomEvent.Listener} implementations can register to be notified of these events.
 * Listeners must register with a {@link GroundKernelStore} so that any GroundKernels
 * that result from the event can be added to it. A Listener can register with multiple
 * GroundKernelStores. Note that if a {@link GroundAtom} is not loaded into memory via
 * {@link #getAtom(Predicate, GroundTerm[])}, then no consideration event can be
 * generated.
 * <p>
 * For each event, an {@link AtomJob} is added to the job queue. Consideration
 * events are added during {@link #getAtom(Predicate, GroundTerm[])} and activation
 * events are added during {@link #checkToActivate()} and
 * {@link #activateAtom(RandomVariableAtom)}.
 * <p>
 * Calling {@link #workOffJobQueue()} will cause the appropriate Listeners to be
 * notified of all events in the queue. Additionally, each activated RandomVariableAtom
 * will be committed to the Database before any Listeners are notified.
 */
public class AtomEventFramework implements AtomManager {
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "atomeventframework";
	
	/**;
	 * Key for double property in [0,1]. Activation events will be generated
	 * for RandomVariableAtoms when they meet or exceed this threshold.
	 */
	public static final String ACTIVATION_THRESHOLD_KEY = CONFIG_PREFIX + ".activation";
	/** Default value for ACTIVATION_THRESHOLD_KEY property */
	public static final double ACTIVATION_THRESHOLD_DEFAULT = 0.01;
	
	private final Database db;
	private final double activationThreshold;
	
	public AtomEventFramework(Database db, ConfigBundle config) {
		this.db = db;
		activationThreshold = config.getDouble(ACTIVATION_THRESHOLD_KEY, ACTIVATION_THRESHOLD_DEFAULT);
	}
	
	/**
	 * Calls {@link Database#getAtom(Predicate, GroundTerm[])} and generates
	 * a {@link AtomEvent#ConsideredGroundAtom} event if the GroundAtom is
	 * not already in the Database's AtomCache. 
	 */
	@Override
	public GroundAtom getAtom(Predicate p, GroundTerm[] arguments) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	 * Registers a listener and {@link GroundKernelStore} for any events in a set.
	 * 
	 * @param events  set of events for which to listen
	 * @param listener  object to register
	 * @param gks  where any GroundKernels resulting from such an event will be stored
	 * @see AtomEvent
	 */
	public void registerAtomEventListener(Set<AtomEvent> events, AtomEvent.Listener listener,
			GroundKernelStore gks) {
		
	}
	
	/**
	 * Registers a listener and {@link GroundKernelStore} for any events in a set
	 * related to {@link GroundAtom GroundAtoms} with a given Predicate.
	 * 
	 * @param events  set of events for which to listen
	 * @param p  Predicate of Atoms for which to listen for events
	 * @param listener  object to register
	 * @param gks  where any GroundKernels resulting from such an event will be stored
	 * @see AtomEvent
	 */
	public void registerAtomEventListener(Set<AtomEvent> events, Predicate p, AtomEvent.Listener listener,
			GroundKernelStore gks) {
		
	}
	
	/**
	 * Unregisters a listener for any events in a set.
	 * <p>
	 * If the listener has registered using multiple GroundKernelStores, all of
	 * its registrations will be removed.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, AtomEvent.Listener listener) {
		
	}
	
	/**
	 * Unregisters a listener for any events in a set related to {@link GroundAtom GroundAtoms}
	 * with a given Predicate.
	 * <p>
	 * If the listener has registered using multiple GroundKernelStores, all of
	 * its registrations for the given Predicate will be removed.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param p  Predicate of Atoms for which to stop listening
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, Predicate p, AtomEvent.Listener listener) {
		
	}
	
	/**
	 * Unregisters a listener and GroundKernelStore pair for any events in a set.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param listener  listener to unregister
	 * @param gks  GroundKernelStore to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, AtomEvent.Listener listener,
			GroundKernelStore gks) {
		
	}
	
	/**
	 * Unregisters a listener and GroundKernelStore pair for any events in a set
	 * related to {@link GroundAtom GroundAtoms} with a given Predicate.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param p  Predicate of Atoms for which to stop listening
	 * @param listener  listener to unregister
	 * @param gks  GroundKernelStore to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, Predicate p, AtomEvent.Listener listener,
			GroundKernelStore gks) {
		
	}
	
	/**
	 * Activates all RandomVariableAtoms which are at or above
	 * the activation threshold for the first time since being loaded into memory
	 * (including being initialized above the threshold).
	 * 
	 * @return number of Atoms activated
	 * @see AtomManager#workOffJobQueue()
	 */
	public int checkToActivate() {
		return 0;
	}
	
	/**
	 * Activates a RandomVariableAtom, regardless of its truth value.
	 * <p>
	 * No event will be generated if the Atom has already been activated by
	 * this AtomEventFramework since being loaded into memory.
	 * 
	 * @param atom  GroundAtom to activate
	 * @see AtomManager#workOffJobQueue()
	 * @throws IllegalArgumentException  if atom does not belong to this
	 *                                       framework's Database
	 */
	public void activateAtom(RandomVariableAtom atom) {
		
	}
	
	/**
	 * Processes all pending {@link AtomJob AtomJobs}.
	 * <p>
	 * This method should be called after considering or activating Atoms
	 * so that listeners will be notified.
	 */
	public void workOffJobQueue() {
		
	}

	@Override
	public Database getDatabase() {
		return db;
	}
}
