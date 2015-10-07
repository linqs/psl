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
package edu.umd.cs.psl.model.kernel.rule;

import java.util.List;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.NegativeWeight;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;

public class CompatibilityRuleKernel extends AbstractRuleKernel implements CompatibilityKernel {
	
	protected Weight weight;
	protected boolean squared;
	protected boolean mutable;

	public CompatibilityRuleKernel(Formula f, double w, boolean squared) {
		super(f);
		weight = (w >= 0.0) ? new PositiveWeight(w) : new NegativeWeight(w);
		this.squared = squared;
		mutable = true;
	}

	@Override
	protected GroundCompatibilityRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
		return new GroundCompatibilityRule(this, posLiterals, negLiterals, squared);
	}
	
	@Override
	public Weight getWeight() {
		return weight.duplicate();
	}
	
	@Override
	public void setWeight(Weight w) {
		if (!mutable)
			throw new IllegalStateException("Kernel weight is not mutable.");
		
		weight = w;
	}
	
	@Override
	public String toString() {
		return "{" + weight.getWeight() + "} " + formula
				+ ((squared) ? " {squared}" : "");
	}
	
	@Override
	public Kernel clone() {
		return new CompatibilityRuleKernel(formula, weight.getWeight(), squared);
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
