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
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.util.graph.Graph;
import edu.umd.cs.psl.util.graph.Node;
import edu.umd.cs.psl.util.graph.Relationship;
import edu.umd.cs.psl.util.graph.memory.MemoryGraph;
import edu.umd.cs.psl.util.graph.partition.Partitioner;
import edu.umd.cs.psl.util.graph.partition.hierarchical.HyperPartitioning;
import edu.umd.cs.psl.util.graph.weight.RelationshipWeighter;

abstract public class HierarchicalPartitioner extends AbstractCompletePartitioner
		implements ConicProgramListener {
	
	private static final Logger log = LoggerFactory.getLogger(HierarchicalPartitioner.class);

	protected BiMap<Cone, Node> coneMap;
	protected BiMap<LinearConstraint, Node> lcMap;
	protected Set<LinearConstraint> alwaysCutConstraints;
	protected Set<LinearConstraint> restrictedConstraints;
	
	protected int p;
	
	private static final String LC_REL = "lcRel";
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
	}
	
	public HierarchicalPartitioner(ConfigBundle config) {
		super();
	}
	
	@Override
	public void setConicProgram(ConicProgram p) {
		if (program != null)
			program.unregisterForConicProgramEvents(this);
		
		super.setConicProgram(p);
		
		program.registerForConicProgramEvents(this);
	}

	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}

	@Override
	protected void doPartition() {
		Graph graph;
		Node node;
		
		partitions.clear();
		
		int numElements = (int) Math.ceil((double) program.getNumLinearConstraints() / 5000);
		
		List<List<Node>> graphPartition = null;
		alwaysCutConstraints = new HashSet<LinearConstraint>();
		restrictedConstraints = new HashSet<LinearConstraint>();

		List<Set<Cone>> blocks;
		
		/* Partitions conic program graph into elements */
		Partitioner partitioner = new HyperPartitioning();
		partitioner.setSize(numElements);
		
		boolean redoPartition;
		p = 0;
		do {
			redoPartition = false;
			try {
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
							return HierarchicalPartitioner.this.getWeight(lc, cone);
						}
						else
							return Double.POSITIVE_INFINITY;
					}
				});
			}
			catch (IllegalArgumentException e) {
				log.debug("Caught illegal argument exception.");
				if (restrictedConstraints.size() > 1) {
					int cut = Math.min((int) Math.ceil(restrictedConstraints.size() / 3), restrictedConstraints.size()-1);
					Iterator<LinearConstraint> itr = restrictedConstraints.iterator();
					for (int i = 0; i < cut; i++) {
						itr.next();
						itr.remove();
					}
					
					redoPartition = true;
				}
				else throw e;
			}
			
			log.trace("Partition finished. Checking for balance.");
			
			/* Checks if blocks are sufficiently balanced */
			boolean balanced = true;
			if (!redoPartition && numElements > 1) {
				int totalSize = 0;
				for (List<Node> block : graphPartition)
					totalSize += block.size();
				
				for (List<Node> block : graphPartition){
					if (block.size() > 2*(totalSize - block.size())) {
						log.debug("{} > {}", block.size(), 2*(totalSize - block.size()));
						balanced = false;
					}
					if (!balanced) {
						redoPartition = true;
						break;
					}
				}
				
				if (!balanced) {
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
				ConicProgramPartition partition = new ConicProgramPartition(program, blocks);
				log.debug("Size of cut constraints: {}", partition.getCutConstraints().size());
				partitions.add(partition);
				
				/* Updates the sets of always cut constraints and restricted constraints */
				if (p == 0) {
					alwaysCutConstraints.addAll(partition.getCutConstraints());
					restrictedConstraints.addAll(partition.getCutConstraints());
				}
				else {
					alwaysCutConstraints.retainAll(partition.getCutConstraints());
					restrictedConstraints.clear();
					restrictedConstraints.addAll(alwaysCutConstraints);
				}

				/* Ensures that each cut constraint has a singleton in each element it spans */
				HashSet<Integer> elements = new HashSet<Integer>();
				ArrayList<Cone> singletons = new ArrayList<Cone>();
				for (LinearConstraint lc : partition.getCutConstraints()) {
					elements.clear();
					singletons.clear();
					for (Variable var : lc.getVariables().keySet()) {
						Cone cone = var.getCone();
						elements.add(partition.getElement(cone));
						if (isSingleton(cone)) {
							partition.removeCone(cone);
							singletons.add(cone);
						}
					}
					
					if (singletons.size() < elements.size())
//						throw new IllegalStateException("Not enough singletons to cut constraint. Needed " + elements.size() + ".");
						log.warn("Not enough singletons to cut constraint. Needed {}.", elements.size());
					
					Iterator<Integer> itr = elements.iterator();
					for (Cone cone : singletons) {
						partition.addCone(cone, itr.next());
						if (!itr.hasNext())
							itr = elements.iterator();
					}
				}
				
				processAcceptedPartition();
				
				log.debug("Number of always cut constraints: {}", alwaysCutConstraints.size());
				p++;
			}
			else {
				log.debug("Redoing partition {}.", p);
			}
		} while (alwaysCutConstraints.size() > 0 || redoPartition);
	}

	@Override
	public void notify(ConicProgram sender, ConicProgramEvent event, Entity entity, Object... data) {
		// TODO Auto-generated method stub
		
	}
	
	abstract protected double getWeight(LinearConstraint lc, Cone cone);
	
	abstract protected void processAcceptedPartition();

	protected boolean isSingleton(Cone cone) {
		if (cone instanceof NonNegativeOrthantCone) {
			return ((NonNegativeOrthantCone) cone).getVariable().getLinearConstraints().size() == 1;
		}
		else if (cone instanceof SecondOrderCone) {
			LinearConstraint lc = null;
			for (Variable socVar : ((SecondOrderCone) cone).getVariables()) {
				Set<LinearConstraint> cons = socVar.getLinearConstraints();
				if (cons.size() > 1) {
					return false;
				}
				else if (cons.size() == 1) {
					if (lc == null)
						lc = cons.iterator().next();
					else if (!lc.equals(cons.iterator().next()))
						return false;
				}
			}
			
			return true;
		}
		else
			throw new IllegalStateException();
	}

}
