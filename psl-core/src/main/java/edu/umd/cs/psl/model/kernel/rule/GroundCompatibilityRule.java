/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.MaxFunction;
import edu.umd.cs.psl.reasoner.function.PowerOfTwo;

public class GroundCompatibilityRule extends AbstractGroundRule implements
		GroundCompatibilityKernel {
	
	private Weight weight;
	private final boolean squared;
	
	GroundCompatibilityRule(CompatibilityRuleKernel k, List<GroundAtom> posLiterals,
			List<GroundAtom> negLiterals, boolean squared) {
		super(k, posLiterals, negLiterals);
		weight = null;
		this.squared = squared;
	}

	@Override
	public CompatibilityKernel getKernel() {
		return (CompatibilityKernel) kernel;
	}

	@Override
	public Weight getWeight() {
		if (weight == null) 
			return getKernel().getWeight();
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
		return "{" + getWeight().toString() + "} " + super.toString()
				+ ((squared) ? " {squared}" : "");
	}
}
