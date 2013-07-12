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

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.formula.Formula;

public class VariableSetTerm implements BasicSetTerm {

	private final Variable var;
	private final ArgumentType varType;
	private final int hashcode;
	
	public VariableSetTerm(Variable v, ArgumentType vtype) {
		var = v;
		varType = vtype;
		hashcode = var.hashCode()*233;
	}
	
	@Override
	public VariableTypeMap getAnchorVariables(VariableTypeMap varMap) {
		varMap.addVariable(var, varType);
		return varMap;
	}

	@Override
	public int getArity() {
		return 1;
	}
	
	@Override
	public Formula getFormula() {
		return null;
	}

	@Override
	public Set<BasicSetTerm> getBasicTerms() {
		return ImmutableSet.of((BasicSetTerm)this);
	}

	@Override
	public ArgumentType getLeafType() {
		return varType;
	}

	@Override
	public Term getLeaf() {
		return var;
	}
	
	@Override
	public String toString() {
		return var.toString() + "[" +varType + "]";
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}

}
