/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.model.atom;

import java.util.*;

import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

/**
 * Maintains a mapping from {@link Variable Variables} to {@link Constant GroundTerms},
 * i.e. a variable substitution.
 *
 * Used to maintain a grounding of a {@link Formula}.
 *
 * @author Matthias Broecheler
 */
public class VariableAssignment extends HashMap<Variable, Constant> {

	private static final long serialVersionUID = 6358039563542554044L;

	/**
	 * Constructor parameterized by an initial size.
	 *
	 * @param size An integer size
	 */
	public VariableAssignment(int size) {
		super(size);
	}

	/**
	 * Default constructor with size=4.
	 */
	public VariableAssignment() {
		this(4);
	}

	/**
	 * This constructor is only used internally for cloning.
	 * @param vars Assignment of cloned variable assignment
	 */
	private VariableAssignment(VariableAssignment vars) {
		this();
		putAll(vars);
	}

	/**
	 * Assigns variable var the ground argument arg
	 * @param var variable
	 * @param arg assigned ground argument
	 */
	public void assign(Variable var, Constant arg) {
		put(var, arg);
	}

	/**
	 * Returns all variables.
	 *
	 * @return A set of all variables
	 */
	public Set<Variable> getVariables() {
		return keySet();
	}

	/**
	 * Removes the ground term for the given variable.
	 * NOTE: Variable must be already be assigned.
	 *
	 * @param var A variable
	 */
	public void releaseVariable(Variable var) {
		assert containsKey(var) : "Variable is not known in assignment: " + var;
		remove(var);
	}

	/**
	 * Assigns the given variable/ground term pair to a copy of this variable assignment.
	 *
	 * @param var A variable
	 * @param arg A ground term
	 * @return A copy of this variable assignment, with the addition of (var, arg)
	 */
	public VariableAssignment copyAssign(Variable var, Constant arg) {
		VariableAssignment copy = this.copy();
		copy.assign(var, arg);
		return copy;
	}

	/**
	 * Returns the ground term for a given variable.
	 *
	 * @param var A variable
	 * @return The gound term associated with var
	 */
	public Constant getVariable(Variable var) {
		if (!containsKey(var)) throw new IllegalArgumentException("Variable has not been set!");
		return get(var);
	}

	/**
	 * Returns whether the given variable is assigned in this assignment.
	 *
	 * @param var A variable
	 * @return TRUE if assigned; FALSE otherwise
	 */
	public boolean hasVariable(Variable var) {
		return containsKey(var);
	}

	/**
	 * Returns a shallow copy.
	 * @return A shallow copy of this variable assignment
	 */
	public VariableAssignment copy() {
		return new VariableAssignment(this);
	}
}
