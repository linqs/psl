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

import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;

class ConstraintConicProgramProxy extends ConicProgramProxy {

	protected LinearConstraint lc = null;
	protected Variable slackVar = null;
	
	ConstraintConicProgramProxy(ConicReasoner reasoner, ConstraintTerm con, GroundKernel gk) {
		super(reasoner, gk);
		updateConstraint(con);
	}

	void updateConstraint(ConstraintTerm con) {
		Variable v;
		
		if (lc != null) lc.delete();
		lc = reasoner.program.createConstraint();
		FunctionTerm fun = con.getFunction();
		double constrainedValue = con.getValue();
		
		if (fun instanceof FunctionSum) {
			FunctionSum sum = (FunctionSum)fun;
			for (FunctionSummand summand : sum) {
				if (summand.getTerm() instanceof ConicReasonerSingleton) {
					v = ((ConicReasonerSingleton)summand.getTerm()).getVariable();
					lc.setVariable(v, summand.getCoefficient()
							+ ((lc.getVariables().get(v) != null) ? lc.getVariables().get(v) : 0.0));
				}
				else if (summand.getTerm().isConstant()) {
					constrainedValue -= summand.getTerm().getValue() * summand.getCoefficient();
				}
				else if (summand.getTerm() instanceof AtomFunctionVariable) {
					v = reasoner.getVarProxy((AtomFunctionVariable)summand.getTerm()).getVariable();
					lc.setVariable(v, summand.getCoefficient()
							+ ((lc.getVariables().get(v) != null) ? lc.getVariables().get(v) : 0.0));
				}
				else
					throw new IllegalArgumentException("Unsupported FunctionSingleton: " + summand.getTerm());
			}
		}
		else if (fun instanceof FunctionSummand) {
			FunctionSummand summand = (FunctionSummand)fun;
			if (summand.getTerm() instanceof ConicReasonerSingleton) {
				v = ((ConicReasonerSingleton)summand.getTerm()).getVariable();
				lc.setVariable(v, summand.getCoefficient());
			}
			else if (summand.getTerm() instanceof AtomFunctionVariable) {
				v = reasoner.getVarProxy((AtomFunctionVariable) summand.getTerm()).getVariable();
				lc.setVariable(v, summand.getCoefficient());
			}
			else
				throw new IllegalArgumentException("Unsupported FunctionSingleton: " + summand.getTerm());
		}
		else
			throw new IllegalArgumentException("Currently, only sums and summands are supported!");
		
		lc.setConstrainedValue(constrainedValue);
		if (!con.getComparator().equals(FunctionComparator.Equality)) {
			if (slackVar == null) {
				slackVar = reasoner.program.createNonNegativeOrthantCone().getVariable();
				slackVar.setObjectiveCoefficient(0.0);
			}
			if (con.getComparator().equals(FunctionComparator.LargerThan))
				lc.setVariable(slackVar, -1.0);
			else
				lc.setVariable(slackVar, 1.0);
		}
		else if (slackVar != null) {
			slackVar.getCone().delete();
			slackVar = null;
		}
	}

	@Override
	void remove() {
		if (lc != null) {
			lc.delete();
			lc = null;
		}
		if (slackVar != null) {
			slackVar.getCone().delete();
			slackVar = null;
		}
	}
}
