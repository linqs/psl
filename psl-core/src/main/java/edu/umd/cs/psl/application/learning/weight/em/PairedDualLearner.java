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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;

/**
 * Learns the parameters of a HL-MRF with latent variables, using a maximum-likelihood
 * technique that interleaves updates of the parameters and inference steps for
 * fast training. See
 * 
 * "Paired-Dual Learning for Fast Training of Latent Variable Hinge-Loss MRFs"
 * Stephen H. Bach, Bert Huang, Jordan Boyd-Graber, and Lise Getoor
 * International Conference on Machine Learning (ICML) 2015
 * 
 * for details.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 * @author Bert Huang <bhuang@vt.edu>
 */
public class PairedDualLearner extends ExpectationMaximization {

	private static final Logger log = LoggerFactory.getLogger(PairedDualLearner.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "pairedduallearner";

	/**
	 * Key for Integer property that indicates how many rounds of paired-dual
	 * learning to run before beginning to update the weights (parameter K in
	 * the ICML paper)
	 */
	public static final String WARMUP_ROUNDS_KEY = CONFIG_PREFIX + ".warmuprounds";
	/** Default value for WARMUP_ROUNDS_KEY */
	public static final int WARMUP_ROUNDS_DEFAULT = 0;
	
	/**
	 * Key for Integer property that indicates how many steps of ADMM to run
	 * for each inner objective before each gradient step (parameter N in the ICML paper)
	 */
	public static final String ADMM_STEPS_KEY = CONFIG_PREFIX + ".admmsteps";
	/** Default value for ADMM_STEPS_KEY */
	public static final int ADMM_STEPS_DEFAULT = 1;

	double[] scalingFactor;
	double[] dualObservedIncompatibility, dualExpectedIncompatibility;
	private final int warmupRounds;
	private final int admmIterations;
	Model model;
	String outputPrefix;

	public PairedDualLearner(Model model, Database rvDB, Database observedDB,
			ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		scalingFactor = new double[kernels.size()];
		warmupRounds = config.getInt(WARMUP_ROUNDS_KEY, WARMUP_ROUNDS_DEFAULT);
		if (warmupRounds < 0) {
			throw new IllegalArgumentException(CONFIG_PREFIX + "." + WARMUP_ROUNDS_KEY
					+ " must be a nonnegative integer.");
		}
		admmIterations = config.getInt(ADMM_STEPS_KEY, ADMM_STEPS_DEFAULT);
		if (admmIterations < 1) {
			throw new IllegalArgumentException(CONFIG_PREFIX + "." + ADMM_STEPS_KEY
					+ " must be a positive integer.");
		}
	}
	
	@Override
	protected void initGroundModel() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		super.initGroundModel();
		if (!(reasoner instanceof ADMMReasoner)) {
			throw new IllegalArgumentException("PairedDualLearning can only be"
					+ " used with ADMMReasoner.");
		}
	}
	
