package edu.umd.cs.psl.application.learning.weight;

import java.util.List;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.Reasoner;

public class MaxLikelihoodMPE extends VotedPerceptron {

	private Reasoner reasoner;
	
	public MaxLikelihoodMPE(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
	}
	
	@Override
	public void initGroundModel(Model m, TrainingMap tMap, Reasoner reasoner) {
		super.initGroundModel(m, tMap, reasoner);
		this.reasoner = reasoner;
	}
	
	@Override
	protected void computeMarginals(List<CompatibilityKernel> kernels, double[] marginals) {
		/* Compute the MPE state */
		reasoner.optimize();
		/* Compute incompatibility */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				marginals[i] += gk.getIncompatibility();
			}
		}
	}

}
