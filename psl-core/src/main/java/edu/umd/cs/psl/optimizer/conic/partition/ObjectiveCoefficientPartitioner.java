/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.util.graph.Graph;
import edu.umd.cs.psl.util.graph.Node;
import edu.umd.cs.psl.util.graph.Relationship;
import edu.umd.cs.psl.util.graph.memory.MemoryGraph;
import edu.umd.cs.psl.util.graph.partition.hierarchical.HierarchicalPartitioning;
import edu.umd.cs.psl.util.graph.partition.hierarchical.HyperPartitioning;
import edu.umd.cs.psl.util.graph.weight.RelationshipWeighter;

public class ObjectiveCoefficientPartitioner {

	private static final Logger log = LoggerFactory.getLogger(ObjectiveCoefficientPartitioner.class);
	
	protected static final int base = 2;
	protected ConicProgram program;
	protected ConicProgramPartition partition;
	
	protected BiMap<Cone, Node> coneMap;
	protected BiMap<LinearConstraint, Node> lcMap;
	
	
	private static final String LC_REL = "lcRel";

	public ObjectiveCoefficientPartitioner(ConfigBundle config) {
		
	}
	
	public void setConicProgram(ConicProgram program) {
		this.program = program;
		doPartition();
	}
	
	protected void doPartition() {
		Graph graph;
		Node node;
		
		int numElements = (int) Math.ceil((double) program.getNumLinearConstraints() / 5000);
		
		List<List<Node>> graphPartition = null;

		List<Set<Cone>> blocks;
		
		/* Partitions conic program graph into elements */
		HierarchicalPartitioning partitioner = new HyperPartitioning();
		partitioner.setNoPartitioningTrials(10);
		partitioner.setSize(numElements);
		
		boolean redoPartition = false;
		
		do {
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
				lcMap.put(con, node);
				
				for (Variable var : con.getVariables().keySet()) {
					coneSet.add(var.getCone());
				}
				
				for (Cone cone : coneSet) {
					node.createRelationship(LC_REL, coneMap.get(cone));
				}
				
				coneSet.clear();
			}
			
			graphPartition = partitioner.partition(graph, graph.getNodeSnapshot(), new RelationshipWeighter() {
				@Override
				public double getWeight(Relationship r) {
					if (r.getRelationshipType().equals(LC_REL)) {
						LinearConstraint lc = (LinearConstraint) lcMap.inverse().get(r.getStart());
						Cone cone = coneMap.inverse().get(r.getEnd());
						return ObjectiveCoefficientPartitioner.this.getWeight(lc, cone);
					}
					else
						return Double.POSITIVE_INFINITY;
				}
			});
				
			log.trace("Partition finished. Checking for balance.");
			
			/* Checks if blocks are sufficiently balanced */
			boolean balanced = true;
			if (numElements > 1) {
				int totalSize = 0;
				for (List<Node> block : graphPartition)
					totalSize += block.size();
				
				for (List<Node> block : graphPartition){
					if (block.size() > 2*(totalSize - block.size())) {
	//						log.debug("{} > {}", block.size(), 2*(totalSize - block.size()));
						balanced = false;
					}
					if (!balanced) {
						redoPartition = true;
						break;
					}
				}
				
				if (!balanced) {
					redoPartition = true;
				}
			}
	
			/* Partition accepted */
			if (!redoPartition) {
				/* Collects cones in blocks */
				blocks = new Vector<Set<Cone>>();
				for (int i = 0; i < graphPartition.size(); i++) {
					Set<Cone> block = new HashSet<Cone>();
					for (Node n : graphPartition.get(i)) {
						/* Adds cone to block */
						if (coneMap.containsValue(n)) {
							block.add(coneMap.inverse().get(n));
						}
					}
					blocks.add(block);
				}
				
				/* Initializes the partition */
				partition = new ConicProgramPartition(program, blocks);
				return;
			}
		} while (true);
	}
	
	public ConicProgramPartition getPartition() {
		return partition;
	}

	protected double getWeight(LinearConstraint lc, Cone cone) {
		if (cone instanceof NonNegativeOrthantCone)
			return Math.pow(base, Math.abs(((NonNegativeOrthantCone) cone).getVariable().getObjectiveCoefficient()) + 1);
		else if (cone instanceof SecondOrderCone) {
			double weight = 0.0;
			for (Variable socVar : ((SecondOrderCone) cone).getVariables())
				weight += socVar.getObjectiveCoefficient();
			return Math.pow(base, Math.abs(weight) + 1);
		}
		else
			throw new IllegalStateException();
	}

}
