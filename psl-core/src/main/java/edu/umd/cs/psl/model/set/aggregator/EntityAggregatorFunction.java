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
package edu.umd.cs.psl.model.set.aggregator;

import java.util.Set;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.set.membership.TermMembership;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;

public interface EntityAggregatorFunction extends AggregatorFunction {

	public ConstraintTerm defineConstraint(GroundAtom setAtom, TermMembership set1,
			TermMembership set2, Set<GroundAtom> comparisonAtoms);
	
	/**
	 * Better err on the save side!!
	 * @param set1
	 * @param set2
	 * @param comparisonAtoms
	 * @return
	 */
	public boolean enoughSupport(TermMembership set1, TermMembership set2, Set<GroundAtom> comparisonAtoms);
	
	public double getSizeMultiplier(TermMembership set1, TermMembership set2);
	
}
