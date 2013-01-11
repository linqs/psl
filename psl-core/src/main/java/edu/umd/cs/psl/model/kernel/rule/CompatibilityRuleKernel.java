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

import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;

public class CompatibilityRuleKernel extends AbstractRuleKernel implements CompatibilityKernel {
	
	protected PositiveWeight weight;
	
	private final int hashcode;

	public CompatibilityRuleKernel(Formula f, double w) {
		super(f);
		weight = new PositiveWeight(w);
		hashcode = new HashCodeBuilder().append(formula).append(weight).toHashCode();
	}

	@Override
	protected GroundCompatibilityRule groundFormulaInstance(Formula f) {
		return new GroundCompatibilityRule(this, f);
	}

	@Override
	public boolean isCompatibilityKernel() {
		return true;
	}
	
	@Override
	public Weight getWeight() {
		return weight.duplicate();
	}
	
	@Override
	public void setWeight(Weight w) {
		if (!(w instanceof PositiveWeight)) 
			throw new IllegalArgumentException("Expected PositiveWeight weight.");
		
		weight = (PositiveWeight) w;
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
		return new CompatibilityRuleKernel(formula, weight.getWeight());
	}
}
