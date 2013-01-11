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

import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

public class GroundConstraintRule extends AbstractGroundRule implements
		GroundConstraintKernel {
	
	public GroundConstraintRule(ConstraintRuleKernel k, Formula f) {
		super(k, f);
	}
	
	@Override
	public double getIncompatibility() {
		return (getTruthValue() == 1.0) ? 0 : Double.POSITIVE_INFINITY;
	}

	@Override
	public ConstraintTerm getConstraintDefinition() {
		return new ConstraintTerm(getFunction(1.0), FunctionComparator.SmallerThan, 0.0);
	}
	
	@Override
	public String toString() {
		return "{constraint} " + formula; 
	}
}
