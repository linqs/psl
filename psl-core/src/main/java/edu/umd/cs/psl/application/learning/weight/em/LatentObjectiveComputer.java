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
package edu.umd.cs.psl.application.learning.weight.em;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;

public class LatentObjectiveComputer extends HardEM {

	public LatentObjectiveComputer(Model model, Database rvDB,
			Database observedDB, ConfigBundle config) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		super(model, rvDB, observedDB, config);

		/* Gathers the CompatibilityKernels */
		for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class))
			if (k.isWeightMutable())
				kernels.add(k);
			else
				immutableKernels.add(k);
		
		/* Sets up the ground model */
		initGroundModel();
	}
	
	/**
	 * Computes primal objective
	 * @return
	 */
	public double getObjective() {
		reasoner.changedGroundKernelWeights();
		minimizeKLDivergence();
		computeObservedIncomp();
		computeExpectedIncomp();
		return computeRegularizer() + computeLoss();
	}

}
