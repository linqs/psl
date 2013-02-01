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
 * @author Steve Bach <bach@cs.umd.edu>
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class MaxMargin implements ModelApplication {
	
	private static final Logger log = LoggerFactory.getLogger(MaxMargin.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "maxmargin";
		
	/**
	 * Key for cutting plane tolerance
	 */
	public static final String CUTTING_PLANE_TOLERANCE = CONFIG_PREFIX + ".tolerance";
	/** Default value for CUTTING_PLANE_TOLERANCE */
	public static final double CUTTING_PLANE_TOLERANCE_DEFAULT = 1e-5;

	/**
	 * Key for slack penalty C, where objective is ||w|| + C (slack)
	 */
	public static final String SLACK_PENALTY = CONFIG_PREFIX + ".slack_penalty";
	/** Default value for SLACK_PENALTY */
	public static final double SLACK_PENALTY_DEFAULT = 10;

	/**
	 * Key for maximum iterations
	 */
	public static final String MAX_ITER = CONFIG_PREFIX + ".max_iter";
	/** Default value for MAX_ITER */
	public static final int MAX_ITER_DEFAULT = 500;
	
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
	
	private final double tolerance;
	private final int maxIter;
	private double slackPenalty;
	
	public MaxMargin(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this.model = model;
		this.rvDB = rvDB;
		this.observedDB = observedDB;
		this.config = config;

		tolerance = config.getDouble(CUTTING_PLANE_TOLERANCE, CUTTING_PLANE_TOLERANCE_DEFAULT);
		maxIter = config.getInt(MAX_ITER, MAX_ITER_DEFAULT);
		slackPenalty = config.getDouble(SLACK_PENALTY, SLACK_PENALTY_DEFAULT);
	}
	
	/**
	 * Sets slack coefficient for max margin constraints
	 * @param C
	 */
	public void setSlackPenalty(double C) {
		slackPenalty = C;
	}
	
	/**
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
		double[] weights;
		double[] truthIncompatibility;
		double mpeIncompatibility;
		
		/* Gathers the CompatibilityKernels */
		for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class))
			kernels.add(k);
		
		weights = new double[kernels.size()+1];
		truthIncompatibility = new double[kernels.size()];

		/* Sets up the ground model */
		Reasoner reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		TrainingMap trainingMap = new TrainingMap(rvDB, observedDB);
		if (trainingMap.getLatentVariables().size() > 0)
			throw new IllegalArgumentException("All RandomVariableAtoms must have " +
					"corresponding ObservedAtoms. Latent variables are not supported " +
					"by MaxMargin.");
		Grounding.groundAll(model, trainingMap, reasoner);
		
		/* Computes the observed incompatibility */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}
		
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
			}
			
			/* Initializes the current weights */
			weights[i] = kernels.get(i).getWeight().getWeight();
		}
		
		int iter = 0;
		double violation = Double.POSITIVE_INFINITY;
		
		// count positive vs negative ground truth atoms
		int posAtoms = 0;
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet())
			if (e.getValue().getValue() == 1.0)
				posAtoms++;
		double posRatio = (double) posAtoms / (double) trainingMap.getTrainingMap().size();
		log.debug("Weighting loss of positive (1.0) examples by {} and negative examples by {}", 1 - posRatio, posRatio);
		
		// set up loss augmenting ground kernels
		List<LossAugmentingGroundKernel> lossKernels = new ArrayList<LossAugmentingGroundKernel>(trainingMap.getTrainingMap().size());
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			double truth = e.getValue().getValue();
			double weight = (truth == 1.0) ? (1 - posRatio) : posRatio;
			LossAugmentingGroundKernel gk = 
					new LossAugmentingGroundKernel(e.getKey(), truth, weight);
			reasoner.addGroundKernel(gk);
			lossKernels.add(gk);
		}

		// init a quadratic program with variables for weights and 1 slack variable
		PositiveMinNormProgram program = new PositiveMinNormProgram(kernels.size() + 1, config);
		
		// add linear objective
		double [] coefficients = new double[kernels.size() + 1];
		coefficients[kernels.size()] = slackPenalty;
		program.setLinearCoefficients(coefficients);
			
		//qp.setHessianFactor()
		boolean [] include = new boolean[kernels.size()+1];
		for (int i = 0; i < kernels.size(); i++) {
			include[i] = true;
		}
		include[kernels.size()] = false;
		program.setQuadraticTerm(include, new double[kernels.size()]);
		
		List<double []> allConstraints = new ArrayList<double[]>();
		List<Double> allLosses = new ArrayList<Double>();
		
		while (iter < maxIter && violation > tolerance) {
			reasoner.optimize();
						
			double slack = weights[kernels.size()];

			/* Computes distance between ground truth and output of separation oracle */
			double loss = 0.0;
			for (LossAugmentingGroundKernel gk : lossKernels)
				loss += gk.getWeight().getWeight() * gk.getIncompatibility();
					
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
			violation += loss;
			
			// slack coefficient
			constraintCoefficients[kernels.size()] = -1.0;
			
			// add linear constraint weights * truthIncompatility < weights * mpeIncompatibility - loss + \xi
			program.addInequalityConstraint(constraintCoefficients, -1 * loss);
			
			allLosses.add(loss);
			allConstraints.add(constraintCoefficients);
			
			log.debug("Violation of most recent constraint: {}", violation);
			log.debug("Distance from ground truth: {}", loss);
			log.debug("Slack: {}", slack);
			
			
			// optimize with constraint set
			program.solve();
			
			// update weights with new solution
			weights = program.getSolution();
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
		proc.terminate();
	}

	@Override
	public void close() {
		model = null;
		rvDB = null;
		config = null;
	}

}
