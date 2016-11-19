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
import org.linqs.psl.util.graph.partition.Partitioner;
import org.linqs.psl.util.graph.weight.HashRelationshipWeighter;
import org.linqs.psl.util.graph.weight.NodeWeighter;
import org.linqs.psl.util.graph.weight.PropertyNodeWeighter;
import org.linqs.psl.util.graph.weight.RelationshipWeighter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import de.mathnbits.util.RandomStack;

public class HierarchicalPartitioning implements Partitioner {

	private static final Logger log =
		LoggerFactory.getLogger(HierarchicalPartitioning.class);
	
	protected static final String relType = "connect";
	protected static final String weightType = "weight";
	
	private static final double shrinkingThreshold = 0.7;
	private static final int finalMultiple = 8;
	private static final int initialMultiple = 400;
	
	private static final int defaultNoTrials = 10;
	private static final int defaultNoPartitions = 2;
	private static final double defaultBalanceExponent = 1.5;
	
	private double balanceExponent;
	private int noTrials;
	private int noPartitions;
	
	public HierarchicalPartitioning(int size) {
		noPartitions=size;
		noTrials = defaultNoTrials;
		balanceExponent = defaultBalanceExponent;
	}

	public HierarchicalPartitioning() {
		this(defaultNoPartitions);
	}

	public void setNoPartitioningTrials(int trials) {
		Preconditions.checkArgument(trials>0,"Need to provide a positive number");
		noTrials = trials;
	}
	
	public int getNoPartitioningTrials() {
		return noTrials;
	}	
	
	public double getBalanceExponent() {
		return balanceExponent;
	}

	public void setBalanceExponent(double balanceExponent) {
		this.balanceExponent = balanceExponent;
	}

	public static final int coarseSizeThreshold(int noPartitions) {
		double alpha =  Math.pow(1.0/noPartitions,0.75);
		return (int)Math.round(alpha * (initialMultiple*noPartitions) + 
				(1-alpha) * (finalMultiple * noPartitions));	
	}
	
	@Override
	public int getSize() {
		return noPartitions;
	}
	
	@Override
	public void setSize(int size) {
		noPartitions=size;	
	}

	@Override
	public List<List<Node>> partition(Graph g, Iterable<? extends Node> nodes,
			RelationshipWeighter rweight) {
		List<List<Node>> partition = new ArrayList<List<Node>>(noPartitions);
		for (int i=0;i<noPartitions;i++) {
			partition.add(new ArrayList<Node>());
		}
		partition(g, nodes,rweight,partition);
		return partition;
	}
	
//	private static final Iterable<Node> convertSuperNode(Iterable<SuperNode> supernodes) {
//		return Iterables.transform(supernodes, new Function<SuperNode,Node>() {
//			@Override
//			public Node apply(SuperNode input) {
//				return input.getSuperNode();
//			}
//		
//		});
//	}

	@Override
	public double partition(Graph g, Iterable<? extends Node> nodes,
			RelationshipWeighter rweight, List<? extends Collection<Node>> partition) {
		return partition(g, nodes, rweight, new ConstantOneNodeWeighter(), partition);
	}

