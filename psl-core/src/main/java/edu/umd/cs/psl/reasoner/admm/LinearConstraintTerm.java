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
package edu.umd.cs.psl.reasoner.admm;

import edu.umd.cs.psl.reasoner.function.FunctionComparator;

/**
 * {@link ADMMReasoner} objective term of the form <br />
 * 0 if coeffs^T * x [?] constant <br />
 * infinity otherwise <br />
 * where [?] is ==, >=, or <=
 * <p>
 * All coeffs must be non-zero.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
class LinearConstraintTerm extends HyperplaneTerm {
	
	private final FunctionComparator comparator;
	
	LinearConstraintTerm(ADMMReasoner reasoner, int[] zIndices, double[] coeffs,
			double constant, FunctionComparator comparator) {
		super(reasoner, zIndices, coeffs, constant);
		this.comparator = comparator;
	}
	
	@Override
	protected void minimize() {
		/* If it's not an equality constraint, first tries to minimize without the constraint */
		if (!comparator.equals(FunctionComparator.Equality)) {
		
			/* Initializes scratch data */
			double total = 0.0;
			
			/*
			 * Minimizes without regard for the constraint, i.e., solves
			 * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
			 */
			for (int i = 0; i < x.length; i++) {
				x[i] = reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize;
				
				total += coeffs[i] * x[i];
			}
			
			/*
			 * Checks if the solution satisfies the constraint. If so, updates
			 * the local primal variables and returns.
			 */
			if ( (comparator.equals(FunctionComparator.SmallerThan) && total <= constant)
					||
				 (comparator.equals(FunctionComparator.LargerThan) && total >= constant)
			   ) {
				return;
			}
		}
		
		/*
		 * If the naive minimization didn't work, or if it's an equality constraint,
		 * projects onto the hyperplane
		 */
		project();
	}
}
