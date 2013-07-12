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

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.collect.Sets;

import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.VariableTypeMap;

public class SetUnion implements SetTerm {

	private final SetTerm left;
	private final SetTerm right;
	
	public SetUnion(SetTerm t1, SetTerm t2) {
		left = t1;
		right = t2;
	}

	@Override
	public VariableTypeMap getAnchorVariables(VariableTypeMap varMap) {
		left.getAnchorVariables(varMap);
		right.getAnchorVariables(varMap);
		return varMap;
	}

	@Override
	public int getArity() {
		assert left.getArity()==right.getArity();
		return left.getArity();
	}

	@Override
	public Set<BasicSetTerm> getBasicTerms() {
		return Sets.union(left.getBasicTerms(), right.getBasicTerms());
	}

	@Override
	public ArgumentType getLeafType() {
		assert left.getLeafType()==right.getLeafType();
		return left.getLeafType();
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		SetUnion is = (SetUnion)oth;
		return (left.equals(is.left) && right.equals(is.right)) || (left.equals(is.right) && right.equals(is.left));
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(left.hashCode()+right.hashCode()).toHashCode();
	}
	
	@Override
	public String toString() {
		return "( " + left.toString() + " u " + right.toString() + ")";
	}

}
