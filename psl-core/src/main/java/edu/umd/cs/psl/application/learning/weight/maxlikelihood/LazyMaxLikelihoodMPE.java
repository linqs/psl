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

import java.util.Iterator;

import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;

/**
 * Voted perceptron algorithm that does not require a ground model of pre-specified
 * dimensionality.
 * <p>
 * For the gradient of the objective, the expected total incompatibility is
 * computed by finding the MPE state.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class LazyMaxLikelihoodMPE extends VotedPerceptron {
	
	private AtomEventFramework eventFramework;
	private Reasoner obsReasoner;
	private AtomEventFramework obsEventFramework;

	/**
	 * Constructs a new weight learner.
	 * 
	 * @param model  the model for which to learn weights
	 * @param rvDB  a Database containing all ObservedAtoms in the ground model
	 * @param observedDB  a Database containing all ObservedAtoms in the ground model,
	 *                        as well as the ground truth as ObservedAtoms
	 * @param config  configuration bundle
	 */
	public LazyMaxLikelihoodMPE(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
	}
	
	@Override
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		eventFramework = new AtomEventFramework(rvDB, config);
		
		/* Registers the Model's Kernels with the AtomEventFramework */
		for (Kernel k : model.getKernels())
			k.registerForAtomEvents(eventFramework, reasoner);
		
		/* Grounds the model */
		Grounding.groundAll(model, eventFramework, reasoner);
		while (eventFramework.checkToActivate() > 0)
			eventFramework.workOffJobQueue();
		
		/* Sets up for computing observed incompatibility */
		obsReasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		obsEventFramework = new AtomEventFramework(observedDB, config);
		
		/* Registers the Model's Kernels with the observations AtomEventFramework */
		for (Kernel k : model.getKernels())
			k.registerForAtomEvents(obsEventFramework, obsReasoner);
	}
	
	@Override
	protected double[] computeObservedIncomp() {
		double[] truthIncompatibility = new double[kernels.size()];
		
		/* Grounds the model with observations */
		Grounding.groundAll(model, obsEventFramework, obsReasoner);
		while (obsEventFramework.checkToActivate() > 0)
			obsEventFramework.workOffJobQueue();
		
		obsReasoner.optimize();
		
		/* Computes the observed incompatibilities */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : obsReasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}
		
		return truthIncompatibility;
	}
	
	@Override
	protected double[] computeExpectedIncomp() {
		double[] expIncomp = new double[kernels.size()];
		
		/* Computes the MPE state */
		do {
			eventFramework.workOffJobQueue();
			reasoner.optimize();
		}
		while (eventFramework.checkToActivate() > 0);
		
		/* Computes incompatibility */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				expIncomp[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}
		
		return expIncomp;
	}
	
	@Override
	protected double[] computeScalingFactor() {
		double[] scalingFactor = new double[kernels.size()];
		
		for (int i = 0; i < kernels.size(); i++) {
			Iterator<GroundKernel> itr = reasoner.getGroundKernels(kernels.get(i)).iterator();
			while(itr.hasNext()) {
				itr.next();
				scalingFactor[i]++;
			}
			
			if (scalingFactor[i] == 0.0)
				scalingFactor[i]++;
		}
		
		return scalingFactor;
	}
	
	@Override
	protected void cleanUpGroundModel() {
		super.cleanUpGroundModel();
		obsReasoner.close();
		obsReasoner = null;
		
		/* Unregisters the Model's Kernels with the AtomEventFramework */
		for (Kernel k : model.getKernels())
			k.unregisterForAtomEvents(eventFramework, reasoner);
		eventFramework = null;
		
		/* Unregisters the Model's Kernels with the observations AtomEventFramework */
		for (Kernel k : model.getKernels())
			k.unregisterForAtomEvents(obsEventFramework, obsReasoner);
		obsEventFramework = null;
	}

}
