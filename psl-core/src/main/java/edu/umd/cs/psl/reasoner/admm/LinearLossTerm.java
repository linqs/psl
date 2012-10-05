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

class LinearLossTerm extends HyperplaneTerm {
	
	LinearLossTerm(ADMMReasoner reasoner, int[] zIndices, double[] lowerBounds, double[] upperBounds,
			double[] coeffs, double constant) {
		super(reasoner, zIndices, lowerBounds, upperBounds, coeffs, constant);
	}
	
	@Override
	protected void minimize() {
		/* Initializes scratch data */
		double a[] = new double[x.length];
		
		for (int i = 0; i < a.length; i++) {
			a[i] = y[i] - reasoner.stepSize * reasoner.z.get(zIndices[i]);
			a[i] += coeffs[i];
			a[i] /= reasoner.stepSize / -2;
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
