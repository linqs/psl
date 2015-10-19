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
package edu.umd.cs.psl.application.learning.weight.maxmargin;

import java.util.Arrays;
import java.util.Iterator;
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
 * in a {@link Model} using max-margin learning.
 * <p>
 * The algorithm is based on structural SVM with cutting plane optimization
 * The objective is to find a weight vector that minimizes an L2 regularizer 
 * subject to the constraint that the ground truth score is better than any 
 * other solution that is scaled by a loss function L.
 * <p>
 * min ||w||^2 + C \xi      <br />
 * s.t. w * f(y) < min_y' (w * f(y') - L(y, y')) + \xi
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 * @author Bert Huang <bert@cs.umd.edu>
 */
abstract public class MaxMargin extends WeightLearningApplication {
	
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
	public static final String CUTTING_PLANE_TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
	/** Default value for CUTTING_PLANE_TOLERANCE_KEY */
	public static final double CUTTING_PLANE_TOLERANCE_DEFAULT = 1e-3;

	/**
	 * Key for double property, slack penalty C, where objective is ||w|| + C (slack)
	 */
	public static final String SLACK_PENALTY_KEY = CONFIG_PREFIX + ".slackpenalty";
	/** Default value for SLACK_PENALTY_KEY */
	public static final double SLACK_PENALTY_DEFAULT = 1;

	/**
	 * Key for positive integer, maximum number of constraints to add to
	 * quadratic program
	 */
	public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiter";
	/** Default value for MAX_ITER_KEY */
	public static final int MAX_ITER_DEFAULT = 500;
	
	/**
	 * Key for boolean property. If true, only non-negative weights will be learned. 
	 */
	public static final String NONNEGATIVE_WEIGHTS_KEY = CONFIG_PREFIX + ".nonnegativeweights";
	/** Default value for NONNEGATIVE_WEIGHTS_KEY */
	public static final boolean NONNEGATIVE_WEIGHTS_DEFAULT = true;
	
	/**
	 * Key for NormScalingType enum property. Determines type of norm scaling
	 * MaxMargin will use in its objective.
	 */
	public static final String SCALE_NORM_KEY = CONFIG_PREFIX + ".scalenorm";
	/** Default value for SCALE_NORM_KEY */
	public static final NormScalingType SCALE_NORM_DEFAULT = NormScalingType.NONE;
	
	/**
	 * Key for SquareSlack boolean property. Determines whether to penalize 
	 * slack linearly or quadratically.
	 */
	public static final String SQUARE_SLACK_KEY= CONFIG_PREFIX + ".squareslack";
	/** Default value for SQUARE_SLACK KEY*/
	public static final boolean SQUARE_SLACK_DEFAULT = false;
	
	/** Types of norm scaling MaxMargin can use during learning */
	public enum NormScalingType {
		/** No norm scaling */
		NONE,
		/**
		 * Each weight is multiplied inside the objective norm by
		 * the number of GroundKernels sharing that weight (and a constant
		 * rescaling factor so that the norm has the same value as no scaling
		 * when all weights are 1.0)
		 */
		NUM_GROUNDINGS,
		/**
		 * Each weight is multiplied inside the objective norm by
		 * 1 / number of GroundKernels sharing that weight  (and a constant
		 * rescaling factor so that the norm has the same value as no scaling
		 * when all weights are 1.0)
		 */
		INVERSE_NUM_GROUNDINGS;
	}
	
	protected final double tolerance;
	protected final int maxIter;
	protected final boolean nonnegativeWeights;
	protected double slackPenalty;
	protected final NormScalingType scaleNorm;
	protected final boolean squareSlack;
	
	protected MinNormProgram normProgram;
	
	public MaxMargin(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		tolerance = config.getDouble(CUTTING_PLANE_TOLERANCE_KEY, CUTTING_PLANE_TOLERANCE_DEFAULT);
		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		nonnegativeWeights = config.getBoolean(NONNEGATIVE_WEIGHTS_KEY, NONNEGATIVE_WEIGHTS_DEFAULT);
		slackPenalty = config.getDouble(SLACK_PENALTY_KEY, SLACK_PENALTY_DEFAULT);
		scaleNorm = (NormScalingType) config.getEnum(SCALE_NORM_KEY, SCALE_NORM_DEFAULT);
		squareSlack = config.getBoolean(SQUARE_SLACK_KEY, SQUARE_SLACK_DEFAULT);
	}
	
	@Override
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		super.initGroundModel();
		
		/* Sets up the MinNormProgram (in this method for appropriate throws declarations) */
		normProgram = new MinNormProgram(kernels.size() + 1, nonnegativeWeights, config);
		
		/* Sets linear objective term */
		double [] coefficients = new double[kernels.size() + 1];
		coefficients[kernels.size()] = (squareSlack)? 0.0 : slackPenalty;
		normProgram.setLinearCoefficients(coefficients);
		
		/* Determines coefficients for the quadratic objective term */
		double [] quadCoeffs = new double[kernels.size()+1];
		if (NormScalingType.NONE.equals(scaleNorm)) {
			for (int i = 0; i < kernels.size(); i++)
				quadCoeffs[i] = 1.0;
		}
		else {
			/* Counts numbers of groundings to scale norm */
			int[] numGroundings = new int[kernels.size()];
			for (int i = 0; i < kernels.size(); i++) {
				Iterator<GroundKernel> itr = reasoner.getGroundKernels(kernels.get(i)).iterator();
				while(itr.hasNext()) {
					itr.next();
					numGroundings[i]++;
				}
			}
			
			if (NormScalingType.NUM_GROUNDINGS.equals(scaleNorm)) {
				for (int i = 0; i < kernels.size(); i++)
					quadCoeffs[i] = numGroundings[i];
			}
			else if (NormScalingType.INVERSE_NUM_GROUNDINGS.equals(scaleNorm)) {
				for (int i = 0; i < kernels.size(); i++)
					quadCoeffs[i] = (numGroundings[i] > 0.0) ? 1.0 / numGroundings[i] : 0.0;
			}
			else
				throw new IllegalStateException("Unrecognized NormScalingType.");
			
			/* 
			 * Rescales the coefficients so that the norm has the same value as no
			 * scaling when all weights are 1.0
			 */
			double coeffNorm = 0.0;
			for (double coeff : quadCoeffs)
				coeffNorm += coeff * coeff;
			coeffNorm = Math.sqrt(coeffNorm);
			double scalar = Math.sqrt(kernels.size()) / coeffNorm;
			for (int i = 0; i < kernels.size(); i++)
				quadCoeffs[i] *= scalar;
		}
		
		/* Sets quadratic objective term */
		log.debug("Quad coeffs: {}", Arrays.toString(quadCoeffs));
		quadCoeffs[kernels.size()] = (squareSlack) ? slackPenalty : 0.0;
		normProgram.setQuadraticTerm(quadCoeffs, new double[kernels.size() + 1]);
	}
	
	@Override
	protected void doLearn() {
		double[] weights;
		double[] truthIncompatibility;
		double oracleIncompatibility;
		
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
		
		setupSeparationOracle();
		
		/* Prepares to begin optimization loop */
		int iter = 0;
		double violation = Double.POSITIVE_INFINITY;
		
		/* Loops to identify separating hyperplane and reoptimize weights */
		while (iter < maxIter && violation > tolerance) {
			
			runSeparationOracle();
			
			double slack = weights[kernels.size()];

			double loss = evaluateLoss();
					
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
				oracleIncompatibility = 0.0;
				
				for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i)))
					oracleIncompatibility += ((GroundCompatibilityKernel) gk).getIncompatibility();	
				
				constraintCoefficients[i] =  truthIncompatibility[i] - oracleIncompatibility;
				
				violation += weights[i] * constraintCoefficients[i];
			}
			violation -= slack;
			violation += loss;
			
			log.debug("Violation of most recent constraint: {}", violation);
			log.debug("Loss at most recent point: {}", loss);
			log.debug("Slack: {}", slack);
			
			if (violation > tolerance) {
				// slack coefficient
				constraintCoefficients[kernels.size()] = -1.0;
				
				// add linear constraint weights * truthIncompatility < weights * mpeIncompatibility - loss + \xi
				normProgram.addInequalityConstraint(constraintCoefficients, -1 * loss);
				
				
				// optimize with constraint set
				try {
					normProgram.solve();
				}
				catch (IllegalArgumentException e) {
					log.error("Norm minimization program failed (IllegalArgumentException). Returning early.");
					return;
				} 
				catch (IllegalStateException e) {
					log.error("Norm minimization program failed (IllegalStateException). Returning early.");
					return;
				}
				
				// update weights with new solution
				weights = normProgram.getSolution();
				/* Sets the weights to the new solution */
				for (int i = 0; i < kernels.size(); i++)
					if (nonnegativeWeights && weights[i] < 0.0)
						kernels.get(i).setWeight(new NegativeWeight(weights[i]));
					else 
						kernels.get(i).setWeight(new PositiveWeight(weights[i]));
				reasoner.changedGroundKernelWeights();
				
				iter++;
			}
		}
		
		log.debug("Number of separation oracle calls: {}", iter);
		
		tearDownSeparationOracle();
	}
	
	/**
	 * Performs any initialization necessary for the separation oracle.
	 */
	abstract protected void setupSeparationOracle();
	
	/**
	 * Sets the current Atom states to
	 * <p>
	 * min_y' w * f(y') - L(y, y')
	 */
	abstract protected void runSeparationOracle();
	
	/**
	 * @return value of L(y, y') for ground truth y and current state y'
	 */
	abstract protected double evaluateLoss();
	
	/**
	 * Undoes {@link #setupSeparationOracle()}.
	 */
	abstract protected void tearDownSeparationOracle();

}
