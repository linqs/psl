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

import java.util.ArrayList;
import java.util.List;

/**
 * Objective term for an ADMMReasoner that is based on a hyperplane in some way.
 *
 * Stores the characterization of the hyperplane as coeffs^T * x = constant
 * and projects onto the hyperplane.
 *
 * All coeffs must be non-zero.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public abstract class HyperplaneTerm extends ADMMObjectiveTerm {

	protected final List<Double> coeffs;
	protected final List<Double> unitNormal;
	protected final double constant;

	HyperplaneTerm(List<LocalVariable> variables, List<Double> coeffs, double constant) {
		super(variables);

		assert(variables.size() == coeffs.size());

		this.coeffs = coeffs;
		this.constant = constant;

		if (variables.size() >= 3) {
			/*
			 * Finds a unit vector normal to the hyperplane and a point in the
			 * hyperplane for future projections
			 */
			double length = 0.0;
			for (Double coeff : coeffs) {
				length += coeff.doubleValue() * coeff.doubleValue();
			}
			length = Math.sqrt(length);

			unitNormal = new ArrayList<Double>(coeffs.size());
			for (Double coeff : coeffs) {
				unitNormal.add(coeff.doubleValue() / length);
			}
		} else {
			unitNormal = null;
		}
	}

	/**
	 * Finds the orthogonal projection onto the hyperplane <br />
	 * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2 <br />
	 * such that coeffs^T * x = constant.
	 * <p>
	 * Stores the result in x.
	 */
	protected void project(double stepSize, double[] consensusValues) {
		// Deal with short hyperplanes specially.
		if (variables.size() == 1) {
			variables.get(0).setValue(constant / coeffs.get(0).doubleValue());
			return;
		}

		// Deal with short hyperplanes specially.
		if (variables.size() == 2) {
			double x0;
			double x1;
			double coeff0 = coeffs.get(0).doubleValue();
			double coeff1 = coeffs.get(1).doubleValue();

			x0 = stepSize * consensusValues[variables.get(0).getGlobalId()] - variables.get(0).getLagrange();
			x0 -= stepSize * coeff0 / coeff1 * (-1.0 * constant / coeff1 + consensusValues[variables.get(1).getGlobalId()] - variables.get(1).getLagrange() / stepSize);
			x0 /= stepSize * (1.0 + coeff0 * coeff0 / coeff1 / coeff1);

			x1 = (constant - coeff0 * x0) / coeff1;

			variables.get(0).setValue(x0);
			variables.get(1).setValue(x1);

			return;
		}

		double[] point = new double[variables.size()];
		for (int i = 0; i < variables.size(); i++) {
			point[i] = consensusValues[variables.get(i).getGlobalId()] - variables.get(i).getLagrange() / stepSize;
		}

		/* For point (constant / coeffs[0], 0,...) in hyperplane dotted with unitNormal */
		double multiplier = -1.0 * constant / coeffs.get(0).doubleValue() * unitNormal.get(0).doubleValue();

		for (int i = 0; i < variables.size(); i++) {
			multiplier += point[i] * unitNormal.get(i).doubleValue();
		}

		for (int i = 0; i < variables.size(); i++) {
			variables.get(i).setValue(point[i] - multiplier * unitNormal.get(i).doubleValue());
		}
	}
}
