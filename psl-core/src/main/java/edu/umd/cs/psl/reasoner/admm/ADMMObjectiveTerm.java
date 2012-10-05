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
package edu.umd.cs.psl.reasoner.admm;

/**
 * A term in the objective to be optimized by an {@link ADMMReasoner}.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
abstract public class ADMMObjectiveTerm {
	protected final ADMMReasoner reasoner;
	protected final double[] x;
	protected final double[] y;
	protected final int[] zIndices;
	protected final double[] lb;
	protected final double[] ub;
	
	public ADMMObjectiveTerm(ADMMReasoner reasoner, int[] zIndices,
			double[] lowerBounds, double[] upperBounds) {
		this.reasoner = reasoner;
		
		x = new double[zIndices.length];
		y = new double[zIndices.length];
		
		this.zIndices = zIndices;
		lb = lowerBounds;
		ub = upperBounds;
		for (int  i = 0; i < x.length; i++)
			if (lb[i] >= ub[i])
				throw new IllegalArgumentException("Lower bound must be less than upper bound.");
	}
	
//	protected void addVariable(AtomFunctionVariable var, double lowerBound, double upperBound) {
//		zIndices.add(reasoner.getConsensusIndex(this, var, x.size()));
//		x.add(reasoner.z.get(zIndices.lastElement()));
//		y.add(0.0);
//		lb.add(lowerBound);
//		ub.add(upperBound);
//	}
	
	protected void setLowerBound(int index, double lowerBound) {
		if (lowerBound < ub[index])
			lb[index] = lowerBound;
		else
			throw new IllegalArgumentException("New lower bound must be less than current upper bound.");
	}
	
	protected void setUpperBound(int index, double upperBound) {
		if (upperBound > lb[index])
			ub[index] = upperBound;
		else
			throw new IllegalArgumentException("New upper bound must be greater than current lower bound.");
	}
	
	protected void setBounds(int index, double lowerBound, double upperBound) {
		if (lowerBound < upperBound) {
			lb[index] = lowerBound;
			ub[index] = upperBound;
		}
		else
			throw new IllegalArgumentException("Lower bound must be less than upper bound.");
	}
	
	abstract protected void minimize();
	
	/**
	 * @return this for convenience
	 */
	protected ADMMObjectiveTerm updateLagrange() {
		for (int i = 0; i < y.length; i++) {
			y[i] = y[i] + reasoner.stepSize * (x[i] - reasoner.z.get(zIndices[i]));
		}
		
		return this;
	}
}
