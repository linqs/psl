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
import edu.umd.cs.psl.reasoner.ConicReasoner;
import edu.umd.cs.psl.reasoner.ConicReasoner.DistributionType;
import edu.umd.cs.psl.sampler.DerivativeSampler;
import edu.umd.cs.psl.sampler.PartitionEstimationSampler;

/**
 * Weight learning that maximizes the likelihood of the training data.
 * 
 * Subclasses should use a particular optimization method to search for weights.
 */
abstract public class AbstractMaxLikelihood implements WeightLearning {

	private static final Logger log = LoggerFactory.getLogger(AbstractMaxLikelihood.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "maxlikelihood";
	
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
	
	protected final Model model;
	protected final FullInference givenData;
	protected final FullInference groundTruth;
	protected ParameterMapper parameters;
	
	protected final int maxRounds;
	protected final double convThresh;
	protected final DistributionType distribution;
	
	public AbstractMaxLikelihood(Model m, Database givenData, Database groundTruth, ConfigBundle config)
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
	
	protected void initialize() {
		givenData.initialize();
		groundTruth.initialize();
		
		for (Kernel et : model.getKernels()) {
			if (et.isCompatibilityKernel()) parameters.add(et);
		}
	}

	protected double evaluateApp(ModelApplication app, double[] gradient) {
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
			}
		}
		return value;
	}
}
