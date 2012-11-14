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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;

/**
 * {@link ADMMReasoner} objective term of the form <br />
 * weight * [max(coeffs^T * x - constant, 0)]^2
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class SquaredHingeLossTerm extends HyperplaneTerm {
	
	private enum VarStatus {FREE, FIXED, LOWER_BOUND, UPPER_BOUND};
	
	private final double weight;
	private final Map<Subproblem, DenseDoubleCholeskyDecomposition> gradCoeffs;
	private final Map<Subproblem, CandidateSolution> candidates;
	private final Subproblem problem;

	public SquaredHingeLossTerm(ADMMReasoner reasoner, int[] zIndices,
			double[] lowerBounds, double[] upperBounds, double[] coeffs,
			double constant, double weight) {
		super(reasoner, zIndices, lowerBounds, upperBounds, coeffs, constant);
		this.weight = weight;
		gradCoeffs = new HashMap<Subproblem, DenseDoubleCholeskyDecomposition>((int) Math.pow(2, x.length));
		candidates = new HashMap<Subproblem, CandidateSolution>((int) Math.pow(3, x.length));
		setGradCoeffs();
		
		VarStatus value[] = new VarStatus[x.length];
		for (int i = 0; i < value.length; i++)
			value[i] = VarStatus.FREE;
		problem = new Subproblem(value);
	}

	@Override
	protected void minimize() {
		/* Initializes scratch data */
		double a[] = new double[x.length];
		double total = 0.0;
		
		/*
		 * Minimizes without the quadratic loss, i.e., solves
		 * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
		 * such that x is within its box
		 */
		for (int i = 0; i < a.length; i++) {
			a[i] = reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize;
			
			if (a[i] < lb[i])
				a[i] = lb[i];
			else if (a[i] > ub[i])
				a[i] = ub[i];
			
			total += coeffs[i] * a[i];
		}
		
		/* If the quadratic loss is NOT active at the computed point, it is the solution... */
		if (total <= constant) {
			for (int i = 0; i < x.length; i++)
				x[i] = a[i];
			return;
		}
		
		/*
		 * Else, minimizes with the quadratic loss, i.e., solves
		 * argmin weight * (coeffs^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
		 * such that x is within its box
		 */
		CandidateSolution sol = solveSubproblem(problem);
		candidates.clear();
		
		for (int i = 0; i < x.length; i++)
			x[i] = sol.x[i];
	}
	
	/**
	 * Solves the subproblem <br />
	 * argmin weight * (coeffs^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
	 * such that x is within its box for a subproblem with some variables free
	 * and the others fixed to either their upper or lower bounds.
	 * 
	 * @param sub  the subproblem to solve
	 * @return the solution to the subproblem
	 */
	private CandidateSolution solveSubproblem(Subproblem sub) {
		/* If solution is cached, returns it */
		CandidateSolution sol = candidates.get(sub);
		if (sol != null)
			return sol;
		
		double[] x = new double[this.x.length]; 
		
		/* If only one free variable is left, just solves it... */
		if (sub.numFree == 1) {
			int freeVar = -1;
			
			for (int i = 0; i < x.length; i++) {
				switch(sub.value[i]) {
				case FREE:
					freeVar = i;
					break;
				case LOWER_BOUND:
					x[i] = lb[i];
					break;
				case UPPER_BOUND:
					x[i] = ub[i];
					break;
				default:
					throw new IllegalArgumentException("Illegal or unrecognized status.");
				}
			}
			
			double gradCoeff = 2 * weight * coeffs[freeVar] * coeffs[freeVar];
			gradCoeff += reasoner.stepSize;
			double gradConstant = -1 * reasoner.stepSize * (reasoner.z.get(zIndices[freeVar]) - y[freeVar] / reasoner.stepSize);
			gradConstant -= 2 * weight * coeffs[freeVar] * constant;
			
			for (int i = 0; i < x.length; i++) {
				if (i != freeVar) {
					gradConstant += 2 * weight * coeffs[freeVar] * coeffs[i] * x[i];
				}
			}
			
			x[freeVar] = -1 * gradConstant / gradCoeff;
			
			if (x[freeVar] < lb[freeVar])
				x[freeVar] = lb[freeVar];
			else if (x[freeVar] > ub[freeVar])
				x[freeVar] = ub[freeVar];
			
			sol = new CandidateSolution(x);
			candidates.put(sub, sol);
			return sol;
		}
		/* Else, tries to solve and then recurses */
		else {
			/*
			 * First checks if the unconstrained minimizer
			 * happens to be in the box
			 */
			DenseDoubleCholeskyDecomposition gradCoeff = gradCoeffs.get(sub.getFixedMask());
			
			/* Constructs constant term in the objective gradient to solve system */
			DenseDoubleMatrix1D gradConstant = new DenseDoubleMatrix1D(sub.numFree);
			double gradConstantElement;
			int nextDimension = 0;
			for (int i = 0; i < x.length; i++) {
				if (VarStatus.FREE.equals(sub.value[i])) {
					gradConstantElement = -1 * reasoner.stepSize * (reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize);
					gradConstantElement -= 2 * weight * coeffs[i] * constant;
					for (int j = 0; j < x.length; j++) {
						if (VarStatus.LOWER_BOUND.equals(sub.value[j])) {
							gradConstantElement += 2 * weight * coeffs[i] * coeffs[j] * lb[j];
						}
						else if (VarStatus.UPPER_BOUND.equals(sub.value[j])) {
							gradConstantElement += 2 * weight * coeffs[i] * coeffs[j] * ub[j];
						}
					}
					
					/*
					 * Divides by the weight and flips the sign because it's moved
					 * from the left hand side to the right
					 */
					gradConstantElement /= -1 * weight;
					
					gradConstant.set(nextDimension, gradConstantElement);
					nextDimension++;
				}
			}
			
			gradCoeff.solve(gradConstant);
			
			/* Reads out free variables and constructs unconstrained minimizer */
			nextDimension = 0;
			for (int i = 0; i < x.length; i++) {
				switch(sub.value[i]) {
				case FREE:
					x[i] = gradConstant.get(nextDimension);
					nextDimension++;
					break;
				case LOWER_BOUND:
					x[i] = lb[i];
					break;
				case UPPER_BOUND:
					x[i] = ub[i];
					break;
				default:
					throw new IllegalArgumentException("Illegal or unrecognized status.");
				}
			}
			
			/* Checks box constraint */
			boolean inBox = true;
			for (int i = 0; i < x.length; i++)
				if (x[i] < lb[i] || x[i] > ub[i])
					inBox = false;
			
			/* If it is in the box, then it is the solution... */
			if (inBox) {
				sol = new CandidateSolution(x);
				candidates.put(sub, sol);
				return sol;
			}
			/* Else, recurses to find best solution on the box */
			else {
				CandidateSolution best = null;
				VarStatus[] newSub = Arrays.copyOf(sub.value, sub.value.length);
				for (int i = 0; i < newSub.length; i++) {
					if (VarStatus.FREE.equals(newSub[i])) {
						newSub[i] = VarStatus.LOWER_BOUND;
						sol = solveSubproblem(new Subproblem(newSub));
						if (best == null || sol.value < best.value)
							best = sol;
						
						newSub[i] = VarStatus.UPPER_BOUND;
						sol = solveSubproblem(new Subproblem(newSub));
						if (best == null || sol.value < best.value)
							best = sol;
						
						newSub[i] = VarStatus.FREE;
					}
				}
				
				candidates.put(sub, best);
				return best;
			}
		}
	}
	
	private void setGradCoeffs() {
		int stop = (int) Math.pow(2, x.length);
		VarStatus[] statuses = new VarStatus[x.length];
		Subproblem subproblem;
		DenseDoubleMatrix2D matrix;
		double coeff;
		int nextDimensionJ, nextDimensionK;
		
		for (int i = 0; i < stop; i++) {
			for (int j = 0; j < x.length; j++) {
				if (((1 << j) & i) == 0) {
					statuses[j] = VarStatus.FREE;
				}
				else {
					statuses[j] = VarStatus.FIXED;
				}
			}
			subproblem = new Subproblem(statuses);
			if (subproblem.numFree > 1) {
				matrix = new DenseDoubleMatrix2D(subproblem.numFree, subproblem.numFree);
				nextDimensionJ = 0;
				
				for (int j = 0; j < x.length; j++) {
					if (VarStatus.FREE.equals(subproblem.value[j])) {
						nextDimensionK = 0;
						for (int k = 0; k < x.length; k++) {
							if (VarStatus.FREE.equals(subproblem.value[k])) {
								if (nextDimensionJ == nextDimensionK) {
									coeff = 2 * coeffs[j] * coeffs[j] + reasoner.stepSize;
									matrix.set(nextDimensionJ, nextDimensionJ, coeff);
								}
								else {
									coeff = 2 * coeffs[j] * coeffs[k];
									matrix.set(nextDimensionJ, nextDimensionK, coeff);
									matrix.set(nextDimensionK, nextDimensionJ, coeff);
								}
								
								nextDimensionK++;
							}
						}
						
						nextDimensionJ++;
					}
				}
				
				gradCoeffs.put(subproblem, new DenseDoubleCholeskyDecomposition(matrix));
			}
		}
	}
	
	private class Subproblem {
		private final VarStatus[] value;
		private final int hashCode;
		private final int numFree;
		
		private Subproblem(VarStatus[] value) {
			this.value = Arrays.copyOf(value, value.length);
			int hashCode = 0;
			int numFree = 0;
			for (int i = 0; i < this.value.length; i++) {
				switch (this.value[i]) {
				case FREE:
					numFree++;
					break;
				case FIXED:
					hashCode += Math.pow(i+1, 2);
					break;
				case LOWER_BOUND:
					hashCode += Math.pow(i+1, 3);
					break;
				case UPPER_BOUND:
					hashCode += Math.pow(i+1, 4);
					break;
				default:
					throw new IllegalArgumentException("Unrecognized variable status.");
				}
			}
			this.hashCode = hashCode;
			this.numFree = numFree;
		}
		
		private Subproblem getFixedMask() {
			VarStatus[] mask = new VarStatus[value.length];
			for (int i = 0; i < value.length; i++) {
				if (VarStatus.LOWER_BOUND.equals(value[i]) || VarStatus.UPPER_BOUND.equals(value[i])) {
					mask[i] = VarStatus.FIXED;
				}
				else {
					mask[i] = value[i];
				}
			}
			
			return new Subproblem(mask);
		}
		
		@Override
		public int hashCode() {
			return hashCode;
		}
		
		@Override
		public boolean equals(Object other) {
			if (other instanceof Subproblem) {
				Subproblem otherSubproblem = (Subproblem) other;
				if (value.length == otherSubproblem.value.length) {
					for (int i = 0; i < value.length; i++)
						if (!value[i].equals(otherSubproblem.value[i]))
							return false;
					return true;
				}
				else
					return false;
			}
			else
				return false;
		}
	}
	
	private class CandidateSolution {
		private final double[] x;
		private final double value;
		
		private CandidateSolution (double[] x) {
			this.x = Arrays.copyOf(x, x.length);
			double termA = 0;
			double termB = 0;
			double d;
			for (int i = 0; i < this.x.length; i++) {
				d = SquaredHingeLossTerm.this.reasoner.z.get(zIndices[i])
						- y[i] / SquaredHingeLossTerm.this.reasoner.stepSize;
				termA += coeffs[i] * this.x[i];
				termB += (this.x[i] - d) * (this.x[i] - d);
			}
			termA -= constant;
			this.value = weight * termA * termA + SquaredHingeLossTerm.this.reasoner.stepSize / 2 * termB;
		}
	}

}
