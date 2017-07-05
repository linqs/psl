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

import java.util.HashMap;
import java.util.Map;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.weight.NegativeWeight;
import org.linqs.psl.model.weight.PositiveWeight;
import org.linqs.psl.model.weight.Weight;
import org.linqs.psl.reasoner.function.FunctionComparator;

/**
 * A template for {@link WeightedGroundArithmeticRule WeightedGroundArithmeticRules}.
 *
 * @author Stephen Bach
 */
public class WeightedArithmeticRule extends AbstractArithmeticRule implements WeightedRule {

	protected Weight weight;
	protected boolean squared;
	protected boolean mutable;

	public WeightedArithmeticRule(ArithmeticRuleExpression expression, double w, boolean squared) {
		this(expression, new HashMap<SummationVariable, Formula>(), w, squared);
	}

	public WeightedArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses,
			double w, boolean squared) {
		super(expression, filterClauses);
		weight = (w >= 0.0) ? new PositiveWeight(w) : new NegativeWeight(w);
		this.squared = squared;
		mutable = true;
	}

	@Override
	protected AbstractGroundArithmeticRule makeGroundRule(double[] coeffs, GroundAtom[] atoms,
			FunctionComparator comparator, double c) {
		return new WeightedGroundArithmeticRule(this, coeffs, atoms, comparator, c, squared);
	}

	@Override
	public Weight getWeight() {
		return weight.duplicate();
	}

	@Override
	public void setWeight(Weight w) {
		if (!mutable)
			throw new IllegalStateException("Rule weight is not mutable.");

		weight = w;
	}

	@Override
	public boolean isWeightMutable() {
		return mutable;
	}

	@Override
	public void setWeightMutable(boolean mutable) {
		this.mutable = mutable;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(weight.getWeight());
		s.append(": ");
		s.append(expression);
		s.append((squared) ? " ^2" : "");
		for (Map.Entry<SummationVariable, Formula> e : filters.entrySet()) {
			s.append("\n{");
			// Appends the corresponding Variable, not the SummationVariable, to leave out the '+'
			s.append(e.getKey().getVariable());
			s.append(" : ");
			s.append(e.getValue());
			s.append("}");
		}
		return s.toString();
	}

}
