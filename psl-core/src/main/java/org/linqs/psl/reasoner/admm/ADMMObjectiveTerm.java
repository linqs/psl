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
package org.linqs.psl.reasoner.admm;

import org.linqs.psl.reasoner.term.Term;

/**
 * A term in the objective to be optimized by an {@link ADMMReasoner}.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public abstract class ADMMObjectiveTerm implements Term {
	protected final ADMMReasoner reasoner;
	protected final double[] x;
	protected final double[] y;
	protected final int[] zIndices;
	
	/**
	 * Caller releases control of |zIndices|.
	 */
	public ADMMObjectiveTerm(ADMMReasoner reasoner, int[] zIndices) {
		this.reasoner = reasoner;
		
		x = new double[zIndices.length];
		y = new double[zIndices.length];
		
		this.zIndices = zIndices;
		
		// This loop ensures that the reasoner, when it first computes y, will keep it at 0.
		// ie. Start each variable in this term at its current consensus value.
		for (int i = 0; i < x.length; i++) {
			// TODO(eriq): This is not always the same. We need to find a proper value w/o the reasoner.
			x[i] = reasoner.getConsensusValue(zIndices[i]);
		}
	}
	
	/**
	 * Updates x to the solution of <br />
	 * argmin f(x) + stepSize / 2 * \|x - z + y / stepSize \|_2^2 <br />
	 * for the objective term f(x)
	 */
	 protected abstract void minimize();
	
	/**
	 * @return this for convenience
	 */
	protected ADMMObjectiveTerm updateLagrange() {
		for (int i = 0; i < y.length; i++) {
			y[i] = y[i] + reasoner.getStepSize() * (x[i] - reasoner.getConsensusValue(zIndices[i]));
		}
		
		return this;
	}
}
