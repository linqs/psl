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
package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.weight.Weight;
import org.linqs.psl.reasoner.function.ConstantNumber;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.reasoner.function.FunctionSummand;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.MaxFunction;
import org.linqs.psl.reasoner.function.PowerOfTwo;

/**
 * An {@link AbstractGroundArithmeticRule} that is weighted, i.e., it corresponds to
 * a weighted hinge-loss potential that measures the compatibility of {@link GroundAtom}
 * values.
 * 
 * @author Stephen Bach
 */
public class WeightedGroundArithmeticRule extends AbstractGroundArithmeticRule 
		implements WeightedGroundRule {
	
	private Weight weight;
	private final boolean squared;

	protected WeightedGroundArithmeticRule(WeightedArithmeticRule rule, double[] coeffs, GroundAtom[] atoms,
			FunctionComparator comparator, double c, boolean squared) {
		super(rule, coeffs, atoms, comparator, c);
		if (FunctionComparator.Equality.equals(comparator))
			throw new IllegalArgumentException("WeightedGroundArithmeticRules do not support equality comparators. "
					+ "Create two ground rules instead, one with " + FunctionComparator.SmallerThan + " and one with "
					+ FunctionComparator.LargerThan + ".");
		else if (!FunctionComparator.SmallerThan.equals(comparator) && !FunctionComparator.LargerThan.equals(comparator))
			throw new IllegalArgumentException("Unrecognized comparator: " + comparator);
		
		weight = null;
		this.squared = squared;
	}

	@Override
	public WeightedRule getRule() {
		return (WeightedRule) rule;
	}

	@Override
	public Weight getWeight() {
		if (weight == null) 
			return getRule().getWeight();
		return weight;
	}

	@Override
	public void setWeight(Weight w) {
		weight = w;
	}

	@Override
	public FunctionTerm getFunctionDefinition() {
		FunctionSum sum = new FunctionSum();
		for (int i = 0; i < coeffs.length; i++)
			sum.add(new FunctionSummand(
					(FunctionComparator.LargerThan.equals(comparator)) ? -1 * coeffs[i] : coeffs[i],
					atoms[i].getVariable()));
		sum.add(new FunctionSummand((FunctionComparator.LargerThan.equals(comparator)) ? 1 : -1, new ConstantNumber(c)));
		
		MaxFunction fun = new MaxFunction();
		fun.add(sum);
		fun.add(new ConstantNumber(0.0));
		return (squared) ? new PowerOfTwo(fun) : fun;
	}

	@Override
	public double getIncompatibility() {
		double sum = 0.0;
		for (int i = 0; i < coeffs.length; i++)
			sum += coeffs[i] * atoms[i].getValue();
		sum -= c;
		if (FunctionComparator.LargerThan.equals(comparator))
			sum *= -1;
		return (squared) ? Math.pow(Math.max(sum, 0.0), 2) : Math.max(sum, 0.0);
	}
	
	@Override
	public String toString() {
		return "" + getWeight().getWeight() + ": " + super.toString()
			+ ((squared) ? " ^2" : "");
	}

}
