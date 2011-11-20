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

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.database.DatabaseEventObserver;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * The event framework for managing observers (listeners) and managers.
 *   
 * @author
 *
 */
public interface AtomEventFramework extends AtomManager, DatabaseEventObserver, ModelEvent.Listener {

	/**
	 * Determines when an atom is activated.
	 *   NonDefault: whenever it crosses the activation threshold
	 *   All: any event
	 */
	public static enum ActivationMode { NonDefault, All }
	public static final ActivationMode defaultActivationMode = ActivationMode.NonDefault;
	
	/**
	 * Unregisters an observer for a given set of atom events.
	 * 
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void unregisterAtomEventObserver(AtomEventSets event, AtomEventObserver me);
	
	/**
	 * Unregisters an observer for a given predicate and set of atom events.
	 * 
	 * @param p A predicate
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void unregisterAtomEventObserver(Predicate p, AtomEventSets event, AtomEventObserver me);
	
	/**
	 * Registers an observer for a given set of atom events.
	 * 
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void registerAtomEventObserver(AtomEventSets event, AtomEventObserver me);
	
	/**
	 * Registers an observer for a given predicate and set of atom events.
	 * 
	 * @param p A predicate
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void registerAtomEventObserver(Predicate p, AtomEventSets event, AtomEventObserver me);
	
	public void workOffJobQueue();
	
	public boolean activateAtom(Atom atom);
	
	public boolean deactivateAtom(Atom atom);
	
	public int checkToActivate();
	
	public void setGroundingMode(GroundingMode mode);
	
}
