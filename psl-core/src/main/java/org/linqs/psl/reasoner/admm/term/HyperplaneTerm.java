/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
 */
public abstract class HyperplaneTerm extends ADMMObjectiveTerm {
	protected final List<Float> coeffs;
	protected final List<Float> unitNormal;
	protected final float constant;
	// Only allocate once.
	protected final float[] point;

	HyperplaneTerm(List<LocalVariable> variables, List<Float> coeffs, float constant) {
		super(variables);

		assert(variables.size() == coeffs.size());

		this.coeffs = coeffs;
		this.constant = constant;
		this.point = new float[variables.size()];

		if (variables.size() >= 3) {
			/*
			 * Finds a unit vector normal to the hyperplane and a point in the
			 * hyperplane for future projections
			 */
			float length = 0.0f;
			for (Float coeff : coeffs) {
				length += coeff.floatValue() * coeff.floatValue();
			}
			length = (float)Math.sqrt(length);

			unitNormal = new ArrayList<Float>(coeffs.size());
			for (Float coeff : coeffs) {
				unitNormal.add(coeff.floatValue() / length);
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
	protected void project(float stepSize, float[] consensusValues) {
		// Deal with short hyperplanes specially.
		if (variables.size() == 1) {
			variables.get(0).setValue(constant / coeffs.get(0).floatValue());
			return;
		}

		// Deal with short hyperplanes specially.
		if (variables.size() == 2) {
			float x0;
			float x1;
			float coeff0 = coeffs.get(0).floatValue();
			float coeff1 = coeffs.get(1).floatValue();

			x0 = stepSize * consensusValues[variables.get(0).getGlobalId()] - variables.get(0).getLagrange();
			x0 -= stepSize * coeff0 / coeff1 * (-1.0 * constant / coeff1 + consensusValues[variables.get(1).getGlobalId()] - variables.get(1).getLagrange() / stepSize);
			x0 /= stepSize * (1.0 + coeff0 * coeff0 / coeff1 / coeff1);

			x1 = (constant - coeff0 * x0) / coeff1;

			variables.get(0).setValue(x0);
			variables.get(1).setValue(x1);

			return;
		}

		for (int i = 0; i < variables.size(); i++) {
			point[i] = consensusValues[variables.get(i).getGlobalId()] - variables.get(i).getLagrange() / stepSize;
		}

		/* For point (constant / coeffs[0], 0,...) in hyperplane dotted with unitNormal */
		float multiplier = -1.0f * constant / coeffs.get(0).floatValue() * unitNormal.get(0).floatValue();

		for (int i = 0; i < variables.size(); i++) {
			multiplier += point[i] * unitNormal.get(i).floatValue();
		}

		for (int i = 0; i < variables.size(); i++) {
			variables.get(i).setValue(point[i] - multiplier * unitNormal.get(i).floatValue());
		}
	}

	/**
	 * coeffs^T * x - constant
	 */
	@Override
	public float evaluate() {
		float value = 0.0f;
		for (int i = 0; i < variables.size(); i++) {
			value += coeffs.get(i).floatValue() * variables.get(i).getValue();
		}
		return value - constant;
	}
}
