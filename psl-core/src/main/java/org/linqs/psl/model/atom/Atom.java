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
import org.linqs.psl.model.term.Term;

import com.healthmarketscience.sqlbuilder.BinaryCondition;

/**
 * A {@link Predicate} combined with the correct number of {@link Term Terms}
 * as arguments.
 * <p>
 * Two Atoms are equal if their Predicate and arguments are equal. Note that this
 * means that their truth values might not match, or one might even be a
 * {@link QueryAtom}.
 *
 * @author Matthias Broecheler
 */
abstract public class Atom implements Formula, SummationAtomOrAtom {

	protected final Predicate predicate;

	protected final Term[] arguments;

	protected final int hashcode;

	protected Atom(Predicate p, Term[] args) {
		predicate = p;
		arguments = Arrays.copyOf(args, args.length);
		hashcode = new HashCodeBuilder().append(predicate).append(arguments).toHashCode();
		checkSchema();
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
		return Arrays.copyOf(arguments, arguments.length);
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
	 *
	 * @throws IllegalArgumentException
	 *             if the number of arguments doesn't match the number of arguments
	 *             of the predicate
	 * @throws IllegalArgumentException  if any argument is null
	 * @throws IllegalArgumentException
	 *             if any argument is a {@link Constant} and does not match
	 *             the Predicate's {@link ConstantType}.
	 */
	private void checkSchema() {
		if (predicate.getArity()!=arguments.length) {
			throw new IllegalArgumentException("Length of Schema does not match the number of arguments.");
		}
		for (int i=0;i<arguments.length;i++) {
			if (arguments[i]==null)
				throw new IllegalArgumentException("Arguments must not be null!");
			if ((arguments[i] instanceof Constant) && !(predicate.getArgumentType(i).isInstance((Constant) arguments[i])))
				throw new IllegalArgumentException("Expected type "+predicate.getArgumentType(i)+" at position "+i+" but was given: " + arguments[i] + " for predicate " + predicate);
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
}
