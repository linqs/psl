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
	 * Neither the {@link Atom}'s truth value nor its confidence value can be modified.
	 * <p>
	 * The Atom was loaded from a read Partition of a {@link Database}.
	 */
	Observed,
	
	/**
	 * The {@link Atom}'s truth value and confidence value should be treated as
	 * constants, but can be modified, which will generate a {@link DatabaseEvent}
	 * and commit it.
	 * <p>
	 * The {@link Atom} has a closed {@link Predicate} and is stored in the
	 * write Partition of a {@link Database} (if committed).
	 */
	Fixed,
	
	/**
	 * The {@link Atom} can have its truth value or confidence value
	 * modified without generating an event.
	 * <p>
	 * The Atom has an open {@link Predicate} and is stored in the
	 * write Partition of a {@link Database} (if committed).
	 */
	RandomVariable,
	
	/**
	 * The {@link Atom} is not ground, i.e., not all of its arguments are
	 * {@link GroundTerm GroundTerms}
	 **/
	Template,
	
	/**
	 * The {@link Atom} is not fully defined. Possible reasons an Atom would be
	 * assigned this status include: not all of the Atom's arguments are specified,
	 * its truth value is not specified, etc.
	 */
	Undefined;
	
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
