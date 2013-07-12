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

import java.util.*;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.formula.Formula;

public class ConstantSetTerm implements BasicSetTerm {

	private final GroundTerm element;
	private final int hashcode;
	
	public ConstantSetTerm(GroundTerm el) {
		if (el==null) throw new IllegalArgumentException("Set cannot be empty");
		element = el;
		hashcode = new HashCodeBuilder().append(element).toHashCode();
	}

	@Override
	public VariableTypeMap getAnchorVariables(VariableTypeMap varMap) {
		return varMap;	
	}
	
	@Override
	public Formula getFormula() {
		return null;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("{");
		s.append(element);
		return s.append("}").toString();
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		ConstantSetTerm s = (ConstantSetTerm)oth;
		return element.equals(s.element);
	}

	@Override
	public int getArity() {
		//For now we assume that the set only contains singleton elements
		return 1;
	}

	@Override
	public ArgumentType getLeafType() {
		return ArgumentType.getType(element);
	}

	@Override
	public Term getLeaf() {
		return element;
	}

	@Override
	public Set<BasicSetTerm> getBasicTerms() {
		return ImmutableSet.of((BasicSetTerm)this);
	}
	
	
	
}
