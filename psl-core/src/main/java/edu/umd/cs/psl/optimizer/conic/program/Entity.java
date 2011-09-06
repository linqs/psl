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

import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.Property;

abstract class Entity {
	protected ConicProgram program;
	protected Node node;

	Entity(ConicProgram p) {
		this(p, p.getGraph().createNode());
		node.createProperty(ConicProgram.NODE_TYPE, getType());
	}
	
	Entity(ConicProgram p, Node n) {
		program = p;
		node = n;
	}
	
	abstract NodeType getType();
	
	Node getNode() {
		return node;
	}
	
	void delete() {
		for (Property p : node.getProperties())
			p.delete();
		node.delete();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o instanceof Entity)
			return node.equals(((Entity) o).node);
		else
			return false;
	}
	
	@Override
	public int hashCode() {
		return node.hashCode();
	}

	static Entity createEntity(ConicProgram p, Node n) {
		NodeType type = (NodeType) n.getAttribute(ConicProgram.NODE_TYPE);
		
		switch (type) {
		case var:
			return new Variable(p, n);
		case nnoc:
			return new NonNegativeOrthantCone(p, n);
		case soc:
			return new SecondOrderCone(p, n);
		case lc:
			return new LinearConstraint(p, n);
		default:
			throw new IllegalArgumentException("Unknown node type: " + type);
		}
	}
}