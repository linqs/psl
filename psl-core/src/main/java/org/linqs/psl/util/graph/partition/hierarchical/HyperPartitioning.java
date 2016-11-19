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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.util.graph.Graph;
import org.linqs.psl.util.graph.Node;
import org.linqs.psl.util.graph.Relationship;
import org.linqs.psl.util.graph.weight.ConstantOneNodeWeighter;
import org.linqs.psl.util.graph.weight.NodeWeighter;
import org.linqs.psl.util.graph.weight.PropertyNodeWeighter;
import org.linqs.psl.util.graph.weight.RelationshipWeighter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import de.mathnbits.util.RandomStack;

public class HyperPartitioning extends HierarchicalPartitioning {

	private static final Logger log = LoggerFactory.getLogger(HyperPartitioning.class);
	
	private double getHyperWeight(Node node, RelationshipWeighter rweight) {
		for (Relationship rel : node.getRelationships())
			if (rel.getStart().equals(node))
				return rweight.getWeight(rel);
		
		return Double.NaN;
	}
	
	@Override
	public double partition(Graph g, Iterable<? extends Node> nodes,
			RelationshipWeighter rweight, List<? extends Collection<Node>> partition) {
		return this.partition(g, nodes, rweight, new ConstantOneNodeWeighter(), partition);
	}
	
	public double partition(Graph g, Iterable<? extends Node> nodes,
			RelationshipWeighter rweight, NodeWeighter nweight, List<? extends Collection<Node>> partition) {
		log.debug("Hyper Partitioning Started!");
		Map<Node,SuperNode> assign = new HashMap<Node,SuperNode>();
		
		ArrayList<Node> hyperedges = new ArrayList<Node>();
		double highestWeight = 0;
		for (Node node : nodes) {
			//Check if HyperEdge with infinite weight
			Double weight = getHyperWeight(node,rweight);
			if (Double.isInfinite(weight)) {
				SuperNode sup = new SuperNode(g.createNode());
				sup.addChild(node, nweight.getWeight(node));
				assign.put(node, sup);
				
				for (Relationship rel : node.getRelationships()) {
					if (rel.getStart().equals(node)) {
						Node ngh = rel.getOtherNode(node);
						SuperNode sngh = assign.get(ngh);
						if (sngh==sup) continue;
						else if (sngh==null) {
							sup.addChild(ngh, nweight.getWeight(ngh));
							assign.put(ngh, sup);
						} else { //Merge
							for (int i=0;i<sngh.getNoChildren();i++) {
								Node child = sngh.getChild(i);
								sup.addChild(child, nweight.getWeight(child));
								assign.put(child, sup);
							}
						}
					}
				}
			} else if (!Double.isNaN(weight)) {
				Preconditions.checkArgument(weight>0,"All weights must be positive: " + weight);
				hyperedges.add(node);
				if (weight>highestWeight) highestWeight = weight;
			}
		}
		
		
		while (!hyperedges.isEmpty()) {
			double currentWeight = highestWeight/2;
			highestWeight = 0;
			
			RandomStack<Node> rnodes = new RandomStack<Node>(hyperedges);
			hyperedges = new ArrayList<Node>();
			
			while (!rnodes.isEmpty()) {
				Node node = rnodes.popRandom();
				assert !assign.containsKey(node);
				double weight = getHyperWeight(node,rweight);
				if (weight>currentWeight) {
					SuperNode sup = new SuperNode(g.createNode());
					sup.addChild(node, nweight.getWeight(node));
					assign.put(node, sup);
					for (Relationship rel : node.getRelationships()) {
						if (rel.getStart().equals(node)) {
							Node ngh = rel.getOtherNode(node);
							if (!assign.containsKey(ngh)) {
								sup.addChild(ngh, nweight.getWeight(ngh));
								assign.put(ngh, sup);
							}
						}
					}
				} else {
					hyperedges.add(node);
					if (weight>highestWeight) highestWeight = weight;
				}
			}
		}
		
		for (Node node : nodes) if (!Double.isNaN(getHyperWeight(node,rweight))) {
			assert !Double.isInfinite(getHyperWeight(node,rweight));
			hyperedges.add(node);
		}
		
		/* Constructs new graph for coarsening */
		Set<SuperNode> supernodes = new HashSet<SuperNode>(assign.values());
		RelationshipWeighter newrweight = createCoarseGraph(g,supernodes,assign,rweight);
		NodeWeighter newnweight = new PropertyNodeWeighter(weightType);
		
		//Map partition back
		List<Node> nextNodes = new ArrayList<Node>(supernodes.size());
		Map<Node,SuperNode> inverse = new HashMap<Node,SuperNode>(supernodes.size());
		for (SuperNode sn : supernodes) {
			nextNodes.add(sn.getRepresentationNode());
			inverse.put(sn.getRepresentationNode(), sn);
		}
		List<List<Node>> newpartition = new ArrayList<List<Node>>(partition.size());
		for (int i=0;i<partition.size();i++) newpartition.add(new ArrayList<Node>());
		double cut = super.partition(g, nextNodes, newrweight, newnweight, newpartition);
		for (int pid=0;pid<partition.size();pid++) {
			Collection<Node> block = partition.get(pid);
			for (Node sn : newpartition.get(pid)) {
				SuperNode supern = inverse.get(sn);
				for (int i=0;i<supern.getNoChildren();i++) {
					block.add(supern.getChild(i));
				}
			}
		}
		return cut;
	}
	
}
