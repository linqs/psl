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

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.AggregatePredicate;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * A GroundAtom with an {@link AggregatePredicate}.
 * <p>
 * An AggregateAtom has a known truth value defined by the truth values of
 * other GroundAtoms. It has an infinite confidence value.
 */
public class AggregateAtom extends GroundAtom {

	protected AggregateAtom(Predicate p, GroundTerm[] args) {
		super(p, args);
	}

	@Override
	public double getValue() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getConfidenceValue() {
		return Double.POSITIVE_INFINITY;
	}

	@Override
	protected boolean isValidPredicate(Predicate predicate) {
		return (predicate instanceof AggregatePredicate);
	}

}
