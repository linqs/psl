/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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

public class GroundCompatibilityRule extends AbstractGroundRule implements
		GroundCompatibilityKernel {
	
	private Weight weight;
	
	GroundCompatibilityRule(CompatibilityRuleKernel k, List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
		super(k, posLiterals, negLiterals);
		weight = ((CompatibilityKernel) kernel).getWeight();
	}

	@Override
	public CompatibilityKernel getKernel() {
		return (CompatibilityKernel) kernel;
	}

	@Override
	public Weight getWeight() {
		return weight;
	}
	
	@Override
	public void setWeight(Weight w) {
		weight = w;
	}
	
	@Override
	public FunctionTerm getFunctionDefinition() {
		if (posLiterals.size() + negLiterals.size() == 1)
			return getFunction(numGroundings);
		else
			return MaxFunction.of(getFunction(numGroundings), new ConstantNumber(0.0));
	}

	@Override
	public double getIncompatibility() {
		return numGroundings*(1.0-getTruthValue());
	}
	
	@Override
	public double getIncompatibilityHessian(int parameterNo1, int parameterNo2) {
		assert parameterNo1==0 && parameterNo2==0;
		return 0;
	}

	@Override
	public double getIncompatibilityDerivative(int parameterNo) {
		assert parameterNo==0;
		return numGroundings*(1.0-getTruthValue());
	}
	
	@Override
	public String toString() {
		return "{" + getWeight().toString() + "} " + super.toString(); 
	}
}
