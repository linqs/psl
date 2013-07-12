/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
 * A variable {@link Term}.
 * <p>
 * Variables are wildcards used to match {@link GroundTerm GroundTerms}.
 * 
 * @author Matthias Broecheler
 */
public class Variable implements Term {

	private final String name;
	
	/**
	 * Constructs a Variable, given a name.
	 * 
	 * @param name A string ID
	 */
	public Variable(String name) {
		if (!name.matches("^[a-zA-Z]\\w*"))
			throw new IllegalArgumentException("Variable name must begin with a-z or A-Z and contain only [a-zA-Z0-9_]. Invalid name: " + name);
		this.name = name;
	}
	
	/**
	 * @return the Variable's name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return {@link #getName()}
	 */
	@Override
	public String toString() {
		return getName();
	}
	
	@Override
	public int hashCode() {
		return name.hashCode() * 1163;
	}
	
	/**
	 * Checks equality with another object.
	 * 
	 * @param oth  Object to check for equality
	 * @return TRUE if oth is a Variable with the same name
	 */
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(oth instanceof Variable)) return false;
		return getName().equals(((Variable)oth).getName());  
	}	
}
