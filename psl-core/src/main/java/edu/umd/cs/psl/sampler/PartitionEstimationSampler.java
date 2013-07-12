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
package edu.umd.cs.psl.sampler;

import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

public class PartitionEstimationSampler extends UniformSampler {

	private double total;
	
	public PartitionEstimationSampler() {
		this(defaultMaxNoSteps, defaultSignificantDigits);
	}
	
	public PartitionEstimationSampler(int maxNoSteps) {
		this(maxNoSteps, defaultSignificantDigits);
	}
 	
	public PartitionEstimationSampler(int maxNoSteps, int significantDigits) {
		super(maxNoSteps, significantDigits);
		total = 0.0;
	}
	
	public double getPartitionEstimate() {
		return total / getNoSamples();
	}
	
	@Override
	protected void processNewDimension(AtomFunctionVariable var, int index) {
		/* Intentionally empty */
	}
	
	@Override
	protected void processSampledPoint(Iterable<GroundKernel> groundKernels) {
		double incompatibility = 0.0;
    	for (GroundKernel gk : groundKernels) {
    		if (gk instanceof GroundCompatibilityKernel) {
    			incompatibility += ((GroundCompatibilityKernel) gk).getIncompatibility();
    		}
    	}
    	total += Math.exp(-1 * incompatibility);
	}
	
}
