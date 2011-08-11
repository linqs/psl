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
 * The standard interface for predicate arguments
 * @author Matthias Broecheler
 *
 */
public interface Term {
	
	/**
	 * The standard toString function
	 * @return A string representing the predicate argument
	 */
	public String toString();
	
	/**
	 * Whether the predicate argument is ground, i.e not a variable
	 * @return
	 */
	public boolean isGround();
	
	/**
	 * Standard Hashcode
	 * @return hashcode
	 */
	public int hashCode();
	
	/**
	 * Standard equals
	 * @param oth Object to compare to
	 * @return TRUE if equal, FALSE otherwise
	 */
	public boolean equals(Object oth);

}
