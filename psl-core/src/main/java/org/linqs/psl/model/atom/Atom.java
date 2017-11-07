/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.atom;

import java.util.Arrays;
import java.util.Set;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;

import com.healthmarketscience.sqlbuilder.BinaryCondition;

/**
 * A {@link Predicate} combined with the correct number of {@link Term Terms}
 * as arguments.
 *
 * Two Atoms are equal if their Predicate and arguments are equal. Note that this
 * means that their truth values might not match, or one might even be a
 * {@link QueryAtom}.
 */
public abstract class Atom implements Formula, SummationAtomOrAtom {
	protected final Predicate predicate;
	protected final Term[] arguments;
	protected final int hashcode;

	/**
	 * Type mismatches will throw an exception unless
	 * the types are trivially convertable like UniqueIntID and IntegerAttribute.
	 */
	protected Atom(Predicate p, Term[] args) {
		predicate = p;
		arguments = Arrays.copyOf(args, args.length);
		validate();
		hashcode = new HashCodeBuilder().append(predicate).append(arguments).toHashCode();
	}

	/**
	 * Returns the predicate associated with this Atom.
	 *
	 * @return A predicate
	 */
	public Predicate getPredicate() {
		return predicate;
	}

	/**
	 * Returns the number of arguments to the associated predicate.
	 *
	 * @return The number of arguments
	 */
	public int getArity() {
		return predicate.getArity();
	}

	/**
	 * Returns the arguments associated with this atom.
	 *
	 * @return The arguments associated with this atom
	 */
	public Term[] getArguments() {
		return arguments;
	}

	@Override
	public Formula getDNF() {
		return this;
	}

	@Override
	public Set<Atom> getAtoms(Set<Atom> atoms) {
		atoms.add(this);
		return atoms;
	}

	/**
	 * Verifies that this atom has valid arguments.
	 * This will also convert arguments of the incorrect type into the declared type if they are trivially similar
	 * (eg. StringAttribute and UniqueStringID).
	 *
	 * @throws IllegalArgumentException
	 *				 if the number of arguments doesn't match the number of arguments
	 *				 of the predicate
	 * @throws IllegalArgumentException  if any argument is null
	 * @throws IllegalArgumentException
	 *				 if any argument is a {@link Constant} and does not match
	 *				 the Predicate's {@link ConstantType}.
	 */
	private void validate() {
		if (predicate.getArity() != arguments.length) {
			throw new IllegalArgumentException("Length of Schema does not match the number of arguments.");
		}

		for (int i = 0; i < arguments.length; i++) {
			if (arguments[i] == null) {
				throw new IllegalArgumentException("Arguments must not be null!");
			}

			// Perform any trivial type conversions to get the correct types.
			ConstantType declaredType = predicate.getArgumentType(i);
			if (declaredType == ConstantType.UniqueIntID && arguments[i] instanceof IntegerAttribute) {
				arguments[i] = new UniqueIntID(((IntegerAttribute)arguments[i]).getValue().intValue());
			} else if (declaredType == ConstantType.Integer && arguments[i] instanceof UniqueIntID) {
				arguments[i] = new IntegerAttribute(((IntegerAttribute)arguments[i]).getValue());
			} else if (declaredType == ConstantType.UniqueStringID && arguments[i] instanceof StringAttribute) {
				arguments[i] = new UniqueStringID(((StringAttribute)arguments[i]).getValue());
			} else if (declaredType == ConstantType.String && arguments[i] instanceof UniqueStringID) {
				arguments[i] = new StringAttribute(((StringAttribute)arguments[i]).getValue());
			} else if (declaredType == ConstantType.DeferredFunctionalUniqueID && arguments[i] instanceof IntegerAttribute) {
				arguments[i] = new UniqueIntID(((IntegerAttribute)arguments[i]).getValue().intValue());
			} else if (declaredType == ConstantType.DeferredFunctionalUniqueID && arguments[i] instanceof StringAttribute) {
				arguments[i] = new UniqueStringID(((StringAttribute)arguments[i]).getValue());
			}

			if ((arguments[i] instanceof Constant) && !(predicate.getArgumentType(i).isInstance((Constant)arguments[i]))) {
				throw new IllegalArgumentException(
						String.format("Expected type %s at position %d but was given: %s (%s) for predicate %s",
						predicate.getArgumentType(i), i, arguments[i], arguments[i].getClass().getName(), predicate));
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		if (predicate instanceof SpecialPredicate)  {
			s.append("(");
			if (predicate == SpecialPredicate.NotEqual) {
				s.append(arguments[0]);
				s.append(" != ");
				s.append(arguments[1]);
			} else if (predicate == SpecialPredicate.Equal) {
				s.append(arguments[0]);
				s.append(" == ");
				s.append(arguments[1]);
			} else if (predicate == SpecialPredicate.NonSymmetric) {
				s.append(arguments[0]);
				s.append(" % ");
				s.append(arguments[1]);
			} else {
				throw new UnsupportedOperationException(
						"Unrecognized SpecialPredicate: " + predicate);
			}
			s.append(")");
		} else {
			s.append(predicate.getName()).append("(");
			String connector = "";
			for (Term arg : arguments) {
				s.append(connector).append(arg);
				connector = ", ";
			}
			s.append(")");
		}

		return s.toString();
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object oth) {
		if (oth == this) return true;
		if (oth == null) return false;
		Atom other = (Atom) oth;

		// First check the hashcode to reduce the time we have to do a deepEquals() on the arguments.
		// Note that the hashcode is not perfect, but provides a quick insurance on inequality.
		return hashCode() == other.hashCode() && predicate.equals(other.predicate) && Arrays.deepEquals(arguments, other.arguments);
	}

	@Override
	public Formula flatten() {
		// Atom's are already flat by nature.
		return this;
	}
}
