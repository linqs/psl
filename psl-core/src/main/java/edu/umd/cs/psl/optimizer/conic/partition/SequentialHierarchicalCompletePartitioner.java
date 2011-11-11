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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

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
import edu.umd.cs.psl.util.graph.memory.MemoryGraph;
import edu.umd.cs.psl.util.graph.partition.Partitioner;
import edu.umd.cs.psl.util.graph.partition.hierarchical.HierarchicalPartitioning;
import edu.umd.cs.psl.util.graph.weight.RelationshipWeighter;

public class SequentialHierarchicalCompletePartitioner extends AbstractCompletePartitioner
		implements ConicProgramListener {
	
	private static final Logger log = LoggerFactory.getLogger(SequentialHierarchicalCompletePartitioner.class);

	private BiMap<Cone, Node> coneMap;
	private BiMap<LinearConstraint, Node> lcMap;
	
	private Graph graph;
	private static final String LC_REL = "lcRel";
	
	private static final int defaultNumPartitionElements = 2;
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
	}
	
	public SequentialHierarchicalCompletePartitioner(ConfigBundle config) {
		super();
	}
	
	@Override
	public void setConicProgram(ConicProgram p) {
		if (program != null)
			program.unregisterForConicProgramEvents(this);
		
		Node node;
		
		graph = new MemoryGraph();
		graph.createRelationshipType(LC_REL);
		
		coneMap = HashBiMap.create();
		lcMap = HashBiMap.create();
		
		for (Cone cone : program.getCones()) {
			node = graph.createNode();
			coneMap.put(cone, node);
		}
		
		Set<Cone> coneSet = new HashSet<Cone>();
		for (LinearConstraint con : program.getConstraints()) {
			node = graph.createNode();
			for (Variable var : con.getVariables().keySet()) {
				coneSet.add(var.getCone());
			}
			
			for (Cone cone : coneSet) {
				node.createRelationship(LC_REL, coneMap.get(cone));
			}
			
			coneSet.clear();
		}
		
		program.registerForConicProgramEvents(this);
	}

	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}

	@Override
	protected void doPartition() {
		int numElements = defaultNumPartitionElements;
		numElements = (int) Math.ceil((double) program.numLinearConstraints() / 5000);
		
		List<List<Node>> graphPartition = null;
		Set<LinearConstraint> cutConstraints = new HashSet<LinearConstraint>();
		Set<LinearConstraint> alwaysCutConstraints = new HashSet<LinearConstraint>();
		final Set<LinearConstraint> restrictedConstraints = new HashSet<LinearConstraint>();
		boolean isInnerConstraint;

		//List<List<Set<Node>>> partitions = new Vector<List<Set<Node>>>(numPartitions);
		List<Set<Cone>> blocks;
		
		/* Partitions IPM graph into elements */
		Partitioner partitioner = new HierarchicalPartitioning();
		partitioner.setSize(numElements);
		
		boolean redoPartition;
		int p = 0;
		do {
			redoPartition = false;
			//if (partitions.size() == 1) {
			//	partitioning.setSize(partitioning.getSize()+1);
			//}
			try {
				if (p % 2 == 0) {
					graphPartition = partitioner.partition(graph, graph.getNodes(), new RelationshipWeighter() {
						@Override
						public double getWeight(Relationship r) {
							if (r.getRelationshipType().equals(LC_REL)) {
								LinearConstraint lc = (LinearConstraint) lcMap.inverse().get(r.getStart());
								if (restrictedConstraints.contains(lc)) {
									return 300;
								}
								else {
									return 1;
								}
							}
							else
								return Double.POSITIVE_INFINITY;
						}
					});
				}
				else {
					graphPartition = partitioner.partition(graph, graph.getNodes(), new RelationshipWeighter()  {
						@Override
						public double getWeight(Relationship r) {
							if (r.getRelationshipType().equals(LC_REL)) {
								LinearConstraint lc = (LinearConstraint) lcMap.inverse().get(r.getStart());
								if (restrictedConstraints.contains(lc)) {
									return 300;
								}
								else {
									return 1;
								}
							}
							else
								return Double.POSITIVE_INFINITY;
						}
					});
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
				for (List<Node> block : graphPartition)
					totalSize += block.size();
				
				for (List<Node> block : graphPartition){
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
				/* Collects cones in blocks and checks which constraints were cut */
				blocks = new Vector<Set<Cone>>();
				for (int i = 0; i < graphPartition.size(); i++) {
					Set<Cone> block = new HashSet<Cone>();
					for (Node n : graphPartition.get(i)) {
						/* Adds cone to block */
						if (coneMap.containsValue(n)) {
							block.add(coneMap.inverse().get(n));
						}
						/* Checks if constraint has been cut */
						else if (lcMap.containsValue(n)) {
							isInnerConstraint = true;
							for (Relationship r : n.getRelationships(LC_REL))
								isInnerConstraint = isInnerConstraint && graphPartition.get(i).contains(r.getEnd());
							if (!isInnerConstraint) {
								LinearConstraint lc = lcMap.inverse().get(n);
								cutConstraints.add(lc);
								if (p == 0) {
									alwaysCutConstraints.add(lc);
									restrictedConstraints.add(lc);
								}
							}
						}
					}
					blocks.add(block);
				}
				
				if (p != 0) {
					alwaysCutConstraints.retainAll(cutConstraints);
					restrictedConstraints.clear();
					restrictedConstraints.addAll(alwaysCutConstraints);
				}
				
				partitions.add(new ConicProgramPartition(program, blocks));
				
				cutConstraints.clear();
				p++;
			}
		} while (alwaysCutConstraints.size() > 0 || redoPartition);
	}

	@Override
	public void notify(ConicProgram sender, ConicProgramEvent event, Entity entity, Object... data) {
		// TODO Auto-generated method stub
		
	}

}
