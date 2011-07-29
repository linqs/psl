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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.Partitioning;
import edu.umd.cs.psl.optimizer.conic.program.graph.RelationshipWeighter;
import edu.umd.umiacs.dogma.diskgraph.core.Relationship;
import edu.umd.umiacs.dogma.operations.partition.hierarchical.HierarchicalPartitioning;
import edu.umd.umiacs.dogma.operations.partition.hierarchical.HyperPartitioning;

public class COSIPartitioning implements Partitioning {
	
	private COSIGraph graph;
	private HyperPartitioning p;
	
	COSIPartitioning(COSIGraph g) {
		graph = g;
		p = new HyperPartitioning();
		p.setNoPartitioningTrails(20);
		p.setBalanceExponent(3);
	}

	@Override
	public int getSize() {
		return p.getSize();
	}

	@Override
	public List<Set<Node>> partition(RelationshipWeighter rweight) {
		List<Set<Node>> partition = new ArrayList<Set<Node>>(getSize());
        for (int i=0; i < getSize(); i++)
        	partition.add(new HashSet<Node>());
        
        List<List<edu.umd.umiacs.dogma.diskgraph.core.Node>> nodes = p.partition(graph.getGraphDatabase().startTransaction().getAllNodes(), new RelationshipWeighterProxy(rweight));
        for (int i = 0; i < partition.size(); i++) {
        	for (edu.umd.umiacs.dogma.diskgraph.core.Node n : nodes.get(i)) {
        		partition.get(i).add(new COSINode(graph, n));
        	}
        }
        
        return partition;
	}

	@Override
	public void setSize(int size) {
		p.setSize(size);
	}
	
	private class RelationshipWeighterProxy implements edu.umd.umiacs.dogma.diskgraph.decorators.RelationshipWeighter {

		RelationshipWeighter weighter;
		
		private RelationshipWeighterProxy(RelationshipWeighter w) {
			weighter = w;
		}
		
		@Override
		public double getWeight(Relationship r) {
			return weighter.getWeight(new COSIRelationship(graph, r));
		}
		
	}

}
