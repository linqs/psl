/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
 * {@link ADMMReasoner} objective term of the form <br />
 * weight * max(coeffs^T * x - constant, 0)
 * <p>
 * All coeffs must be non-zero.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class HingeLossTerm extends HyperplaneTerm implements WeightedTerm {
	private double weight;
	
	HingeLossTerm(List<LocalVariable> variables, List<Double> coeffs, double constant, double weight) {
		super(variables, coeffs, constant);

		if (weight < 0.0) {
			throw new IllegalArgumentException("Only non-negative weights are supported.");
		}
		setWeight(weight);
	}

	@Override
	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	@Override
	public void minimize(double stepSize, double[] consensusValues) {
		/* Initializes scratch data */
		double total = 0.0;
		
		/*
		 * Minimizes without the linear loss, i.e., solves
		 * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
		 */
		for (int i = 0; i < variables.size(); i++) {
			LocalVariable variable = variables.get(i);
			variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
			total += (coeffs.get(i).doubleValue() * variable.getValue());
		}
		
		/* If the linear loss is NOT active at the computed point, it is the solution... */
		if (total <= constant) {
			return;
		}
		
		/*
		 * Else, minimizes with the linear loss, i.e., solves
		 * argmin weight * coeffs^T * x + stepSize/2 * \|x - z + y / stepSize \|_2^2
		 */
		total = 0.0;
		for (int i = 0; i < variables.size(); i++) {
			LocalVariable variable = variables.get(i);

			// TODO(eriq): We just took this step above. Is ADMM accidentally taking two steps?
			variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);
			variable.setValue(variable.getValue() - weight * coeffs.get(i).doubleValue() / stepSize);

			total += coeffs.get(i).doubleValue() * variable.getValue();
		}
		
		/* If the linear loss IS active at the computed point, it is the solution... */
		if (total >= constant) {
			return;
		}
		
		/* Else, the solution is on the hinge */
		project(stepSize, consensusValues);
	}
}
