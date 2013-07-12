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

import edu.umd.cs.psl.model.set.membership.TermMembership;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;


public class AggregateSetOverlap extends AggregateSetEquality {

	
	public AggregateSetOverlap() {
	}	
	
	public AggregateSetOverlap(String[] args) {
		this();
	}
	
	@Override
	public String getName() {
		return "<=>{}";
	}
	
	@Override
	FunctionComparator getConstraintType() {
		return FunctionComparator.LargerThan;
	}
	
	@Override
	double getDefaultSimilarityforEmptySets() {
		return 1.0;
	}
	
	@Override
	public double getSizeMultiplier(TermMembership set1, TermMembership set2) {
		//assert set1.size()>0.0 && set2.size()>0.0;
		return Math.min(set1.size(),set2.size());
	}
	
	@Override
	protected double constantFactor(TermMembership set1, TermMembership set2) {
		//assert set1.size()>0.0 && set2.size()>0.0;
		return 1.0/Math.min(set1.size(),set2.size());
	}


	
}
