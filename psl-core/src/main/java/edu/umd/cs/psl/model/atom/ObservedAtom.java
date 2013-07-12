/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.reasoner.function.ConstantAtomFunctionVariable;

/**
 * A {@link GroundAtom} with a fixed truth value and a confidence value of infinity.
 * <p>
 * Circumstances that cause a GroundAtom to be instantiated as an ObservedAtom
 * include
 * <ul>
 *   <li>its Predicate is a {@link StandardPredicate} and closed in the Atom's Database</li>
 *   <li>its Predicate is a {@link FunctionalPredicate}</li>
 *   <li>its Predicate is a StandardPredicate and it is persisted in one of its
 *   Database's read-only Partitions</li>
 * </ul>
 * Other reasons may exist for specific Database implementations.
 */
public class ObservedAtom extends GroundAtom {

	protected ObservedAtom(Predicate p, GroundTerm[] args, Database db, double value,
			double confidenceValue) {
		super(p, args, db, value, confidenceValue);
	}

	@Override
	public ConstantAtomFunctionVariable getVariable() {
		return new ConstantAtomFunctionVariable(this);
	}

}
