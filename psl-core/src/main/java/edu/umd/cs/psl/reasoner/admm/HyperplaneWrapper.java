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
import java.util.Vector;

import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.reasoner.function.FunctionSingleton;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

/**
 * Objective term for an {@link ADMMReasoner} that is based on a hyperplane in some way.
 * <p>
 * Provides tools for extracting the characterization of the hyperplane and solving continuous
 * quadratic knapsack problems using it.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
abstract class HyperplaneWrapper extends ADMMObjectiveTerm {
	
	protected final Vector<Double> coeffs;
	protected double constant;
	
	HyperplaneWrapper(ADMMReasoner reasoner, FunctionSum hyperplane) {
		super(reasoner, hyperplane.size());
		
		coeffs = new Vector<Double>(x.size());
		constant = 0.0;
		
		for (Iterator<FunctionSummand> itr = hyperplane.iterator(); itr.hasNext(); ) {
			FunctionSummand summand = itr.next();
			FunctionSingleton term = summand.getTerm();
			if (term instanceof AtomFunctionVariable && !term.isConstant()) {
				addVariable((AtomFunctionVariable) term, 0, 1);
				coeffs.add(summand.getCoefficient() * groundKernel.getWeight().getWeight());
			}
			else if (term.isConstant()) {
				constant += summand.getValue() * groundKernel.getWeight().getWeight();
			}
			else
				throw new IllegalArgumentException("Unexpected summand.");
		}
	}
	
	/**
	 * Solves the continuous quadratic knapsack problem <br />
	 * arg min \|...\| <br />
	 * such that coeffs^T x = constant and all x are within their bounds.
	 * <p>
	 * Stores the result in x.
	 * <p>
	 * See <br />
	 * K.C. Kiwiel. "On linear-time algorithms for the continuous quadratic
	 * knapsack problem." J. Optim. Theory Appl. (2007) 134: 549-554.
	 */
	protected void solveKnapsackProblem() {
		if (x.size() == 1) {
			
		}
		else if (x.size() == 2) {
			
		}
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
			for (int i = 0; i < x.size(); i++)
				openDimensions.add(i);
			
			/* Computes initializes a, b, u, and l */
			double[] a = new double[x.size()];
			double[] b = new double[a.length];
			double[] u = new double[a.length];
			double[] l = new double[a.length];
			for (int i = 0; i < a.length; i++) {
				if (coeffs.get(i) > 0) {
					a[i] = reasoner.stepSize * reasoner.z.get(zIndices.get(i)) - y.get(i);
					b[i] = coeffs.get(i);
					u[i] = ub.get(i);
					l[i] = lb.get(i);
				}
				else if (coeffs.get(i) < 0) {
					a[i] = -1 * (reasoner.stepSize * reasoner.z.get(zIndices.get(i)) - y.get(i));
					b[i] = -1 * coeffs.get(i);
					u[i] = -1 * lb.get(i);
					l[i] = -1 * ub.get(i);
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
				 if (g > -1 * constant) {
					lowerBound = median;
					breakPoints = selector.getGreater();
				 }
				 else if (g < -1 * constant) {
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
				 
				 optimum = lowerBound - (gL + constant) * (upperBound - lowerBound) / (gU - gL);
			}
			
			/* Finds the projection using the optimal Lagrange multiplier */
			for (int i = 0; i < a.length; i++) {
				if (optimum < tU[i])
					x.set(i, u[i]);
				else if (optimum <= tL[i])
					x.set(i, (a[i] - optimum * b[i]) / reasoner.stepSize);
				else
					x.set(i, l[i]);
			}
		}
	}
}
