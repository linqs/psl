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

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Objective term for an {@link ADMMReasoner} that is based on a hyperplane in some way.
 * <p>
 * Stores the characterization of the hyperplane and solves continuous
 * quadratic knapsack problems using it.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
abstract class HyperplaneTerm extends ADMMObjectiveTerm {
	
	protected final double[] coeffs;
	protected final double constant;
	
	HyperplaneTerm(ADMMReasoner reasoner, int[] zIndices, double[] lowerBounds,
			double[] upperBounds, double[] coeffs, double constant) {
		super(reasoner, zIndices, lowerBounds, upperBounds);
		
		this.coeffs = coeffs;
		this.constant = constant;
	}
	
	/**
	 * Solves the continuous quadratic knapsack problem <br />
	 * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2 <br />
	 * such that coeffs^T * x = constant and x is within its box.
	 * <p>
	 * It is assumed that the problem is feasible.
	 * <p>
	 * Stores the result in x.
	 * <p>
	 * See <br />
	 * K.C. Kiwiel. "On linear-time algorithms for the continuous quadratic
	 * knapsack problem." J. Optim. Theory Appl. (2007) 134: 549-554.
	 */
	protected void solveKnapsackProblem() {
		/* If 2 or fewer variables, no need for Kiwiel algorithm... */
		if (x.length == 1) {
			x[0] = constant / coeffs[0];
		}
		// TODO: make this work for arbitrary boxes
//		else if (x.length == 2) {
//			double[] a = new double[2];
//			double min = 0.0, max = 1.0;
//			a[0] = reasoner.stepSize * reasoner.z.get(zIndices[0]) - y[0];
//			a[0] -= reasoner.stepSize * coeffs[0] / coeffs[1] * (-1 * constant / coeffs[1] + reasoner.z.get(zIndices[1]) - y[1]);
//			a[0] /= reasoner.stepSize * (1 + coeffs[0] * coeffs[0] / coeffs[1] / coeffs[1]);
//			
//			a[1] = constant / coeffs[1];
//			if (a[1] < 0.0) {
//				min = constant / coeffs[0];
//			}
//			else if (a[1] > 1.0) {
//				min = (constant - coeffs[1]) / coeffs[0];
//			}
//			a[1] = (constant - coeffs[0]) / coeffs[1];
//			if (a[1] < 0.0) {
//				max = constant / coeffs[0];
//			}
//			else if (a[1] > 1.0) {
//				max = (constant - coeffs[1]) / coeffs[0];
//			}
//			if (min > max) {
//				double temp = max;
//				max = min;
//				min = temp;
//			}
//			
//			if (a[0] < min) {
//				a[0] = min;
//			}
//			else if (a[0] > max) {
//				a[0] = max;
//			}
//			
//			a[1] = (constant - coeffs[0] * a[0]) / coeffs[1];
//			
//			/* Updates the local primal variables */
//			for (int i = 0; i < x.length; i++)
//				x[i] = a[i];
//		}
		/* Else, uses Kiwiel's algorithm */
		else {
			/* Initialization */
			double median, g;
			QuickSelector<Double> selector;
			int dim;
			Iterator<Integer> itr;
			
			double optimum = Double.NaN;
			double lowerBound = Double.NEGATIVE_INFINITY;
			double upperBound = Double.POSITIVE_INFINITY;
			double p = 0;
			double q = 0;
			double s = 0;
			LinkedList<Integer> openDimensions = new LinkedList<Integer>();
			for (int i = 0; i < x.length; i++)
				openDimensions.add(i);
			
			/* Initializes a, b, u, and l */
			double[] a = new double[x.length];
			double[] b = new double[a.length];
			double[] u = new double[a.length];
			double[] l = new double[a.length];
			for (int i = 0; i < a.length; i++) {
				if (coeffs[i] > 0) {
					a[i] = reasoner.stepSize * reasoner.z.get(zIndices[i]) - y[i];
					b[i] = coeffs[i];
					u[i] = ub[i];
					l[i] = lb[i];
				}
				else if (coeffs[i] < 0) {
					a[i] = -1 * (reasoner.stepSize * reasoner.z.get(zIndices[i]) - y[i]);
					b[i] = -1 * coeffs[i];
					u[i] = -1 * lb[i];
					l[i] = -1 * ub[i];
				}
				else
					throw new IllegalStateException();
			}
			
			/* Builds list of break points */
			double[] tL = new double[a.length];
			double[] tU = new double[a.length];
			LinkedList<Double> breakPoints = new LinkedList<Double>();
			for (int i = 0; i < a.length; i++) {
				/* Lower break point */
				tL[i] = (a[i] - l[i] * reasoner.stepSize) / b[i];
				breakPoints.add(tL[i]);
				/* Upper break point */
				tU[i] = (a[i] - u[i] * reasoner.stepSize) / b[i];
				breakPoints.add(tU[i]);
			}
			
			while (breakPoints.size() > 0) {
				selector = new QuickSelector<Double>(breakPoints);
				median = selector.getValue();
				
				/* Evaluates g at the median */
				g = 0;
				for (itr = openDimensions.iterator(); itr.hasNext();) {
					dim = itr.next();
					if (median < tU[dim])
						g += u[dim] * b[dim];
					else if (median <= tL[dim])
						g += b[dim] * (a[dim] - median * b[dim]) / reasoner.stepSize;
					else
						g += l[dim] * b[dim];
				 }
				 g += p - median * q + s;
				 
				 /* Compares g with target value */
				 if (g > constant) {
					lowerBound = median;
					breakPoints = selector.getGreater();
				 }
				 else if (g < constant) {
					upperBound = median;
					breakPoints = selector.getLess();
				 }
				 else {
					optimum = median;
					break;
				 }
				 
				 /* Removes fixed dimensions */
				 for (itr = openDimensions.iterator(); itr.hasNext();) {
					dim = itr.next();
					if (tL[dim] <= lowerBound) {
						itr.remove();
						s += b[dim] * l[dim];
					}
					if (upperBound <= tU[dim]) {
						itr.remove();
						s += b[dim] * u[dim];
					}
					if (tU[dim] <= lowerBound && upperBound <= tL[dim]) {
						itr.remove();
						p += a[dim] * b[dim] / reasoner.stepSize;
						q += b[dim] * b[dim] / reasoner.stepSize;
					}
				 }
			}
			
			/* If the optimum hasn't been found, interpolates between the bounds */
			if (Double.isNaN(optimum)) {
				double gU = 0.0;
				double gL = 0.0;
				
				/* Evaluates g at the upper bound */
				for (itr = openDimensions.iterator(); itr.hasNext();) {
					dim = itr.next();
					if (upperBound < tU[dim])
						gU += u[dim] * b[dim];
					else if (upperBound <= tL[dim])
						gU += b[dim] * (a[dim] - upperBound * b[dim]) / reasoner.stepSize;
					else
						gU += l[dim] * b[dim];
				}
				gU += p - upperBound * q + s;
				 
				/* Evaluates g at the lower bound */
				for (itr = openDimensions.iterator(); itr.hasNext();) {
					dim = itr.next();
					if (lowerBound < tU[dim])
						gL += u[dim] * b[dim];
					else if (lowerBound <= tL[dim])
						gL += b[dim] * (a[dim] - lowerBound * b[dim]) / reasoner.stepSize;
					else
						gL += l[dim] * b[dim];
				 }
				 gL += p - lowerBound * q + s;
				 
				 optimum = lowerBound - (gL - constant) * (upperBound - lowerBound) / (gU - gL);
			}
			
			/* Finds the projection using the optimal Lagrange multiplier */
			for (int i = 0; i < a.length; i++) {
				if (optimum < tU[i])
					x[i] = u[i];
				else if (optimum <= tL[i])
					x[i] = (a[i] - optimum * b[i]) / reasoner.stepSize;
				else
					x[i] = l[i];
			}
			
			/* Flips back the sign of any component that was flipped earlier */
			for (int i = 0; i < x.length; i++)
				if (coeffs[i] < 0)
					x[i] = -1 * x[i];
		}
	}
}
