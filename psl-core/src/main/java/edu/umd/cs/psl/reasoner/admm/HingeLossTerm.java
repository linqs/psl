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

class HingeLossTerm extends HyperplaneTerm {
	
	HingeLossTerm(ADMMReasoner reasoner, int[] zIndices, double[] lowerBounds,
			double[] upperBounds, double[] coeffs, double constant) {
		super(reasoner, zIndices, lowerBounds, upperBounds, coeffs, constant);
	}
	
	@Override
	protected void minimize() {
		// TODO: Switch order of checking linear loss and zero
		
		/* Initializes scratch data */
		double a[] = new double[x.length];
		double min1 = 0.0, min2 = 0.0;
		
		/* First the linear loss */
		for (int i = 0; i < a.length; i++) {
			a[i] = y[i] - reasoner.stepSize * reasoner.z.get(zIndices[i]);
			a[i] += coeffs[i];
			a[i] /= -1 * reasoner.stepSize;
			
			min1 += reasoner.stepSize / 2 * a[i] * a[i];
			min1 += a[i] * (y[i] - reasoner.stepSize * reasoner.z.get(zIndices[i]));
		}
		
		min2 = min1;
		
		for (int i = 0; i < a.length; i++)
			min1 += coeffs[i] * a[i];
		min1 += constant;
		
		/* Tries without the linear loss */
		if (min1 < min2) {
			min1 = 0.0;
			min2 = 0.0;
			for (int i = 0; i < a.length; i++) {
				a[i] = y[i] - reasoner.stepSize * reasoner.z.get(zIndices[i]);
				a[i] /= -1 * reasoner.stepSize;
				
				min1 += reasoner.stepSize / 2 * a[i] * a[i];
				min1 += a[i] * (y[i] - reasoner.stepSize * reasoner.z.get(zIndices[i]));
			}
			
			min2 = min1;
			
			for (int i = 0; i < a.length; i++)
				min1 += coeffs[i] * a[i];
			min1 += constant;
			
			/* Now tries minimizing along their intersection */
			if (min2 < min1) {
				solveKnapsackProblem();
				return;
			}
		}
		
		/* Projects on to a box */
		for (int i = 0; i < x.length; i++)
			if (a[i] < 0)
				a[i] = 0;
			else if (a[i] > 1)
				a[i] = 1;
		
		/* Updates the local primal variables */
		for (int i = 0; i < a.length; i++)
			x[i] = a[i];
	}
}
