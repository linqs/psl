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
import java.util.HashSet;
import java.util.Set;

public class Variable extends Entity {
	
	private Cone cone;
	
	private double primalValue;
	private double dualValue;
	private double objCoeff;
	
	private Set<LinearConstraint> cons;
	
	Variable(ConicProgram p, Cone c) {
		super(p);
		cone = c;
		setValue(0.5);
		setDualValue(0.5);
		doSetObjectiveCoefficient(0.0);
		cons = new HashSet<LinearConstraint>(8);
	}

	public Cone getCone() {
		return cone;
	}
	
	public Double getValue() {
		return primalValue;
	}
	
	void setValue(Double v) {
		primalValue = v;
	}
	
	public Double getDualValue() {
		return dualValue;
	}
	
	void setDualValue(Double v) {
		dualValue = v;
	}
	
	public Double getObjectiveCoefficient() {
		return objCoeff;
	}
	
	public void setObjectiveCoefficient(Double c) {
		program.verifyCheckedIn();
		doSetObjectiveCoefficient(c);
		program.notify(ConicProgramEvent.ObjCoeffChanged, this);
	}
	
	private void doSetObjectiveCoefficient(Double c) {
		objCoeff = c;
	}
	
	public Set<LinearConstraint> getLinearConstraints() {
		return Collections.unmodifiableSet(cons);
	}
	
	void notifyAddedToLinearConstraint(LinearConstraint con) {
		cons.add(con);
	}
	void notifyRemovedFromLinearConstraint(LinearConstraint con) {
		cons.remove(con);
	}
	
	boolean isDualFeasible() {
		return Math.abs(distanceFromDualFeasiblity()) < 10e-8;
	}
	
	double distanceFromDualFeasiblity() {
		double dist = 0.0;
		for (LinearConstraint lc : getLinearConstraints()) {
			dist += lc.getVariables().get(this) * lc.getLagrange();
		}
		dist += getDualValue();
		dist -= getObjectiveCoefficient();
		return dist;
	}
	
	@Override
	final void delete() {
		Set<LinearConstraint> originalCons = new HashSet<LinearConstraint>(cons);
		for (LinearConstraint lc : originalCons) {
			lc.setVariable(this, 0.0);
		}
		cone = null;
		cons = null;
	}
}
