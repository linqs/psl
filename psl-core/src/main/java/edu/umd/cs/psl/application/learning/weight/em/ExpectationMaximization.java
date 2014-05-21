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
package edu.umd.cs.psl.application.learning.weight.em;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.application.learning.weight.TrainingMap;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.linearconstraint.GroundValueConstraint;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;

/**
 * Abstract superclass for implementations of the expectation-maximization
 * algorithm for learning with latent variables.
 * <p>
 * This class extends {@link VotedPerceptron}, which is used during the M-step
 * to update the weights.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
abstract public class ExpectationMaximization extends VotedPerceptron {

	private static final Logger log = LoggerFactory.getLogger(ExpectationMaximization.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "em";
	
	/**
	 * Key for positive int property for the number of iterations of expectation
	 * maximization to perform
	 */
	public static final String ITER_KEY = CONFIG_PREFIX + ".iterations";
	/** Default value for ITER_KEY property */
	public static final int ITER_DEFAULT = 10;
	
	/**
	 * Key for positive double property for the minimum absolute change in weights
	 * such that EM is considered converged
	 */
	public static final String TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
	/** Default value for TOLERANCE_KEY property */
	public static final double TOLERANCE_DEFAULT = 1e-3;
	
	protected final int iterations;
	protected final double tolerance;
	
	private int round;
	
	/**
	 * A reasoner for inferring the latent variables conditioned on
	 * the observations and labels
	 */
	protected Reasoner latentVariableReasoner;
	
	protected List<CompatibilityKernel> kernels;
	protected double [] weights;
	
	public ExpectationMaximization(Model model, Database rvDB,
			Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);

		iterations = config.getInt(ITER_KEY, ITER_DEFAULT);

		tolerance = config.getDouble(TOLERANCE_KEY, TOLERANCE_DEFAULT);
		
		kernels = new ArrayList<CompatibilityKernel>();
		
		for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class))
			kernels.add(k);
		weights = new double[kernels.size()];
		for (int i = 0; i < weights.length; i++)
			weights[i] = Double.POSITIVE_INFINITY;
		
		latentVariableReasoner = null;
	}

	@Override
	protected void doLearn() {
		round = 0;
		while (round++ < iterations) {
			log.debug("Beginning EM round {} of {}", round, iterations);
			/* E-step */
			minimizeKLDivergence();
			/* M-step */
			super.doLearn();
			
			double change = 0;
			for (int i = 0; i < kernels.size(); i++) {
				change += Math.pow(weights[i] - kernels.get(i).getWeight().getWeight(), 2);
				weights[i] = kernels.get(i).getWeight().getWeight();
			}
			
			double loss = getLoss();
			
			change = Math.sqrt(change);
			if (change <= tolerance) {
				log.info("EM converged with absolute weight change {} in {} rounds. Perceptron loss: " + loss, change, round);
				break;
			} else
				log.info("Finished EM round {} with change {}. Perceptron loss: " + loss, round, change);
		}
	}

	abstract protected void minimizeKLDivergence();

	@Override
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		trainingMap = new TrainingMap(rvDB, observedDB);
		
		reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		Grounding.groundAll(model, trainingMap, reasoner);
		
		/* 
		 * The latentVariableReasoner should be cleaned up in close(), not
		 * cleanUpGroundModel(), so that calls to inferLatentVariables() still
		 * work
		 */
		if (latentVariableReasoner != null)
			latentVariableReasoner.close();
		latentVariableReasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		Grounding.groundAll(model, trainingMap, latentVariableReasoner);
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet())
			latentVariableReasoner.addGroundKernel(new GroundValueConstraint(e.getKey(), e.getValue().getValue()));
	}
	
	/**
	 * Infers the most probable assignment to the latent variables conditioned
	 * on the observations and labeled unknowns using the most recently learned
	 * model.
	 * 
	 * The atoms with corresponding labels will be set to their label values.
	 * 
	 * @throws IllegalStateException  if no model has been learned
	 */
	public void inferLatentVariables() {
		if (latentVariableReasoner == null)
			throw new IllegalStateException("A model must have been learned " +
					"before latent variables can be inferred.");
		
		/* 
		 * Infers most probable assignment latent variables
		 * 
		 * (Called changedGroundKernelWeights() might be unnecessary, but this is
		 * the easiest way to be sure latentVariableReasoner is updated.)
		 */
		latentVariableReasoner.changedGroundKernelWeights();
		latentVariableReasoner.optimize();
	}
	
	@Override
	public void close() {
		super.close();
		if (latentVariableReasoner != null) {
			latentVariableReasoner.close();
			latentVariableReasoner = null;
		}
	}

}
