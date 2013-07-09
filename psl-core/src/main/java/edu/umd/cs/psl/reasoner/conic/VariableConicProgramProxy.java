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
package edu.umd.cs.psl.reasoner.conic;

import java.util.Map;

import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

class VariableConicProgramProxy extends ConicProgramProxy {
	protected Variable v;
	protected ConstraintConicProgramProxy upperBound;
	
	VariableConicProgramProxy(ConicReasoner reasoner, GroundKernel gk) {
		super(reasoner, gk);
		v = reasoner.program.createNonNegativeOrthantCone().getVariable();
		FunctionSummand summand = new FunctionSummand(1.0, new ConicReasonerSingleton(v));
		ConstraintTerm con = new ConstraintTerm(summand, FunctionComparator.SmallerThan, 1.0);
		upperBound = new ConstraintConicProgramProxy(reasoner, con, gk);
	}
	
	Variable getVariable() {
		return v;
	}
	
	@Override
	void remove() {
		Map<? extends Variable, Double> vars;
		double coeff;
		
		upperBound.remove();
		
		for (LinearConstraint lc : v.getLinearConstraints()) {
			vars = lc.getVariables();
			if (vars.size() == 1) {
				lc.delete();
			}
			else {
				coeff = vars.get(v);
				lc.setVariable(v, 0.0);
				lc.setConstrainedValue(lc.getConstrainedValue() - coeff * v.getValue());
			}
		}
		v.getCone().delete();
	}
}
