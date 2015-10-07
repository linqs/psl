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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

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
import edu.umd.cs.psl.model.kernel.linearconstraint.GroundValueConstraint;

/**
 * EM algorithm which fits a Bernoulli mean field (product of independent Bernoulli
 * distributions) during the E-step.
 * <p>
 * During the E-step, the mean field is fit via block coordinate descent.
 * <p>
 * During the M-step, the likelihood is maximized using the voted perceptron
 * algorithm with the expectation of the potential functions estimated using the
 * MPE state.
 * <p>
 * This algorithm does not support models with constraints.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BernoulliMeanFieldEM extends ExpectationMaximization {

	private static final Logger log = LoggerFactory.getLogger(BernoulliMeanFieldEM.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "bernoullimeanfieldem";
	
	/**
	 * Key for Boolean property. If true, the mean field will be reinitialized
	 * via MPE inference at each round. If false, each mean will be initialized
	 * to 0.5 before the first round. 
	 */
	public static final String MPE_INITIALIZATION_KEY = CONFIG_PREFIX + ".mpeinit";
	/** Default value for MPE_INITIALIZATION_KEY property */
	public static final boolean MPE_INITIALIZATION_DEFAULT = true;
	
	protected final Map<RandomVariableAtom, Double> means;
	protected final boolean mpeInit;

	public BernoulliMeanFieldEM(Model model, Database rvDB, Database observedDB,
			ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		means = new HashMap<RandomVariableAtom, Double>();
		mpeInit = config.getBoolean(MPE_INITIALIZATION_KEY, MPE_INITIALIZATION_DEFAULT);
	}
	
	@Override
	protected void doLearn() {
		super.doLearn();
		for (Map.Entry<RandomVariableAtom, Double> e : means.entrySet())
			log.debug("Mean for {}: {}", e.getKey(), e.getValue());
	}
	
	@Override
	protected void minimizeKLDivergence() {
		if (mpeInit)
			setMeansToMPE();
		setLabeledRandomVariables();
		log.debug("Starting KL divergence: {}", getKLDivergence());
		Vector<RandomVariableAtom> incidentLatentRVs = new Vector<RandomVariableAtom>();
		/* Iteratively minimizes the KL divergence */
		for (int round = 0; round < 10; round++) {
			/* 
			 * Iterates over each latent random variable and minimizes with respect
			 * to the corresponding mean in the mean field 
			 */
			for (RandomVariableAtom latentRV : trainingMap.getLatentVariables()) {
				double c = 0.0;
				/*
				 * Iterates over each potential which is a function of the current
				 * latent random variable
				 */
				for(GroundKernel gk : latentRV.getRegisteredGroundKernels()) {
					if (gk instanceof GroundCompatibilityKernel) {
						if (reasoner.containsGroundKernel(gk)) {
							GroundCompatibilityKernel gck = (GroundCompatibilityKernel) gk;
							incidentLatentRVs.clear();
							
							/* Collects latent variables incident on this potential */
							for (GroundAtom atom : gck.getAtoms())
								if (trainingMap.getLatentVariables().contains(atom))
									incidentLatentRVs.add((RandomVariableAtom) atom);
							
							/*
							 * Iterates over joint settings of latent variables
							 */
							if (incidentLatentRVs.size() > 0) {
								for (int i = 0; i < Math.pow(2, incidentLatentRVs.size()); i++) {
									double meanFieldProb = 1.0;
									double sign = 1.0;
									
									for (int j = 0; j < incidentLatentRVs.size(); j++) {
										double mean = means.get(incidentLatentRVs.get(j));
										/* If the jth variable is 1 in the current setting... */
										if ((i >> j & 1) == 1) {
											if (!latentRV.equals(incidentLatentRVs.get(j)))
												meanFieldProb *= mean;
											incidentLatentRVs.get(j).setValue(1.0);
										}
										/* Else, if it is 0 */
										else {
											if (!latentRV.equals(incidentLatentRVs.get(j)))
												meanFieldProb *= (1 - mean);
											else
												sign = -1.0;
											incidentLatentRVs.get(j).setValue(0.0);
										}
									}
									
									c += gck.getWeight().getWeight() * gck.getIncompatibility() * meanFieldProb * sign; 
								}
							}
							else
								throw new IllegalStateException("Expected there to be at least one incident latent RV.");
						}
						else
							log.warn("Ground kernel {} registered to atom {} is not in " +
									"the current distribution. Skipping.", gk, latentRV);
					}
					else
						throw new IllegalStateException("Model contains a constraint: " + gk);
				}
				double newMean = 1 / (1 + Math.exp(c));
				if (newMean == 0.0)
					newMean = 0.0001;
				else if (newMean == 1.0)
					newMean = 0.9999;
				means.put(latentRV, newMean);
			}
			
			log.debug("KL divergence after round {}: {}", round+1, getKLDivergence());
		}
	}
	
	/**
	 * Computes the expected ground truth incompatibility with respect to the mean field.
	 * <p>
	 * Also counts the numbers of groundings.
	 */
	@Override
	protected double[] computeObservedIncomp() {
		numGroundings = new double[kernels.size()];
		double[] truthIncompatibility = new double[kernels.size()];
		setLabeledRandomVariables();
		
		/* Computes the expected observed incompatibilities and numbers of groundings */
		Vector<RandomVariableAtom> incidentLatentRVs = new Vector<RandomVariableAtom>();
		for (int iKernel = 0; iKernel < kernels.size(); iKernel++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(iKernel))) {
				GroundCompatibilityKernel gck = (GroundCompatibilityKernel) gk;
				incidentLatentRVs.clear();
				
				/* Collects latent variables incident on this potential */
				for (GroundAtom atom : gck.getAtoms())
					if (trainingMap.getLatentVariables().contains(atom))
						incidentLatentRVs.add((RandomVariableAtom) atom);
				
				/*
				 * Iterates over joint settings of latent variables. If there are
				 * no incident latent variables, just adds the value of the potential
				 */
				for (int i = 0; i < Math.pow(2, incidentLatentRVs.size()); i++) {
					double meanFieldProb = 1.0;
					
					for (int j = 0; j < incidentLatentRVs.size(); j++) {
						double mean = means.get(incidentLatentRVs.get(j));
						/* If the jth variable is 1 in the current setting... */
						if ((i >> j & 1) == 1) {
							meanFieldProb *= mean;
							incidentLatentRVs.get(j).setValue(1.0);
						}
						/* Else, if it is 0 */
						else {
							meanFieldProb *= (1 - mean);
							incidentLatentRVs.get(j).setValue(0.0);
						}
					}
					
					truthIncompatibility[iKernel] += gck.getIncompatibility() * meanFieldProb;
				}
				numGroundings[iKernel]++;
			}
		}
		
		return truthIncompatibility;
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
	
	@Override
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		super.initGroundModel();
		
		/* Sets all means to 0.5 if MPE_INITIALIZATION_KEY is false */
		if (!mpeInit) {
			means.clear();
			for (RandomVariableAtom latentRV : trainingMap.getLatentVariables())
				means.put(latentRV, 0.5);
		}
	}
	
	protected void setMeansToMPE() {
		/* Runs MPE inference in p(Z|X,Y) to set mean field */
		log.debug("Running MPE inference to initialize mean field.");
		
		/* Creates constraints to fix labeled random variables to their true values */
		List<GroundValueConstraint> labelConstraints = new ArrayList<GroundValueConstraint>();
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet())
			labelConstraints.add(new GroundValueConstraint(e.getKey(), e.getValue().getValue()));
		
		/* Infers most probable assignment latent variables */
		reasoner.optimize();
		
		/* Removes constraints */
		for (GroundValueConstraint con : labelConstraints)
			reasoner.removeGroundKernel(con);
		
		/* Sets mean field */
		means.clear();
		for (RandomVariableAtom latentRV : trainingMap.getLatentVariables())
			means.put(latentRV, latentRV.getValue());
	}
	
	/**
	 * Computes the KL divergence from the mean field to the distribution p(Z|X,Y),
	 * minus a constant (the log partition function plus some constant potentials).
	 * 
	 * @return the KL divergence
	 */
	protected double getKLDivergence() {
		double kl = 0.0;
		
		/* First computes negative entropy of the mean field */
		for (Double mean : means.values())
			kl += mean * Math.log(mean) + (1 - mean) * Math.log(1 - mean);
		
		/*
		 * Then computes the cross entropy from the mean field to the (unnormalized)
		 * distribution p(Z|X,Y) 
		 */
		setLabeledRandomVariables();
		Vector<RandomVariableAtom> incidentLatentRVs = new Vector<RandomVariableAtom>();
		for (GroundCompatibilityKernel gck : reasoner.getCompatibilityKernels()) {
			incidentLatentRVs.clear();
			
			/* Collects latent variables incident on this potential */
			for (GroundAtom atom : gck.getAtoms())
				if (trainingMap.getLatentVariables().contains(atom))
					incidentLatentRVs.add((RandomVariableAtom) atom);
			
			/*
			 * Iterates over joint settings of latent variables. If there are
			 * no incident latent variables, just adds the weighted value of
			 * the potential
			 */
			for (int i = 0; i < Math.pow(2, incidentLatentRVs.size()); i++) {
				double meanFieldProb = 1.0;
				
				for (int j = 0; j < incidentLatentRVs.size(); j++) {
					double mean = means.get(incidentLatentRVs.get(j));
					/* If the jth variable is 1 in the current setting... */
					if ((i >> j & 1) == 1) {
						meanFieldProb *= mean;
						incidentLatentRVs.get(j).setValue(1.0);
					}
					/* Else, if it is 0 */
					else {
						meanFieldProb *= (1 - mean);
						incidentLatentRVs.get(j).setValue(0.0);
					}
				}
				
				kl += gck.getWeight().getWeight() * gck.getIncompatibility() * meanFieldProb; 
			}
		}
		
		return kl;
	}
}
