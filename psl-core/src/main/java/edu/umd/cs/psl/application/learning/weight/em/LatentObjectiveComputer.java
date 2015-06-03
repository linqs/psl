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
