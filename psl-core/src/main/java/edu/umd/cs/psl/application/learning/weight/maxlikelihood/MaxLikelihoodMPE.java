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
package edu.umd.cs.psl.application.learning.weight.maxlikelihood;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;

/**
 * Learns weights by optimizing the log likelihood of the data using
 * the voted perceptron algorithm.
 * <p>
 * The expected total incompatibility is estimated with the total incompatibility
 * in the MPE state.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class MaxLikelihoodMPE extends VotedPerceptron {

	public MaxLikelihoodMPE(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
	}
	
	@Override
	protected double[] computeExpectedIncomp() {
		double[] expIncomp = new double[kernels.size()];
		
		/* Computes the MPE state */
		reasoner.optimize();
		
		/* Computes incompatibility */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				expIncomp[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}
		
		return expIncomp;
	}

}
