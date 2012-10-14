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

import java.util.Set;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * An Atom that can be used as a {@link Formula} (or part of one).
 * <p>
 * Arguments to a FormulaAtom can be a mix of {@link Variable Variables} and
 * {@link GroundTerm GroundTerms}. In other words, they are not necessarily
 * ground and can be used for matching GroundAtoms in a query.
 */
public class FormulaAtom extends Atom implements Formula {

	public FormulaAtom(Predicate p, Term[] args) {
		super(p, args);
	}
	
	@Override
	public Formula getDNF() {
		return this;
	}
	
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
	public Set<Atom> getAtoms(Set<Atom> atoms) {
		atoms.add(this);
		return atoms;
	}

	@Override
	protected boolean isValidPredicate(Predicate predicate) {
		return true;
	}
}
