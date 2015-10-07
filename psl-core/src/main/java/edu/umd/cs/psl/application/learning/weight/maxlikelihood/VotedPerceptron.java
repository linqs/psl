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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.application.learning.weight.WeightLearningApplication;
import edu.umd.cs.psl.application.learning.weight.maxmargin.LossAugmentingGroundKernel;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.NegativeWeight;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;

/**
 * TODO: rewrite class documentation to describe general gradient-based learning algorithms
 * TODO: refactor class so loss augmentation is a strategy that can only be applied to inference-based learning objectives
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
	 * Key for boolean property for whether to add loss-augmentation for online large margin
	 */
	public static final String AUGMENT_LOSS_KEY = CONFIG_PREFIX + ".augmentloss";
	/** Default value for AUGMENT_LOSS_KEY */
	public static final boolean AUGMENT_LOSS_DEFAULT = false;
	
	/**
	 * Key for positive double property scaling the L2 regularization
	 * (\lambda / 2) * ||w||^2
	 */
	public static final String L2_REGULARIZATION_KEY = CONFIG_PREFIX + ".l2regularization";
	/** Default value for L2_REGULARIZATION_KEY */
	public static final double L2_REGULARIZATION_DEFAULT = 0.0;
	
	/**
	 * Key for positive double property scaling the L1 regularization
	 * \gamma * |w|
	 */
	public static final String L1_REGULARIZATION_KEY = CONFIG_PREFIX + ".l1regularization";
	/** Default value for L1_REGULARIZATION_KEY */
	public static final double L1_REGULARIZATION_DEFAULT = 0.0;
		
	/**
	 * Key for positive double property which will be multiplied with the
	 * objective gradient to compute a step.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
	/** Default value for STEP_SIZE_KEY */
	public static final double STEP_SIZE_DEFAULT = 1.0;

	/**
	 * Key for Boolean property that indicates whether to shrink the stepsize by
	 * a 1/t schedule.
	 */
	public static final String STEP_SCHEDULE_KEY = CONFIG_PREFIX + ".schedule";
	/** Default value for STEP_SCHEDULE_KEY */
	public static final boolean STEP_SCHEDULE_DEFAULT = true;

	/**
	 * Key for Boolean property that indicates whether to scale gradient by 
	 * number of groundings
	 */
	public static final String SCALE_GRADIENT_KEY = CONFIG_PREFIX + ".scalegradient";
	/** Default value for SCALE_GRADIENT_KEY */
	public static final boolean SCALE_GRADIENT_DEFAULT = true;
	
	/**
	 * Key for Boolean property that indicates whether to average all visited
	 * weights together for final output.
	 */
	public static final String AVERAGE_STEPS_KEY = CONFIG_PREFIX + ".averagesteps";
	/** Default value for AVERAGE_STEPS_KEY */
	public static final boolean AVERAGE_STEPS_DEFAULT = true;

	/**
	 * Key for positive integer property. VotedPerceptron will take this many
	 * steps to learn weights.
	 */
	public static final String NUM_STEPS_KEY = CONFIG_PREFIX + ".numsteps";
	/** Default value for NUM_STEPS_KEY */
	public static final int NUM_STEPS_DEFAULT = 25;
	
	/**
	 * Key for boolean property. If true, only non-negative weights will be learned. 
	 */
	public static final String NONNEGATIVE_WEIGHTS_KEY = CONFIG_PREFIX + ".nonnegativeweights";
	/** Default value for NONNEGATIVE_WEIGHTS_KEY */
	public static final boolean NONNEGATIVE_WEIGHTS_DEFAULT = true;
	
	protected double[] numGroundings;
	
	protected final double stepSize;
	protected final int numSteps;
	protected final double l2Regularization;
	protected final double l1Regularization;
	protected final boolean augmentLoss;
	protected final boolean scheduleStepSize;
	protected final boolean scaleGradient;
	protected final boolean averageSteps;
	protected final boolean nonnegativeWeights;
	protected double[] truthIncompatibility;
	protected double[] expectedIncompatibility;
	
	/** Stop flag to quit the loop. */
	protected boolean toStop = false;
	
	/** Learning loss at current point */
	private double loss = Double.POSITIVE_INFINITY;
	
	public VotedPerceptron(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		if (stepSize <= 0)
			throw new IllegalArgumentException("Step size must be positive.");
		numSteps = config.getInt(NUM_STEPS_KEY, NUM_STEPS_DEFAULT);
		if (numSteps <= 0)
			throw new IllegalArgumentException("Number of steps must be positive.");
		l2Regularization = config.getDouble(L2_REGULARIZATION_KEY, L2_REGULARIZATION_DEFAULT);
		if (l2Regularization < 0)
			throw new IllegalArgumentException("L2 regularization parameter must be non-negative.");
		l1Regularization = config.getDouble(L1_REGULARIZATION_KEY, L1_REGULARIZATION_DEFAULT);
		if (l1Regularization < 0)
			throw new IllegalArgumentException("L1 regularization parameter must be non-negative.");
		augmentLoss = config.getBoolean(AUGMENT_LOSS_KEY, AUGMENT_LOSS_DEFAULT);
		scheduleStepSize = config.getBoolean(STEP_SCHEDULE_KEY, STEP_SCHEDULE_DEFAULT);
		scaleGradient = config.getBoolean(SCALE_GRADIENT_KEY, SCALE_GRADIENT_DEFAULT);
		averageSteps = config.getBoolean(AVERAGE_STEPS_KEY, AVERAGE_STEPS_DEFAULT);
		nonnegativeWeights = config.getBoolean(NONNEGATIVE_WEIGHTS_KEY, NONNEGATIVE_WEIGHTS_DEFAULT);
	}
	
	protected void addLossAugmentedKernels() {
		double obsvTrueWeight, obsvFalseWeight;
		obsvTrueWeight = -1.0;
		obsvFalseWeight = -1.0;


		/* Sets up loss augmenting ground kernels */
		List<LossAugmentingGroundKernel> lossKernels = new ArrayList<LossAugmentingGroundKernel>(trainingMap.getTrainingMap().size());
		List<LossAugmentingGroundKernel> nonExtremeLossKernels = new ArrayList<LossAugmentingGroundKernel>();
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			double truth = e.getValue().getValue();
			LossAugmentingGroundKernel gk;

			/* If ground truth is at 1.0 or 0.0, sets up ground kernel without planning to change it */
			if (truth == 1.0 || truth == 0.0) {
				NegativeWeight weight = new NegativeWeight((truth == 1.0) ? obsvTrueWeight : obsvFalseWeight);
				gk = new LossAugmentingGroundKernel(e.getKey(), truth, weight);
			}
			/* Else, does a little more to check it and change it later */
			else {
				if (truth >= 0.5)
					gk = new LossAugmentingGroundKernel(e.getKey(), 1.0, new NegativeWeight(obsvTrueWeight));
				else
					gk = new LossAugmentingGroundKernel(e.getKey(), 1.0, new PositiveWeight(-1 * obsvTrueWeight));

				nonExtremeLossKernels.add(gk);
				// log.error("Non extreme ground truth found at atom {}. This is not properly supported yet in online max-margin learning.", e.getValue());
			}

			reasoner.addGroundKernel(gk);
			lossKernels.add(gk);
		}
	}
	
	protected void removeLossAugmentedKernels() {
		List<LossAugmentingGroundKernel> lossKernels = new ArrayList<LossAugmentingGroundKernel>();
		for (LossAugmentingGroundKernel k : Iterables.filter(reasoner.getGroundKernels(), LossAugmentingGroundKernel.class))
			lossKernels.add(k);
		for (LossAugmentingGroundKernel k : lossKernels)
			reasoner.removeGroundKernel(k);
		lossKernels = new ArrayList<LossAugmentingGroundKernel>();
	}

	protected double getStepSize(int iter) {
		if (scheduleStepSize)
			return stepSize / (double) (iter + 1);
		else
			return stepSize;
	}
	
	@Override
	protected void doLearn() {
		double[] avgWeights;
		double[] scalingFactor;
		
		avgWeights = new double[kernels.size()];
		
		/* Computes the observed incompatibilities */
		truthIncompatibility = computeObservedIncomp();
	
		if (augmentLoss)
			addLossAugmentedKernels();
		
		/* Resets random variables to default values for computing expected incompatibility */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet())
			e.getKey().setValue(0.0);
		for (RandomVariableAtom atom : trainingMap.getLatentVariables())
			atom.setValue(0.0);
		
		/* Computes the gradient steps */
		for (int step = 0; step < numSteps; step++) {
			log.debug("Starting iter {}", step+1);
			
			/* Computes the expected total incompatibility for each CompatibilityKernel */
			expectedIncompatibility = computeExpectedIncomp();
			scalingFactor  = computeScalingFactor();
			loss = computeLoss();
			
			/* Updates weights */
			for (int i = 0; i < kernels.size(); i++) {
				double weight = kernels.get(i).getWeight().getWeight();
				double currentStep = (expectedIncompatibility[i] - truthIncompatibility[i]
						- l2Regularization * weight
						- l1Regularization) / scalingFactor[i];
				currentStep *= getStepSize(step);
				log.debug("Step of {} for kernel {}", currentStep, kernels.get(i));
				log.debug(" --- Expected incomp.: {}, Truth incomp.: {}", expectedIncompatibility[i], truthIncompatibility[i]);weight += currentStep;
				weight += currentStep;
				if (nonnegativeWeights)
					weight = Math.max(weight, 0.0);
				avgWeights[i] += weight;
				Weight newWeight = (weight >= 0.0) ? new PositiveWeight(weight) : new NegativeWeight(weight); 
				kernels.get(i).setWeight(newWeight);
			}
			
			reasoner.changedGroundKernelWeights();
			// notify the registered observers
			setChanged();
			notifyObservers(new IntermediateState(step, numSteps));
			// if stop() has been called, exit the loop early
			if (toStop) {
				break;
			}
		}
		
		/* Sets the weights to their averages */
		if (averageSteps) {
			for (int i = 0; i < kernels.size(); i++) {
				double avgWeight = avgWeights[i] / numSteps;
				kernels.get(i).setWeight((avgWeight >= 0.0) ? new PositiveWeight(avgWeight) : new NegativeWeight(avgWeight));
			}
			reasoner.changedGroundKernelWeights();
		}
		
		if (augmentLoss)
			removeLossAugmentedKernels();
	}
	
	protected double[] computeObservedIncomp() {
		numGroundings = new double[kernels.size()];
		double[] truthIncompatibility = new double[kernels.size()];
		setLabeledRandomVariables();
		
		/* Computes the observed incompatibilities and numbers of groundings */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
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
	
	protected double computeRegularizer() {
		double l2 = 0;
		double l1 = 0;
		for (int i = 0; i < kernels.size(); i++) {
			l2 += Math.pow(kernels.get(i).getWeight().getWeight(), 2);
			l1 += Math.abs(kernels.get(i).getWeight().getWeight());
		}
		return 0.5 * l2Regularization * l2 + l1Regularization * l1;
	}
	
	/**
	 * Internal method for computing the loss at the current point
	 * before taking a step.
	 * 
	 * Returns 0.0 if not overridden by a subclass
	 * 
	 * @return current learning loss
	 */
	protected double computeLoss() {
		return Double.POSITIVE_INFINITY;
	}
	
	public double getLoss() {
		return loss;
	}
	
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
			factor[i] = (scaleGradient && numGroundings[i] > 0) ? numGroundings[i] : 1.0;
		return factor;
	}
	
	/**
	* Notifies VotedPerceptron to exit after the current step
	*/
	public void stop() {
		toStop = true;
	}

	/**
	 * Intermediate state object to notify the registered observers.
	 */
	public class IntermediateState {
		public final int step;
		public final int maxStep;
		
		public IntermediateState(int currStep, int numSteps) {
			this.step = currStep;
			this.maxStep = numSteps;
		}
	}
}