	public double partition(Graph g, Iterable<? extends Node> nodes,
			RelationshipWeighter rweight, NodeWeighter nweight, List<? extends Collection<Node>> partition) {
		if (partition==null || partition.size()!=noPartitions) 
			throw new IllegalArgumentException("Partition container does not have the right size - expected: " + noPartitions);
		for (int i=0;i<noPartitions;i++) Preconditions.checkNotNull(partition.get(i));
		
		log.debug("Partitioning into {} blocks with {} trials", noPartitions,noTrials);
		
		int level = 1;
		int sizeThreshold = coarseSizeThreshold(noPartitions);
		CoarseningResult coarsening = coarsen(g, nodes, nweight, rweight,level);
		Map<Node,SuperNode> coarsemap = coarsening.map;
		
		log.debug("New Size: {} | Shrinkage: {}",coarsening.getNoSuperNodes(),coarsening.getShrinkageFactor());
		
		while (coarsening.getNoSuperNodes()>sizeThreshold && 
				coarsening.getShrinkageFactor()<=shrinkingThreshold) {
			//Keep coarsening
			level++;
			CoarseningResult nextCoarse = coarsen(g, coarsening.getSuperNodes(), coarsening.nweight, coarsening.rweight,level);

			//Update coarsemap to map "through"
			Map<Node,SuperNode> newcoarsemap = new HashMap<Node,SuperNode>(coarsemap.size());
			for (Map.Entry<Node, SuperNode> old : coarsemap.entrySet()) {
				newcoarsemap.put(old.getKey(), nextCoarse.map.get(old.getValue().getRepresentationNode()));
			}
			coarsemap = newcoarsemap;
			coarsening = nextCoarse;
			log.debug("New Size: {} | Shrinkage: {}",coarsening.getNoSuperNodes(),coarsening.getShrinkageFactor());			
		}
		//Now partition
		Set<Node> topnodes = coarsening.getSuperNodes();
		
		Map<Node,Integer> bestAssign = null;
		double bestEdgeCut = Double.POSITIVE_INFINITY;
		double bestBalance = Double.POSITIVE_INFINITY;
		
		for (int trial=1; trial<=noTrials; trial++) {
			Map<Node,Integer> pAssign = new HashMap<Node,Integer>();
			List<Map<Node, Double>> pnghs = new ArrayList<Map<Node, Double>>(noPartitions);
			double[] pweights = new double[noPartitions];
			double edgeCut = 0.0;
			//initial assignment
			RandomStack<Node> rnodes = new RandomStack<Node>(topnodes);
			for (int pid=0;pid<noPartitions;pid++) {
				Map<Node, Double> nghs = new HashMap<Node, Double>(topnodes.size()/noPartitions);
				pnghs.add(nghs);
				Node n = rnodes.popRandom();
				assert n!=null;
				assert !pAssign.containsKey(n);
				edgeCut += assign(n,pid,pweights,pAssign,pnghs,
						coarsening.nweight,coarsening.rweight);
			}
			//assign remaining in neighborhood
			while(!rnodes.isEmpty()) {
				int pid = findMinPartitionBlock(pweights,pnghs);
				if (pid<0) {
					Node n;
					for (pid=0; pid<noPartitions && !rnodes.isEmpty(); pid++) {
						do {
							n = rnodes.popRandom();
						} while (pAssign.containsKey(n) && !rnodes.isEmpty());
						edgeCut += assign(n, pid, pweights, pAssign, pnghs,
								coarsening.nweight,coarsening.rweight);
					}
				}
				else {
					Node n = findMostConnected(pnghs.get(pid));
					assert !pAssign.containsKey(n);
					edgeCut += assign(n,pid,pweights,pAssign,pnghs,
							coarsening.nweight,coarsening.rweight);
				}
			}
//			while(true) {
//				int pid = findMinPartitionBlock(pweights,pnghs);
//				if (pid<0) break;
//				Node n = findMostConnected(pnghs.get(pid));
//				assert !pAssign.containsKey(n);
//				edgeCut += assign(n,pid,pweights,pAssign,pnghs,
//						coarsening.nweight,coarsening.rweight);
//			}
			//check whether any node has not yet been assigned
//			for (Node n : topnodes) {
//				if (!pAssign.containsKey(n)) {
//					int pid = indexOfMin(pweights);
//					pAssign.put(n, pid);
//					pweights[pid]+= coarsening.nweight.getWeight(n);
//				}
//			}
			double balance = stdDev(pweights);
			
			log.debug("Current partitions edge cut: {} | Balance : {}",edgeCut,balance);
			//find best partition
			if (partitionEvaluation(edgeCut,balance)<partitionEvaluation(bestEdgeCut,bestBalance)) {
				bestEdgeCut = edgeCut;
				bestBalance = balance;
				bestAssign = pAssign;
			}
		}
		
		if (bestAssign==null)
			throw new IllegalArgumentException("No feasible partition could be found!");
		
		//Use coarsemap to find partition
		for (Map.Entry<Node, SuperNode> entry : coarsemap.entrySet()) {
			Node node = entry.getKey();
			int pid = bestAssign.get(entry.getValue().getRepresentationNode());
			partition.get(pid).add(node);
		}
		
		return bestEdgeCut;
	}
	
	private final double partitionEvaluation(double edgeCut, double balance) {
		if (edgeCut < 5)
			return 10e30;
		else
			return edgeCut+Math.pow(balance,balanceExponent);
	}
	
	private static final int findMinPartitionBlock(double[] pweights, List<Map<Node, Double>> neighborhoods) {
		int index = -1;
		for (int i=0;i<pweights.length;i++) {
			if ( (index<0 || pweights[i]<pweights[index]) && neighborhoods.get(i).size()!=0) {
				index = i;
			}
		}
		return index;
	}
	
	private static final Node findMostConnected(Map<Node, Double> neighborhood) {
		Node bestNode = null;
		double bestValue = Double.NEGATIVE_INFINITY;
		for (Map.Entry<Node, Double> e : neighborhood.entrySet()) {
			if (e.getValue()>bestValue) {
				bestValue = e.getValue();
				bestNode = e.getKey();
			}
		}
		return bestNode;
	}
	
