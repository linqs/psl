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
package edu.umd.cs.psl.reasoner.admm;

import edu.umd.cs.psl.reasoner.function.FunctionComparator;

class LinearConstraintTerm extends HyperplaneTerm {
	
	private final FunctionComparator comparator;
	
	LinearConstraintTerm(ADMMReasoner reasoner, int[] zIndices, double[] lowerBounds,
			double[] upperBounds, double[] coeffs, double constant,
			FunctionComparator comparator) {
		super(reasoner, zIndices, lowerBounds, upperBounds, coeffs, constant);
		this.comparator = comparator;
	}
	
	@Override
	protected void minimize() {
		/* If it's not an equality constraint, first tries to minimize without the constraint */
		if (!comparator.equals(FunctionComparator.Equality)) {
		
			/* Initializes scratch data */
			double a[] = new double[x.length];
			
			/* Minimizes without regard for any constraints, then projects on to a box */
			for (int i = 0; i < a.length; i++) {
				a[i] = reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize;
				
				if (a[i] < lb[i])
					a[i] = lb[i];
				else if (a[i] > ub[i])
					a[i] = ub[i];
			}
			
			/*
			 * Checks if the new point satisfies the constraint. If so, updates
			 * the local primal variables and returns.
			 */
			double total = 0.0;
			for (int i = 0; i < a.length; i++)
				total += coeffs[i] * a[i];
			total += constant;
			
			if ( (comparator.equals(FunctionComparator.SmallerThan) && total <= constant)
					||
				 (comparator.equals(FunctionComparator.LargerThan) && total >= constant)
			   ) {
				for (int i = 0; i < a.length; i++)
					x[i] = a[i];
				return;
			}
		}
		
		/*
		 * If the naive minimization didn't work, or if it's an equality constraint,
		 * solves the knapsack problem
		 */
		solveKnapsackProblem();
	}
}
