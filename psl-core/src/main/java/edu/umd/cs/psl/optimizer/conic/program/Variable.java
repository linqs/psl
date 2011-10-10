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
package edu.umd.cs.psl.optimizer.conic.program;

import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.psl.util.graph.Node;
import edu.umd.cs.psl.util.graph.Relationship;

public class Variable extends Entity {
	Variable(ConicProgram p) {
		super(p);
		setValue(0.5);
		setDualValue(0.5);
		doSetObjectiveCoefficient(0.0);
	}
	
	Variable(ConicProgram p, Node n) {
		super(p, n);
	}
	
	@Override
	NodeType getType() {
		return NodeType.var;
	}

	public Double getValue() {
		return (Double) node.getAttribute(ConicProgram.VAR_VALUE);
	}
	
	void setValue(Double v) {
		for (Node n : node.getProperties(ConicProgram.VAR_VALUE))
			n.delete();
		node.createProperty(ConicProgram.VAR_VALUE, v);
	}
	
	public Double getObjectiveCoefficient() {
		return (Double) node.getAttribute(ConicProgram.OBJ_COEFF);
	}
	
	public void setObjectiveCoefficient(Double c) {
		program.verifyCheckedIn();
		doSetObjectiveCoefficient(c);
		program.notify(ConicProgramEvent.ObjCoeffChanged, this);
	}
	
	private void doSetObjectiveCoefficient(Double c) {
		for (Node n : node.getProperties(ConicProgram.OBJ_COEFF))
			n.delete();
		node.createProperty(ConicProgram.OBJ_COEFF, c);
	}

	public Cone getCone() {
		return (Cone) Entity.createEntity(program, node.getRelationshipIterator(ConicProgram.CONE_REL).next().getStart());
	}
	
	public Set<LinearConstraint> getLinearConstraints() {
		Set<LinearConstraint> lc = new HashSet<LinearConstraint>();
		for (Relationship rel : node.getRelationships(ConicProgram.LC_REL)) {
			lc.add((LinearConstraint) Entity.createEntity(program, rel.getStart()));
		}
		return lc;
	}
	
	public Double getDualValue() {
		return (Double) node.getAttribute(ConicProgram.VAR_DUAL_VALUE);
	}
	
	void setDualValue(Double v) {
		for (Node n : node.getProperties(ConicProgram.VAR_DUAL_VALUE))
			n.delete();
		node.createProperty(ConicProgram.VAR_DUAL_VALUE, v);
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
		for (LinearConstraint lc : getLinearConstraints()) {
			lc.removeVariable(this);
		}
		super.delete();
	}
}
