package edu.umd.cs.psl.learning.weight;

import static edu.umd.cs.psl.model.DistanceNorm.L1;
import static edu.umd.cs.psl.model.DistanceNorm.L2;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mathnbits.util.ArrayUtil;

import edu.umd.cs.psl.application.FullInference;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.inference.MaintainedMemoryFullInference;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.WeightLearningConfiguration;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.local.LocalProcessMonitor;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.kernel.priorweight.PriorWeightKernel;
import edu.umd.cs.psl.model.kernel.rule.CompatibilityRuleKernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.optimizer.lbfgs.ConvexFunc;
import edu.umd.cs.psl.optimizer.lbfgs.FunctionEvaluation;
import edu.umd.cs.psl.optimizer.lbfgs.LBFGSB;
import edu.umd.cs.psl.sampler.DerivativeSampler;
import edu.umd.cs.psl.sampler.UniformSampler;

public class MaxLikelihoodWeightLearning implements WeightLearning, ConvexFunc {

	private static final Logger log = LoggerFactory.getLogger(MaxLikelihoodWeightLearning.class);
	
	private final Model model;
	private final FullInference groundTruth;
	private final FullInference training;
	private final WeightLearningConfiguration configuration;
//	private final ConfigBundle config;
	
	private ParameterMapper parameters;
	
	private double[] gradientCache;
	private double[][] hessianCache;

	private double[] means;

	private double  minValue;
	private double[] minParams;
	
	public MaxLikelihoodWeightLearning(Model m, Database truth, Database train, WeightLearningConfiguration configuration, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		model = m;
		groundTruth = new MaintainedMemoryFullInference(model,truth, config);
		training = new MaintainedMemoryFullInference(model,train, config);
		this.configuration = configuration;
//		this.config = config;
		parameters = new ParameterMapper();
	}
	
	private void initialize() {
		groundTruth.initialize();
		
		for (Kernel et : model.getKernels()) {
			if (et.isCompatibilityKernel()) parameters.add(et);
		}
		parameters.setAllParameters(configuration.getInitialParameter());
		
		training.initialize();
		
		//For testing only
		gradientCache = new double[parameters.getNumParameters()];
		hessianCache = new double[parameters.getNumParameters()][parameters.getNumParameters()];
	}
	
	@Override
	public void learn() {
		//CHANGED:
		if (configuration.getLearningType()==WeightLearningConfiguration.Type.LBFGSB) learnLBFGSB();
		//else if (configuration.getLearningType()==WeightLearningConfiguration.Type.Perceptron) learnPerceptron();
		else throw new AssertionError("unsupporte type!");
	}

