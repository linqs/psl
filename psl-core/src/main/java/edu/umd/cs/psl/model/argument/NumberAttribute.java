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

import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;

/**
 * A domain attribute for numerical values.
 * 
 * @author
 *
 */
public class NumberAttribute implements Attribute {

	private final double number;
	
	/**
	 * Constructs a numerical attribute.
	 * 
	 * @param no A numerical value
	 */
	public NumberAttribute(double no) {
		number = no;
	}
	
	/**
	 * Returns a string representation of the numerical value.
	 * 
	 * @return A string representation of the numerical value
	 */
	@Override
	public String toString() {
		return "'" + number + "'";
	}
	
	/**
	 * Returns the attribute value.
	 * 
	 * @return The attribute value
	 */
	@Override
	public Double getAttribute() {
		return number;
	}
	
	/**
	 * Returns TRUE, as an attribute is ground.
	 * 
	 * @return TRUE
	 */
	@Override
	public boolean isGround() {
		return true;
	}
	
	/**
	 * Returns the argument type.
	 * 
	 * @return {@link ArgumentType.Number}
	 */
	@Override
	public ArgumentType getType() {
		return ArgumentTypes.Number;
	}
	
	/**
	 * Returns the hash code.
	 * 
	 * @return The integer hash code
	 */
	@Override
	public int hashCode() {
		return (new Double(number)).hashCode();
	}
	
	/**
	 * Determines equality with another object.
	 * 
	 * @return true if equal; false otherwise
	 */
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		return number==((NumberAttribute)oth).number;  
	}

}
