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

import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.Relationship;

public class LinearConstraint extends Entity {
	protected static final String UNOWNED_VAR = "Variable does not belong to this conic program.";
	
	LinearConstraint(ConicProgram p) {
		super(p);
		doSetConstrainedValue(0.0);
		setLagrange(0.0);
		program.notify(ConicProgramEvent.ConCreated, this);
	}
	
	LinearConstraint(ConicProgram p, Node n) {
		super(p, n);
	}

	@Override
	NodeType getType() {
		return NodeType.lc;
	}
	
	public void addVariable(Variable v, Double coefficient) {
		program.verifyCheckedIn();
		Double currentCoefficient = getVariables().get(v);
		if (currentCoefficient != null) {
			removeVariable(v);
			coefficient += currentCoefficient;
		}
		Node vNode = v.getNode();
		Relationship rel = node.createRelationship(ConicProgram.LC_REL, vNode);
		rel.createProperty(ConicProgram.LC_REL_COEFF, coefficient);
		program.notify(ConicProgramEvent.VarAddedToCon, this, v);
	}

	public void removeVariable(Variable v) {
		program.verifyCheckedIn();
		Node vNode = ((Variable) v).getNode();
		for (Relationship rel : node.getRelationships(ConicProgram.LC_REL)) {
			if (rel.getEnd().equals(vNode)) {
				rel.delete();
			}
		}
		program.notify(ConicProgramEvent.VarRemovedFromCon, this, v);
	}

	public Map<Variable, Double> getVariables() {
		Map<Variable, Double> vars = new HashMap<Variable, Double>();
		Iterable<? extends Relationship> rels = node.getRelationships(ConicProgram.LC_REL);
		for (Relationship rel : rels) {
			vars.put((Variable) Entity.createEntity(program, rel.getEnd()), (Double) rel.getAttribute(ConicProgram.LC_REL_COEFF));
		}
		return vars;
	}

	public Double getConstrainedValue() {
		return (Double) node.getAttribute(ConicProgram.LC_VALUE);
	}

	public void setConstrainedValue(Double v) {
		program.verifyCheckedIn();
		doSetConstrainedValue(v);
		program.notify(ConicProgramEvent.ConValueChanged, this);
	}
	
	private void doSetConstrainedValue(Double v) {
		for (Node n : node.getProperties(ConicProgram.LC_VALUE))
			n.delete();
		node.createProperty(ConicProgram.LC_VALUE, v);
	}
	
	Double getLagrange() {
		return (Double) node.getAttribute(ConicProgram.LAGRANGE);
	}
	
	void setLagrange(Double v) {
		for (Node n : node.getProperties(ConicProgram.LAGRANGE))
			n.delete();
		node.createProperty(ConicProgram.LAGRANGE, v);
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
		for (Variable var : getVariables().keySet()) {
			removeVariable(var);
		}
		program.notify(ConicProgramEvent.ConDeleted, this);
		super.delete();
	}
}
