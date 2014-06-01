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

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.optimizer.lbfgs.ConvexFunc;
import edu.umd.cs.psl.optimizer.lbfgs.LBFGSB;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;

/**
 * EM algorithm which fits a point distribution to the single most probable
 * assignment of truth values to the latent variables during the E-step. 
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class DualEM extends ExpectationMaximization implements ConvexFunc {

	private static final Logger log = LoggerFactory.getLogger(DualEM.class);

	//TODO make these actual config options (and probably hide LBFGS since it's not working)
	private static boolean useLBFGS = false;
	private static boolean useAdagrad = true;
	private static boolean augmentLoss = false;

	double[] scalingFactor;
	double[] dualObservedIncompatibility, dualExpectedIncompatibility;

	public DualEM(Model model, Database rvDB, Database observedDB,
			ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		scalingFactor = new double[kernels.size()];
	}

	/**
	 * Minimizes the KL divergence by setting the latent variables to their
	 * most probable state conditioned on the evidence and the labeled
	 * random variables.
	 * <p>
	 * This method assumes that the inferred truth values will be used
	 * immediately by {@link VotedPerceptron#computeObservedIncomp()}.
	 */
	@Override
	protected void minimizeKLDivergence() {
		inferLatentVariables();
	}

	@Override
	protected double[] computeExpectedIncomp() {
		dualExpectedIncompatibility = new double[kernels.size() + immutableKernels.size()];

		/* Computes the MPE state */
		reasoner.optimize();

		ADMMReasoner admm = (ADMMReasoner) reasoner;

		// Compute the dual incompatbility for each ADMM subproblem
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				dualExpectedIncompatibility[i] += admm.getDualIncompatibility(gk);
			}
		}

		for (int i = 0; i < immutableKernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(immutableKernels.get(i))) {
				dualExpectedIncompatibility[kernels.size() + i] += admm.getDualIncompatibility(gk);
			}
		}

		return Arrays.copyOf(dualExpectedIncompatibility, kernels.size());
	}

	@Override
	protected double[] computeObservedIncomp() {
		numGroundings = new double[kernels.size()];
		dualObservedIncompatibility = new double[kernels.size() + immutableKernels.size()];
		setLabeledRandomVariables();

		ADMMReasoner admm = (ADMMReasoner) latentVariableReasoner;
		
		/* Computes the observed incompatibilities and numbers of groundings */
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : latentVariableReasoner.getGroundKernels(kernels.get(i))) {
				dualObservedIncompatibility[i] += admm.getDualIncompatibility(gk);
				numGroundings[i]++;
			}
		}
		for (int i = 0; i < immutableKernels.size(); i++) {
			for (GroundKernel gk : latentVariableReasoner.getGroundKernels(immutableKernels.get(i))) {
				dualObservedIncompatibility[kernels.size() + i] += admm.getDualIncompatibility(gk);
			}
		}

		return Arrays.copyOf(dualObservedIncompatibility, kernels.size());
	}

	@Override
	protected double computeLoss() {
		double loss = 0.0;
		for (int i = 0; i < kernels.size(); i++)
			loss += kernels.get(i).getWeight().getWeight() * (dualObservedIncompatibility[i] - dualExpectedIncompatibility[i]);
		for (int i = 0; i < immutableKernels.size(); i++)
			loss += immutableKernels.get(i).getWeight().getWeight() * (dualObservedIncompatibility[kernels.size() + i] - dualExpectedIncompatibility[kernels.size() + i]);
		return loss;
	}

	private void lbfgs() {
		LBFGSB optimizer = new LBFGSB(iterations, tolerance, kernels.size()-1, this);

		for (int i = 0; i < kernels.size(); i++) {
			optimizer.setLowerBound(i, 0.0);
			optimizer.setBoundSpec(i, 1);
		}

		double [] weights = new double[kernels.size()];
		for (int i = 0; i < kernels.size(); i++)
			weights[i] = kernels.get(i).getWeight().getWeight();
		int [] iter = new int[1];	
		boolean [] error = new boolean[1];	


		double objective = optimizer.minimize(weights, iter, error);

		log.info("LBFGS learning finished with final objective value {}", objective);

		for (int i = 0; i < kernels.size(); i++) 
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
	}

	private void adagrad() {
		/*
		 * adaptive subgradient algorithm
		 * of John Duchi, Elad Hazan, Yoram Singer (JMLR 2010)
		 */
		log.info("Starting adagrad");
		double [] weights = new double[kernels.size()];
		for (int i = 0; i < kernels.size(); i++)
			weights[i] = kernels.get(i).getWeight().getWeight();

		double [] avgWeights = new double[kernels.size()];

		double [] gradient = new double[kernels.size()];
		double [] scale = new double[kernels.size()];
		double objective = 0;
		for (int step = 0; step < iterations; step++) {
			objective = getValueAndGradient(gradient, weights);
			double gradNorm = 0;
			double change = 0;
			for (int i = 0; i < kernels.size(); i++) {
				scale[i] += gradient[i] * gradient[i];
//				scale[i] += Math.pow((double) (step + 1), 2);
//				scale[i] += 1.0;
				
				gradNorm += Math.pow(weights[i] - Math.max(0, weights[i] - gradient[i]), 2);
				
				if (scale[i] > 0.0) {
					double coeff = stepSize / Math.sqrt(scale[i]);
					weights[i] = Math.max(0, weights[i] - coeff * gradient[i]);
					change += Math.pow(weights[i] - kernels.get(i).getWeight().getWeight(), 2);
				}
				avgWeights[i] = (1 - (1.0 / (double) (step + 1.0))) * avgWeights[i] + (1.0 / (double) (step + 1.0)) * weights[i];
			}

			gradNorm = Math.sqrt(gradNorm);
			change = Math.sqrt(change);
			DecimalFormat df = new DecimalFormat("0.0000E00");
			if (step % 1 == 0)
				log.info("Iter {}, obj: {}, norm grad: " + df.format(gradNorm) + ", change: " + df.format(change), step, df.format(objective));

			if (change < tolerance) {
				log.info("Change in w ({}) is less than tolerance. Finishing adagrad.", change);
				break;
			}
		}

		log.info("Adagrad learning finished with final objective value {}", objective);

		for (int i = 0; i < kernels.size(); i++) {
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
		}
	}

	@Override
	protected void doLearn() {
//		computeExpectedIncomp();
//		minimizeKLDivergence();
		
		int maxIter = ((ADMMReasoner) reasoner).getMaxIter();
		int admmIterations = 10;
		((ADMMReasoner) reasoner).setMaxIter(admmIterations);
		((ADMMReasoner) latentVariableReasoner).setMaxIter(admmIterations);
		if (augmentLoss)
			addLossAugmentedKernels();
		if (useLBFGS) {
			lbfgs();
		} else if (useAdagrad) {
			adagrad();
		} else {
			super.doLearn();
			double [] weights = new double[kernels.size()];
			for (int i = 0; i < kernels.size(); i++)
				weights[i] = kernels.get(i).getWeight().getWeight();
			//			checkGradient(weights, 1.0);
		}
		if (augmentLoss)
			removeLossAugmentedKernels();

		((ADMMReasoner) reasoner).setMaxIter(maxIter);
		((ADMMReasoner) latentVariableReasoner).setMaxIter(maxIter);

	}

	/**
	 * computes coordinates for a curve from the current weights for plotting
	 * prints to System.out in MATLAB-friendly text format
	 * TODO move this somewhere else or delete it
	 * @param weights
	 * @param scale
	 */
	private void checkGradient(double[] weights, double scale) {
		double [] newWeights = new double[weights.length];
		double [] gradient = new double[weights.length];

		// getValueAndGradient(gradient, weights);

		// pick a random direction
		Random rand = new Random(1);
		for (int i = 0; i < gradient.length; i++)
			// if weight is at boundary, don't perturb to avoid clipping
			if (weights[i] > 0) 
				gradient[i] = rand.nextGaussian();



		double [] increments = new double[100];
		double [] objectives = new double[increments.length];
		for (int i = 0; i < increments.length; i++) {
			increments[i] = scale * (((double) i / (double) (increments.length - 1)) - 0.5);

			for (int j = 0; j < weights.length; j++) {
				newWeights[j] = weights[j] + increments[i] * gradient[j];
				if (newWeights[j] < 0) {
					objectives[i] = Double.NaN;
				}
			}

			// use NaN to indicate when we hit nonnegativity bound

			if (objectives[i] == 0)
				objectives[i] = getValueAndGradient(null, newWeights);
		}

		for (int i = 0; i < increments.length; i++) 
			System.out.println(increments[i] + "\t" + objectives[i]);


		for (int i = 0; i < kernels.size(); i++) 
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
	}

	@Override
	public double getValueAndGradient(double[] gradient, double[] weights) {
		for (int i = 0; i < kernels.size(); i++) {
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
		}
		minimizeKLDivergence();
		computeObservedIncomp();

		reasoner.changedGroundKernelWeights();
		computeExpectedIncomp();

		double loss = 0.0;
		for (int i = 0; i < kernels.size(); i++)
			loss += weights[i] * (dualObservedIncompatibility[i] - dualExpectedIncompatibility[i]);
		for (int i = 0; i < immutableKernels.size(); i++)
			loss += immutableKernels.get(i).getWeight().getWeight() * (dualObservedIncompatibility[kernels.size() + i] - dualExpectedIncompatibility[kernels.size() + i]);
		loss += ((ADMMReasoner) latentVariableReasoner).getLagrangianPenalty() - ((ADMMReasoner) reasoner).getLagrangianPenalty();

		
		double regularizer = computeRegularizer();

		if (null != gradient) 
			for (int i = 0; i < kernels.size(); i++) 
				gradient[i] = (dualObservedIncompatibility[i] - dualExpectedIncompatibility[i]) + l2Regularization * weights[i] + l1Regularization;

		return loss + regularizer;
	}

}
