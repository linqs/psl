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

import edu.umd.cs.psl.model.argument.GroundTerm;

/**
 * A status of an {@link Atom}.
 */
public enum AtomStatus {
	
	/**
	 * The {@link Atom} is not ground, i.e., not all of its arguments are
	 * {@link GroundTerm GroundTerms}
	 **/
	Template,
	
	/**
	 * The {@link Atom} is not fully defined. Possible reasons an Atom would be
	 * assigned this status include: not all of the Atom's arguments are specified,
	 * it's truth value is not specified, etc.
	 */
	Undefined,
	
	/**
	 * The {@link Atom} is unconsidered.
	 * 
	 * An unconsidered Atom does not appear in its {@link AtomManager}'s set of explicitly
	 * represented Atoms.
	 */
	Unconsidered,
	
	/**
	 * The {@link Atom} is considered and observed.
	 * <p>
	 * A considered Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms.
	 * <p>
	 * An observed Atom has a known truth value. No other truth value can be assigned
	 * and the Atom's confidence value is infinity.
	 */
	ConsideredObserved,
	
	/**
	 * The {@link Atom} is active and observed.
	 * <p>
	 * An active Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms and, further, is able to be queried.
	 * <p>
	 * An observed Atom has a known truth value. No other truth value can be assigned
	 * and the Atom's confidence value is infinity.
	 */
	ActiveObserved,
	
	/**
	 * The {@link Atom} is considered and a random variable.
	 * 
	 * A considered Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms.
	 * 
	 * A fixed Atom will generate an event whenever its truth value or confidence
	 * value is changed.
	 */
	ConsideredFixed, 
	
	/**
	 * The {@link Atom} is active and a random variable.
	 * 
	 * An active Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms and, further, is able to be queried.
	 * 
	 * A fixed Atom will generate an event whenever its truth value or confidence
	 * value is changed.
	 */
	ActiveFixed,
	
	/**
	 * The {@link Atom} is considered and a random variable.
	 * 
	 * A considered Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms.
	 * 
	 * A random-variable Atom can have its truth value or confidence value
	 * changed without generating an event.
	 */
	ConsideredRV, 
	
	/**
	 * The {@link Atom} is active and a random variable.
	 * 
	 * An active Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms and, further, is able to be queried.
	 * 
	 * A random-variable Atom can have its truth value or confidence value
	 * changed without generating an event.
	 */
	ActiveRV;
	
	/**
	 * Returns the corresponding active status.
	 * 
	 * @return the status's corresponding active status
	 * @throws UnsupportedOperationException  if this status is not a considered-type status
	 */
	public AtomStatus activate() {
		switch(this) {
		case ConsideredObserved:
			return ActiveObserved;
		case ConsideredFixed:
			return ActiveFixed;
		case ConsideredRV:
			return ActiveRV;
		default:
			throw new UnsupportedOperationException("Cannot activate status: " + this);
		}
	}
	
	/**
	 * Returns the corresponding considered status.
	 * 
	 * @return the status's corresponding considered status
	 * @throws UnsupportedOperationException  if this status is not an active-type status
	 */
	public AtomStatus deactivate() {
		switch(this) {
		case ActiveObserved:
			return ConsideredObserved;
		case ActiveFixed:
			return ConsideredFixed;
		case ActiveRV:
			return ConsideredRV;
		default:
			throw new UnsupportedOperationException("Cannot deactivate status: " + this);
		}
	}
	
	/**
	 * Returns whether this status is an observed-type status
	 * 
	 * @return true if this status is an observed-type status
	 * @see AtomStatusSets#Observed
	 */
	public boolean isObserved() {
		return AtomStatusSets.Observed.contains(this);
	}
	
	/**
	 * Returns whether this status is a fixed-type status
	 * 
	 * @return true if this status is a fixed-type status
	 * @see AtomStatusSets#Fixed
	 */
	public boolean isFixed() {
		return AtomStatusSets.Fixed.contains(this);
	}

	/**
	 * Returns whether this status is a random-variable-type status
	 * 
	 * @return true if this status is a random-variable-type status
	 * @see AtomStatusSets#RandomVariable
	 */
	public boolean isRandomVariable() {
		return AtomStatusSets.RandomVariable.contains(this);
	}
	
	/**
	 * Returns whether this status is an unconsidered-type status
	 * 
	 * @return true if this status is an unconsidered-type status
	 * @see AtomStatusSets#Unconsidered
	 */
	public boolean isUnconsidered() {
		return AtomStatusSets.Unconsidered.contains(this);
	}
	
	/**
	 * Returns whether this status is a considered-type status
	 * 
	 * @return true if this status is a considered-type status
	 * @see AtomStatusSets#Considered
	 */
	public boolean isConsidered() {
		return AtomStatusSets.Considered.contains(this);
	}
	
	/**
	 * Returns whether this status is an active-type status
	 * 
	 * @return true if this status is an active-type status
	 * @see AtomStatusSets#Active
	 */
	public boolean isActive() {
		return AtomStatusSets.Active.contains(this);
	}
	
	/**
	 * Returns whether this status is an active or considered-type status
	 * 
	 * @return true if this status is an active or considered-type status
	 * @see AtomStatusSets#ActiveOrConsidered
	 */
	public boolean isActiveOrConsidered() {
		return AtomStatusSets.ActiveOrConsidered.contains(this);
	}
	
	/**
	 * Returns whether this status is neither {@value #Template} nor {@value #Undefined}.
	 * 
	 * @return true if this status is neither {@value #Template} nor {@value #Undefined}
	 * @see AtomStatusSets#DefinedAndGround
	 */
	public boolean isDefinedAndGround() {
		return AtomStatusSets.DefinedAndGround.contains(this);
	}
	
}
