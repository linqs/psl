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

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * A GroundAtom with a {@link FunctionalPredicate}.
 * <p>
 * A FunctionalAtom has a known truth value defined by its arguments
 * and the {@link ObservedAtom ObservedAtoms} in its {@link Database}.
 * It has an infinite confidence value.
 */
public class FunctionalAtom extends GroundAtom {

	protected FunctionalAtom(Predicate p, GroundTerm[] args, Database db, double value) {
		super(p, args, db, value);
	}

	@Override
	public double getConfidenceValue() {
		return Double.POSITIVE_INFINITY;
	}

	@Override
	protected boolean isValidPredicate(Predicate predicate) {
		return (predicate instanceof FunctionalPredicate);
	}

}
