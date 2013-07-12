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

import java.util.*;


/**
 * A hashed storage class for arguments, keyed on their associated variables.
 * 
 * This class extends the functionality of its parent class, {@link HashMap},
 * adding functionality specific to predicate arguments.
 * 
 * @author
 *
 */
public class VariableTypeMap extends HashMap<Variable,ArgumentType> {

	private static final long serialVersionUID = -6590175777602710989L;

	/**
	 * Adds a variable-type pair to the hashmap.
	 * 
	 * @param var A variable
	 * @param type An argument type
	 */
	public void addVariable(Variable var, ArgumentType type) {
		ArgumentType t = get(var);
		if (t!=null) {
			if (t!=type) throw new IllegalStateException("Variable has inconsistent type: " + var);
		} else put(var,type);
	}

	/**
	 * Returns all variables in the hashmap.
	 *  
	 * @return A set of variables
	 */
	public Set<Variable> getVariables() {
		return keySet();
	}
	
	/**
	 * Returns the type of a given variable.
	 * 
	 * @param var A variable
	 * @return The argument type of the given variable
	 */
	public ArgumentType getType(Variable var) {
		ArgumentType t = get(var);
		if (t==null) throw new IllegalArgumentException("Specified variable is unknown: "+var);
		return t;
	}
	
	/**
	 * Returns whether the given variable exists in the hashmap.
	 * 
	 * @param var A variable
	 * @return TRUE if exists; FALSE otherwise
	 */
	public boolean hasVariable(Variable var) {
		return containsKey(var);
	}
	
	/**
	 * Performs a shallow copy of all variable-type pairs from another VariableTypeMap to this one.
	 * 
	 * @param other Another VariableTypeMap
	 */
	public void addAll(VariableTypeMap other) {
		for (Map.Entry<Variable, ArgumentType> entry : other.entrySet()) {
			addVariable(entry.getKey(),entry.getValue());
		}
	}
	
}
