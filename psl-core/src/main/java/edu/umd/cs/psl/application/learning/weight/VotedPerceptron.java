/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.application.learning.weight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.local.LocalProcessMonitor;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;

/**
 * Learns new weights for the {@link CompatibilityKernel CompatibilityKernels}
 * in a {@link Model} using the voted perceptron algorithm.
 * <p>
 * The weight-learning objective is to maximize the likelihood according to the
 * distribution:
 * <p>
 * p(X) = 1 / Z(w)   *   exp{-sum[w * f(X)]}
 * <p>
 * where X is the set of RandomVariableAtoms, f(X) the incompatibility of
 * each GroundKernel, w is the weight of that GroundKernel, and Z(w)
 * is a normalization factor.
 * <p>
 * The voted perceptron algorithm starts at the current weights and at each step
 * computes the gradient of the objective, takes that step multiplied by a step size
 * (possibly truncated to stay in the region of feasible weights), and
 * saves the new weights. The components of the gradient are each divided by the
 * number of GroundCompatibilityKernels from that Kernel. The learned weights
 * are the averages of the saved weights.
 * <p>
 * For the gradient of the objective, the expected total incompatibility is
 * computed by subclasses in {@link #computeExpectedIncomp(List, double[])}.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public abstract class VotedPerceptron implements ModelApplication {
	
	private static final Logger log = LoggerFactory.getLogger(VotedPerceptron.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "votedperceptron";
	
	/**
	 * Key for positive double property which will be multiplied with the
	 * objective gradient to compute a step.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
	/** Default value for STEP_SIZE_KEY */
	public static final double STEP_SIZE_DEFAULT = 1.0;
	
	/**
	 * Key for positive integer property. VotedPerceptron will take this many
	 * steps to learn weights.
	 */
	public static final String NUM_STEPS_KEY = CONFIG_PREFIX + ".numsteps";
	/** Default value for NUM_STEPS_KEY */
	public static final int NUM_STEPS_DEFAULT = 10;
	
	/**
	 * Key for {@link Factory} or String property.
	 * <p>
	 * Should be set to a {@link ReasonerFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link Reasoner}.
	 */
	public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
	/**
	 * Default value for REASONER_KEY.
	 * <p>
	 * Value is instance of {@link ADMMReasonerFactory}.
	 */
	public static final ReasonerFactory REASONER_DEFAULT = new ADMMReasonerFactory();
	
	protected Model model;
	protected Database rvDB, observedDB;
	protected ConfigBundle config;
	
	protected final List<CompatibilityKernel> kernels;
	protected TrainingMap trainingMap;
	protected Reasoner reasoner;
	protected double[] numGroundings;
	
	private final double stepSize;
	private final int numSteps;
	
	public VotedPerceptron(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this.model = model;
		this.rvDB = rvDB;
		this.observedDB = observedDB;
		this.config = config;
		
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		if (stepSize <= 0)
			throw new IllegalArgumentException("Step size must be positive.");
		numSteps = config.getInt(NUM_STEPS_KEY, NUM_STEPS_DEFAULT);
		if (numSteps <= 0)
			throw new IllegalArgumentException("Number of steps must be positive.");
		kernels = new ArrayList<CompatibilityKernel>();
	}
	
	/**
	 * Learns new weights.
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} in the distribution are those
	 * persisted in the random variable Database when this method is called. All
	 * RandomVariableAtoms which the Model might access must be persisted in the Database.
	 * <p>
	 * Each such RandomVariableAtom should have a corresponding {@link ObservedAtom}
	 * in the observed Database.
	 * 
	 * @see DatabasePopulator
	 */
	public void learn()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		RunningProcess proc = LocalProcessMonitor.get().startProcess();
		
		double[] avgWeights;
		double[] truthIncompatibility;
		double[] expectedIncompatibility;
		double[] scalingFactor;
		
		/* Gathers the CompatibilityKernels */
		for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class))
			kernels.add(k);
		
		avgWeights = new double[kernels.size()];
		numGroundings = new double[kernels.size()];
		
		/* Initializes weights array

		/* Sets up the ground model */
		initGroundModel();
		
		/* Computes the observed incompatibilities */
		truthIncompatibility = computeObservedIncomp();
		
		/* Computes the Perceptron steps */
		for (int step = 0; step < numSteps; step++) {
			/* Computes the expected total incompatibility for each CompatibilityKernel */
			expectedIncompatibility = computeExpectedIncomp();
			scalingFactor  = computeScalingFactor();

			/* Updates weights */
			for (int i = 0; i < kernels.size(); i++) {
				double currentStep = stepSize / scalingFactor[i] * (expectedIncompatibility[i] - truthIncompatibility[i]);
				log.debug("Step of {} for kernel {}", currentStep, kernels.get(i));
				log.debug(" --- Expected incomp.: {}, Truth incomp.: {}", expectedIncompatibility[i], truthIncompatibility[i]);
				double weight = kernels.get(i).getWeight().getWeight() + currentStep;
				weight = Math.max(weight, 0.0);
				avgWeights[i] += weight;
				kernels.get(i).setWeight(new PositiveWeight(weight));	
			}
			reasoner.changedKernelWeights();
		}
		
		/* Sets the weights to their averages */
		for (int i = 0; i < kernels.size(); i++) {
			kernels.get(i).setWeight(new PositiveWeight(avgWeights[i] / numSteps));
		}
		
		proc.terminate();
		kernels.clear();
		cleanUpGroundModel();
	}
	
	/**
	 * Constructs a ground model using model and trainingMap, and stores the
	 * resulting GroundKernels in reasoner.
	 */
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		trainingMap = new TrainingMap(rvDB, observedDB);
		reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		if (trainingMap.getLatentVariables().size() > 0)
			throw new IllegalArgumentException("All RandomVariableAtoms must have " +
					"corresponding ObservedAtoms. Latent variables are not supported " +
					"by VotedPerceptron. Example latent variable: " + trainingMap.getLatentVariables().iterator().next());
		Grounding.groundAll(model, trainingMap, reasoner);
	}
	
	protected double[] computeObservedIncomp() {
		double[] truthIncompatibility = new double[kernels.size()];
		
		/* Computes the observed incompatibilities and numbers of groundings */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}
		
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += gk.getIncompatibility();
				numGroundings[i]++;
			}
		}
		
		return truthIncompatibility;
	}
	
	/**
	 * Computes the expected (unweighted) total incompatibility of the
	 * {@link GroundCompatibilityKernel GroundCompatibilityKernels} in reasoner
	 * for each {@link CompatibilityKernel}.
	 * 
	 * @return expected incompatibilities, ordered according to kernels
	 */
	protected abstract double[] computeExpectedIncomp();
	
	protected double[] computeScalingFactor() {
		return numGroundings;
	}
	
	protected void cleanUpGroundModel() {
		trainingMap = null;
		reasoner = null;
	}

	@Override
	public void close() {
		model = null;
		rvDB = null;
		config = null;
	}

}
