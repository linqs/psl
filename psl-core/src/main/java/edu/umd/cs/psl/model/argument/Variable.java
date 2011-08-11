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
package edu.umd.cs.psl.model.argument;

/**
 * This class implements a variable predicate argument which is specified by the variable name.
 * @author Matthias Broecheler
 *
 */
public class Variable implements Term {

	private final String identifier;
	
	/**
	 * Constructs a variable, given a string ID.
	 * 
	 * @param id A string ID
	 */
	public Variable(String id) {
		identifier = id;
	}
	
	/**
	 * Returns the variable ID.
	 * 
	 * @return The variable ID
	 */
	public String getName() {
		return identifier;
	}

	/**
	 * Returns the variable ID
	 * 
	 * @return The variable ID
	 */
	@Override
	public String toString() {
		return identifier;
	}
	
	/**
	 * Returns FALSE, as a variable is not ground.
	 * 
	 * @return FALSE
	 */
	@Override
	public boolean isGround() {
		return false;
	}
	
	/**
	 * Returns the hash code.
	 * 
	 * @return The integer hash code
	 */
	@Override
	public int hashCode() {
		return identifier.hashCode() * 1163;
	}
	
	/**
	 * Determines equality with another object.
	 * 
	 * @return TRUE if equal, FALSE otherwise
	 */
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		return identifier.equals(((Variable)oth).identifier);  
	}	
}
