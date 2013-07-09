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
package edu.umd.cs.psl.model.formula;

import java.util.*;

import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;

/**
 * A logical formula composed of {@link Atom Atoms} and logical operators.
 * 
 * @author Matthias Broecheler
 */
public interface Formula {
	
	/**
	 * @return a logically equivalent Formula in disjunctive normal form
	 */
	public Formula getDNF();
	
	/**
	 * @return Atoms in the Formula
	 */
	public Set<Atom> getAtoms(Set<Atom> atoms);
	
	/**
	 * Adds the {@link Variable Variables}
	 * 
	 * @param varMap
	 * @return
	 */
	public VariableTypeMap collectVariables(VariableTypeMap varMap);
	
}
