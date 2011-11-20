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

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;

public class CompatibilityRuleKernel extends AbstractRuleKernel {
	
	protected PositiveWeight weight;
	
	private final int hashcode;

	public CompatibilityRuleKernel(Model m, Formula f, double w) {
		super(m, f);
		weight = new PositiveWeight(w);
		hashcode = new HashCodeBuilder().append(model).append(formula)
				.append(weight).toHashCode();
	}

	@Override
	protected GroundCompatibilityRule groundFormulaInstance(Formula f) {
		return new GroundCompatibilityRule(this, f);
	}

	@Override
	public boolean isCompatibilityKernel() {
		return true;
	}
	
	public Weight getWeight() {
		return weight;
	}

	@Override
	public Parameters getParameters() {
		return weight.duplicate();
	}

	@Override
	public void setParameters(Parameters para) {
		if (!(para instanceof PositiveWeight)) throw new IllegalArgumentException("Expected PositiveWeight parameter.");
		PositiveWeight newweight = (PositiveWeight)para;
		if (!newweight.equals(weight)) {
			weight = newweight;
			model.notifyKernelParametersModified(this);
		}
	}
	
	@Override
	public String toString() {
		return "{" + weight.getWeight() + "} " + formula;
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public Kernel clone() {
		return new CompatibilityRuleKernel(model, formula, weight.getWeight());
	}
}
