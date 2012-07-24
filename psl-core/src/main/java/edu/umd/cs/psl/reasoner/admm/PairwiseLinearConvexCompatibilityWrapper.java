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
import java.util.Vector;

import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.reasoner.function.FunctionSingleton;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.MaxFunction;

class PairwiseLinearConvexCompatibilityWrapper extends GroundKernelWrapper {
	
	protected final Vector<Double> coeffs;
	protected double constant;
	protected final boolean nonNegative;
	
	PairwiseLinearConvexCompatibilityWrapper(ADMMReasoner reasoner, GroundCompatibilityKernel groundKernel) {
		super(reasoner, groundKernel);
		
		coeffs = new Vector<Double>(x.size());
		constant = 0.0;
		
		FunctionTerm function = groundKernel.getFunctionDefinition();
		FunctionTerm innerFunction;
		FunctionTerm constantTerm;
		
		/* If the FunctionTerm is a MaxFunction, it must be to make it non-negative */
		if (function instanceof MaxFunction) {
			if (((MaxFunction) function).size() != 2)
				throw new IllegalArgumentException("Max function must have two arguments.");
			innerFunction = ((MaxFunction) function).get(0);
			if (innerFunction.isConstant()) {
				constantTerm = innerFunction;
				innerFunction = ((MaxFunction) function).get(1);
			}
			else {
				constantTerm = ((MaxFunction) function).get(1);
			}
			
			if (!constantTerm.isConstant() || constantTerm.getValue() != 0)
				throw new IllegalArgumentException("Max function must have one variable function and 0.0 as arguments.");
			
			function = innerFunction;
			nonNegative = true;
		}
		else
			nonNegative = false;
		
		/* Processes the function */
		if (function instanceof FunctionSum) {
			for (Iterator<FunctionSummand> itr = ((FunctionSum) function).iterator(); itr.hasNext(); ) {
				FunctionSummand summand = itr.next();
				FunctionSingleton term = summand.getTerm();
				if (term instanceof AtomFunctionVariable && !term.isConstant()) {
					addVariable((AtomFunctionVariable) term);
					coeffs.add(summand.getCoefficient() * groundKernel.getWeight().getWeight());
				}
				else if (term.isConstant()) {
					constant += summand.getValue() * groundKernel.getWeight().getWeight();
				}
				else
					throw new IllegalArgumentException("Unexpected summand.");
			}
		}
		else
			throw new IllegalArgumentException("Inner function must be a FunctionSum.");
		
		if (x.size() > 2)
			throw new IllegalArgumentException("At most two variables are supported.");
	}
	
	@Override
	protected void minimize() {
		/* Initializes scratch data */
		double a[] = new double[x.size()];
		double min1 = 0.0, min2 = 0.0;
		
		/*
		 * Considers both functions and minimizes along their intersection if
		 * both minima are less than than the other's value at those points
		 */
		if (nonNegative) {
			/* First with the variable function */
			for (int i = 0; i < a.length; i++) {
				a[i] = y.get(i) - reasoner.stepSize * reasoner.z.get(zIndices.get(i));
				a[i] += coeffs.get(i);
				a[i] /= -1 * reasoner.stepSize;
				
				min1 += reasoner.stepSize / 2 * a[i] * a[i];
				min1 += a[i] * (y.get(i) - reasoner.stepSize * reasoner.z.get(zIndices.get(i)));
			}
			
			min2 = min1;
			
			for (int i = 0; i < a.length; i++)
				min1 += coeffs.get(i) * a[i];
			min1 += constant;
			
			/* Tries without the variable function */
			if (min1 < min2) {
				min1 = 0.0;
				min2 = 0.0;
				for (int i = 0; i < a.length; i++) {
					a[i] = y.get(i) - reasoner.stepSize * reasoner.z.get(zIndices.get(i));
					a[i] /= -1 * reasoner.stepSize;
					
					min1 += reasoner.stepSize / 2 * a[i] * a[i];
					min1 += a[i] * (y.get(i) - reasoner.stepSize * reasoner.z.get(zIndices.get(i)));
				}
				
				min2 = min1;
				
				for (int i = 0; i < a.length; i++)
					min1 += coeffs.get(i) * a[i];
				min1 += constant;
				
				/* Now tries minimizing along their intersection */
				if (min2 < min1) {
					if (x.size() == 1)
						a[0] = -1 * constant / coeffs.get(0);
					else if (x.size() == 2) {
						a[0] = y.get(0) - reasoner.stepSize * reasoner.z.get(zIndices.get(0));
						a[0] -= coeffs.get(0) / coeffs.get(1) * (y.get(1) - reasoner.stepSize * reasoner.z.get(zIndices.get(1)));
						a[0] += 2 * coeffs.get(0) * constant / (coeffs.get(1) * coeffs.get(1));
						a[0] /= -1 * reasoner.stepSize * (1 + coeffs.get(0) * coeffs.get(0) / coeffs.get(1) / coeffs.get(1));
						
						a[1] = (-1 * coeffs.get(0) * a[0] - constant) / coeffs.get(1);
					}
					else
						throw new IllegalStateException();
				}
			}
		}
		/* Else if there is no hinge function */
		else {
			for (int i = 0; i < a.length; i++) {
				a[i] = y.get(i) - reasoner.stepSize * reasoner.z.get(zIndices.get(i));
				a[i] += coeffs.get(i);
				a[i] /= reasoner.stepSize / -2;
			}
		}
		
		/* Projects on to a box */
		for (int i = 0; i < x.size(); i++)
			if (a[i] < 0)
				a[i] = 0;
			else if (a[i] > 1)
				a[i] = 1;
		
		/* Updates the local primal variables */
		for (int i = 0; i < a.length; i++)
			x.set(i, a[i]);
	}
}
