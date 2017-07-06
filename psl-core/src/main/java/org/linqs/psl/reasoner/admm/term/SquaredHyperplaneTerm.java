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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.reasoner.term.WeightedTerm;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Objective term for an {@link ADMMReasoner} that is based on a squared
 * hyperplane in some way.
 * <p>
 * Stores the characterization of the hyperplane as coeffs^T * x = constant
 * and minimizes with the weighted, squared hyperplane in the objective.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public abstract class SquaredHyperplaneTerm extends ADMMObjectiveTerm implements WeightedTerm {
	protected final List<Double> coeffs;
	protected final double constant;
	protected double weight;

	private DoubleMatrix2D L;

	private static Map<DenseDoubleMatrix2DWithHashcode, DoubleMatrix2D> lCache = new HashMap<DenseDoubleMatrix2DWithHashcode, DoubleMatrix2D>();

	private static final Semaphore matrixSemaphore = new Semaphore(1);

	// TODO(eriq): All the matrix work is suspect.
	// The old code was using some cache that didn't seem too useful. Could it have been?

	SquaredHyperplaneTerm(List<LocalVariable> variables, List<Double> coeffs, double constant, double weight) {
		super(variables);

		assert(variables.size() == coeffs.size());

		this.coeffs = coeffs;
		this.constant = constant;

		L = null;

		if (weight < 0.0) {
			throw new IllegalArgumentException("Only non-negative weights are supported.");
		}
		setWeight(weight);
	}

	private void computeL(double stepSize) {
		// Since the method is synchronized, check to see if we have already computed L.
		if (L != null) {
			return;
		}

		double coeff;
		DenseDoubleMatrix2DWithHashcode matrix = new DenseDoubleMatrix2DWithHashcode(variables.size(), variables.size());
		for (int i = 0; i < variables.size(); i++) {
			for (int j = 0; j < variables.size(); j++) {
				if (i == j) {
					coeff = 2 * weight * coeffs.get(i).doubleValue() * coeffs.get(i).doubleValue() + stepSize;
					matrix.setQuick(i, i, coeff);
				}
				else {
					coeff = 2 * weight * coeffs.get(i).doubleValue() * coeffs.get(j).doubleValue();
					matrix.setQuick(i, j, coeff);
					matrix.setQuick(j, i, coeff);
				}
			}
		}

		L = lCache.get(matrix);
		if (L == null) {
			// The matrix library itself cannot be called concurrently.
			try {
				matrixSemaphore.acquire();
			} catch (InterruptedException ex) {
				throw new RuntimeException("Interrupted constructing matrix", ex);
			}

			L = new DenseDoubleCholeskyDecomposition(matrix).getL();
			lCache.put(matrix, L);

			matrixSemaphore.release();
		}
	}

	@Override
	public void setWeight(double weight) {
		this.weight = weight;
		// Recompute L.
		L = null;
	}

	/**
	 * Minimizes the weighted, squared hyperplane <br />
	 * argmin weight * (coeffs^T * x - constant)^2 + stepSize/2 * \|x - z + y / stepSize \|_2^2
	 * <p>
	 * Stores the result in x.
	 */
	protected void minWeightedSquaredHyperplane(double stepSize, double[] consensusValues) {
		// Constructs constant term in the gradient (moved to right-hand side).
		for (int i = 0; i < variables.size(); i++) {
			LocalVariable variable = variables.get(i);

			double value = stepSize * (consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
			value += 2 * weight * coeffs.get(i).doubleValue() * constant;

			variable.setValue(value);
		}

		// Solve for x

		// Handle small hyperplanes specially.
		if (variables.size() == 1) {
			LocalVariable variable = variables.get(0);
			double coeff = coeffs.get(0).doubleValue();

			variable.setValue(variable.getValue() / (2 * weight * coeff * coeff + stepSize));
			return;
		}

		// Handle small hyperplanes specially.
		if (variables.size() == 2) {
			LocalVariable variable0 = variables.get(0);
			LocalVariable variable1 = variables.get(1);
			double coeff0 = coeffs.get(0).doubleValue();
			double coeff1 = coeffs.get(1).doubleValue();

			double a0 = 2 * weight * coeff0 * coeff0 + stepSize;
			double b1 = 2 * weight * coeff1 * coeff1 + stepSize;
			double a1b0 = 2 * weight * coeff0 * coeff1;

			variable1.setValue(variable1.getValue() - a1b0 * variable0.getValue() / a0);
			variable1.setValue(variable1.getValue() / (b1 - a1b0 * a1b0 / a0));

			variable0.setValue((variable0.getValue() - a1b0 * variable1.getValue()) / a0);

			return;
		}

		// Fast system solve.
		if (L == null) {
			computeL(stepSize);
		}

		for (int i = 0; i < variables.size(); i++) {
			for (int j = 0; j < i; j++) {
				variables.get(i).setValue(variables.get(i).getValue() - L.getQuick(i, j) * variables.get(j).getValue());
			}
			variables.get(i).setValue(variables.get(i).getValue() / L.getQuick(i, i));
		}

		for (int i = variables.size() - 1; i >= 0; i--) {
			for (int j = variables.size() - 1; j > i; j--) {
				variables.get(i).setValue(variables.get(i).getValue() - L.getQuick(j, i) * variables.get(j).getValue());
			}
			variables.get(i).setValue(variables.get(i).getValue() / L.getQuick(i, i));
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
