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
package org.linqs.psl.model.atom;

import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;

/**
 * An Atom that can be used in a query, but does not have a truth value.
 * <p>
 * Arguments to a QueryAtom can be a mix of {@link Variable Variables} and
 * {@link Constant GroundTerms}. In other words, they are not necessarily
 * ground and can be used for matching GroundAtoms in a query.
 */
public class QueryAtom extends Atom {
	public QueryAtom(Predicate p, Term... args) {
		super(p, args);
	}

	public GroundAtom ground(AtomManager atomManager, ResultList res, int resultIndex) {
		Constant[] newArgs = new Constant[arguments.length];

		for (int i = 0; i < arguments.length; i++) {
			if (arguments[i] instanceof Variable) {
				newArgs[i] = res.get(resultIndex, (Variable)arguments[i]);
			} else if (arguments[i] instanceof Constant) {
				newArgs[i] = (Constant)arguments[i];
			} else {
				throw new IllegalArgumentException("Unrecognized type of Term.");
			}
		}

		return atomManager.getAtom(predicate, newArgs);
	}

	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		for (int i=0;i<arguments.length;i++) {
			if (arguments[i] instanceof Variable) {
				ConstantType t = predicate.getArgumentType(i);
				varMap.addVariable((Variable)arguments[i], t);
			}
		}

		return varMap;
	}
}
