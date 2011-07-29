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

import java.util.*;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Variable;

/**
 * The VariableAssignment is used in the grounding of formulas as it maintains a mapping from encountered
 * variables to ground objects, i.e. it maintains the current substitution.
 * 
 * Furthermore, the variable assignment stores the current grounding of the formula.
 * 
 * @author Matthias Broecheler
 *
 */
public class VariableAssignment extends HashMap<Variable, GroundTerm> {

	private static final long serialVersionUID = 6358039563542554044L;

	
	
	public VariableAssignment(int size) {
		super(size);
	}
	
	public VariableAssignment() {
		this(4);
	}

	/** This constructor is only used internally for cloning.
	 * @param ass Assignment of cloned variable assignment
	 */
	private VariableAssignment(VariableAssignment ass) {
		this();
		putAll(ass);
	}

	
	/**
	 * Assigns variable var the ground argument arg
	 * @param var variable
	 * @param arg assigned ground argument
	 */
	public void assign(Variable var, GroundTerm arg) {
//		if (assignment.containsKey(var))
//			throw new AssertionError("Cannot reassign variable: "+ var);
		put(var, arg);
	}
	
	public Set<Variable> getVariables() {
		return keySet();
	}
	
	public void releaseVariable(Variable var) {
		assert containsKey(var) : "Variable is not known in assignment: " + var;
		remove(var);
	}
	
	public VariableAssignment copyAssign(Variable var, GroundTerm arg) {
		VariableAssignment copy = this.copy();
		copy.assign(var, arg);
		return copy;
	}

	public GroundTerm getVariable(Variable var) {
		if (!containsKey(var)) throw new IllegalArgumentException("Variable has not been set!");
		return get(var);
	}
	
	public boolean hasVariable(Variable var) {
		return containsKey(var);
	}
	
	public VariableAssignment copy() {
		return new VariableAssignment(this);
	}
	
	
	
}
