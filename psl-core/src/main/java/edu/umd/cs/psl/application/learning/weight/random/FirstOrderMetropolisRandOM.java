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
package edu.umd.cs.psl.application.learning.weight.random;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;

/**
 * A {@link MetropolisRandOM} learning algorithm that samples one weight for
 * all {@link GroundCompatibilityKernel GroundCompatibilityKernels} with the
 * same parent {@link CompatibilityKernel}.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class FirstOrderMetropolisRandOM extends MetropolisRandOM {

	public FirstOrderMetropolisRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
	}

	@Override
	protected void prepareForRound() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void sampleAndSetWeights() {
		// TODO Auto-generated method stub

	}

	@Override
	protected double getLogLikelihoodSampledWeights() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected void acceptSample(boolean burnIn) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void rejectSample(boolean burnIn) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void finishRound() {
		// TODO Auto-generated method stub

	}

}
