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
package edu.umd.cs.psl.optimizer.conic.program;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LinearConstraint extends Entity {
	
	private Map<Variable, Double> vars;
	
	private double constrainedValue;
	private double lagrange;
	
	protected static final String UNOWNED_VAR = "Variable does not belong to this conic program.";
	
	LinearConstraint(ConicProgram p) {
		super(p);
		vars = new HashMap<Variable, Double>(8);
		doSetConstrainedValue(0.0);
		setLagrange(0.0);
		program.notify(ConicProgramEvent.ConCreated, this);
	}
	
	public void setVariable(Variable v, Double coefficient) {
		program.verifyCheckedIn();
		Double currentCoefficient = vars.get(v);
		if (currentCoefficient != null) {
			if (coefficient == 0.0) {
				vars.remove(v);
				v.notifyRemovedFromLinearConstraint(this);
				program.notify(ConicProgramEvent.VarRemovedFromCon, this, v);
			}
			else if (coefficient != currentCoefficient) {
				vars.put(v, coefficient);
				program.notify(ConicProgramEvent.ConCoeffChanged, this, new Object[] {v, currentCoefficient});
			}
		}
		else if (coefficient != 0.0) {
			vars.put(v, coefficient);
			v.notifyAddedToLinearConstraint(this);
			program.notify(ConicProgramEvent.VarAddedToCon, this, v);
		}
	}

	public Map<Variable, Double> getVariables() {
		return Collections.unmodifiableMap(vars);
	}

	public Double getConstrainedValue() {
		return constrainedValue;
	}

	public void setConstrainedValue(Double v) {
		program.verifyCheckedIn();
		doSetConstrainedValue(v);
		program.notify(ConicProgramEvent.ConValueChanged, this);
	}
	
	private void doSetConstrainedValue(Double v) {
		constrainedValue = v;
	}
	
	public Double getLagrange() {
		return lagrange;
	}
	
	void setLagrange(Double l) {
		lagrange = l;
	}
	
	boolean isPrimalFeasible() {
		return Math.abs(distanceFromPrimalFeasibility()) < 10e-8;
	}
	
	double distanceFromPrimalFeasibility() {
		double dist = 0.0;
		for (Map.Entry<Variable, Double> e : getVariables().entrySet()) {
			dist += e.getValue() * e.getKey().getValue();
		}
		dist -= getConstrainedValue();
		return dist;
	}
	
	@Override
	final public void delete() {
		program.verifyCheckedIn();
		Set<Variable> originalVars = new HashSet<Variable>(getVariables().keySet());
		for (Variable var : originalVars) {
			setVariable(var, 0.0);
		}
		program.notify(ConicProgramEvent.ConDeleted, this, Collections.unmodifiableSet(originalVars));
		vars = null;
	}
}
