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

import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.util.MathUtils;

import java.util.List;

/**
 * ADMMReasoner objective term of the form <br />
 * 0 if coeffs^T * x [?] constant <br />
 * infinity otherwise <br />
 * where [?] is ==, >=, or <= <br />
 *
 * All coeffs must be non-zero.
 */
public class LinearConstraintTerm extends HyperplaneTerm {
	private final FunctionComparator comparator;

	protected LinearConstraintTerm(List<LocalVariable> variables, List<Float> coeffs, float constant, FunctionComparator comparator) {
		super(variables, coeffs, constant);
		this.comparator = comparator;
	}

	/**
	 * if (coeffs^T * x [comparator] constant) { 0.0 }
	 * else { infinity }
	 */
	@Override
	public float evaluate() {
		if (comparator.equals(FunctionComparator.Equality)) {
			if (MathUtils.isZero(super.evaluate(), MathUtils.RELAXED_EPSILON)) {
				return 0.0f;
			}
			return Float.POSITIVE_INFINITY;
		} else if (comparator.equals(FunctionComparator.SmallerThan)) {
			if (super.evaluate() <= 0.0f) {
				return 0.0f;
			}
			return Float.POSITIVE_INFINITY;
		} else if (comparator.equals(FunctionComparator.LargerThan)) {
			if (super.evaluate() >= 0.0f) {
				return 0.0f;
			}
			return Float.POSITIVE_INFINITY;
		} else {
			throw new IllegalStateException("Unknown comparison function.");
		}
	}

	@Override
	public void minimize(float stepSize, float[] consensusValues) {
		// If it's not an equality constraint, first tries to minimize without the constraint.
		if (!comparator.equals(FunctionComparator.Equality)) {

			// Initializes scratch data.
			float total = 0.0f;

			// Minimizes without regard for the constraint, i.e., solves
			// argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
			for (int i = 0; i < variables.size(); i++) {
				LocalVariable variable = variables.get(i);
				variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);

				total += coeffs.get(i).floatValue() * variable.getValue();
			}

			// Checks if the solution satisfies the constraint. If so, updates
			// the local primal variables and returns.
			if ( (comparator.equals(FunctionComparator.SmallerThan) && total <= constant)
					||
				 (comparator.equals(FunctionComparator.LargerThan) && total >= constant)
				) {
				return;
			}
		}

		// If the naive minimization didn't work, or if it's an equality constraint,
		// projects onto the hyperplane
		project(stepSize, consensusValues);
	}
}
