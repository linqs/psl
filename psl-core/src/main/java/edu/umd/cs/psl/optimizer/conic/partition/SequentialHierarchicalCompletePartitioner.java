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

public class SequentialHierarchicalCompletePartitioner extends AbstractCompletePartitioner
		implements ConicProgramListener {
	
	private static final Logger log = LoggerFactory.getLogger(SequentialHierarchicalCompletePartitioner.class);

	private BiMap<Cone, Node> coneMap;
	private BiMap<LinearConstraint, Node> lcMap;
	
	private Graph graph;
	private static final String LC_REL = "lcRel";
	
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
		
		super.setConicProgram(p);
		
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
		Node node;
		
		partitions.clear();
		
		int numElements = (int) Math.ceil((double) program.getNumLinearConstraints() / 20000);
		
		List<List<Node>> graphPartition = null;
		Set<LinearConstraint> alwaysCutConstraints = new HashSet<LinearConstraint>();
		final Set<LinearConstraint> restrictedConstraints = new HashSet<LinearConstraint>();

		List<Set<Cone>> blocks;
		
		/* Partitions IPM graph into elements */
		Partitioner partitioner = new HyperPartitioning();
		partitioner.setSize(numElements);
		
		boolean redoPartition;
		int p = 0;
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
				
				if (p % 2 == 0) {
					graphPartition = partitioner.partition(graph, graph.getNodeSnapshot(), new RelationshipWeighter() {
						@Override
						public double getWeight(Relationship r) {
							if (r.getRelationshipType().equals(LC_REL)) {
								LinearConstraint lc = (LinearConstraint) lcMap.inverse().get(r.getStart());
								if (restrictedConstraints.contains(lc)) {
									return 2000;
								}
								else {
									Cone cone = coneMap.inverse().get(r.getEnd());
									if (cone instanceof NonNegativeOrthantCone)
										return Math.abs(((NonNegativeOrthantCone) cone).getVariable().getObjectiveCoefficient()) + 1;
									else if (cone instanceof SecondOrderCone) {
										double weight = 0.0;
										for (Variable var : ((SecondOrderCone) cone).getVariables())
											weight += var.getObjectiveCoefficient();
										return Math.abs(weight) + 1;
									}
									else
										throw new IllegalStateException();
//									return 1;
								}
							}
							else
								return Double.POSITIVE_INFINITY;
						}
					});
				}
				else {
					graphPartition = partitioner.partition(graph, graph.getNodeSnapshot(), new RelationshipWeighter()  {
						@Override
						public double getWeight(Relationship r) {
							if (r.getRelationshipType().equals(LC_REL)) {
								LinearConstraint lc = (LinearConstraint) lcMap.inverse().get(r.getStart());
								if (restrictedConstraints.contains(lc)) {
									return 2000;
								}
								else {
									Cone cone = coneMap.inverse().get(r.getEnd());
									if (cone instanceof NonNegativeOrthantCone)
										return 1 / (Math.abs(((NonNegativeOrthantCone) cone).getVariable().getObjectiveCoefficient()) + 1);
									else if (cone instanceof SecondOrderCone) {
										double weight = 0.0;
										for (Variable var : ((SecondOrderCone) cone).getVariables())
											weight += var.getObjectiveCoefficient();
										return 1 / (Math.abs(weight) + 1);
									}
									else
										throw new IllegalStateException();
								}
							}
							else
								return Double.POSITIVE_INFINITY;
						}
					});
				}
			}
			catch (IllegalArgumentException e) {
				log.trace("Caught illegal argument exception.");
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

				log.debug("Number of always cut constraints right after update: {}", alwaysCutConstraints.size());
				
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
				
				log.debug("Number of cut constraints after singleton shuffling: {}", partition.getCutConstraints().size());
				
				log.debug("Number of always cut constraints: {}", alwaysCutConstraints.size());
				p++;
			}
			else {
				log.trace("Redoing partition {}.", p);
			}
		} while (alwaysCutConstraints.size() > 0 || redoPartition);
	}

	@Override
	public void notify(ConicProgram sender, ConicProgramEvent event, Entity entity, Object... data) {
		// TODO Auto-generated method stub
		
	}

	private boolean isSingleton(Cone cone) {
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
