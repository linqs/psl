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

import java.util.Arrays;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * Partial implementation of {@link Atom}.
 */
abstract public class AbstractAtom implements Atom {
	
	protected final Predicate predicate;
	
	protected final Term[] arguments;
	
	protected final int hashcode;

	protected AtomStatus status;

	protected AbstractAtom(Predicate p, Term[] args, AtomStatus status) {
		predicate = p;
		arguments = args;
		hashcode = new HashCodeBuilder().append(predicate).append(arguments).toHashCode();
		this.status = status;
		checkSchema();
	}
	
	/**
	 * Verifies that this atom has valid arguments.
	 * 
	 * @throws IllegalArgumentException 
	 *             if the number of arguments doesn't match the number of arguments
	 *             of the predicate
	 * @throws IllegalArgumentException  if any argument is null
	 * @throws IllegalArgumentException
	 *             if any argument is a {@link GroundTerm} and is not a subtype
	 *             of the Predicate's {@link ArgumentType}.
	 */
	private void checkSchema() {
		if (predicate.getArity()!=arguments.length) {
			throw new IllegalArgumentException("Length of Schema does not match the number of arguments.");
		}
		for (int i=0;i<arguments.length;i++) {
			if (arguments[i]==null)
				throw new IllegalArgumentException("Arguments must not be null!");
			if ((arguments[i] instanceof GroundTerm) && !((GroundTerm)arguments[i]).getType().isSubTypeOf(predicate.getArgumentType(i)))
				throw new IllegalArgumentException("Expected type "+predicate.getArgumentType(i)+" at position "+i+" but was given: " + arguments[i] + " for predicate " + predicate);
		}
	}
	
	@Override
	public Formula getDNF() {
		return this;
	}
	
	@Override
	public Predicate getPredicate() {
		return predicate;
	}
	
	@Override
	public int getArity() {
		return predicate.getArity();
	}
	
	@Override
	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		for (int i=0;i<arguments.length;i++) {
			if (arguments[i] instanceof Variable) {
				ArgumentType t = predicate.getArgumentType(i);
				varMap.addVariable((Variable)arguments[i], t);
			}
		}
		return varMap;
	}
	
	@Override
	public Term[] getArguments() {
		return arguments;
	}
	
	@Override
	public Set<Atom> getAtoms(Set<Atom> atoms) {
		atoms.add(this);
		return atoms;
	}
	
	@Override 
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(predicate.getName()).append("(");
		String connector = "";
		for (Term arg : arguments) {
			s.append(connector).append(arg);
			connector = ", ";
		}
		s.append(")");
		return s.toString();
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		AbstractAtom other = (AbstractAtom) oth;
		return predicate.equals(other.predicate) && Arrays.deepEquals(arguments, other.arguments);  
	}

	@Override
	public AtomStatus getStatus() {
		return status;
	}
}
