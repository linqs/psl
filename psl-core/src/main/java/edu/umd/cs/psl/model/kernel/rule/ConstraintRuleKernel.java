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

public class ConstraintRuleKernel extends AbstractRuleKernel {
	
	private final int hashcode;

	public ConstraintRuleKernel(Model m, Formula f) {
		super(m, f);
		hashcode = new HashCodeBuilder().append(model).append(formula).toHashCode();
	}

	@Override
	public Parameters getParameters() {
		return Parameters.NoParameters;
	}
	
	@Override
	public void setParameters(Parameters para) {
		throw new UnsupportedOperationException("Aggregate Predicates have no parameters!");
	}

	@Override
	public boolean isCompatibilityKernel() {
		return false;
	}

	@Override
	protected AbstractGroundRule groundFormulaInstance(Formula f) {
		return new GroundConstraintRule(this, f);
	}
	
	@Override
	public String toString() {
		return "{constraint} " + formula; 
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public Kernel clone() {
		return new ConstraintRuleKernel(model, formula);
	}
}
