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
package edu.umd.cs.psl.application.learning.weight.maxmargin;

import java.util.ArrayList;
import java.util.Iterator;
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
import edu.umd.cs.psl.model.parameters.NegativeWeight;
import edu.umd.cs.psl.model.parameters.PositiveWeight;

/**
 * Learns new weights for the {@link CompatibilityKernel CompatibilityKernels}
 * in a {@link Model} using max margin inference.
 * <p>
 * The algorithm is based on structural SVM with cutting plane optimization
 * The objective is to find a weight vector that minimizes an L2 regularizer 
 * subject to the constraint that the ground truth score is better than any 
 * other solution.
 * 
 * min ||w||^2 + C \xi
 * s.t. w * f(y) < min_x (w * f(x) - || x - y ||_1) + \xi
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class MaxMargin extends WeightLearningApplication {
	
	private static final Logger log = LoggerFactory.getLogger(MaxMargin.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "maxmargin";
		
	/**
	 * Key for double property, cutting plane tolerance
	 */
	public static final String CUTTING_PLANE_TOLERANCE = CONFIG_PREFIX + ".tolerance";
	/** Default value for CUTTING_PLANE_TOLERANCE */
	public static final double CUTTING_PLANE_TOLERANCE_DEFAULT = 1e-5;

	/**
	 * Key for double property, slack penalty C, where objective is ||w|| + C (slack)
	 */
	public static final String SLACK_PENALTY = CONFIG_PREFIX + ".slack_penalty";
	/** Default value for SLACK_PENALTY */
	public static final double SLACK_PENALTY_DEFAULT = 10;

	/**
	 * Key for positive integer, maximum iterations
	 */
	public static final String MAX_ITER = CONFIG_PREFIX + ".max_iter";
	/** Default value for MAX_ITER */
	public static final int MAX_ITER_DEFAULT = 500;
	
	/**
	 * Key for boolean property. If true, loss augmenting ground kernels will
	 * be weighted using ratio of true to false atoms in observedDB. If false,
	 * they will all have weight 0.5.
	 */
	public static final String BALANCE_LOSS = CONFIG_PREFIX + ".balanceloss";
	/** Default value for BALANCE_LOSS */
	public static final boolean BALANCE_LOSS_DEFAULT = true;
	
	/**
	 * Key for boolean property. If true, the i-th component of weights in the
	 * objective will be scaled by 1 over the number of GroundCompatibilityKernels
	 * with that weight. If false, they will not be scaled. 
	 */
	public static final String SCALE_NORM = CONFIG_PREFIX + ".scalenorm";
	/** Default value for SCALE_NORM */
	public static final boolean SCALE_NORM_DEFAULT = true;
	
	protected final double tolerance;
	protected final int maxIter;
	protected double slackPenalty;
	protected final boolean balanceLoss;
	protected final boolean scaleNorm;
	
	protected PositiveMinNormProgram normProgram;
	
	public MaxMargin(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		tolerance = config.getDouble(CUTTING_PLANE_TOLERANCE, CUTTING_PLANE_TOLERANCE_DEFAULT);
		maxIter = config.getInt(MAX_ITER, MAX_ITER_DEFAULT);
		slackPenalty = config.getDouble(SLACK_PENALTY, SLACK_PENALTY_DEFAULT);
		balanceLoss = config.getBoolean(BALANCE_LOSS, BALANCE_LOSS_DEFAULT);
		scaleNorm = config.getBoolean(SCALE_NORM, SCALE_NORM_DEFAULT);
	}
	
	/**
	 * Sets slack coefficient for max margin constraints
	 * @param C
	 */
	public void setSlackPenalty(double C) {
		slackPenalty = C;
	}
	
	@Override
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		super.initGroundModel();
		
		/* Sets up the PositiveMinNormProgram (in this method for appropriate throws declarations) */
		normProgram = new PositiveMinNormProgram(kernels.size() + 1, config);
		
		/* Sets linear objective term */
		double [] coefficients = new double[kernels.size() + 1];
		coefficients[kernels.size()] = slackPenalty;
		normProgram.setLinearCoefficients(coefficients);
		
		/* Determines coefficients for the quadratic objective term */
		double [] quadCoeffs = new double[kernels.size()+1];
		if (scaleNorm) {
			/* Counts numbers of groundings to scale norm */
			for (int i = 0; i < kernels.size(); i++) {
				Iterator<GroundKernel> itr = reasoner.getGroundKernels(kernels.get(i)).iterator();
				while(itr.hasNext()) {
					itr.next();
					quadCoeffs[i]++;
				}
				
				if (quadCoeffs[i] == 0.0)
					quadCoeffs[i]++;
				
				quadCoeffs[i] = 1 / quadCoeffs[i];
			}
		}
		else {
			for (int i = 0; i < kernels.size(); i++)
				quadCoeffs[i] = 1.0;
		}
		
		/* Sets quadratic objective term */
		quadCoeffs[kernels.size()] = 0.0;
		normProgram.setQuadraticTerm(quadCoeffs, new double[kernels.size()]);
	}
	
	@Override
	protected void doLearn() {
		double[] weights;
		double[] truthIncompatibility;
		double mpeIncompatibility;
		
		/* Initializes weights */
		weights = new double[kernels.size()+1];
		for (int i = 0; i < kernels.size(); i++)
			weights[i] = kernels.get(i).getWeight().getWeight();
		
		/* Computes the observed incompatibility */
		truthIncompatibility = new double[kernels.size()];
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
		}
		
		/* Determines weights of loss augmenting ground kernels */
		double posRatio;
		if (balanceLoss) {
			/*
			 * Counts positive vs negative ground truth atoms in order to weight
			 * loss augmenting ground kernels
			 */
			int posAtoms = 0;
			for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet())
				if (e.getValue().getValue() == 1.0)
					posAtoms++;
			posRatio = (double) -1 * posAtoms / (double) trainingMap.getTrainingMap().size();
		}
		else {
			posRatio = -0.5;
		}
		log.debug("Weighting loss of positive (value = 1.0) examples by {} " +
				"and negative examples by {}", -1 - posRatio, posRatio);
		
		/* Sets up loss augmenting ground kernels */
		List<LossAugmentingGroundKernel> lossKernels = new ArrayList<LossAugmentingGroundKernel>(trainingMap.getTrainingMap().size());
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			double truth = e.getValue().getValue();
			double weight = (truth == 1.0) ? (-1 - posRatio) : posRatio;
			LossAugmentingGroundKernel gk = 
					new LossAugmentingGroundKernel(e.getKey(), truth, new NegativeWeight(weight));
			reasoner.addGroundKernel(gk);
			lossKernels.add(gk);
		}
		
		/* Prepares to begin optimization loop */
		int iter = 0;
		double violation = Double.POSITIVE_INFINITY;
		List<double []> allConstraints = new ArrayList<double[]>();
		List<Double> allLosses = new ArrayList<Double>();
		
		/* Loops to identify separating hyperplane and reoptimize weights */
		while (iter < maxIter && violation > tolerance) {
			/* Runs separation oracle */
			reasoner.optimize();
						
			double slack = weights[kernels.size()];

			/* Computes distance between ground truth and output of separation oracle */
			double negativeLoss = 0.0;
			for (LossAugmentingGroundKernel gk : lossKernels)
				negativeLoss += gk.getWeight().getWeight() * gk.getIncompatibility();
					
			/* The next loop computes constraint coefficients for max margin constraints:
			 * w * f(y) < min_x w * f(x) - ||x-y|| + \xi
			 * For current x from separation oracle, this translates to
			 * w * (f(y) - f(x)) - \xi < -|| x - y ||
			 * 
			 * loss = ||x - y||
			 * constraintCoefficients = f(y) - f(x)
			 */
			double [] constraintCoefficients = new double[kernels.size() + 1];
			violation = 0.0;
			for (int i = 0; i < kernels.size(); i++) {
				mpeIncompatibility = 0.0;
				
				for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i)))
					mpeIncompatibility += ((GroundCompatibilityKernel) gk).getIncompatibility();	
				
				constraintCoefficients[i] =  truthIncompatibility[i] - mpeIncompatibility;
				
				violation += weights[i] * constraintCoefficients[i];
			}
			violation -= slack;
			violation -= negativeLoss;
			
			// slack coefficient
			constraintCoefficients[kernels.size()] = -1.0;
			
			// add linear constraint weights * truthIncompatility < weights * mpeIncompatibility - loss + \xi
			normProgram.addInequalityConstraint(constraintCoefficients, negativeLoss);
			
			allLosses.add(-1 * negativeLoss);
			allConstraints.add(constraintCoefficients);
			
			log.debug("Violation of most recent constraint: {}", violation);
			log.debug("Distance from ground truth: {}", -1 * negativeLoss);
			log.debug("Slack: {}", slack);
			
			
			// optimize with constraint set
			normProgram.solve();
			
			// update weights with new solution
			weights = normProgram.getSolution();
			/* Sets the weights to the new solution */
			for (int i = 0; i < kernels.size(); i++)
				kernels.get(i).setWeight(new PositiveWeight(weights[i]));
			reasoner.changedGroundKernelWeights();

			log.debug("Current model: {}", model);
			
			iter++;
			
			
			/* TODO: temporary debug code */
			for (int j = 0; j < allConstraints.size(); j++) {
				double [] cons = allConstraints.get(j);
				StringBuilder sb = new StringBuilder();
				double product = 0.0;
				for (int i = 0; i < kernels.size(); i++) {			
					product += weights[i] * cons[i];
					sb.append("" + cons[i] + ", ");
				}
				log.debug("Constraint {}: " + sb.toString(), j);
				log.debug("Loss {}", allLosses.get(j));
				log.debug("Violation of included constraint {}: {}", j, product - weights[kernels.size()] + allLosses.get(j));
			}
			//TODO: end temp debug code
		}
	}

}
