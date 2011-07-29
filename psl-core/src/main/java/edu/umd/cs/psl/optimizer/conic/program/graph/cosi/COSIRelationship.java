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
package edu.umd.cs.psl.optimizer.conic.program.graph.cosi;

import java.util.Collection;

import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.Relationship;

public class COSIRelationship extends COSIEdge implements Relationship {
	protected static final String ILLEGAL_NODE = "Node is not incident on this edge."; 
	
	COSIRelationship(COSIGraph g, edu.umd.umiacs.dogma.diskgraph.core.Relationship r) {
		super(g, r);
	}
	
	@Override
	public Node getEnd() {
		return wrap(graph, ((edu.umd.umiacs.dogma.diskgraph.core.Relationship) node).getEnd());
	}

	@Override
	public int getMultiplicity(Node n) {
		if (n instanceof COSINode) {
			return ((edu.umd.umiacs.dogma.diskgraph.core.Relationship) node).getMultiplicity(
					(edu.umd.umiacs.dogma.diskgraph.core.Node) n);
		}
		else
			throw new IllegalArgumentException(ILLEGAL_NODE);
	}

	@Override
	public Node getOtherNode(Node n) {
		if (n instanceof COSINode) {
			return wrap(graph, ((edu.umd.umiacs.dogma.diskgraph.core.Relationship) node).getOtherNode(
					(edu.umd.umiacs.dogma.diskgraph.core.Node) n));
		}
		else
			throw new IllegalArgumentException(ILLEGAL_NODE);
	}

	@Override
	public Collection<? extends Node> getOtherNodes(Node n) {
		if (n instanceof COSINode) {
			return new CollectionProxy<Node>(graph,
					((edu.umd.umiacs.dogma.diskgraph.core.Relationship) node).getOtherNodes(
							(edu.umd.umiacs.dogma.diskgraph.core.Node) n));
		}
		else
			throw new IllegalArgumentException(ILLEGAL_NODE);
	}

	@Override
	public String getRelationshipType() {
		return ((edu.umd.umiacs.dogma.diskgraph.core.Relationship) node).getRelationshipType().getName();
	}
}
