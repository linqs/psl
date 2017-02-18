/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.model.formula;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.term.VariableTypeMap;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Set;

/**
 * This class implements fuzzy negation. Note that we currently only allow the negation of singletons, i.e
 * single atoms. This is not really a restriction, because every formula can be converted into one where
 * only atoms are negated.
 *
 * @author Matthias Broecheler
 * @author Stephen Bach
 */
public class Negation implements Formula {
	/**
	 * The fuzzy singleton of which this is the negation
	 */
	private final Formula body;
	private final int hash;

	public Negation(Formula f) {
		assert(f != null);
		body = f;

		// Note that we are adding a prime to our hash code to avoid
		// A and !A from conflicting.
		hash = new HashCodeBuilder().append(f).toHashCode() + 37;
	}

	public Formula getFormula() {
		return body;
	}

	@Override
	public Formula getDNF() {
		if (body instanceof Atom) {
			return this;
		}

		if (body instanceof Negation) {
			// Collapse the double negation.
			return ((Negation)body).body.getDNF();
		}

		if (body instanceof Conjunction) {
			// Apply DeMorgans Law.
			Conjunction conjunction = (Conjunction)body;
			Formula[] components = new Formula[conjunction.length()];
			for (int i = 0; i < components.length; i++) {
				components[i] = new Negation(conjunction.get(i));
			}
			return new Disjunction(components).getDNF();
		}

		if (body instanceof Disjunction) {
			// Apply DeMorgans Law.
			Disjunction disjunction = (Disjunction)body;
			Formula[] components = new Formula[disjunction.length()];
			for (int i = 0; i < components.length; i++) {
				components[i] = new Negation(disjunction.get(i));
			}
			return new Conjunction(components).getDNF();
		}

		if (body instanceof Implication) {
			return new Negation(body.getDNF()).getDNF();
		}

		throw new IllegalStateException("Body of negation is unrecognized type.");
	}

	@Override
	public String toString() {
		return "~( " + body + " )";
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object oth) {
		if (oth == this) {
			return true;
		}

		if (oth == null || !(getClass().isInstance(oth))) {
			return false;
		}

		Negation other = (Negation)oth;
		return this.hash == other.hash && body.equals(other.body);
	}

	@Override
	public Set<Atom> getAtoms(Set<Atom> atoms) {
		body.getAtoms(atoms);
		return atoms;
	}

	@Override
	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		body.collectVariables(varMap);
		return varMap;
	}
}
