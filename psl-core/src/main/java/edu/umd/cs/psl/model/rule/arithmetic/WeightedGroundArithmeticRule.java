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
package edu.umd.cs.psl.model.rule.arithmetic;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.rule.WeightedGroundRule;
import edu.umd.cs.psl.model.rule.WeightedRule;
import edu.umd.cs.psl.model.rule.arithmetic.AbstractArithmeticRule.Comparator;
import edu.umd.cs.psl.model.weight.Weight;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.MaxFunction;
import edu.umd.cs.psl.reasoner.function.PowerOfTwo;

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
			Comparator comparator, double c, boolean squared) {
		super(rule, coeffs, atoms, comparator, c);
		if (Comparator.EQUAL.equals(comparator))
			throw new IllegalArgumentException("WeightedGroundArithmeticRules do not support equality comparators. "
					+ "Create two ground rules instead, one with " + Comparator.LESS_EQUAL + " and one with "
					+ Comparator.GREATER_EQUAL + ".");
		else if (!Comparator.LESS_EQUAL.equals(comparator) && !Comparator.GREATER_EQUAL.equals(comparator))
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
					(Comparator.GREATER_EQUAL.equals(comparator)) ? -1 * coeffs[i] : coeffs[i],
					atoms[i].getVariable()));
		sum.add(new FunctionSummand((Comparator.GREATER_EQUAL.equals(comparator)) ? 1 : -1, new ConstantNumber(c)));
		
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
		if (Comparator.GREATER_EQUAL.equals(comparator))
			sum *= -1;
		return (squared) ? Math.pow(Math.max(sum, 0.0), 2) : Math.max(sum, 0.0);
	}
	
	@Override
	public String toString() {
		return Double.toString(getWeight().getWeight()) + ": " + super.toString()
			+ ((squared) ? " ^2" : ""); 
	}

}
