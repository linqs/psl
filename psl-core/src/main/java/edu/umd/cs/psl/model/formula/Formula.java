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
package edu.umd.cs.psl.model.formula;

import java.util.*;

import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;



/**
 * This is the top most interface for fuzzy formulas and defines the standard methods that
 * every fuzzy formula must provide
 * 
 * @author Matthias Broecheler
 *
 */

public interface Formula {
	
	/**
	 * Returns a logically equivalent formula in disjunctive normal form
	 */
	public Formula dnf();
	
	/**
	 * Returns a list of all non constant atoms contained in the formula
	 * @return list of all atoms contained in the formula
	 */
	public Collection<Atom> getAtoms(Collection<Atom> atoms);
	
	
	public VariableTypeMap getVariables(VariableTypeMap varMap);
	
	
	/** A standard set of methods that need to be overriden
	 */
	public String toString();
	public int hashCode();
	public boolean equals(Object oth);
	
}
