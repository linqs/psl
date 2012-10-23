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

/**
 * An event related to a {@link RandomVariableAtom}.
 * <p>
 * An AtomEvent provides two pieces of information in addition to the type of
 * event: the {@link RandomVariableAtom} that caused the event and the
 * {@link AtomEventFramework} that created the event.
 */
public enum AtomEvent {
	
	/** A {@link RandomVariableAtom} was instantiated in memory */
	ConsideredRVAtom,
	
	/** A {@link RandomVariableAtom} was activated */
	ActivatedRVAtom;
	
	/** A listener for AtomEvents. */
	public interface Listener {
		/**
		 * Notifies this object of an AtomEvent.
		 * 
		 * @param event  event information
		 */
		public void notifyAtomEvent(AtomEvent event);
	}
	
	private RandomVariableAtom atom;
	
	private AtomEventFramework eventFramework;
	
	private AtomEvent() {
		atom = null;
		eventFramework = null;
	}
	
	/**
	 * @return the associated Atom, or null if none is associated.
	 */
	public RandomVariableAtom getAtom() {
		return atom;
	}
	
	/**
	 * Associates an Atom with this event.
	 * 
	 * @param atom  the Atom to associate
	 * @return this event, for convenience
	 */
	public AtomEvent setAtom(RandomVariableAtom atom) {
		this.atom = atom;
		return this;
	}
	
	/**
	 * @return the associated AtomEventFramework, or null if none is associated.
	 */
	public AtomEventFramework getEventFramework() {
		return eventFramework;
	}
	
	/**
	 * Associates an AtomEventFramework with this event.
	 * 
	 * @param app  the AtomEventFramework to associate
	 * @return this event, for convenience
	 */
	public AtomEvent setEventFramework(AtomEventFramework eventFramework) {
		this.eventFramework = eventFramework;
		return this;
	}
	
}
