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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.util.IteratorUtils;

import java.util.List;

public class WeightedGroundLogicalRule extends AbstractGroundLogicalRule implements WeightedGroundRule {
	private double weight;
	private final boolean squared;

	protected WeightedGroundLogicalRule(WeightedLogicalRule rule, List<GroundAtom> posLiterals,
			List<GroundAtom> negLiterals, int rvaCount, boolean squared) {
		super(rule, posLiterals, negLiterals, rvaCount);
		// TODO(eriq): I hate this weight deferment. See if it is actually necessary.
		weight = Double.NaN;
		this.squared = squared;
		function.setSquared(squared);
	}

	@Override
	public WeightedRule getRule() {
		return (WeightedRule)rule;
	}

	@Override
	public boolean isSquared() {
		return squared;
	}

	@Override
	public double getWeight() {
		if (Double.isNaN(weight)) {
			return getRule().getWeight();
		}
		return weight;
	}

	@Override
	public void setWeight(double weight) {
		this.weight = weight;
	}

	@Override
	public GeneralFunction getFunctionDefinition() {
		return function;
	}

	@Override
	public double getIncompatibility() {
		return function.getValue();
	}

	@Override
	public double getIncompatibility(GroundAtom replacementAtom, double replacementValue) {
		return function.getValue(replacementAtom, replacementValue);
	}

	@Override
	public String toString() {
		return "" + getWeight() + ": " + super.toString() + ((squared) ? " ^2" : "");
	}

	@Override
	protected GroundRule instantiateNegatedGroundRule(
			Formula disjunction, List<GroundAtom> positiveAtoms,
			List<GroundAtom> negativeAtoms, String name) {
		int rvaCount = 0;
		for (GroundAtom atom : IteratorUtils.join(positiveAtoms, negativeAtoms)) {
			if (atom instanceof RandomVariableAtom) {
				rvaCount++;
			}
		}

		WeightedLogicalRule newRule = new WeightedLogicalRule(rule.getFormula(), -1.0 * ((WeightedLogicalRule)rule).getWeight(), squared, name);
		return new WeightedGroundLogicalRule(newRule, positiveAtoms, negativeAtoms, rvaCount, squared);
	}
}
