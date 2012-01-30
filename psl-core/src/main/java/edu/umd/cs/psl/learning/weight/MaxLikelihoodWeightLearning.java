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
package edu.umd.cs.psl.learning.weight;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.FullInference;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.inference.MaintainedMemoryFullInference;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.local.LocalProcessMonitor;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.optimizer.lbfgs.ConvexFunc;
import edu.umd.cs.psl.optimizer.lbfgs.LBFGSB;
import edu.umd.cs.psl.reasoner.ConicReasoner;
import edu.umd.cs.psl.reasoner.ConicReasoner.DistributionType;
import edu.umd.cs.psl.sampler.DerivativeSampler;
import edu.umd.cs.psl.sampler.PartitionEstimationSampler;

public class MaxLikelihoodWeightLearning implements WeightLearning, ConvexFunc {

	private static final Logger log = LoggerFactory.getLogger(MaxLikelihoodWeightLearning.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "maxlikelihoodweightlearner";
	
	/**
	 * Key for nonnegative integer property. Its value is an upper bound on
	 * the number of rounds of optimization the weight learner will perform.
	 * A value of zero means that there will be no limit on the number of rounds.
	 */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";
	/** Default value for MAX_ROUNDS_KEY property */
	public static final int MAX_ROUNDS_DEFAULT = 50;
	
	/**
	 * Key for positive double property. Weight learning will iterate 
	 * until the negated, relative change in the objective in a round is less
	 * than its value.
	 */
	public static final String CONV_THRESH_KEY = CONFIG_PREFIX + ".convthreshold";
	/** Default value for CONV_THRESH_KEY property */
	public static final double CONV_THRESH_DEFAULT = 1e-5;
	
	private final Model model;
	private final FullInference givenData;
	private final FullInference groundTruth;
	private ParameterMapper parameters;
	
	private final int maxRounds;
	private final double convThresh;
	private final DistributionType distribution;
	
	public MaxLikelihoodWeightLearning(Model m, Database givenData, Database groundTruth, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		model = m;
		this.givenData = new MaintainedMemoryFullInference(model, givenData, config);
		this.groundTruth = new MaintainedMemoryFullInference(model, groundTruth, config);
		parameters = new ParameterMapper();
		
		maxRounds = config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
		if (maxRounds < 0)
			throw new IllegalArgumentException("Property " + MAX_ROUNDS_KEY + " must be nonnegative.");
		
		convThresh = config.getDouble(CONV_THRESH_KEY, CONV_THRESH_DEFAULT);
		if (convThresh <= 0.0)
			throw new IllegalArgumentException("Property " + CONV_THRESH_KEY + " must be positive.");
		
		distribution = (DistributionType) config.getEnum(ConicReasoner.DISTRIBUTION_KEY, ConicReasoner.DISTRIBUTION_DEFAULT);
	}
	
	private void initialize() {
		givenData.initialize();
		groundTruth.initialize();
		
		for (Kernel et : model.getKernels()) {
			if (et.isCompatibilityKernel()) parameters.add(et);
		}
	}
	
	@Override
	public void learn() {
		initialize();
		int    numParams  = parameters.getNumParameters();

		LBFGSB lbfgsb = new LBFGSB(maxRounds, convThresh, numParams, this);

		for (int i = 0; i < parameters.getNumParameters(); i++)
			/* Each parameter has upper and lower bounds (indicated by 2) */
			lbfgsb.setBoundSpec(i+1,2); 

		for (int i = 0; i < parameters.getNumParameters(); i++)
		{
			double[] bounds = parameters.getBounds(i);

			int boundCode = -1;
			if (bounds[0] == Double.NEGATIVE_INFINITY && bounds[1] == Double.POSITIVE_INFINITY)        boundCode = 0;
			else if (bounds[0] != Double.NEGATIVE_INFINITY && bounds[1] == Double.POSITIVE_INFINITY)   boundCode = 1; //lower bound
			else if (bounds[0] != Double.NEGATIVE_INFINITY && bounds[1] != Double.POSITIVE_INFINITY)   boundCode = 2; //lower & upper bounds
			else if (bounds[0] == Double.NEGATIVE_INFINITY && bounds[1] != Double.POSITIVE_INFINITY)   boundCode = 3; //upper bound

			lbfgsb.setBoundSpec(i+1,boundCode);

			if (boundCode == 1 || boundCode == 2) lbfgsb.setLowerBound(i+1, bounds[0]);
			if (boundCode == 3 || boundCode == 2) lbfgsb.setUpperBound(i+1, bounds[1]);
		}

		int[]     iter   = {0};       //store number of iterations taken by L-BFGS-B
		boolean[] error  = {false};   //indicate whether L-BFGS-B encountered an error
		double[]  params = new double[numParams+1]; //parameters (e.g., wts of formulas) found by L-BFGS-B
		double[]  paras  = parameters.getAllParameterValues();
		
		for (int i = 1; i < params.length; i++)
			params[i] = 10.0;

		lbfgsb.minimize(params, iter, error);

		if (error[0])
		{
			throw new RuntimeException("LBFGSB returned with an error!");
		}

		log.debug("LBFGSB number of iterations = " + iter[0]);

		for (int i = 1; i < params.length; i++) //NOTE: the params are indexed starting from 1!
		{
			paras[i-1] = params[i];
		}
		parameters.setAllParameters(paras);
		log.debug("Learned parameters: {}", Arrays.toString(paras));
	}
	
	/**
	 * Returns the value and gradients with respect to each parameter.
	 * @param gradient  array storing returned gradients
	 * @param params    array storing weights (parameters of function)
	 * @return value of function
	 */
	public double getValueAndGradient(double[] gradient, final double[] params)
	{
		assert params.length == parameters.getNumParameters()+1;

		//Set the weights of the formulas to the current weights
		parameters.setAllParametersWithOffset(params);
		log.debug("{}", model);
		log.debug("Current point (start from index 1): {}", Arrays.toString(params));

		// Evaluate ground truth with current weights
		double[]  gradientTruth = new double[parameters.getNumParameters()];
		double    fctValueTruth = evaluateApp(groundTruth, gradientTruth);
		
		RunningProcess proc1 = LocalProcessMonitor.get().startProcess();
		RunningProcess proc2 = LocalProcessMonitor.get().startProcess();
		
		PartitionEstimationSampler peSampler = new PartitionEstimationSampler(proc1, 50000);
		DerivativeSampler derivativeSampler = new DerivativeSampler(proc2, parameters.getOffsets().keySet(), 50000);
		
		givenData.runInference();
		
		peSampler.sample(givenData.getGroundKernel(), 1.1, Integer.MAX_VALUE);

		givenData.runInference();
		
		derivativeSampler.sample(givenData.getGroundKernel(), 1.1, Integer.MAX_VALUE);
		
		double partitionEstimate = peSampler.getPartitionEstimate();
		double fctValue = fctValueTruth + Math.log(partitionEstimate);
		
		double[] gradientSample = new double[parameters.getOffsets().keySet().size()];
		for (Map.Entry<Kernel, Integer> e: parameters.getOffsets().entrySet())
			gradientSample[e.getValue()] = derivativeSampler.getAverage(e.getKey());
		
		for (int i = 1; i < gradient.length;i++)
			gradient[i] = gradientTruth[i-1] + gradientSample[i-1] / partitionEstimate;

		log.debug("Negated ground truth value : {}", fctValueTruth);
		log.debug("Partition estimate : {}", partitionEstimate);
		log.debug("Log partition estimate : {}", Math.log(partitionEstimate));
		log.debug("Function value : {}", fctValue);
		
		log.debug("Gradient ground truth  : {}", Arrays.toString(gradientTruth));
		log.debug("Gradient Sample  : {}", Arrays.toString(gradientSample));
		log.debug("Gradient : {} ", Arrays.toString(gradient));

		proc1.terminate();
		proc2.terminate();
		return fctValue;
	}

	private double evaluateApp(ModelApplication app, double[] gradient)
	{
//		if (configuration.getNorm()!= L1 && configuration.getNorm()!= L2) throw new UnsupportedOperationException("Not yet implemented");

		double value = 0.0;
		for (GroundCompatibilityKernel e : app.getCompatibilityKernels())
		{
			double pvalue = e.getIncompatibility();
			value += (DistributionType.linear.equals(distribution)) ? pvalue : pvalue*pvalue;
			Kernel k = e.getKernel();
			int numParas = k.getParameters().numParameters();
			double[] pgradient = new double[numParas];
			for (int x1 = 0; x1 < numParas; x1++)
			{
				pgradient[x1] = e.getIncompatibilityDerivative(x1);
				parameters.add2ArrayValue(k, x1, gradient, (DistributionType.linear.equals(distribution)) ? pgradient[x1] : (2*pvalue*pgradient[x1]));
				//parameters.add2ArrayValue2(et, x1, gradient, (config.getNorm()==L1) ? pgradient[x1] : (2*pvalue*pgradient[x1]));
				//gradient does not start from 1
			}
		}
		return value;
	}
}
