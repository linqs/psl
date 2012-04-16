package edu.umd.cs.psl.learning.weight;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.local.LocalProcessMonitor;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.optimizer.lbfgs.ConvexFunc;
import edu.umd.cs.psl.optimizer.lbfgs.LBFGSB;
import edu.umd.cs.psl.sampler.DerivativeSampler;
import edu.umd.cs.psl.sampler.PartitionEstimationSampler;

public class BFGSMaxLikelihood extends AbstractMaxLikelihood implements ConvexFunc {

	private static final Logger log = LoggerFactory.getLogger(BFGSMaxLikelihood.class);

	public BFGSMaxLikelihood(Model m, Database givenData,
			Database groundTruth, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		super(m, givenData, groundTruth, config);
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

}
