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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import edu.umd.cs.psl.model.parameters.PositiveWeight;
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
	 * Key for Boolean property that indicates whether to reset step-size schedule
	 * for each EM round. If TRUE, schedule will be {@link VotedPerceptron#STEP_SIZE_KEY}
	 * at start of each round. If FALSE, schedule will smoothly decrease across rounds,
	 * i.e., the schedule will be 1/ (round number * num steps + step number).
	 * 
	 * This property has no effect if {@link VotedPerceptron#STEP_SCHEDULE_KEY} is false.
	 */
	public static final String RESET_SCHEDULE_KEY = CONFIG_PREFIX + ".resetschedule";
	/** Default value for STORE_WEIGHTS_KEY */
	public static final boolean RESET_SCHEDULE_DEFAULT = true;
	
	/**
	 * Key for Boolean property that indicates whether to store weights along entire optimization path
	 */
	public static final String STORE_WEIGHTS_KEY = CONFIG_PREFIX + ".storeweights";
	/** Default value for STORE_WEIGHTS_KEY */
	public static final boolean STORE_WEIGHTS_DEFAULT = false;
	
	/**
	 * Key for positive double property for the minimum absolute change in weights
	 * such that EM is considered converged
	 */
	public static final String TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
	/** Default value for TOLERANCE_KEY property */
	public static final double TOLERANCE_DEFAULT = 1e-3;
	
	protected final int iterations;
	protected final double tolerance;
	protected final boolean resetSchedule;
	
	private int round;
	
	protected final boolean storeWeights;
	protected ArrayList<Map<CompatibilityKernel, Double>> storedWeights;

	
	/**
	 * A reasoner for inferring the latent variables conditioned on
	 * the observations and labels
	 */
	protected Reasoner latentVariableReasoner;
	
	public ExpectationMaximization(Model model, Database rvDB,
			Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);

		iterations = config.getInt(ITER_KEY, ITER_DEFAULT);

		tolerance = config.getDouble(TOLERANCE_KEY, TOLERANCE_DEFAULT);
		
		resetSchedule = config.getBoolean(RESET_SCHEDULE_KEY, RESET_SCHEDULE_DEFAULT);
		
		latentVariableReasoner = null;
		
		storeWeights = config.getBoolean(STORE_WEIGHTS_KEY, STORE_WEIGHTS_DEFAULT);
		if (storeWeights) 
			storedWeights = new ArrayList<Map<CompatibilityKernel, Double>>();
	}

	@Override
	protected void doLearn() {
		double[] weights = new double[kernels.size()];
		for (int i = 0; i < weights.length; i++)
			weights[i] = kernels.get(i).getWeight().getWeight();
		double [] avgWeights = new double[kernels.size()];
		
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

				avgWeights[i] = (1 - (1.0 / (double) round)) * avgWeights[i] + (1.0 / (double) round) * weights[i];		
			}
			
			if (storeWeights) {
				Map<CompatibilityKernel,Double> weightMap = new HashMap<CompatibilityKernel, Double>();
				for (int i = 0; i < kernels.size(); i++) {
					double weight = (averageSteps)? avgWeights[i] : weights[i];
					if (weight > 0.0)
						weightMap.put(kernels.get(i), weight);
				}
				storedWeights.add(weightMap);
			}

			double loss = getLoss();
			double regularizer = computeRegularizer();
			double objective = loss + regularizer;
			
			change = Math.sqrt(change);
			if (change <= tolerance) {
				log.info("EM converged with m-step norm {} in {} rounds. Loss: " + loss, change, round);
				break;
			} else
				log.info("Finished EM round {} with m-step norm {}. Loss: " + loss + ", regularizer: " + regularizer + ", objective: " + objective, round, change);
		}
		
		if (averageSteps) 
			for (int i = 0; i < kernels.size(); i++)
				kernels.get(i).setWeight(new PositiveWeight(avgWeights[i]));
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
	protected double getStepSize(int iter) {
		if (scheduleStepSize && !resetSchedule) {
			return stepSize / (double) ((round-1) * numSteps + iter + 1);
		}
		else
			return super.getStepSize(iter);
	}
	
	public ArrayList<Map<CompatibilityKernel, Double>> getStoredWeights() {
		return (storeWeights)? storedWeights : null;
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
