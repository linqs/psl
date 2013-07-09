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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;

abstract public class AbstractCompletePartitioner implements CompletePartitioner {
	
	protected Vector<ConicProgramPartition> partitions;
	
	protected ConicProgram program;
	
	public AbstractCompletePartitioner() {
		program = null;
		partitions = new Vector<ConicProgramPartition>();
	}

	@Override
	public void setConicProgram(ConicProgram p) {
		program = p;
		if (!supportsConeTypes(p.getConeTypes()))
			throw new IllegalArgumentException("Unsupported cone type.");
	}

	@Override
	public void partition() {
		if (program == null)
			throw new IllegalStateException("No conic program has been set.");
		
		program.verifyCheckedOut();
		
		if (!supportsConeTypes(program.getConeTypes()))
			throw new IllegalStateException("Unsupported cone type.");
		
		doPartition();
		// reorderPartitions();
	}
	
	abstract protected void doPartition();

	@Override
	public ConicProgramPartition getPartition(int i) {
		return partitions.get(i);
	}

	@Override
	public int size() {
		return partitions.size();
	}

	@Override
	public void checkOutAllMatrices() {
		for (ConicProgramPartition p : partitions)
			p.checkOutMatrices();
	}

	@Override
	public void checkInAllMatrices() {
		for (ConicProgramPartition p : partitions)
			p.checkInMatrices();
	}
	
	protected void reorderPartitions() {
		if (partitions.size() > 1) {
			Vector<ConicProgramPartition> newOrdering = new Vector<ConicProgramPartition>();
			Set<ConicProgramPartition> remainingPartitions = new HashSet<ConicProgramPartition>(partitions);
			
			Set<LinearConstraint> cutSet1, cutSet2, intersection, union;
			ConicProgramPartition bestPartition1 = null;
			ConicProgramPartition bestPartition2 = null;
			double similarity;
			double lowestSimilarity = 1.0;
			
			for (int i = 0; i < partitions.size(); i++) {
				for (int j = i+1; j < partitions.size(); j++) {
					cutSet1 = partitions.get(i).getCutConstraints();
					cutSet2 = partitions.get(j).getCutConstraints();
					
					intersection = new HashSet<LinearConstraint>(cutSet1);
					intersection.retainAll(cutSet2);
					union = new HashSet<LinearConstraint>(cutSet1);
					union.addAll(cutSet2);
					similarity = (double) intersection.size() / union.size();
					
					if (bestPartition1 == null || similarity < lowestSimilarity) {
						bestPartition1 = partitions.get(i);
						bestPartition2 = partitions.get(j);
						lowestSimilarity = similarity;
					}
				}
			}
			
			newOrdering.add(bestPartition1);
			newOrdering.add(bestPartition2);
			remainingPartitions.remove(bestPartition1);
			remainingPartitions.remove(bestPartition2);
			
			while (!remainingPartitions.isEmpty()) {
				bestPartition1 = null;
				lowestSimilarity = 1.0;
				cutSet1 = newOrdering.lastElement().getCutConstraints();
				
				for (ConicProgramPartition p : remainingPartitions) {
					cutSet2 = p.getCutConstraints();
					
					intersection = new HashSet<LinearConstraint>(cutSet1);
					intersection.retainAll(cutSet2);
					union = new HashSet<LinearConstraint>(cutSet1);
					union.addAll(cutSet2);
					similarity = (double) intersection.size() / union.size();
					
					if (bestPartition1 == null || similarity < lowestSimilarity) {
						bestPartition1 = p;
						lowestSimilarity = similarity;
					}
				}
				
				newOrdering.add(bestPartition1);
				remainingPartitions.remove(bestPartition1);
			}
			
			partitions = newOrdering;
		}
	}
	
	@Override
	public String toString() {
		double[] stats;
		ArrayList<Double> sizes = new ArrayList<Double>(size());
		ArrayList<Double> similarities = new ArrayList<Double>(size() * (size()-1));
		
		for (ConicProgramPartition p : partitions) {
			sizes.add(Double.valueOf(p.getCutConstraints().size()));
		}
		
		stats = meanAndStdDev(sizes);
		double sizeMean = stats[0];
		double sizeStdDev = stats[1];
		
		Set<LinearConstraint> cutSet1, cutSet2, intersection, union;
		
		for (int i =  0; i < partitions.size(); i++) {
			for (int j = i+1; j < partitions.size(); j++) {
				cutSet1 = partitions.get(i).getCutConstraints();
				cutSet2 = partitions.get(j).getCutConstraints();
				
				intersection = new HashSet<LinearConstraint>(cutSet1);
				intersection.retainAll(cutSet2);
				union = new HashSet<LinearConstraint>(cutSet1);
				union.addAll(cutSet2);
				
				similarities.add((double) intersection.size() / union.size());
			}
		}
		
		stats = meanAndStdDev(similarities);
		
		double simMean = stats[0];
		double simStdDev = stats[1];
		String toReturn = "Complete Partition\n"
				+ "Size: " + size() + "\n"
				+ "Mean cut set size: " + sizeMean + "\n"
				+ "Std. dev.: " + sizeStdDev + "\n"
				+ "Mean pairwise similarity: " + simMean + "\n"
				+ "Std. dev.: " + simStdDev;
		
		for (ConicProgramPartition p : partitions) {
			toReturn = toReturn + "\n" + p.getCutConstraints().size();
		}
		
		return toReturn;
	}
	
	private double[] meanAndStdDev(List<Double> values) {
		double[] toReturn = new double[2];
		double sum = 0.0;
		double sumOfSquares = 0.0;
		for (Double sim : values) {
			sum += sim;
			sumOfSquares += Math.pow(sim, 2);
		}
		
		toReturn[0] = sum / values.size();
		toReturn[1] = Math.sqrt(sumOfSquares / values.size() - Math.pow(toReturn[0], 2));
		
		return toReturn;
	}

}
