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

import org.linqs.psl.reasoner.term.WeightedTerm;

import java.util.List;

/**
 * ADMMReasoner objective term of the form <br />
 * weight * max(coeffs^T * x - constant, 0)
 *
 * All coeffs must be non-zero.
 */
public class HingeLossTerm extends HyperplaneTerm implements WeightedTerm {
	private float weight;

	HingeLossTerm(List<LocalVariable> variables, List<Float> coeffs, float constant, float weight) {
		super(variables, coeffs, constant);
		setWeight(weight);
	}

	@Override
	public void setWeight(float weight) {
		this.weight = weight;
	}

	@Override
	public float getWeight() {
		return weight;
	}

	@Override
	public void minimize(float stepSize, float[] consensusValues) {
		// Initializes scratch data,
		float total = 0.0f;

		// Minimizes without the linear loss, i.e., solves
		// argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
		for (int i = 0; i < variables.size(); i++) {
			LocalVariable variable = variables.get(i);
			variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
			total += (coeffs.get(i).floatValue() * variable.getValue());
		}

		// If the linear loss is NOT active at the computed point, it is the solution...
		if (total <= constant) {
			return;
		}

		// Else, minimizes with the linear loss, i.e., solves
		// argmin weight * coeffs^T * x + stepSize/2 * \|x - z + y / stepSize \|_2^2
		total = 0.0f;
		for (int i = 0; i < variables.size(); i++) {
			LocalVariable variable = variables.get(i);

			// TODO(eriq): We just took this step above. Is ADMM accidentally taking two steps?
			variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
			variable.setValue(variable.getValue() - weight * coeffs.get(i).floatValue() / stepSize);

			total += coeffs.get(i).floatValue() * variable.getValue();
		}

		// If the linear loss IS active at the computed point, it is the solution...
		if (total >= constant) {
			return;
		}

		// Else, the solution is on the hinge.
		project(stepSize, consensusValues);
	}

	/**
	 * weight * max(coeffs^T * x - constant, 0)
	 */
	@Override
	public float evaluate() {
		return weight * Math.max(super.evaluate(), 0.0f);
	}
}
