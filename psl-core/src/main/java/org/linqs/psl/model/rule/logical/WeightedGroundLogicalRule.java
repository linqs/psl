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
package org.linqs.psl.model.rule.logical;

import java.util.List;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.weight.Weight;
import org.linqs.psl.reasoner.function.ConstantNumber;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.MaxFunction;
import org.linqs.psl.reasoner.function.PowerOfTwo;

public class WeightedGroundLogicalRule extends AbstractGroundLogicalRule
		implements WeightedGroundRule {
	
	private Weight weight;
	private final boolean squared;
	
	protected WeightedGroundLogicalRule(WeightedLogicalRule r, List<GroundAtom> posLiterals,
			List<GroundAtom> negLiterals, boolean squared) {
		super(r, posLiterals, negLiterals);
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
		if (posLiterals.size() + negLiterals.size() == 1)
			return (squared) ? new PowerOfTwo(getFunction()) : getFunction();
		else
			return (squared) ? new PowerOfTwo(MaxFunction.of(getFunction(), new ConstantNumber(0.0)))
					: MaxFunction.of(getFunction(), new ConstantNumber(0.0));
	}

	@Override
	public double getIncompatibility() {
		double inc = 1.0 - getTruthValue();
		return (squared) ? inc * inc : inc;
	}
	
	@Override
	public String toString() {
		return "" + getWeight().getWeight() + ": " + super.toString()
				+ ((squared) ? " ^2" : "");
	}
}
