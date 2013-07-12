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
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

public class GroundConstraintRule extends AbstractGroundRule implements
		GroundConstraintKernel {
	
	GroundConstraintRule(ConstraintRuleKernel k, List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
		super(k, posLiterals, negLiterals);
	}

	@Override
	public ConstraintKernel getKernel() {
		return (ConstraintKernel) kernel;
	}
	
	@Override
	public double getInfeasibility() {
		return Math.abs(getTruthValue() - 1);
	}

	@Override
	public ConstraintTerm getConstraintDefinition() {
		return new ConstraintTerm(getFunction(), FunctionComparator.SmallerThan, 0.0);
	}
	
	@Override
	public String toString() {
		return "{constraint} " + super.toString(); 
	}
}