	private static final double assign(Node n, int pid, double[] pweights, Map<Node,Integer> pAssign, 
			List<Map<Node, Double>> neighborhoods, NodeWeighter nweight, RelationshipWeighter rweight) {
		pAssign.put(n, pid);
		pweights[pid]+= nweight.getWeight(n);
		
		double incEdgeCut = 0.0;
		Map<Node, Double> nghs = neighborhoods.get(pid);
		//Update neighborhoods and compute edge cut
		for (Relationship r : n.getRelationships()) {
			assert r.getRelationshipType().equals(relType);
			double rw = rweight.getWeight(r);
			Node other = r.getOtherNode(n);
			Integer opid = pAssign.get(other);
			if (opid==null) { //Not yet assigned => add to neighborhood
				nghs.put(other, (nghs.get(other) != null) ? nghs.get(other) + rw : rw);
			} else { //Already assigned => remove from its neighborhood, increase edge cut
				Map<Node, Double> onghs = neighborhoods.get(opid);
				onghs.remove(n);
				if (opid!=pid) incEdgeCut += rw;
			}
		}
		//System.out.println(incEdgeCut);
		return incEdgeCut;
	}

	private static final double evaluateNeighbor(Node node, Relationship rel, Node neighbor, 
			NodeWeighter nweight, RelationshipWeighter rweight, Set<Node> visited) {
		return rweight.getWeight(rel)/Math.pow(nweight.getWeight(neighbor),0.5);
	}
	
	private CoarseningResult coarsen(Graph g, Iterable<? extends Node> nodes, NodeWeighter nweight, 
			RelationshipWeighter rweight, int level) {
		
		CoarseningResult result = new CoarseningResult();

		result.g = g;
		
		List<SuperNode> supernodes = new ArrayList<SuperNode>();
		
		for (Node node : nodes) {
			if (result.map.containsKey(node)) continue;
			result.noBaseNodes++;
			Set<Node> visited = new HashSet<Node>();
			double highscore = Double.NEGATIVE_INFINITY;
			Node bestngh = null;
			for (Relationship r : node.getRelationships()) {
				Node ngh = r.getOtherNode(node);
				if (!visited.contains(ngh)) {
					double score = evaluateNeighbor(node,r,ngh,nweight,rweight,visited);
					if (score>highscore) {
						bestngh = ngh;
						highscore = score;
					}
				}
			}
			if (bestngh!=null) {
				SuperNode nsup = result.map.get(bestngh);
				if (nsup == null) {
					nsup = new SuperNode(result.g.createNode());
					result.addSuperNode(nsup);
					nsup.addChild(bestngh, nweight.getWeight(bestngh));
					result.map.put(bestngh, nsup);
					supernodes.add(nsup);
				}
				nsup.addChild(node, nweight.getWeight(node));
				result.map.put(node, nsup);
			} else { //Create singleton node
				SuperNode sup = new SuperNode(result.g.createNode());
				result.addSuperNode(sup);
				sup.addChild(node, nweight.getWeight(node));
				result.map.put(node, sup);
				supernodes.add(sup);

			}
			
		}

		/* Constructs new graph for coarsening */
		result.rweight = createCoarseGraph(result.g, supernodes, result.map, rweight);
		result.nweight = new PropertyNodeWeighter(weightType);
		
		return result;
	}
	
	protected static HashRelationshipWeighter createCoarseGraph(Graph g,
			Iterable<SuperNode> supernodes, Map<Node,SuperNode> assign,
			RelationshipWeighter rweight) {
		
		g.createRelationshipType(relType);
		g.createPropertyType(weightType, Double.class);
		HashRelationshipWeighter relWeighter = new HashRelationshipWeighter();
		
		
		for (SuperNode snode : supernodes) {
			Map<Node, Double> acc = new HashMap<Node, Double>();
			for (int ch=0;ch<snode.getNoChildren();ch++) {
				Node child = snode.getChild(ch);
				for (Relationship r : child.getRelationships()) {
					SuperNode other = assign.get(r.getOtherNode(child));
					if (snode.compareTo(other)>0) {
						acc.put(other.getRepresentationNode(),
								(acc.get(other.getRepresentationNode()) != null)
									? acc.get(other.getRepresentationNode()) + rweight.getWeight(r)
									: rweight.getWeight(r)
						);
					}
				}
			}
			Node center = snode.getRepresentationNode();
			center.createProperty(weightType, snode.getWeight());
			for (Map.Entry<Node, Double> e : acc.entrySet()) {
				Relationship rel = center.createRelationship(relType, e.getKey());
				relWeighter.setWeight(rel, e.getValue());
			}
		}
		return relWeighter;
	}
	
	private double stdDev(double[] values) {
		double sum = 0.0;
		double sumOfSquares = 0.0;
		for (int i = 0; i < values.length; i++) {
			sum += values[i];
			sumOfSquares += Math.pow(values[i], 2);
		}
		return Math.sqrt((sumOfSquares - Math.pow(sum, 2) / values.length) / values.length);
	}
	
	private class ConstantOneNodeWeighter implements NodeWeighter {
		@Override
		public double getWeight(Node n) {
			return 1;
		}
	}
}
