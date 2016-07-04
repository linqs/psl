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
package edu.umd.cs.psl.model.rule.arithmetic.expression;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * A variant of an {@link Atom} that can additionally take {@link SummationVariable SummationVariables}
 * as arguments.
 * <p>
 * SummationAtoms can be used in an {@link ArithmeticRuleExpression}.
 * <p>
 * Note that SummationAtom is not a subclass of Atom.
 * 
 * @author Stephen Bach
 */
public class SummationAtom implements SummationAtomOrAtom {
	
	private final Predicate p;
	private final SummationVariableOrTerm[] args;

	public SummationAtom(Predicate p, SummationVariableOrTerm[] args) {
		this.p = p;
		this.args = args;
	}
	
	@Override
	public String toString() {
		// TODO
		return null;
	}
	
	@Override
	public int hashCode() {
		// TODO
		return 0;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(oth instanceof SummationAtom)) return false;
		
		// TODO
		return false;
	}	

}
