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

/**
 * {@link ADMMReasoner} objective term of the form <br />
 * weight * [max(coeffs^T * x - constant, 0)]^2
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
class SquaredHingeLossTerm extends SquaredHyperplaneTerm {
	
	public SquaredHingeLossTerm(ADMMReasoner reasoner, int[] zIndices,
			double[] coeffs, double constant, double weight) {
		super(reasoner, zIndices, coeffs, constant, weight);
	}

	@Override
	protected void minimize() {
		/* Initializes scratch data */
		double total = 0.0;
		
		/*
		 * Minimizes without the quadratic loss, i.e., solves
		 * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
		 */
		for (int i = 0; i < x.length; i++) {
			x[i] = reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize;
			total += coeffs[i] * x[i];
		}
		
		/* If the quadratic loss is NOT active at the computed point, it is the solution... */
		if (total <= constant) {
			return;
		}
		
		/*
		 * Else, minimizes with the quadratic loss, i.e., solves
		 * argmin weight * (coeffs^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
		 */
		minWeightedSquaredHyperplane();
	}

}
