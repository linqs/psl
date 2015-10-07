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

/**
 * {@link ADMMReasoner} objective term of the form <br />
 * weight * coeffs^T * x
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
class LinearLossTerm extends ADMMObjectiveTerm implements WeightedObjectiveTerm {
	
	private final double[] coeffs;
	private double weight;
	
	LinearLossTerm(ADMMReasoner reasoner, int[] zIndices, double[] coeffs, double weight) {
		super(reasoner, zIndices);
		this.coeffs = coeffs;
		setWeight(weight);
	}

	@Override
	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	@Override
	protected void minimize() {
		for (int i = 0; i < x.length; i++) {
			x[i] = reasoner.z.get(zIndices[i]) - y[i] / reasoner.stepSize;
			x[i] -= weight * coeffs[i] / reasoner.stepSize;
		}
	}
}
