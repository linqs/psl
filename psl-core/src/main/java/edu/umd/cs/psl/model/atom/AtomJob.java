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
 * Encapsulates an {@link Atom} and a related {@link AtomEvent}.
 * 
 * AtomJobs are used to keep track of events which must be handled
 * by an {@link AtomManager}.
 */
public class AtomJob {
	
	private final Atom atom;
	private final AtomEvent event;
	
	/**
	 * Sole constructor.
	 * 
	 * Instantiates an AtomJob with an Atom and a related AtomEvent.
	 * 
	 * @param a  the Atom to which the AtomEvent relates
	 * @param j  the related AtomEvent
	 */
	public AtomJob(Atom a, AtomEvent j) {
		event = j;
		atom = a;
	}

	/**
	 * Returns the associated Atom.
	 * 
	 * @return the associated Atom
	 */
	public Atom getAtom() {
		return atom;
	}

	/**
	 * Returns the associated AtomEvent.
	 * 
	 * @return the associated AtomEvent
	 */
	public AtomEvent getEvent() {
		return event;
	}
	
}