	public void setModel(Model m, String s) {
		model = m;
		outputPrefix = s;
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

	Random random = new Random();
	
	private void subgrad() {
		log.info("Starting optimization");
		double [] weights = new double[kernels.size()];
		for (int i = 0; i < kernels.size(); i++)
			weights[i] = kernels.get(i).getWeight().getWeight();

		double [] avgWeights = new double[kernels.size()];

		double [] gradient = new double[kernels.size()];
		
		for (int i = 0; i < kernels.size(); i++)
			gradient[i] = 1.0;
		
		double [] scale = new double[kernels.size()];
		double objective = 0;
		for (int step = 0; step < iterations; step++) {
			objective = getValueAndGradient(gradient, weights);
			double gradNorm = 0;
			double change = 0;
			for (int i = 0; i < kernels.size(); i++) {
				if (scheduleStepSize)
					scale[i] = Math.pow((double) (step + 1), 2);
				else
					scale[i] = 1.0;

				gradNorm += Math.pow(weights[i] - Math.max(0, weights[i] - gradient[i]), 2);

				if (scale[i] > 0.0) {
					double coeff = stepSize / Math.sqrt(scale[i]);
					double delta = Math.max(-weights[i], - coeff * gradient[i]);
					weights[i] += delta;
					// use gradient array to store change
					gradient[i] = delta;
					change += Math.pow(delta, 2);
				}
				avgWeights[i] = (1 - (1.0 / (double) (step + 1.0))) * avgWeights[i] + (1.0 / (double) (step + 1.0)) * weights[i];		
			}

			if (storeWeights) {
				Map<CompatibilityKernel,Double> weightMap = new HashMap<CompatibilityKernel, Double>();
				for (int i = 0; i < kernels.size(); i++) {
					double weight = (averageSteps)? avgWeights[i] : weights[i];
					if (weight != 0.0)
						weightMap.put(kernels.get(i), weight);
				}

				storedWeights.add(weightMap);
			}
			
			gradNorm = Math.sqrt(gradNorm);
			change = Math.sqrt(change);
			DecimalFormat df = new DecimalFormat("0.0000E00");
			if (step % 1 == 0)
				log.info("Iter {}, obj: {}, norm grad: " + df.format(gradNorm) + ", change: " + df.format(change), step, df.format(objective));

			if (step % 50 == 0)
				outputModel(step);
			
			if (change < tolerance) {
				log.info("Change in w ({}) is less than tolerance. Finishing subgrad.", change);
				break;
			}
		}
		outputModel(iterations);

		log.info("Learning finished with final objective value {}", objective);

		for (int i = 0; i < kernels.size(); i++) {
			if (averageSteps)
				weights[i] = avgWeights[i];
			kernels.get(i).setWeight(new PositiveWeight(weights[i]));
		}
	}
	
	private void outputModel(int step) {
		if (model == null)
			return;
		String filename = outputPrefix + "model" + step + ".txt";
		try {
			File file = new File(filename);
			if (file.getParentFile() != null)
				file.getParentFile().mkdirs();
			FileWriter fw = new FileWriter(file);
			BufferedWriter bw = new BufferedWriter(fw);
			for (Predicate predicate : PredicateFactory.getFactory().getPredicates())
				bw.write(predicate.toString() + "\n");
			bw.write(model.toString());

			bw.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void doLearn() {
		int maxIter = ((ADMMReasoner) reasoner).getMaxIter();
		
		((ADMMReasoner) reasoner).setMaxIter(admmIterations);
		((ADMMReasoner) latentVariableReasoner).setMaxIter(admmIterations);
		
		if (augmentLoss)
			addLossAugmentedKernels();
		
		if (warmupRounds > 0) {
			log.info("Warming up optimizers with {} iterations each.", warmupRounds * admmIterations);
			for (int i = 0; i < warmupRounds; i++) {
				reasoner.optimize();
				latentVariableReasoner.optimize();
			}
		}
		
		subgrad();
		
		if (augmentLoss)
			removeLossAugmentedKernels();

		((ADMMReasoner) reasoner).setMaxIter(maxIter);
		((ADMMReasoner) latentVariableReasoner).setMaxIter(maxIter);

	}

	private double getValueAndGradient(double[] gradient, double[] weights) {
		for (int i = 0; i < kernels.size(); i++) {
			if (gradient[i] != 0.0)
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
		double eStepLagrangianPenalty = ((ADMMReasoner) latentVariableReasoner).getLagrangianPenalty();
		double eStepAugLagrangianPenalty = ((ADMMReasoner) latentVariableReasoner).getAugmentedLagrangianPenalty();
		double mStepLagrangianPenalty = ((ADMMReasoner) reasoner).getLagrangianPenalty();
		double mStepAugLagrangianPenalty = ((ADMMReasoner) reasoner).getAugmentedLagrangianPenalty();
		loss += eStepLagrangianPenalty + eStepAugLagrangianPenalty - mStepLagrangianPenalty - mStepAugLagrangianPenalty;
		
		for (int i = 0; i < kernels.size(); i++) {
			log.debug("Incompatibility for kernel {}", kernels.get(i));
			log.debug("Truth incompatbility {}, expected incompatibility {}", dualObservedIncompatibility[i], dualExpectedIncompatibility[i]);
		}
		log.debug("E Penalty: {}, E Aug Penalty: {}, M Penalty: {}, M Aug Penalty: {}",
				new Double[] {eStepLagrangianPenalty, eStepAugLagrangianPenalty, mStepLagrangianPenalty, mStepAugLagrangianPenalty});

		
		double regularizer = computeRegularizer();

		if (null != gradient) 
			for (int i = 0; i < kernels.size(); i++) {
				gradient[i] = dualObservedIncompatibility[i] - dualExpectedIncompatibility[i];
				if (scaleGradient && numGroundings[i] > 0.0)
					gradient[i] /= numGroundings[i];
				gradient[i] += l2Regularization * weights[i] + l1Regularization;
			}

		return loss + regularizer;
	}

}
