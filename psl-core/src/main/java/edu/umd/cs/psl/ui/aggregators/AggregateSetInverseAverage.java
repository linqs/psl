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
package edu.umd.cs.psl.ui.aggregators;

import java.util.Set;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.set.membership.TermMembership;

/**
 * Terms are in set2, not set1 like AggregateSetAverage
 */
public class AggregateSetInverseAverage extends AggregateSetEquality {

	private final double supportThreshold=0.01;
	
	public AggregateSetInverseAverage() {
	}	
	
	@Override
	public String getName() {
		return "#{}";
	}
	
	@Override
	public double getSizeMultiplier(TermMembership set1, TermMembership set2) {
		assert set1.size()==1;
		return set2.size();
	}
	
	@Override
	protected double constantFactor(TermMembership set1, TermMembership set2) {
		assert set1.size()==1;
		//return 1.0/Math.max(set1.size(),set2.size());
		if (set2.size()<=0.0) return 0.0;
		else return 1.0/set2.size();
	}

	@Override
	public boolean enoughSupport(TermMembership set1,
			TermMembership set2, Set<GroundAtom> comparisonAtoms) {
		if (set1.size()<=0.0 || set2.size()<=0.0) return false;
		return comparisonAtoms.size()*constantFactor(set1,set2)>=supportThreshold;
	}

	
}
