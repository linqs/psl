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
package org.linqs.psl.util.graph.partition.hierarchical;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.util.graph.Graph;
import org.linqs.psl.util.graph.Node;
import org.linqs.psl.util.graph.weight.NodeWeighter;
import org.linqs.psl.util.graph.weight.RelationshipWeighter;

class CoarseningResult {

	final Map<Node,SuperNode> map = new HashMap<Node,SuperNode>();
	RelationshipWeighter rweight = null;
	NodeWeighter nweight = null;
	Graph g = null;
	
	private int noSuperNodes = 0;
	private final Set<Node> superNodes = new HashSet<Node>();
	
	int noBaseNodes = 0;
	
	double getShrinkageFactor() {
		return (1.0*noSuperNodes)/noBaseNodes;
	}
	
	int getNoSuperNodes() {
		return noSuperNodes;
	}
	
	void addSuperNode(SuperNode n) {
		if (superNodes.add(n.getRepresentationNode())) noSuperNodes++;
	}
	
	Set<Node> getSuperNodes() {
		return superNodes;
	}
	
}
