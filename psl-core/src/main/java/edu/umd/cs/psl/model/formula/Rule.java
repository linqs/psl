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

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;

public class Rule implements Formula {

	/**
	 * The fuzzy singletons that constitute the body of the rule
	 */
	protected final Formula body;
	/**
	 * The fuzzy singleton that is the head of the rule
	 */
	protected final Formula head;
	
	//private final int hashcode;
	
	public Rule(Formula b, Formula h) {
		assert b!=null && h!=null;
		body = b;
		head = h;
	}


	public Formula getBody() {
		return body;
	}
	
	public Formula getHead() {
		return head;
	}
	
	@Override
	public Formula getDNF() {
		return new Disjunction(new Negation(body), head).getDNF();
	}
	
	@Override
	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		body.collectVariables(varMap);
		head.collectVariables(varMap);
		return varMap;
	}
	
	@Override
	public Set<Atom> getAtoms(Set<Atom> atoms) {
		body.getAtoms(atoms);
		head.getAtoms(atoms);
		return atoms;
		
	}	

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(body).append(head).toHashCode();
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		Rule of = (Rule)oth;
		return body.equals(of.body) && head.equals(of.head);
	}
	
	@Override
	public String toString() {
		return body.toString() + " >> " + head.toString();
	}



}
