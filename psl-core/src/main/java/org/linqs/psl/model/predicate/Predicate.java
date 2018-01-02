/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.model.predicate;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Term;

/**
 * A relation that can be applied to {@link Term Terms} to form {@link Atom Atoms}.
 * <p>
 * Predicates must be constructed using the {@link PredicateFactory}.
 * <p>
 * A Predicate is uniquely identified by its name.
 *
 * @author Matthias Broecheler
 */
public abstract class Predicate {
	private final String predicateName;

	private final ConstantType[] types;

	/**
	 * Sole constructor.
	 * <p>
	 * Should only be called by {@link PredicateFactory}.
	 *
	 * @param name  name for this predicate
	 * @param types  types for each of the predicate's arguments
	 */
	Predicate(String name, ConstantType[] types) {
		this.types = types;
		predicateName = name.toUpperCase();
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
	public ConstantType getArgumentType(int position) {
		return types[position];
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(getName()).append("(");
		for (int i=0;i<types.length;i++) {
			if (i>0) s.append(", ");
			s.append(types[i]);
		}
		return s.append(")").toString();
	}

	@Override
	public int hashCode() {
		/*
		 * The PredicateFactory ensures that names and signatures uniquely identify Predicates.
		 * Hence, equality of Predicates can be determined by identity;
		 */
		return super.hashCode();
	}

	@Override
	public boolean equals(Object oth) {
		/*
		 * The PredicateFactory ensures that names and signatures uniquely identify Predicates.
		 * Hence, equality of Predicates can be determined by identity;
		 */
		return oth == this;
	}
}
