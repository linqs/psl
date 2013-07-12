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
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class WeightedDistanceCompletePartitioner extends HierarchicalPartitioner {
	
	private double[] weights;
	
	private static final int base = 10;
	private static final int depthLimit = 1;

	public WeightedDistanceCompletePartitioner(ConfigBundle config) {
		super(config);
	}
	
	@Override
	protected void doPartition() {
		weights = new double[program.getNumLinearConstraints()];
		for (int i = 0; i < weights.length; i++)
			weights[i] = 1.0;
		super.doPartition();
	}

	@Override
	protected double getWeight(LinearConstraint lc, Cone cone) {
		boolean hasFirstSingleton = false;
		
		for (Variable var : lc.getVariables().keySet()) {
			if (isSingleton(var.getCone())) {
				if (hasFirstSingleton) {
					if (p == 0) {
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
					else {
						return weights[program.getIndex(lc)];
					}
				}
				else {
					hasFirstSingleton = true;
				}
			}
		}
		
		return Double.POSITIVE_INFINITY;

	}

	@Override
	protected void processAcceptedPartition() {
		/* Computes weights */
//		for (int i = 0; i < weights.length; i++)
//			weights[i] = 1;
		
		Queue<ConstraintDepthPair> queue = new LinkedList<ConstraintDepthPair>();
		Set<LinearConstraint> closedSet = new HashSet<LinearConstraint>();
		Set<LinearConstraint> consToProcess = new HashSet<LinearConstraint>();
		ConstraintDepthPair cdPair, newCDPair;
		int index;
		
		for (LinearConstraint lcToUpdate : partitions.lastElement().getCutConstraints()) {
			index = program.getIndex(lcToUpdate);
			
			if (alwaysCutConstraints.contains(lcToUpdate))
				weights[index] += 10000;
			
			closedSet.add(lcToUpdate);
			newCDPair = new ConstraintDepthPair();
			newCDPair.constraint = lcToUpdate;
			newCDPair.depth = 0;
			queue.add(newCDPair);
			
			while (!queue.isEmpty()) {
				cdPair =  queue.remove();
				
				for (Variable conVar : cdPair.constraint.getVariables().keySet()) {
					Cone cone = conVar.getCone();
					if (cone instanceof NonNegativeOrthantCone) {
						consToProcess.addAll(((NonNegativeOrthantCone) cone).getVariable().getLinearConstraints());
					}
					else if (cone instanceof SecondOrderCone) {
						for (Variable socVar : ((SecondOrderCone) cone).getVariables())
							consToProcess.addAll(socVar.getLinearConstraints());
					}
					else
						throw new IllegalStateException();
				}
				
				for (LinearConstraint conToProcess : consToProcess) {
					if (!closedSet.contains(conToProcess)) {
						weights[index] += Math.pow(base, (depthLimit - cdPair.depth));
						if (cdPair.depth < depthLimit) {
							newCDPair = new ConstraintDepthPair();
							newCDPair.constraint = conToProcess;
							newCDPair.depth = cdPair.depth + 1;
							queue.add(newCDPair);
							closedSet.add(conToProcess);
						}
					}
				}
			}
			closedSet.clear();
			consToProcess.clear();
		}
	}
	
	final class ConstraintDepthPair {
		private LinearConstraint constraint;
		private int depth;
	}

}
