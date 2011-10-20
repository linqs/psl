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
package edu.umd.cs.psl.optimizer.conic.partition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import cern.colt.Partitioning;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.NodeType;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramEvent;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramListener;
import edu.umd.cs.psl.optimizer.conic.program.Entity;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.util.graph.Graph;
import edu.umd.cs.psl.util.graph.Node;
import edu.umd.cs.psl.util.graph.Relationship;
import edu.umd.cs.psl.util.graph.weight.RelationshipWeighter;

public class SequentialHierarchicalCompletePartitioner extends AbstractCompletePartitioner
		implements ConicProgramListener {

	private BiMap<Cone, Node> coneMap;
	private BiMap<LinearConstraint, Node> lcMap;
	
	private Graph graph;
	
	private static final int defaultNumPartitions = 2;
	private static final int defaultNumPartitionElements = 2;
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
	}
	
	public SequentialHierarchicalCompletePartitioner(ConicProgram p, ConfigBundle config) {
		super(p);
		
		coneMap = HashBiMap.create();
		lcMap = HashBiMap.create();
		
		for (LinearConstraint con : program.getConstraints()) {
			
		}
		
		program.registerForConicProgramEvents(this);
	}

	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}

	@Override
	protected void doPartition() {
		int numPartitions = defaultNumPartitions;
		int numElements = defaultNumPartitionElements;
		numElements = (int) Math.ceil((double) program.numLinearConstraints() / 5000);
		
		double mu, tau, muInitial, theta, err, epsilon_1;
		boolean inNeighborhood;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		List<Set<Node>> partition;
		Variable var;
		Set<LinearConstraint> cutConstraints = new HashSet<LinearConstraint>();
		Set<LinearConstraint> alwaysCutConstraints = new HashSet<LinearConstraint>();
		final Set<LinearConstraint> restrictedConstraints = new HashSet<LinearConstraint>();
		boolean isInnerConstraint;

		List<List<Set<Node>>> partitions = new Vector<List<Set<Node>>>(numPartitions);
		
		/* Partitions IPM graph into elements */
		Partitioning partitioning = graph.getPartitioning();
		partitioning.setSize(numElements);
		
		final Set<LinearConstraint> isolatedConstraints =  new HashSet<LinearConstraint>();
		
		for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.lc)) {
			if (((LinearConstraint) IPMEntity.createEntity(this, n)).getIsolatedStructure() != null)
				isolatedConstraints.add(((LinearConstraint) IPMEntity.createEntity(this, n)));
		}
		
		for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.lis))
			((LinearIsolatedStructure) IPMEntity.createEntity(this, n)).delete();
		
		//for (int p = 0; p < numPartitions; p++) {
		boolean redoPartition;
		int p = 0;
		do {
			redoPartition = false;
			//if (partitions.size() == 1) {
			//	partitioning.setSize(partitioning.getSize()+1);
			//}
			try {
				if (p % 2 == 0) {
					partitions.add(partitioning.partition(new RelationshipWeighter() {
						@Override
						public double getWeight(Relationship r) {
							if (r.getEdgeType().equals(LC_REL)) {
								double weight = 0.0;
								LinearConstraint lc = (LinearConstraint) IPMEntity.createEntity(PartitionedIPM.this, r.getStart());
	
								//if (lc.getIsolatedStructure() == null) {
								if (!isolatedConstraints.contains(lc)) {
									return Double.POSITIVE_INFINITY;
								}
								else if (restrictedConstraints.contains(lc)) {
									return 300;
									//return Double.POSITIVE_INFINITY;
								}
								else {
									Variable v = (Variable) IPMEntity.createEntity(PartitionedIPM.this, r.getEnd());
									weight += Math.abs(lc.getConstrainedValue());
									weight += Math.abs(lc.getVariables().get(v));
									return weight;
									//return 1;
								}
							}
							else
								return Double.POSITIVE_INFINITY;
						}
					}));
				}
				else {
					partitions.add(partitioning.partition(new RelationshipWeighter()  {
						@Override
						public double getWeight(Relationship r) {
							if (r.getEdgeType().equals(LC_REL)) {
								double weight = 0.0;
								LinearConstraint lc = (LinearConstraint) IPMEntity.createEntity(PartitionedIPM.this, r.getStart());
								//if (lc.getIsolatedStructure() == null) {
								if (!isolatedConstraints.contains(lc)) {
									return Double.POSITIVE_INFINITY;
								}
								else if (restrictedConstraints.contains(lc)) {
									return 300;
									//return Double.POSITIVE_INFINITY;
								}
								else {
									Variable v = (Variable) IPMEntity.createEntity(PartitionedIPM.this, r.getEnd());
									weight += Math.abs(lc.getConstrainedValue());
									weight += Math.abs(lc.getVariables().get(v));
									return 1/(2*weight);
									//return 1;
								}
							}
							else
								return Double.POSITIVE_INFINITY;
						}
					}));
				}
			}
			catch (IllegalArgumentException e) {
				if (restrictedConstraints.size() > 1) {
					int cut = Math.min((int) Math.ceil(restrictedConstraints.size() / 3), restrictedConstraints.size()-1);
					Iterator<LinearConstraint> itr = restrictedConstraints.iterator();
					for (int i = 0; i < cut; i++) {
						itr.next();
						itr.remove();
					}
					
					redoPartition = true;
				}
				else throw new IllegalStateException("Could not complete partitioning.");
			}
			
			/* Checks if blocks are sufficiently balanced */
			boolean balanced = true;
			if (!redoPartition) {
				int totalSize = 0;
				for (Set<Node> block : partitions.get(p))
					totalSize += block.size();
				
				for (Set<Node> block : partitions.get(p)){
					if (block.size() > 2*(totalSize - block.size())) {
						log.trace("{} > {}", block.size(), 2*(totalSize - block.size()));
						balanced = false;
					}
					if (!balanced) {
						redoPartition = true;
						break;
					}
				}
				
				if (!balanced) {
					log.trace("Redoing parition {}.", p);
					//log.trace("Block size: {}", partitions.get(p).size());
					//log.trace("Need at least: {}", varMap.size()/(numElements) );
					partitions.remove(p);
					redoPartition = true;
					
					if (restrictedConstraints.size() > 1 && restrictedConstraints.size() > alwaysCutConstraints.size() / 2) {
						Iterator<LinearConstraint> itr = restrictedConstraints.iterator();
						while (restrictedConstraints.size() > alwaysCutConstraints.size() / 1.5) {
							itr.next();
							itr.remove();
						}
					}
				}
			}

			/* Partition accepted */
			if (!redoPartition) {
				partition = partitions.get(p);
				
				for (int i = 0; i < partition.size(); i++) {
					System.out.println("Size: " + partition.get(i).size());
					for (Node n : partition.get(i)) {
						if (NodeType.var.equals(n.getAttribute(NODE_TYPE))) {
							var = (Variable) IPMEntity.createEntity(this, n);
							for (LinearConstraint lc : var.getLinearConstraints()) {
								/* Checks if constraint has been cut */
								isInnerConstraint = partition.get(i).contains(lc.getNode());
								for (Variable v : lc.getVariables().keySet())
									isInnerConstraint = isInnerConstraint && partition.get(i).contains(v.getNode());
								if (!isInnerConstraint) {
									cutConstraints.add(lc);
									if (p == 0) {
										alwaysCutConstraints.add(lc);
										restrictedConstraints.add(lc);
									}
								}
							}
						}
					}
				}
				
				if (p != 0) {
					alwaysCutConstraints.retainAll(cutConstraints);
					restrictedConstraints.clear();
					restrictedConstraints.addAll(alwaysCutConstraints);
				}
				
				for (LinearConstraint lc : cutConstraints) {
					List<Set<Node>> blocks = new Vector<Set<Node>>();
					CurrentStructure cs;
					for (Variable v : lc.getVariables().keySet()) {
						for (Set<Node> block : partition) {
							if (block.contains(v.getNode())) {
								if (!blocks.contains(block))
									blocks.add(block);
								break;
							}
						}
					}
					
					cs = ((LinearIsolatedStructure) lc.getIsolatedStructure()).getStructure();
					if (blocks.size() < 3) {
						blocks.get(0).remove(cs.negativeSlack.getNode());
						blocks.get(0).add(cs.positiveSlack.getNode());
						blocks.get(1).remove(cs.positiveSlack.getNode());
						blocks.get(1).add(cs.negativeSlack.getNode());
					}
					else throw new IllegalStateException();
				}
				cutConstraints.clear();
				p++;
			}
		} while (alwaysCutConstraints.size() > 0 || redoPartition);
		
		log.trace("Beginning subproblem construction.");
		
		/* Constructs subproblem representation for each element */
		for (p = 0; p < partitions.size(); p++) {
			subProblemSets.add(new SubProblemSet(partitions.get(p), varMap, lcMap, dx, ds, dw, r, Hinv));
		}
	}

	@Override
	public void notify(ConicProgramEvent e, Entity sender, Object... data) {
		// TODO Auto-generated method stub
		
	}

}
