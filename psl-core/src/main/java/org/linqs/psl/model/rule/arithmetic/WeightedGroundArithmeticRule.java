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
package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.GeneralFunction;

import java.util.List;

public class WeightedGroundArithmeticRule extends AbstractGroundArithmeticRule implements WeightedGroundRule {
	protected WeightedGroundArithmeticRule(WeightedArithmeticRule rule, List<Double> coeffs,
			List<GroundAtom> atoms, FunctionComparator comparator, double constant) {
		super(rule, coeffs, atoms, comparator, constant);
		validate();
	}

	protected WeightedGroundArithmeticRule(WeightedArithmeticRule rule, double[] coeffs, GroundAtom[] atoms,
			FunctionComparator comparator, double constant) {
		super(rule, coeffs, atoms, comparator, constant);
		validate();
	}

	private void validate() {
		if (FunctionComparator.Equality.equals(comparator)) {
			throw new IllegalArgumentException("WeightedGroundArithmeticRules do not support equality comparators. "
					+ "Create two ground rules instead, one with " + FunctionComparator.SmallerThan + " and one with "
					+ FunctionComparator.LargerThan + ".");
		} else if (!FunctionComparator.SmallerThan.equals(comparator) && !FunctionComparator.LargerThan.equals(comparator)) {
			throw new IllegalArgumentException("Unrecognized comparator: " + comparator);
		}
	}

	@Override
	public WeightedRule getRule() {
		return (WeightedRule)rule;
	}

	@Override
	public boolean isSquared() {
		return ((WeightedRule)rule).isSquared();
	}

	@Override
	public double getWeight() {
		return ((WeightedRule)rule).getWeight();
	}

	@Override
	public void setWeight(double weight) {
		((WeightedRule)rule).setWeight(weight);
	}

	@Override
	public GeneralFunction getFunctionDefinition() {
		GeneralFunction sum = new GeneralFunction(true, isSquared(), coeffs.length);

		double termSign = FunctionComparator.LargerThan.equals(comparator) ? -1.0 : 1.0;
		for (int i = 0; i < coeffs.length; i++) {
			// Skip any special predicates.
			if (atoms[i].getPredicate() instanceof SpecialPredicate) {
				continue;
			}

			sum.add(termSign * coeffs[i], atoms[i]);
		}
		sum.add(-1.0 * termSign * constant);

		return sum;
	}

	@Override
	public double getIncompatibility() {
		return getIncompatibility(null, 0);
	}

	@Override
	public double getIncompatibility(GroundAtom replacementAtom, double replacementValue) {
		double sum = 0.0;
		for (int i = 0; i < coeffs.length; i++) {
			// Skip any special predicates.
			if (atoms[i].getPredicate() instanceof SpecialPredicate) {
				continue;
			}

			if (atoms[i] == replacementAtom) {
				sum += coeffs[i] * replacementValue;
			} else {
				sum += coeffs[i] * atoms[i].getValue();
			}
		}
		sum -= constant;

		if (FunctionComparator.LargerThan.equals(comparator)) {
			sum *= -1;
		}

		return (isSquared()) ? Math.pow(Math.max(sum, 0.0), 2) : Math.max(sum, 0.0);
	}

	@Override
	public String toString() {
		return "" + getWeight() + ": " + super.toString() + ((isSquared()) ? " ^2" : "");
	}
}
