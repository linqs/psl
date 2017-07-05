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
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.weight.NegativeWeight;
import org.linqs.psl.model.weight.PositiveWeight;
import org.linqs.psl.model.weight.Weight;

public class WeightedLogicalRule extends AbstractLogicalRule implements WeightedRule {
	
	protected Weight weight;
	protected boolean squared;
	protected boolean mutable;

	public WeightedLogicalRule(Formula f, double w, boolean squared) {
		super(f);
		weight = (w >= 0.0) ? new PositiveWeight(w) : new NegativeWeight(w);
		this.squared = squared;
		mutable = true;
	}

	@Override
	protected WeightedGroundLogicalRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
		return new WeightedGroundLogicalRule(this, posLiterals, negLiterals, squared);
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
	public String toString() {
		return "" + weight.getWeight() + ": " + formula
				+ ((squared) ? " ^2" : "");
	}
	
	@Override
	public Rule clone() {
		return new WeightedLogicalRule(formula, weight.getWeight(), squared);
	}

	@Override
	public boolean isWeightMutable() {
		return mutable;
	}

	@Override
	public void setWeightMutable(boolean mutable) {
		this.mutable = mutable;
	}
}
