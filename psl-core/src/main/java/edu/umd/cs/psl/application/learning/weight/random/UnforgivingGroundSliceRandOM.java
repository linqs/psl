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
package edu.umd.cs.psl.application.learning.weight.random;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;

/**
 * A {@link GroundSliceRandOM} learning algorithm that constrains the ground
 * truth to be within an L1-ball centered on the mode of the energy function's
 * optimum.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class UnforgivingGroundSliceRandOM extends GroundSliceRandOM {
	
	private static final Logger log = LoggerFactory.getLogger(UnforgivingGroundSliceRandOM.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "unforgivinggroundslicerandom";
	
	/**
	 * Key for positive double to be used as dimension of L1 ball around ground truth
	 */
	public static final String L1_DIMENSION_KEY = CONFIG_PREFIX + ".l1dimension";
	/** Default value for L1_DIMENSION_KEY */
	public static final double L1_DIMENSION_DEFAULT = .25;
	
	protected double l1Dimension;

	public UnforgivingGroundSliceRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		l1Dimension = config.getDouble(L1_DIMENSION_KEY, L1_DIMENSION_DEFAULT);
		if (l1Dimension <= 0.0)
			throw new IllegalArgumentException("L1 dimension must be positive.");
	}
	
	@Override
	protected void doLearn() {
		/* Initializes GroundKernel weights to non-zero likelihood */
		Set<GroundCompatibilityKernel> unaryKernels = new HashSet<GroundCompatibilityKernel>();
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			/*
			 * Collects unary ground compatibility kernels which can be used to
			 * adjust optimal truth value. All others are given weight zero for now.
			 */
			for (GroundKernel gk : e.getKey().getRegisteredGroundKernels()) {
				if (gk instanceof GroundCompatibilityKernel) {
					GroundCompatibilityKernel gck = (GroundCompatibilityKernel) gk;
					
					/* Checks if gck is unary */
					boolean unary = true;
					for (GroundAtom atom : gck.getAtoms()) {
						if (atom instanceof GroundCompatibilityKernel && !atom.equals(e.getKey()))
							unary = false;
					}
					
					/* Handles gck based on whether it is unary */
					if (unary)
						unaryKernels.add(gck);
					else
						gck.setWeight(new PositiveWeight(0.0));
				}
			}
			
			/*
			 * Now that the unary ground compatibility kernels have been collected,
			 * adjusts their weights
			 */
			
			/* Finds one that makes the truth value go up and one that makes it go down */
			GroundCompatibilityKernel posGCK = null, negGCK = null;
			double incAtZero, incAtOne;
			for (GroundCompatibilityKernel gck : unaryKernels) {
				e.getKey().setValue(0.0);
				incAtZero = gck.getIncompatibility();
				e.getKey().setValue(1.0);
				incAtOne = gck.getIncompatibility();
				
				if (incAtZero == 1.0 && incAtOne == 0.0 && gck.getAtoms().size() == 2 && posGCK == null)
					posGCK = gck;
				else if (incAtZero == 0.0 && incAtOne == 1.0 && gck.getAtoms().size() == 2 && negGCK == null)
					negGCK = gck;
			}
			
			if (posGCK == null || negGCK == null)
				throw new IllegalStateException("Did not find a positive and a " +
						"negative unary ground compatibility kernel for atom: " +
						e.getKey());
			
			for (GroundCompatibilityKernel gck : unaryKernels)
				gck.setWeight(new PositiveWeight(0.0));
			
			if (e.getValue().getValue() == 1.0)
				posGCK.setWeight(new PositiveWeight(1.0));
			else if (e.getValue().getValue() == 0.0)
				negGCK.setWeight(new PositiveWeight(1.0));
			else
				throw new IllegalStateException("Unexpected truth value of " +
						e.getValue().getValue() + " for atom " + e.getKey() + ".");
			
			/* Clears unaryKernels for next atom */
			unaryKernels.clear();
		}
		
		reasoner.changedGroundKernelWeights();
		reasoner.optimize();
		log.warn("Log likelihood of observations: {}", getLogLikelihoodObservations());
		
		/* Begins learning */
		super.doLearn();
	}
	
	@Override
	protected double getLogLikelihoodObservations() {
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet())
			if (Math.abs(e.getKey().getValue() - e.getValue().getValue()) >= l1Dimension) {
//				log.warn("Atom {} has truth value of {} but observed is {}.", new Object[] {e.getKey(), e.getKey().getValue(), e.getValue().getValue()});
//				for (GroundKernel gk : e.getKey().getRegisteredGroundKernels())
//					log.warn("Ground kernel: {}", gk);
				return Double.NEGATIVE_INFINITY;
//				throw new IllegalStateException();
			}
		
		return 0.0;
	}

}