	private void learnLBFGSB()
	{
		initialize();
		int    maxIter    = configuration.getMaxOptIterations();
		double convThresh = configuration.getPointMoveConvergenceThres();
		int    numParams  = parameters.getNumParameters();

		minValue = Double.POSITIVE_INFINITY;
		minParams = new double[numParams+1];

		if (convThresh < 0) convThresh = 1e-5;

		log.debug("maxIter         = {}", maxIter);
		log.debug("convThresh      = {}", convThresh);
		log.debug("numParams       = {}", numParams);
		log.debug("1/var           = {}", configuration.getParameterPrior());
		log.debug("rule mean       = {}", configuration.getRuleMean());
		log.debug("unit rule mean  = {}", configuration.getUnitRuleMean());

		LBFGSB lbfgsb = new LBFGSB(maxIter, convThresh, numParams, this);

		for (int i = 0; i < parameters.getNumParameters(); i++)
			lbfgsb.setBoundSpec(i+1,2); //each param has upper and lower bounds (indicated by 2)

		for (int i = 0; i < parameters.getNumParameters(); i++)
		{
			double[] bounds = parameters.getBounds(i);
			//if (bounds[0]==0.0) bounds[0]=NumericUtilities.relaxedEpsilon;
			//if (bounds[1]==0.0) bounds[1]=-NumericUtilities.relaxedEpsilon;

			int boundCode = -1;
			if (bounds[0] == Double.NEGATIVE_INFINITY && bounds[1] == Double.POSITIVE_INFINITY)        boundCode = 0;
			else if (bounds[0] != Double.NEGATIVE_INFINITY && bounds[1] == Double.POSITIVE_INFINITY)   boundCode = 1; //lower bound
			else if (bounds[0] != Double.NEGATIVE_INFINITY && bounds[1] != Double.POSITIVE_INFINITY)   boundCode = 2; //lower & upper bounds
			else if (bounds[0] == Double.NEGATIVE_INFINITY && bounds[1] != Double.POSITIVE_INFINITY)   boundCode = 3; //upper bound

			lbfgsb.setBoundSpec(i+1,boundCode);

			if (boundCode == 1 || boundCode == 2) lbfgsb.setLowerBound(i+1, bounds[0]);
			if (boundCode == 3 || boundCode == 2) lbfgsb.setUpperBound(i+1, bounds[1]);

			log.debug("bounds " + i + " = [" +  bounds[0] + ", " + bounds[1] + "]");
		}

		int[]     iter   = {0};       //store number of iterations taken by L-BFGS-B
		boolean[] error  = {false};   //indicate whether L-BFGS-B encountered an error
		double[]  params = new double[numParams+1]; //parameters (e.g., wts of formulas) found by L-BFGS-B
		double[]  paras  = parameters.getAllParameterValues();

		means = new double[numParams+1];

		//CHANGED:
		//for (int i = 0; i < paras.length; i++)
		//  params[i+1] = paras[i];

		Iterable<Kernel> modelEvidence = model.getKernels();
		int ii = 0;
		for (Kernel et : modelEvidence) 
		{
			if (et instanceof PriorWeightKernel)   
			{
				params[ii+1] = configuration.getUnitRuleMean();
				means[ii+1]  = configuration.getUnitRuleMean();
				log.debug("initParam+rule: " + params[ii+1] + "  " + et.toString());
			}
			else if (et instanceof CompatibilityRuleKernel) 
			{
				params[ii+1] = configuration.getRuleMean();
				means[ii+1]  = configuration.getRuleMean();
				log.debug("initParam+rule: " + params[ii+1] + "  " + et.toString());
			}
			else continue;
			ii++;
		}


		for (int i = 0; i < paras.length; i++)
			log.debug("initial param ,  mean " + params[i+1] + " ,  " + means[i+1]);
		//System.exit(-1);    

		lbfgsb.minimize(params, iter, error);

		if (error[0])
		{
			log.debug("LBFGSB returned with an error!");
			//throw new RuntimeException("LBFGSB returned with an error!");
			log.debug("using minParams");
			System.arraycopy(minParams, 0, params, 0, params.length);
		}

		log.debug("LBFGSB number of iterations = " + iter[0]);

		for (int i = 1; i < params.length; i++) //NOTE: the params are indexed starting from 1!
		{
			//System.out.println("params[" + i + "] = " + params[i]);
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
		log.debug("*****************************************************************");
		log.debug("Current point (start from index 1): {}", Arrays.toString(params));

		//Set the weights of the formulas to the current weights
		parameters.setAllParameters2(params);

		//Evaluate for ground truth with current weights
		double[]  gradientTruth = new double[parameters.getNumParameters()];
		double    fctValueTruth = evaluateApp(groundTruth, gradientTruth);
		
		RunningProcess proc1 = LocalProcessMonitor.get().startProcess();
		RunningProcess proc2 = LocalProcessMonitor.get().startProcess();
		
		UniformSampler uniformSampler = new UniformSampler(proc1, 1000);
		DerivativeSampler derivativeSampler = new DerivativeSampler(proc2, parameters.getOffsets().keySet(), 1000);
		
		training.runInference();
		
		uniformSampler.sample(training.getGroundKernel(), 1.1, Integer.MAX_VALUE);

		training.runInference();
		
		derivativeSampler.sample(training.getGroundKernel(), 1.1, Integer.MAX_VALUE);
		
		double fctValueSample = uniformSampler.getLogAverageDensity();
		double[] gradientSample = new double[parameters.getOffsets().keySet().size()];
		for (Map.Entry<Kernel, Integer> e: parameters.getOffsets().entrySet())
			gradientSample[e.getValue()] = derivativeSampler.getAverage(e.getKey());
		
		System.out.println(fctValueSample);
		System.out.println(Arrays.toString(gradientSample));
		if (true) throw new IllegalStateException();

		//Finally, add priors on weights
		double squaredSum = 0.0;
		//double inverseSum = 0.0;
		double prior = configuration.getParameterPrior();
		for (int i = 1; i < params.length; i++)
		{
			//squaredSum += params[i] * params[i] * prior;
			//inverseSum += 1.0/params[i]*prior;
			//gradient[i] = 2*params[i] *prior ;
			//gradient[i] = ( 2*params[i] - 1.0/(params[i]*params[i]) )*prior ;
			squaredSum += (params[i]-means[i]) * (params[i]-means[i]) * prior;
			gradient[i] = 2 * (params[i]-means[i]) *prior ;
		}
		log.debug("Prior gradient (start from index 1): {}", Arrays.toString(gradient));

		//Now, put it all together
		double fctvalue = fctValueTruth - fctValueSample + squaredSum; //+ inverseSum;
		for (int i = 1; i < gradient.length;i++)
			gradient[i] += gradientTruth[i-1] - gradientSample[i-1];

		//for (int i = 1; i < gradient.length;i++)
		//  gradient[i] = -gradient[i];
		//fctvalue = -fctvalue;

		log.debug("-----------------------------------------");
		log.debug("Gradient Truth  : {}", Arrays.toString(gradientTruth));
		log.debug("Gradient Sample  : {}", Arrays.toString(gradientSample));
		log.debug("\n");
		log.debug("Fct Value Truth : {}", fctValueTruth);
		log.debug("Fct Value Train : {}", fctValueSample);
		log.debug("\n");
		log.debug("Fct Value {}, no paras {}", fctvalue, params.length-1);
		log.debug("Gradient (start from index 1): {}", Arrays.toString(gradient));
		log.debug("=========================================");


		if (fctvalue < minValue) { minValue = fctvalue; System.arraycopy(params, 0, minParams, 0, minParams.length); }

		log.debug("*************************************************");
		proc1.terminate();
		proc2.terminate();
		return fctvalue;
	}

	private double evaluateApp(ModelApplication app, double[] gradient)
	{
		if (configuration.getNorm()!= L1 && configuration.getNorm()!= L2) throw new UnsupportedOperationException("Not yet implemented");

		double value = 0.0;
		for (GroundCompatibilityKernel e : app.getCompatibilityKernels())
		{
			double pvalue = e.getIncompatibility();
			value += (configuration.getNorm()==L1) ? pvalue : pvalue*pvalue;
			Kernel et = e.getKernel();
			int numParas = et.getParameters().numParameters();
			double[] pgradient = new double[numParas];
			for (int x1 = 0; x1 < numParas; x1++)
			{
				pgradient[x1] = e.getIncompatibilityDerivative(x1);
				parameters.add2ArrayValue(et, x1, gradient, (configuration.getNorm()==L1) ? pgradient[x1] : (2*pvalue*pgradient[x1]));
				//parameters.add2ArrayValue2(et, x1, gradient, (config.getNorm()==L1) ? pgradient[x1] : (2*pvalue*pgradient[x1]));
				//gradient does not start from 1
			}
		}
		return value;
	}
}
