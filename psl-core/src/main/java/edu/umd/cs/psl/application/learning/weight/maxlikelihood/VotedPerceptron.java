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

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.learning.weight.WeightLearningApplication;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;

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
public abstract class VotedPerceptron extends WeightLearningApplication {
	
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
	public static final int NUM_STEPS_DEFAULT = 25;
	protected double[] numGroundings;
	
	private final double stepSize;
	private final int numSteps;
	

	/** stop flag to quit the loop. */
	protected boolean toStop = false;
	
	public VotedPerceptron(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		if (stepSize <= 0)
			throw new IllegalArgumentException("Step size must be positive.");
		numSteps = config.getInt(NUM_STEPS_KEY, NUM_STEPS_DEFAULT);
		if (numSteps <= 0)
			throw new IllegalArgumentException("Number of steps must be positive.");
	}
	
	@Override
	protected void doLearn() {
		double[] avgWeights;
		double[] truthIncompatibility;
		double[] expectedIncompatibility;
		double[] scalingFactor;
		
		avgWeights = new double[kernels.size()];
		numGroundings = new double[kernels.size()];
		
		/* Computes the observed incompatibilities */
		truthIncompatibility = computeObservedIncomp();
		
		/* Computes the Perceptron steps */
		for (int step = 0; step < numSteps; step++) {
			log.debug("Starting iter {}", step+1);
			
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
			reasoner.changedGroundKernelWeights();
			// notify the registered observers
			setChanged();
			notifyObservers(new IntermidateState(step, numSteps));
			// if stop() has been called, exit the loop early
			if (toStop) {
				break;
			}
		}
		
		/* Sets the weights to their averages */
		for (int i = 0; i < kernels.size(); i++) {
			kernels.get(i).setWeight(new PositiveWeight(avgWeights[i] / numSteps));
		}
		
	}
	
	protected double[] computeObservedIncomp() {
		double[] truthIncompatibility = new double[kernels.size()];
		
		/* Computes the observed incompatibilities and numbers of groundings */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}
		
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
				numGroundings[i]++;
			}
		}
		
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(0.0);
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
	
	/**
	 * Computes the amount to scale gradient for each rule
	 * Scales by the number of groundings of each rule
	 * unless the rule is not grounded in the training set, in which case 
	 * scales by 1.0
	 * @return
	 */
	protected double[] computeScalingFactor() {
		double [] factor = new double[numGroundings.length];
		for (int i = 0; i < numGroundings.length; i++) 
			factor[i] = (numGroundings[i] > 0) ? numGroundings[i] : 1.0;
		return factor;
	}
	
	/**
	* Notifies VotedPerceptron to exit after the current step
	*/
	public void stop() {
		toStop = true;
	}


	/**
	 * Intermediate state object to 
	 * notify the registered observers.
	 *
	 */
	public class IntermidateState {
		public final int step;
		public final int maxStep;
		
		public IntermidateState(int currStep, int numSteps) {
			this.step = currStep;
			this.maxStep = numSteps;
		}
	}
}
