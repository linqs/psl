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

import edu.umd.cs.psl.model.argument.type.ArgumentType;

/**
 * General, abstract Predicate.
 * 
 * @author Matthias Broecheler
 */
abstract class AbstractPredicate implements Predicate {
	
	private final String predicateName;
	
	private final ArgumentType[] types;
	
	/**
	 * Sole constructor.
	 * 
	 * @param name  name for this predicate
	 * @param types  types for each of the predicate's arguments
	 * @throws IllegalArgumentException  if name begins with '#'
	 */
	AbstractPredicate(String name, ArgumentType[] types) {
		if (name.startsWith("#"))
			throw new IllegalArgumentException("Predicate name must not begin with '#'." +
					" That prefix is reserved for SpecialPredicates.");
		this.types = types;
		predicateName = name;
	}
	
	@Override
	public int getArity() {
		return types.length;
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(predicateName).append("(");
		for (int i=0;i<types.length;i++) {
			if (i>0) s.append(", ");
			if (types[i]!=null) s.append(types[i]);
			else s.append("?");
		}
		return s.append(")").toString();
	}
	
	@Override
	public ArgumentType getArgumentType(int position) {
		return types[position];
	}
	
	@Override
	public String getName() {
		return predicateName;
	}
	
	@Override
	public int hashCode() {
		/*
		 * The predicate factory ensures that names uniquely identify predicates within the same {@link PredicateFactory}.
		 * Hence, equality of predicates can be determined by identity;
		 */
		return super.hashCode();
		//return predicateName.hashCode()*17;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		else return false;
//		if (oth==null || !(getClass().isInstance(oth)) ) return false;
//		return predicateName.equals(((AbstractPredicate)oth).predicateName);  
	}
	
}
