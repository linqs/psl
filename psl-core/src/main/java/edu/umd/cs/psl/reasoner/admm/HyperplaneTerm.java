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

/**
 * Objective term for an {@link ADMMReasoner} that is based on a hyperplane in some way.
 * <p>
 * Stores the characterization of the hyperplane as coeffs^T * x = constant
 * and projects onto the hyperplane.
 * <p>
 * All coeffs must be non-zero.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
abstract class HyperplaneTerm extends ADMMObjectiveTerm {
	
	protected final double[] coeffs;
	protected final double constant;
	protected final double[] unitNormal;
	
	HyperplaneTerm(ADMMReasoner reasoner, int[] zIndices, double[] coeffs, double constant) {
		super(reasoner, zIndices);
		
		this.coeffs = coeffs;
		this.constant = constant;
		
		if (x.length >= 3) {
			/* 
			 * Finds a unit vector normal to the hyperplane and a point in the
			 * hyperplane for future projections
			 */
			double length = 0.0;
			for (int i = 0; i < coeffs.length; i++)
				length += coeffs[i] * coeffs[i];
			length = Math.sqrt(length);
			
			unitNormal = new double[coeffs.length];
			for (int i = 0; i< unitNormal.length; i++)
				unitNormal[i] = coeffs[i] / length;
		}
		else
			unitNormal = null;
	}
	
	/**
	 * Finds the orthogonal projection onto the hyperplane <br />
	 * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2 <br />
	 * such that coeffs^T * x = constant
	 * <p>
	 * Stores the result in x.
	 */
	protected void project() {
		if (x.length == 1) {
			x[0] = constant / coeffs[0];
		}
		else if (x.length == 2) {
			x[0] = reasoner.stepSize * reasoner.z.get(zIndices[0]) - y[0];
			x[0] -= reasoner.stepSize * coeffs[0] / coeffs[1] * (-1 * constant / coeffs[1] + reasoner.z.get(zIndices[1]) - y[1]);
			x[0] /= reasoner.stepSize * (1 + coeffs[0] * coeffs[0] / coeffs[1] / coeffs[1]);
			
			x[1] = (constant - coeffs[0] * x[0]) / coeffs[1];
		}
		else {
			double[] point = new double[x.length];
			for (int i = 0; i < x.length; i++)
				point[i] = reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize;
			
			/* For point (constant / coeffs[0], 0,...) in hyperplane dotted with unitNormal */
			double multiplier = -1 * constant / coeffs[0] * unitNormal[0];
			
			for (int i = 0; i < x.length; i++)
				multiplier += point[i] * unitNormal[i];
			
			for (int i = 0; i < x.length; i++)
				x[i] = point[i] - multiplier * unitNormal[i];
		}
	}
}
