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
package org.linqs.psl.application.topicmodel;

import java.util.HashMap;
import java.util.Map;

import org.linqs.psl.application.learning.weight.maxlikelihood.SimplexSampler;
import org.linqs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.ConstraintBlocker;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;

/**
 * Learns weights by optimizing the pseudo-log-likelihood of the data.
 * Modified version of MaxPseudoLikelihood which takes the LTN's log losses into account.
 * This is the naive version which still importance samples from the uniform distribution
 * instead of the Dirichlet.  It runs into numerical stability problems (which could be
 * improved by the log-sum-exp trick, TODO, though I don't recommend to use this code!)
 * 
 * @author Ben London <blondon@cs.umd.edu>
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class LatentTopicNetworkMaxPseudoLikelihood_Naive extends VotedPerceptron {

	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "LTNmaxspeudolikelihood";
	
	/**
	 * Boolean property. If true, MaxPseudoLikelihood will treat RandomVariableAtoms
	 * as boolean valued. Note that this restricts the types of contraints supported.
	 */
	public static final String BOOLEAN_KEY = CONFIG_PREFIX + ".bool";
	/** Default value for BOOLEAN_KEY */
	public static final boolean BOOLEAN_DEFAULT = false;
	
	/**
	 * Key for positive integer property.
	 * MaxPseudoLikelihood will sample this many values to approximate
	 * the integrals in the marginal computation.
	 */
	public static final String NUM_SAMPLES_KEY = CONFIG_PREFIX + ".numsamples";
	/** Default value for NUM_SAMPLES_KEY */
	public static final int NUM_SAMPLES_DEFAULT = 10;
	
	/**
	 * Key for constraint violation tolerance
	 */
	public static final String CONSTRAINT_TOLERANCE_KEY = CONFIG_PREFIX + ".constrainttolerance";
	/** Default value for CONSTRAINT_TOLERANCE **/
	public static final double CONSTRAINT_TOLERANCE_DEFAULT = 1e-5;
	
	/**
	 * Key for positive double property.
	 * Used as minimum width for bounds of integration.
	 */
	public static final String MIN_WIDTH_KEY = CONFIG_PREFIX + ".minwidth";
	/** Default value for MIN_WIDTH_KEY */
	public static final double MIN_WIDTH_DEFAULT = 1e-2;
	
	private ConstraintBlocker blocker;
	private final boolean bool;
	private final int numSamples;
	private final double minWidth;
	private final double constraintTol;
	
	//Latent Topic Network-specific variables.
	private double dirichletParam; //alpha or beta.  This should be greater than one.
	private Predicate p; //Either Theta or Phi.
	
	/**
	 * Constructor
	 * @param model
	 * @param rvDB
	 * @param observedDB
	 * @param config
	 * @param expectedCounts The matrix of LDA expected counts for either Theta or Phi.
	 * @param Predicate p Either Theta or Phi.
	 */
	public LatentTopicNetworkMaxPseudoLikelihood_Naive(Model model, Database rvDB, Database observedDB, ConfigBundle config, double dirichletParam, Predicate p) {
		super(model, rvDB, observedDB, config);
		bool = config.getBoolean(BOOLEAN_KEY, BOOLEAN_DEFAULT);
		numSamples = config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
		if (numSamples <= 0)
			throw new IllegalArgumentException("Number of samples must be positive integer.");
		minWidth = config.getDouble(MIN_WIDTH_KEY, MIN_WIDTH_DEFAULT);
		if (minWidth <= 0)
			throw new IllegalArgumentException("Minimum width must be positive double.");
		constraintTol = config.getDouble(CONSTRAINT_TOLERANCE_KEY, CONSTRAINT_TOLERANCE_DEFAULT);
		if (constraintTol <= 0)
			throw new IllegalArgumentException("Minimum width must be positive double.");
		
		//LTN-specific variables
		this.dirichletParam = dirichletParam;
		this.p = p;
	}
	
	/**
	 * Note: calls super.initGroundModel() first, in order to ground model. 
	 */
	@Override
	public void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		/* Invoke method in the parent class to setup ground model */
		super.initGroundModel();
		blocker = new ConstraintBlocker(reasoner);
		blocker.prepareBlocks(true);
	}
	
	/**
	 * Computes the expected incompatibility using the pseudolikelihood.
	 * Uses Monte Carlo integration to approximate definite integrals,
	 * since they do not admit a closed-form antiderivative.
	 */
	@Override
	protected double[] computeExpectedIncomp() {
		/* Puts RandomVariableAtoms in 2d array by block */
		RandomVariableAtom[][] rvBlocks = blocker.getRVBlocks();
		/* If true, exactly one Atom in the RV block must be 1.0. If false, at most one can. */
		boolean[] exactlyOne = blocker.getExactlyOne();
		/* Collects GroundCompatibilityKernels incident on each block of RandomVariableAtoms */
		WeightedGroundRule[][] incidentGKs = blocker.getIncidentGKs();
		
		double[] expInc = new double[kernels.size()];
		
		/* Accumulate the expected incompatibility over all atoms */
		for (int iBlock = 0; iBlock < rvBlocks.length; iBlock++) {
			
			if (rvBlocks[iBlock].length == 0)
				// TODO: Is this correct?
				continue;
			if (incidentGKs[iBlock].length == 0)
				continue; //Also TODO: Is this correct? -JF
			
			/* Sample numSamples random numbers in the range of integration */
			double[][] s;
			if (!bool) {
				s = new double[Math.max(numSamples * rvBlocks[iBlock].length, 150)][];
				SimplexSampler simplexSampler = new SimplexSampler();
				for (int iSample = 0; iSample < s.length; iSample++) {
					s[iSample] = simplexSampler.getNext(s.length);
				}
			}
			else {
				s = new double[(exactlyOne[iBlock]) ? rvBlocks[iBlock].length : rvBlocks[iBlock].length+1][];
				for (int iRV = 0; iRV < ((exactlyOne[iBlock]) ? s.length : s.length - 1); iRV++) {
					s[iRV] = new double[rvBlocks[iBlock].length];
					s[iRV][iRV] = 1.0;
				}
				if (!exactlyOne[iBlock])
					s[s.length-1] = new double[rvBlocks[iBlock].length];
			}
				
			/* Compute the incompatibility of each sample for each kernel */
			HashMap<WeightedRule,double[]> incompatibilities = new HashMap<WeightedRule,double[]>();
			/* Also compute the latent topic network log-loss of each sample -JF*/
			double[] logLosses = new double[s.length];
			
			/* Saves original state */
			double[] originalState = new double[rvBlocks[iBlock].length];
			for (int iSave = 0; iSave < rvBlocks[iBlock].length; iSave++)
				originalState[iSave] = rvBlocks[iBlock][iSave].getValue();
			
			/* Computes the probability */
			for (GroundRule gk : incidentGKs[iBlock]) {
				if (gk instanceof WeightedGroundRule) {
					WeightedRule k = (WeightedRule) gk.getRule();
					if (!incompatibilities.containsKey(k))
						incompatibilities.put(k, new double[s.length]);
					double[] inc = incompatibilities.get(k);
					for (int iSample = 0; iSample < s.length; iSample++) {
						/* Changes the state of the block to the next point */
						for (int iChange = 0; iChange < rvBlocks[iBlock].length; iChange++)
							rvBlocks[iBlock][iChange].setValue(s[iSample][iChange]);
						
						inc[iSample] += ((WeightedGroundRule) gk).getIncompatibility();
						
						/* Compute the log loss terms for the block. -JF */
						for (int iVar = 0; iVar < rvBlocks[iBlock].length; iVar++) {
							if (rvBlocks[iBlock][iVar].getPredicate() != p) //only Theta or Phi
								continue;
							double val = rvBlocks[iBlock][iVar].getValue();
							logLosses[iSample] += (dirichletParam - 1) * Math.log(val);
						}
					}
				}
			}
			
			/* Remember to return the block to its original state! */
			for (int iChange = 0; iChange < rvBlocks[iBlock].length; iChange++)
				rvBlocks[iBlock][iChange].setValue(originalState[iChange]);
			
			/* Compute the exp incomp and accumulate the partition for the current atom. */
			HashMap<WeightedRule,Double> expIncAtom = new HashMap<WeightedRule,Double>();
			double Z = 0.0;
			for (int j = 0; j < s.length; j++) {
				/* Compute the exponent */
				double sum = 0.0;
				for (Map.Entry<WeightedRule,double[]> e2 : incompatibilities.entrySet()) {
					WeightedRule k = e2.getKey();
					double[] inc = e2.getValue();
					sum -= k.getWeight().getWeight() * inc[j];
				}
				//add the log-loss term. -JF
				sum += logLosses[j];
				double exp = Math.exp(sum);
				/* Add to partition */
				Z += exp;
				/* Compute the exp incomp for current atom */
				for (Map.Entry<WeightedRule,double[]> e2 : incompatibilities.entrySet()) {
					WeightedRule k = e2.getKey();
					if (!expIncAtom.containsKey(k))
						expIncAtom.put(k, 0.0);
					double val = expIncAtom.get(k).doubleValue();
					val += exp * incompatibilities.get(k)[j];
					expIncAtom.put(k, val);
				}
			}
			/* Finally, we add to the exp incomp for each kernel */ 
			for (int i = 0; i < kernels.size(); i++) {
				WeightedRule k = kernels.get(i);
				if (expIncAtom.containsKey(k))
					if (expIncAtom.get(k) > 0.0) 
						expInc[i] += expIncAtom.get(k) / Z;
			}
		}
	
		return expInc;
	}

}
