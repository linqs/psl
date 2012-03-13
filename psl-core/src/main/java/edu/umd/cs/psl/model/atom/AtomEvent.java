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
 * An event related to an {@link Atom}.
 */
public enum AtomEvent {

	/** A fixed Atom became explicitly represented by an {@link AtomManager} */
	ConsideredFixedAtom,
	
	/** A fixed Atom stopped being explicitly represented by an {@link AtomManager} */
	UnconsideredFixedAtom,
	
	/** The truth value of a fixed Atom was changed */
	ChangedTruthValueOfFixedAtom,
	
	/** A random variable Atom became explicitly represented by an {@link AtomManager} */
	ConsideredRVAtom,
	
	/** A random variable Atom stopped being explicitly represented by an {@link AtomManager} */
	UnconsideredRVAtom,
	
	/** A random variable Atom was activated */
	ActivatedRVAtom,
	
	/** A random variable Atom was deactivated */
	DeactivatedRVAtom;
	
	/** A listener for AtomEvents. */
	public interface Listener {
		/**
		 * Notifies this object of an AtomEvent.
		 * 
		 * @param event  event information
		 */
		public void notifyAtomEvent(AtomEvent event);
	}
	
	private Atom atom;
	
	private AtomEvent() {
		atom = null;
	}
	
	/**
	 * @return the associated Atom, or null if no Atom is associated.
	 */
	public Atom getAtom() {
		return atom;
	}
	
	/**
	 * Associates an Atom with this event.
	 * 
	 * @param atom  the Atom to associate
	 * @return this event, for convenience
	 */
	public AtomEvent setAtom(Atom atom) {
		this.atom = atom;
		return this;
	}
}
