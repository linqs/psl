/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.HashCodeBuilder;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;

/**
 * Objective term for an {@link ADMMReasoner} that is based on a squared
 * hyperplane in some way.
 * <p>
 * Stores the characterization of the hyperplane as coeffs^T * x = constant
 * and minimizes with the weighted, squared hyperplane in the objective.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
abstract class SquaredHyperplaneTerm extends ADMMObjectiveTerm implements WeightedObjectiveTerm {
	
	protected final double[] coeffs;
	protected final double constant;
	protected double weight;
	private DoubleMatrix2D L;
	
	static Map<DenseDoubleMatrix2DWithHashcode, DoubleMatrix2D> lCache = new HashMap<DenseDoubleMatrix2DWithHashcode, DoubleMatrix2D>();
	
	SquaredHyperplaneTerm(ADMMReasoner reasoner, int[] zIndices, double[] coeffs,
			double constant, double weight) {
		super(reasoner, zIndices);
		
		this.coeffs = coeffs;
		this.constant = constant;
		if (weight < 0.0)
			throw new IllegalArgumentException("Only non-negative weights are supported.");
		setWeight(weight);
		
		if (x.length >= 3) {
			computeL();
		}
		else
			L = null;
	}
	
	private void computeL() {
		double coeff;
		DenseDoubleMatrix2DWithHashcode matrix = new DenseDoubleMatrix2DWithHashcode(x.length, x.length);
		for (int i = 0; i < x.length; i++) {
			for (int j = 0; j < x.length; j++) {
				if (i == j) {
					coeff = 2 * weight * coeffs[i] * coeffs[i] + reasoner.stepSize;
					matrix.setQuick(i, i, coeff);
				}
				else {
					coeff = 2 * weight * coeffs[i] * coeffs[j];
					matrix.setQuick(i, j, coeff);
					matrix.setQuick(j, i, coeff);
				}
			}
		}
		
		L = lCache.get(matrix);
		if (L == null) {
			L = new DenseDoubleCholeskyDecomposition(matrix).getL();
			lCache.put(matrix, L);
		}
	}
	
	@Override
	public void setWeight(double weight) {
		this.weight = weight;
		if (x.length >= 3)
			computeL();
	}
	
	/**
	 * Minimizes the weighted, squared hyperplane <br />
	 * argmin weight * (coeffs^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
	 * <p>
	 * Stores the result in x.
	 */
	protected void minWeightedSquaredHyperplane() {
		/* Constructs constant term in the gradient (moved to right-hand side) */
		for (int i = 0; i < x.length; i++) {
			x[i] = reasoner.stepSize * (reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize);
			x[i] += 2 * weight * coeffs[i] * constant;
		}
		
		/* Solves for x */
		if (x.length == 1) {
			x[0] /= 2 * weight * coeffs[0] * coeffs[0] + reasoner.stepSize;
		}
		else if (x.length == 2) {
			double a0 = 2 * weight * coeffs[0] * coeffs[0] + reasoner.stepSize;
			double b1 = 2 * weight * coeffs[1] * coeffs[1] + reasoner.stepSize;
			double a1b0 = 2 * weight * coeffs[0] * coeffs[1];
			
			x[1] -= a1b0 * x[0] / a0;
			x[1] /= b1 - a1b0 * a1b0 / a0;
			
			x[0] -= a1b0 * x[1];
			x[0] /= a0;
		}
		else {
			/* Fast system solve */
			for (int i = 0; i < x.length; i++) {
				for (int j = 0; j < i; j++) {
					x[i] -= L.getQuick(i, j) * x[j];
				}
				x[i] /= L.getQuick(i, i);
			}
			for (int i = x.length-1; i >= 0; i--) {
				for (int j = x.length-1; j > i; j--) {
					x[i] -= L.getQuick(j, i) * x[j];
				}
				x[i] /= L.getQuick(i, i);
			}
		}
	}
	
	private class DenseDoubleMatrix2DWithHashcode extends DenseDoubleMatrix2D {
		
		private static final long serialVersionUID = -8102931034927566306L;
		private boolean needsNewHashcode;
		private int hashcode = 0;

		public DenseDoubleMatrix2DWithHashcode(int rows, int columns) {
			super(rows, columns);
			needsNewHashcode = true;
		}
		
		@Override
		public void setQuick(int row, int column, double value) {
			needsNewHashcode = true;
			super.setQuick(row, column, value);
		}
		
		@Override
		public int hashCode() {
			if (needsNewHashcode) {
				HashCodeBuilder builder = new HashCodeBuilder();
				for (int i = 0; i < rows(); i++)
					for (int j = 0; j < columns(); j++)
						builder.append(getQuick(i, j));
				
				hashcode = builder.toHashCode();
				needsNewHashcode = false;
			}
			return hashcode;
		}
	}
}
