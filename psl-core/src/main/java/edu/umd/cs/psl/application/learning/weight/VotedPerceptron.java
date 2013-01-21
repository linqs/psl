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
 * saves the new weights. The learned weights are the averages of the saved weights.
 * <p>
 * In the gradient of the objective, the expected total incompatibility is
 * approximated by the total incompatibility at the most-probable assignment
 * to the random variables given the current weights.  
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class VotedPerceptron implements ModelApplication {
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "votedperceptron";
	
	/**
	 * Key for double property which will be multiplied with the objective
	 * gradient to compute a step.
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
	
	private Model model;
	private Database rvDB, observedDB;
	private ConfigBundle config;
	
	private final double stepSize;
	private final int numSteps;
	
	public VotedPerceptron(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this.model = model;
		this.rvDB = rvDB;
		this.observedDB = observedDB;
		this.config = config;
		
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		numSteps = config.getInt(NUM_STEPS_KEY, NUM_STEPS_DEFAULT);
		if (numSteps <= 0)
			throw new IllegalArgumentException("Number of steps must be positive integer.");
	}
	
	/**
	 * TODO
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} in the distribution are those
	 * persisted in the Database when this method is called. All RandomVariableAtoms
	 * which the Model might access must be persisted in the Database.
	 * 
	 * @see DatabasePopulator
	 */
	public void learn() 
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		RunningProcess proc = LocalProcessMonitor.get().startProcess();
		
		List<CompatibilityKernel> kernels = new ArrayList<CompatibilityKernel>();
		double[] avgWeights;
		double[] oldWeights;
		double[] newWeights;
		double[] truthIncompatibility;
		double mpeIncompatibility;
		
		/* Gathers the CompatibilityKernels */
		for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class))
			kernels.add(k);
		
		avgWeights = new double[kernels.size()];
		oldWeights = new double[kernels.size()];
		newWeights = new double[kernels.size()];
		truthIncompatibility = new double[kernels.size()];

		/* Sets up the ground model */
		Reasoner reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		TrainingMap trainingMap = new TrainingMap(rvDB, observedDB);
		if (trainingMap.getLatentVariables().size() > 0)
			throw new IllegalArgumentException("All RandomVariableAtoms must have " +
					"corresponding ObservedAtoms. Latent variables are not supported " +
					"by VotedPerceptron.");
		Grounding.groundAll(model, trainingMap, reasoner);
		
		/* Computes the observed incompatibility */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}
		
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += gk.getIncompatibility();
			}
			
			/* Initializes the current weights */
			newWeights[i] = kernels.get(i).getWeight().getWeight();
		}
		
		/* Does the perceptron steps */
		for (int step = 0; step < numSteps; step++) {
			reasoner.optimize();
			
			for (int i = 0; i < kernels.size(); i++) {
				oldWeights[i] = newWeights[i];
				mpeIncompatibility = 0.0;
				
				for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
					mpeIncompatibility += gk.getIncompatibility();
				}
				
				newWeights[i] = oldWeights[i] + stepSize * (truthIncompatibility[i] - mpeIncompatibility);
				newWeights[i] = Math.max(newWeights[i], 0.0);
				avgWeights[i] += newWeights[i];
				
				kernels.get(i).setWeight(new PositiveWeight(newWeights[i]));
			}
			reasoner.changedKernelWeights();
		}
		
		/* Sets the weights to their averages */
		for (int i = 0; i < kernels.size(); i++)
			kernels.get(i).setWeight(new PositiveWeight(avgWeights[i] / numSteps));
		
		proc.terminate();

	}

	@Override
	public void close() {
		model=null;
		rvDB = null;
		config = null;
	}

}
