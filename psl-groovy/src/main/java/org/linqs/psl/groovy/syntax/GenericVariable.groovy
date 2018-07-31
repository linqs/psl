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
package org.linqs.psl.groovy.syntax;

import org.linqs.psl.groovy.PSLModel;

import org.linqs.psl.model.atom.QueryAtom
import org.linqs.psl.model.predicate.SpecialPredicate
import org.linqs.psl.model.term.Variable

public class GenericVariable {

	private final String name;
	private final PSLModel model;

	public GenericVariable(String s, PSLModel m) {
		name = s;
		model = m;
	}

	public String toString() {
		return name;
	}

	public String getName() {
		return name;
	}

	public Variable toAtomVariable() {
		return new Variable(name);
	}

	/**
	 * Alias for xor (non-symmetric).
	 */
	def mod(other) {
		return xor(other);
	}

	def xor(other) {
		if (!(other instanceof GenericVariable)) {
			throw new IllegalArgumentException("Can only compare variables to variables! ${this} compared to ${other}");
		}
		assert other instanceof GenericVariable
		return new FormulaContainer(new QueryAtom(SpecialPredicate.NonSymmetric, this.toAtomVariable(), other.toAtomVariable()));
	}

	def minus(other) {
		if (!(other instanceof GenericVariable)) {
			throw new IllegalArgumentException("Can only compare variables to variables! ${this} compared to ${other}");
		}
		return new FormulaContainer(new QueryAtom(SpecialPredicate.NotEqual, this.toAtomVariable(), other.toAtomVariable()));
	}
}
