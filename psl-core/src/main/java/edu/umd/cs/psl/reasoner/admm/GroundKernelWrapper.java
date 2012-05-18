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

import java.util.Vector;

import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

abstract class GroundKernelWrapper {
	
	protected final ADMMReasoner reasoner;
	protected final Vector<Double> x;
	protected final Vector<Double> y;
	protected final Vector<Integer> zIndices;
	
	protected GroundKernelWrapper(ADMMReasoner reasoner, GroundKernel groundKernel) {
		this.reasoner = reasoner;
		
		/* Might be an overestimate of variables, because it also counts fixed and functional atoms */
		int numAtoms = groundKernel.getAtoms().size();
		
		x = new Vector<Double>(numAtoms);
		y = new Vector<Double>(numAtoms);
		zIndices = new Vector<Integer>(numAtoms);
	}
	
	protected void addVariable(AtomFunctionVariable var) {
		zIndices.add(reasoner.getConsensusIndex(this, var, x.size()));
		x.add(reasoner.z.get(zIndices.lastElement()));
		y.add(0.0);
	}
	
	abstract protected void minimize();
	
	/**
	 * @return this for convenience
	 */
	protected GroundKernelWrapper updateLagrange() {
		for (int i = 0; i < y.size(); i++) {
			y.set(i, y.get(i) + reasoner.stepSize * (x.get(i) - reasoner.z.get(zIndices.get(i))));
		}
		
		return this;
	}
}
