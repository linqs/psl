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
package edu.umd.cs.psl.model.predicate;

import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.atom.Atom;

/**
 * A relation that can be applied to {@link Term Terms} to form {@link Atom Atoms}.
 * <p>
 * A Predicate is defined by its name and its signature, i.e. number and
 * types of its arguments. Predicates should be constructed using a
 * {@link PredicateFactory}. Different PredicateFactories can be used as
 * different namespaces for predicates to avoid name conflicts. Note that two
 * Predicates are only equal if they are the same object.
 * <p>
 * Names starting with '#' are not allowed. That prefix is reserved for
 * {@link SpecialPredicate SpecialPredicates}.
 * 
 * @author Matthias Broecheler
 */
abstract public class Predicate {
	
	private final String predicateName;
	
	private final ArgumentType[] types;
	
	/**
	 * Sole constructor.
	 * 
	 * @param name  name for this predicate
	 * @param types  types for each of the predicate's arguments
	 * @throws IllegalArgumentException  if name begins with '#'
	 */
	Predicate(String name, ArgumentType[] types) {
		if (name.startsWith("#"))
			throw new IllegalArgumentException("Predicate name must not begin with '#'." +
					" That prefix is reserved for SpecialPredicates.");
		this.types = types;
		predicateName = name;
	}

	/**
	 * Returns the name of this Predicate.
	 * 
	 * @return a string identifier for this Predicate
	 */
	public String getName() {
		return predicateName;
	}
	
	/**
	 * Returns the number of {@link Term Terms} that are related when using
	 * this Predicate.
	 * <p>
	 * In other words, the arity of a Predicate is the number of arguments it
	 * accepts. For example, the Predicate Related(A,B) has an arity of 2.
	 * 
	 * @return the arity of this Predicate
	 */
	public int getArity() {
		return types.length;
	}
	
	/**
	 * Returns the ArgumentType which a {@link Term} must have to be a valid
	 * argument for a particular argument position of this Predicate.
	 * 
	 * @param position  the argument position
	 * @return the type of argument accepted for the given position
	 */
	public ArgumentType getArgumentType(int position) {
		return types[position];
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(getName()).append("(");
		for (int i=0;i<types.length;i++) {
			if (i>0) s.append(", ");
			if (types[i]!=null) s.append(types[i]);
			else s.append("?");
		}
		return s.append(")").toString();
	}
	
	@Override
	public int hashCode() {
		/*
		 * The predicate factory ensures that names uniquely identify predicates within the same {@link PredicateFactory}.
		 * Hence, equality of predicates can be determined by identity;
		 */
		return super.hashCode();
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		else return false;
	}
	
}
