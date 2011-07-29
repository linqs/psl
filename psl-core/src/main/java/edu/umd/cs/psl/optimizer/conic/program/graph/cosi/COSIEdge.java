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

import edu.umd.cs.psl.optimizer.conic.program.graph.Edge;
import edu.umd.cs.psl.optimizer.conic.program.graph.Node;

public class COSIEdge extends COSINode implements Edge {
	
	COSIEdge(COSIGraph g, edu.umd.umiacs.dogma.diskgraph.core.Edge e) {
		super(g, e);
	}

	@Override
	public int getArity() {
		return ((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).getArity();
	}

	@Override
	public String getEdgeType() {
		return ((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).getEdgeType().getName();
	}

	@Override
	public Collection<? extends Node> getEndNodes() {
		return new CollectionProxy<Node>(graph, 
				((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).getEndNodes());
	}

	@Override
	public Collection<? extends Node> getNodes() {
		return new CollectionProxy<Node>(graph, 
				((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).getNodes());
	}

	@Override
	public Node getStart() {
		return wrap(graph, ((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).getStart());
	}

	@Override
	public Collection<? extends Node> getStartNodes() {
		return new CollectionProxy<Node>(graph, 
				((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).getStartNodes());
	}

	@Override
	public boolean isBinaryEdge() {
		return ((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).isBinaryEdge();
	}

	@Override
	public boolean isIncidentOn(Node n) {
		if (n instanceof COSINode)
			return ((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).isIncidentOn(((COSINode) n).node);
		else
			return false;
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
	public boolean isSelfLoop(Node n) {
		if (node instanceof COSINode)
			return ((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).isSelfLoop(((COSINode) n).node);
		else
			return false;
	}

	@Override
	public boolean isSimple() {
		return ((edu.umd.umiacs.dogma.diskgraph.core.Edge) node).isSimple();
	}
}
