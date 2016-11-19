/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.util.graph.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.linqs.psl.util.graph.Node;
import org.linqs.psl.util.graph.Relationship;

public class MemoryRelationship extends MemoryEdge implements Relationship {
	
	final MemoryNode endNode;
	final private Integer relationshipType;

	MemoryRelationship(MemoryGraph g, Integer rt, MemoryNode start, MemoryNode end) {
		super(g, start);
		endNode = end;
		relationshipType = rt;
	}

	@Override
	public boolean isProperty() {
		return false;
	}

	@Override
	public boolean isRelationship() {
		return true;
	}

	@Override
	public Node getEnd() {
		return endNode;
	}

	@Override
	public Node getOtherNode(Node n) {
		if (startNode.equals(n))
			return getEnd();
		else if (endNode.equals(n))
			return getStart();
		else
			throw new IllegalArgumentException("Node is not incident on this edge.");
	}

	@Override
	public String getRelationshipType() {
		return graph.getRelationshipTypeName(relationshipType);
	}

	@Override
	public boolean isSelfLoop(Node node) {
		return startNode.equals(endNode);
	}

	@Override
	public boolean isIncidentOn(Node n) {
		return startNode.equals(n) || endNode.equals(n);
	}

	@Override
	public Collection<? extends Node> getNodes() {
		List<MemoryNode> nodes = new ArrayList<MemoryNode>(2);
		nodes.add(startNode);
		nodes.add(endNode);
		return nodes;
	}

	@Override
	public void delete() {
		startNode.notifyRelationshipDeleted(this);
		endNode.notifyRelationshipDeleted(this);
		super.delete();
	}
	
}
