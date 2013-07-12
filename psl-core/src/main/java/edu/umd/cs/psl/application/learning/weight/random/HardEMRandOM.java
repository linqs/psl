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
package edu.umd.cs.psl.application.learning.weight.random;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.learning.weight.TrainingMap;
import edu.umd.cs.psl.application.learning.weight.maxmargin.LossAugmentingGroundKernel;
import edu.umd.cs.psl.application.learning.weight.maxmargin.PositiveMinNormProgram;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.parameters.NegativeWeight;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;

/**
 * Learns new weights for the {@link CompatibilityKernel CompatibilityKernels}
 * in a {@link Model} using hard EM RandOM learning.
 * <p>
 * TODO: description
 * 
 * @author Steve Bach <bach@cs.umd.edu>
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class HardEMRandOM implements ModelApplication {

	private static final Logger log = LoggerFactory.getLogger(HardEMRandOM.class);
	
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
	 * Key for slack penalty C, where objective is ||w|| + C * slack
	 */
	public static final String SLACK_PENALTY = CONFIG_PREFIX + ".slack_penalty";
	/** Default value for SLACK_PENALTY */
	public static final double SLACK_PENALTY_DEFAULT = 1.0;

	/**
	 * Key for maximum iterations
	 */
	public static final String MAX_INNER_ITER = CONFIG_PREFIX + ".max_inner_iter";
	/** Default value for MAX_INNER_ITER */
	public static final int MAX_INNER_ITER_DEFAULT = 500;
	
	/**
	 * Key for maximum iterations
	 */
	public static final String MAX_OUTER_ITER = CONFIG_PREFIX + ".max_outer_iter";
	/** Default value for MAX_OUTER_ITER */
	public static final int MAX_OUTER_ITER_DEFAULT = 500;
	
	/**
	 * Key for maximum iterations
	 */
	public static final String CHANGE_THRESHOLD = CONFIG_PREFIX + ".change_threshold";
	/** Default value for CHANGE_THRESHOLD */
	public static final double CHANGE_THRESHOLD_DEFAULT = 1e-3;
	

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
	private final int maxInnerIter;
	private final int maxOuterIter;
	private double slackPenalty;
	private double changeThreshold;

	public HardEMRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this.model = model;
		this.rvDB = rvDB;
		this.observedDB = observedDB;
		this.config = config;

		tolerance = config.getDouble(CUTTING_PLANE_TOLERANCE, CUTTING_PLANE_TOLERANCE_DEFAULT);
		maxInnerIter = config.getInt(MAX_INNER_ITER, MAX_INNER_ITER_DEFAULT);
		maxOuterIter = config.getInt(MAX_OUTER_ITER, MAX_OUTER_ITER_DEFAULT);
		slackPenalty = config.getDouble(SLACK_PENALTY, SLACK_PENALTY_DEFAULT);
		changeThreshold = config.getDouble(CHANGE_THRESHOLD, CHANGE_THRESHOLD_DEFAULT);
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

		double[] weights;
		double[] truthIncompatibility;
		double mpeIncompatibility;

		/* Sets up the ground model */
		Reasoner reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		TrainingMap trainingMap = new TrainingMap(rvDB, observedDB);
		if (trainingMap.getLatentVariables().size() > 0)
			throw new IllegalArgumentException("All RandomVariableAtoms must have " +
					"corresponding ObservedAtoms. Latent variables are not supported " +
					"by MaxMargin.");
		Grounding.groundAll(model, trainingMap, reasoner);

		List<GroundCompatibilityKernel> groundKernels = new ArrayList<GroundCompatibilityKernel>();
		for (GroundCompatibilityKernel k : Iterables.filter(reasoner.getGroundKernels(), GroundCompatibilityKernel.class))
			groundKernels.add(k);

		weights = new double[groundKernels.size()+1];
		truthIncompatibility = new double[groundKernels.size()];


		/* Computes the observed incompatibility */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}

		for (int i = 0; i < groundKernels.size(); i++) {
			GroundCompatibilityKernel gk = groundKernels.get(i);
			truthIncompatibility[i] += gk.getIncompatibility();

			/* Initializes the current weights */
			weights[i] = gk.getWeight().getWeight();
		}

		boolean converged = false;

		// set up loss augmenting ground kernels
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
			reasoner.addGroundKernel(new LossAugmentingGroundKernel(
					e.getKey(), e.getValue().getValue(), new NegativeWeight(-1.0)));
		}
		
		int outerIter = 0;
		
		while (!converged) {

			int iter = 0;
			double violation = Double.POSITIVE_INFINITY;

			// init a quadratic program with variables for weights and 1 slack variable
			PositiveMinNormProgram program = new PositiveMinNormProgram(groundKernels.size() + 1, config);
			

			// add linear objective
			double [] coefficients = new double[groundKernels.size() + 1];
			coefficients[groundKernels.size()] = slackPenalty;
			program.setLinearCoefficients(coefficients);

			//qp.setHessianFactor()
			double [] quadCoeffs = new double[groundKernels.size()+1];
			for (int i = 0; i < groundKernels.size(); i++) {
				quadCoeffs[i] = 1.0;
			}
			quadCoeffs[groundKernels.size()] = 0.0;
			program.setQuadraticTerm(quadCoeffs, getOrigin(groundKernels));

			while (iter < maxInnerIter && violation > tolerance) {
				reasoner.optimize();

				double [] constraintCoefficients = new double[groundKernels.size() + 1];
			
				/* Computes distance between ground truth and output of separation oracle */
				double loss = 0.0;
				for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet())
					loss += Math.abs(e.getKey().getValue() - e.getValue().getValue());
			
				violation = 0.0;
				
				for (int i = 0; i < groundKernels.size(); i++) {
					mpeIncompatibility = 0.0;

					GroundCompatibilityKernel gk = groundKernels.get(i);
					mpeIncompatibility = gk.getIncompatibility();	

					constraintCoefficients[i] =  truthIncompatibility[i] - mpeIncompatibility;

					violation += weights[i] * constraintCoefficients[i];
				}
				violation -= weights[groundKernels.size()];
				violation += loss;

				// slack coefficient
				constraintCoefficients[groundKernels.size()] = -1.0;

				// add linear constraint weights * truthIncompatility < weights * mpeIncompatibility - loss + \xi
				program.addInequalityConstraint(constraintCoefficients, -1 * loss);

				// optimize with constraint set
				program.solve();

				// update weights with new solution
				weights = program.getSolution();
				/* Sets the weights to the new solution */
				for (int i = 0; i < groundKernels.size(); i++)
					groundKernels.get(i).setWeight(new PositiveWeight(weights[i]));
				reasoner.changedGroundKernelWeights();

				iter++;
				log.debug("Violation: {}" , violation);
				log.debug("Slack: {}", weights[groundKernels.size()]);
				log.debug("Model: {}", model);
				
			}

			
			/*
			 * set kernel weights to average groundKernel weight
			 * TODO: if we really want to make PSL a RandOM, we could also learn variances here
			 */
			double totalChange = 0;
			for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class)) {
				double avgWeight = 0.0;
				int count = 0;
				for (GroundCompatibilityKernel gk : Iterables.filter(
						reasoner.getGroundKernels(k), GroundCompatibilityKernel.class)) {
					avgWeight += gk.getWeight().getWeight();
					count++;
				}
				avgWeight = avgWeight / (double) count;
				totalChange += Math.abs(avgWeight - k.getWeight().getWeight());
				k.setWeight(new PositiveWeight(avgWeight));
			}
			
			
			outerIter++;
			
			/*
			 * Convergence check
			 */
			if (totalChange < changeThreshold || outerIter > maxOuterIter)
				converged = true;

		}
	}

	private double[] getOrigin(List<GroundCompatibilityKernel> groundKernels) {
		double [] origin = new double[groundKernels.size() + 1];
		for (int i = 0; i < groundKernels.size(); i++) 
			origin[i] = groundKernels.get(i).getKernel().getWeight().getWeight();
		return origin;
	}

	@Override
	public void close() {
		model = null;
		rvDB = null;
		config = null;
	}

}
