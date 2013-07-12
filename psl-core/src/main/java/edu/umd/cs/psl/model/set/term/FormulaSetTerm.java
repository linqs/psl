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
package edu.umd.cs.psl.model.set.term;

import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.formula.Formula;

import java.util.*;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.collect.ImmutableSet;

public class FormulaSetTerm implements BasicSetTerm {

	private final Formula identificationFormula;
	
	private final VariableTypeMap anchorVariables;
	private final Variable leafVar;
	private final ArgumentType leafType;
	
	private final int hashcode;
	
	public FormulaSetTerm(Formula f, Variable leaf, Set<Variable> anchorVars) {
		assert f!=null;
		identificationFormula = f;
		leafVar = leaf;
		
		//Determine free variables
		VariableTypeMap variables = new VariableTypeMap();
		identificationFormula.collectVariables(variables);
		
		leafType = variables.getType(leafVar);
	
		anchorVariables = new VariableTypeMap();
		for (Variable var : anchorVars) {
			if (!variables.containsKey(var)) throw new IllegalArgumentException("Anchor variable does not occur in formula: "+var);
			anchorVariables.addVariable(var, variables.get(var));
		}
	
		hashcode = new HashCodeBuilder().append(identificationFormula).append(leafVar).append(anchorVariables.keySet()).toHashCode();
	}
	
	@Override
	public Formula getFormula() {
		return identificationFormula;
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(identificationFormula.toString());
		s.append("[");
		s.append(leafVar);
		return s.append("]").toString();
	}

	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public VariableTypeMap getAnchorVariables(VariableTypeMap varMap) {
		varMap.addAll(anchorVariables);
		return varMap;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		FormulaSetTerm is = (FormulaSetTerm)oth;
		return identificationFormula.equals(is.identificationFormula) && leafVar.equals(is.leafVar) && anchorVariables.keySet().equals(is.anchorVariables.keySet()); 
	}

	@Override
	public int getArity() {
		return 1;
	}

	@Override
	public ArgumentType getLeafType() {
		return leafType;
	}
	
	@Override
	public Term getLeaf() {
		return leafVar;
	}

	@Override
	public Set<BasicSetTerm> getBasicTerms() {
		return ImmutableSet.of((BasicSetTerm)this);
	}

	
}
