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
import edu.umd.cs.psl.reasoner.Reasoner;

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
	 * The {@link Atom} is considered and fixed.
	 * 
	 * A considered Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms.
	 * 
	 * A fixed Atom has a known truth value. No other truth value can be assigned and the
	 * Atom's confidence value is infinity.
	 */
	ConsideredFixed,
	
	/**
	 * The {@link Atom} is considered and its truth value is a random variable.
	 * 
	 * A considered Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms.
	 * 
	 * An Atom with an unknown truth value is represented as a random variable.
	 * Its value will be inferred when using a {@link Reasoner}.
	 */
	ConsideredRV, 
	
	/**
	 * The {@link Atom} is considered, its truth value is a random variable,
	 * and its value under the current interpretation is sufficiently high
	 * that it should be considered to ground rules which contain it in
	 * their bodies.
	 * 
	 * A considered Atom appears in its {@link AtomManager}'s set of explicitly
	 * represented Atoms.
	 * 
	 * An Atom with an unknown truth value is represented as a random variable.
	 * Its value will be inferred when using a {@link Reasoner}.
	 */
	ActiveRV;
	
	/**
	 * Returns the {@value #ActiveRV} status.
	 * 
	 * @return the {@value #ActiveRV} status
	 * @throws UnsupportedOperationException  if this status is not {@value #ConsideredRV}
	 */
	public AtomStatus activate() {
		switch(this) {
		case ConsideredRV:
			return ActiveRV;
		default:
			throw new UnsupportedOperationException("Cannot activate on status: " + this);
		}
	}
	
	/**
	 * Returns the {@value #ConsideredRV} status.
	 * 
	 * @return the {@value #ConsideredRV} status
	 * @throws UnsupportedOperationException  if this status is not {@value #ActiveRV}
	 */
	public AtomStatus deactivate() {
		switch(this) {
		case ActiveRV:
			return ConsideredRV;
		default:
			throw new UnsupportedOperationException("Cannot deactivate on status: " + this);
		}
	}

	/**
	 * Returns whether this status is a random-variable-atom status
	 * 
	 * @return true if this status is a random=variable-atom status
	 * @see AtomStatusSets#RandomVariable
	 */
	public boolean isRandomVariable() {
		return AtomStatusSets.RandomVariable.contains(this);
	}
	
	/**
	 * Returns whether this status is a fixed-atom status
	 * 
	 * @return true if this status is a fixed-atom status
	 * @see AtomStatusSets#Fixed
	 */
	public boolean isFixed() {
		return AtomStatusSets.Fixed.contains(this);
	}
	
	/**
	 * Returns whether this status is an unconsidered-atom status
	 * 
	 * @return true if this status is an unconsidered-atom status
	 * @see AtomStatusSets#Unconsidered
	 */
	public boolean isUnconsidered() {
		return AtomStatusSets.Unconsidered.contains(this);
	}
	
	/**
	 * Returns whether this status is a considered-atom status
	 * 
	 * @return true if this status is a considered-atom status
	 * @see AtomStatusSets#Considered
	 */
	public boolean isConsidered() {
		return AtomStatusSets.Considered.contains(this);
	}
	
	/**
	 * Returns whether this status is an active-atom status
	 * 
	 * @return true if this status is an active-atom status
	 * @see AtomStatusSets#Active
	 */
	public boolean isActive() {
		return AtomStatusSets.Active.contains(this);
	}
	
	/**
	 * Returns whether this status is an active or considered-atom status
	 * 
	 * @return true if this status is an active or considered-atom status
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
