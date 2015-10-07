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
package edu.umd.cs.psl.application.learning.weight.maxlikelihood;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.kernel.linearconstraint.GroundValueConstraint;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.util.database.Queries;

/**
 * Voted perceptron algorithm that does not require a ground model of pre-specified
 * dimensionality.
 * <p>
 * For the gradient of the objective, the expected total incompatibility is
 * computed by finding the MPE state.
 * <p>
 * Note that this class does not support latent variables but will not throw
 * an error if the labelDB does not include a corresponding label for a
 * RandomVariableAtom. All unspecified labels will set to their most probable
 * values conditioned on the observations in distributionDB and the labels in labelDB.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class LazyMaxLikelihoodMPE extends VotedPerceptron {
	
	private static final Logger log = LoggerFactory.getLogger(AtomEventFramework.class);
	
	private AtomEventFramework eventFramework;

	/**
	 * Constructs a new weight learner.
	 * 
	 * @param model  the model for which to learn weights
	 * @param distributionDB  a Database containing all atoms for the ground distribution
	 * @param labelDB  a Database containing labels for the unknowns in the distribution
	 * @param config  configuration bundle
	 */
	public LazyMaxLikelihoodMPE(Model model, Database distributionDB, Database labelDB, ConfigBundle config) {
		super(model, distributionDB, labelDB, config);
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
	}
	
	@Override
	protected double[] computeObservedIncomp() {
		
		/*
		 * In order to ground out the graphical model with the label truth values,
		 * all labeled atoms with non-zero truth values are activated and constrained.
		 * 
		 * Then, a loop is used to extend the network based on MPE inference and
		 * activation events, and then check for new labeled RandomVariableAtoms
		 * to constrain as necessary.
		 */
	
		Map<RandomVariableAtom, GroundValueConstraint> targetMap = new HashMap<RandomVariableAtom, GroundValueConstraint>();
		
		/* Activates all non-zero labeled atoms */
		for (StandardPredicate p : observedDB.getRegisteredPredicates()) {
			Set<GroundAtom> labeledAtoms = Queries.getAllAtoms(observedDB, p);
			for (GroundAtom labeledAtom : labeledAtoms) {
				/*
				 * Double checks that it is observed in observedDB and unobserved in rvDB,
				 * since those are the only atoms in observedDB to be considered. Also,
				 * checks if the labeled truth value is greater than 0.0, since activation
				 * would be unnecessary until the corresponding atom in rvDB had a non-zero
				 * truth value. If all three conditions are met, activates and constrains the atom.
				 */
				if (labeledAtom instanceof ObservedAtom && labeledAtom.getValue() > 0.0) {
					GroundAtom correspondingAtom = eventFramework.getAtom(labeledAtom.getPredicate(), labeledAtom.getArguments());
					if (correspondingAtom instanceof RandomVariableAtom) {
						eventFramework.activateAtom((RandomVariableAtom) correspondingAtom);
						GroundValueConstraint con = new GroundValueConstraint((RandomVariableAtom) correspondingAtom, ((ObservedAtom) labeledAtom).getValue());
						targetMap.put((RandomVariableAtom) correspondingAtom, con);
						reasoner.addGroundKernel(con);
					}
				}
			}
		}
		
		boolean continueGrowing;
		/* Maintains a temporary set during collection to avoid concurrent modification errors */
		Set<GroundValueConstraint> toAdd = new HashSet<GroundValueConstraint>();
		
		log.debug("Beginning to grow labeled network.");
		do {
			continueGrowing = false;
			
			/* Computes the MPE state and grows graphical model */
			do {
				eventFramework.workOffJobQueue();
				reasoner.optimize();
			}
			while (eventFramework.checkToActivate() > 0);
			
			/* Collects existing RandomVariableAtoms and pairs them with label constraints */
			for (GroundKernel k : reasoner.getGroundKernels()) {
				for (Atom a : k.getAtoms()) {
					if (a instanceof RandomVariableAtom) {
						RandomVariableAtom rv = (RandomVariableAtom) a;
						if (!targetMap.containsKey(rv)) {
							Atom possibleLabel = observedDB.getAtom(rv.getPredicate(), rv.getArguments());
							if (possibleLabel instanceof ObservedAtom) {
								GroundValueConstraint con = new GroundValueConstraint(rv, ((ObservedAtom) possibleLabel).getValue());
								targetMap.put(rv, con);
								toAdd.add(con);
								continueGrowing = true;
							}
						}
					}
				}
			}
			
			/* Adds new constraints to reasoner */
			for (GroundValueConstraint con : toAdd)
				reasoner.addGroundKernel(con);
			toAdd.clear();
			
		}
		while (continueGrowing);
		log.debug("Finished growing labeled network.");
		
		/* Computes the observed incompatibilities */
		double[] truthIncompatibility = new double[kernels.size()];
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}
		
		/* Removes label value constraints */
		for (GroundValueConstraint con : targetMap.values()) {
			reasoner.removeGroundKernel(con);
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
		/* Unregisters the Model's Kernels with the AtomEventFramework */
		for (Kernel k : model.getKernels())
			k.unregisterForAtomEvents(eventFramework, reasoner);
		eventFramework = null;
		
		super.cleanUpGroundModel();
	}

}
