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

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//CHANGED
import edu.umd.cs.psl.optimizer.lbfgs.*;
import edu.umd.cs.psl.application.FullInference;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.inference.MaintainedMemoryFullInference;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.config.WeightLearningConfiguration;
import edu.umd.cs.psl.database.Database;
import static edu.umd.cs.psl.model.DistanceNorm.*;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import de.mathnbits.util.ArrayUtil;
import edu.umd.cs.psl.model.kernel.priorweight.PriorWeightKernel;
import edu.umd.cs.psl.model.kernel.rule.AbstractRuleKernel;
import edu.umd.cs.psl.model.kernel.rule.CompatibilityRuleKernel;


public class WeightLearningGlobalOpt implements FunctionEvaluation, WeightLearning, ConvexFunc {

	private static final Logger log = LoggerFactory.getLogger(WeightLearningGlobalOpt.class);


//	private static final double optimizationLowerBound = 0.001;
//	private static final double optimizationUpperBound = Double.POSITIVE_INFINITY;
	
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

	
	public WeightLearningGlobalOpt(Model m, Database truth, Database train, WeightLearningConfiguration configuration, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		model = m;
		groundTruth = new MaintainedMemoryFullInference(model,truth, config);
		training = new MaintainedMemoryFullInference(model,train, config);
		this.configuration = configuration;
//		this.config = config;
		parameters = new ParameterMapper();
	}

	public WeightLearningGlobalOpt(Model m, Database truth, Database train)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		this(m,truth,train,new WeightLearningConfiguration(), new EmptyBundle());
	}
	
	private void initialize() {
		groundTruth.initialize();
//		int counter = 0;
//		for (ProbabilisticEvidence e : groundTruth.getProbabilisticEvidence()) {
//			parameters.add(e.getType());
//			counter++;
//		}
//		log.debug("Ground probabilistic evidence # {}",counter);
		for (Kernel et : model.getKernels()) {
			if (et.isCompatibilityKernel()) parameters.add(et);
		}
		parameters.setAllParameters(configuration.getInitialParameter());
		//assert verifyModelCoverage() : "Not all probabilistic evidence types in the model are covered in this learning!";
		
		training.initialize();
		
		//For testing only
		gradientCache = new double[parameters.getNumParameters()];
		hessianCache = new double[parameters.getNumParameters()][parameters.getNumParameters()];
		evaluateApp(groundTruth,gradientCache,hessianCache);
	}
	
//	private boolean verifyModelCoverage() {
//		for (EvidenceType et : model.getEvidenceTypes()) {
//			if (et.getParameters()!=Parameters.NoParameters) {
//				if (!parameters.contains(et)) {
//					log.debug("{}",et);
//					return false;
//				}
//			}
//		}
//		return true;
//	}

	@Override
  public void learn() {
    //CHANGED:
    if (configuration.getLearningType()==WeightLearningConfiguration.Type.LBFGSB) learnLBFGSB();
    else if (configuration.getLearningType()==WeightLearningConfiguration.Type.Perceptron) learnPerceptron();
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
    double    fctValueTruth = evaluateApp2(groundTruth, gradientTruth);

    //log.debug("Gradient Truth  (start from index 1):  {}", Arrays.toString(gradientTruth));
    //log.debug("Fct Value Truth (start from index 1): {}", fctValueTruth);

    // Run optimizer to find MAP state and subtract from function value and gradient

    training.runInference();
    double[] gradientTrain = new double[parameters.getNumParameters()];
    double   fctValueTrain = evaluateApp2(training, gradientTrain);

    //log.debug("Gradient Train (start from index 1): {}", Arrays.toString(gradientTrain));
    //log.debug("Fct Value Train(start from index 1): {}", fctValueTrain);

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
    double fctvalue = fctValueTruth - fctValueTrain + squaredSum; //+ inverseSum;
    for (int i = 1; i < gradient.length;i++)
      gradient[i] += gradientTruth[i-1] - gradientTrain[i-1];

    //for (int i = 1; i < gradient.length;i++)
    //  gradient[i] = -gradient[i];
    //fctvalue = -fctvalue;

    log.debug("-----------------------------------------");
    log.debug("Gradient Truth  : {}", Arrays.toString(gradientTruth));
    log.debug("Gradient Train  : {}", Arrays.toString(gradientTrain));
    log.debug("\n");
    log.debug("Fct Value Truth : {}", fctValueTruth);
    log.debug("Fct Value Train : {}", fctValueTrain);
    log.debug("\n");
    log.debug("Fct Value {}, no paras {}", fctvalue, params.length-1);
    log.debug("Gradient (start from index 1): {}", Arrays.toString(gradient));
    log.debug("=========================================");


    if (fctvalue < minValue) { minValue = fctvalue; System.arraycopy(params, 0, minParams, 0, minParams.length); }

    log.debug("*************************************************");
    return fctvalue;
  }

  private double evaluateApp2(ModelApplication app, double[] gradient)
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

  private double evaluateApp(ModelApplication app, double[] gradient, double[][] hessian) {
		if (configuration.getNorm()!=L1 && configuration.getNorm()!=L2) throw new UnsupportedOperationException("Not yet implemented");
		double value = 0.0;
		for (GroundCompatibilityKernel e : app.getCompatibilityKernels()) {
			double pvalue = e.getIncompatibility();
			value += (configuration.getNorm()==L1)?pvalue:pvalue*pvalue;
			Kernel et = e.getKernel();
			int numParas = et.getParameters().numParameters();
			double[] pgradient = new double[numParas];
			for (int x1=0;x1<numParas;x1++) {
				pgradient[x1] = e.getIncompatibilityDerivative(x1);
				parameters.add2ArrayValue(et, x1, gradient, (configuration.getNorm()==L1)?pgradient[x1]:(2*pvalue*pgradient[x1]));
				if (hessian!=null) {
					for (int x2=0;x2<=x1;x2++) {
						double hessvalue = e.getIncompatibilityHessian(x1, x2);
						double secDeriv = hessvalue;
						if (configuration.getNorm()==L2) {
							secDeriv = 2 * pgradient[x1]*pgradient[x2] + 2 * pvalue * hessvalue;
						}
						parameters.add2ArrayValue(et, x1, x2, hessian, secDeriv);
						if (x2!=x1) {
							assert x2<x1;
							parameters.add2ArrayValue(et, x2, x1, hessian, secDeriv);
						}
					}
				}
			}
		}
		return value;
	}
	
	@Override
	public double evaluateFunction(double[] paras, double[] gradient, double[][] hessian) {
    log.debug("**********************************************");

		assert paras.length==parameters.getNumParameters();
		log.debug("Current point: {}",Arrays.toString(paras));
		
		//Set the weights of the formulas to the current weights
		parameters.setAllParameters(paras);
		
		//Evalute for ground truth with current weights
		groundTruth.runInference();
		double[] gradientTruth = new double[parameters.getNumParameters()];
		double[][] hessianTruth = new double[parameters.getNumParameters()][parameters.getNumParameters()];
		double fctValueTruth = evaluateApp(groundTruth,gradientTruth,hessianTruth);
    
    log.debug("-------------------------------------------");
		log.debug("Gradient Truth {}",Arrays.toString(gradientTruth));
		log.debug("Fct Value Truth {}",fctValueTruth);
		
		//Testing, only works for L1 norm since it will change otherwise
//		assert Arrays.equals(gradientCache, gradientTruth);
//		assert ArrayUtil.equals(hessianCache, hessianTruth);

		// Run optimizer to find MAP state and subtract from function value and gradient
		
		training.runInference();
		double[] gradientTrain = new double[parameters.getNumParameters()];
		double[][] hessianTrain = new double[parameters.getNumParameters()][parameters.getNumParameters()];
		double fctValueTrain = evaluateApp(training,gradientTrain,hessianTrain);

    log.debug("-------------------------------------------");	
		log.debug("Gradient Train {}",Arrays.toString(gradientTrain));
		log.debug("Fct Value Train {}",fctValueTrain);
		

		//Finally, add priors on weights
		double squaredSum = 0.0;
		//double inverseSum = 0.0;
		double prior = configuration.getParameterPrior();
    //System.out.println("---PRIOR: " + prior);

		for (int i=0;i<paras.length;i++) {
			//squaredSum += paras[i]*paras[i]*prior;
			//inverseSum += 1.0/paras[i]*prior;
			//gradient[i] = (2*paras[i])*prior ;
			//gradient[i] = (2*paras[i] - 1.0/(paras[i]*paras[i]) )*prior ;

      squaredSum += (paras[i]-means[i]) * (paras[i]-means[i]) * prior;
      gradient[i] = 2 * (paras[i]-means[i]) *prior ;

			hessian[i][i] = (2 + Math.pow(paras[i],-3.0) ) *prior;
			
			//log-version
//			inverseSum += -Math.log(paras[i])*parameterPrior;
//			gradient[i] = (2*paras[i] - 1.0/paras[i] )*parameterPrior ;
//			hessian[i][i] = (2 + 1.0/(paras[i]*paras[i]) ) *parameterPrior;

		}
    log.debug("-------------------------------------------");
		log.debug("Prior gradient {}",Arrays.toString(gradient));
		
		//Now, put it all together
		double fctvalue = fctValueTruth - fctValueTrain + squaredSum;// + inverseSum;
		ArrayUtil.addTo(gradientTruth, gradient);
		ArrayUtil.subtractFrom(gradientTrain, gradient);
		
		ArrayUtil.addTo(hessianTruth, hessian);
		ArrayUtil.subtractFrom(hessianTrain, hessian);
		
		log.debug("Fct Value {}, no paras {}",fctvalue,paras.length);
		log.debug("Gradient {}",Arrays.toString(gradient));
		//BasicUserInteraction.readline();

    log.debug("************************");		
		return fctvalue;
	}
	
	
	public void learnPerceptron() {
		initialize();
		double[] paras = parameters.getAllParameterValues();
		double prior = configuration.getParameterPrior();
		double[][] weights = new double[configuration.getPerceptronIterations()+1][paras.length];
		for (int i=0;i<paras.length;i++) weights[0][i]=paras[i];
		
		for (int iteration=1;iteration<=configuration.getPerceptronIterations();iteration++) {
			double[] currentWeights = weights[iteration-1];
			log.debug("Current point: {}",Arrays.toString(currentWeights));
			parameters.setAllParameters(currentWeights);
			
			//Evalute for ground truth with current weights
			//groundTruth.runInference();
			double[] gradientTruth = new double[parameters.getNumParameters()];
			double fctValueTruth = evaluateApp(groundTruth,gradientTruth,null);
			log.debug("Gradient Truth {}",Arrays.toString(gradientTruth));
			log.debug("Fct Value Truth {}",fctValueTruth);
			
			
			
			training.runInference();
			double[] gradientTrain = new double[parameters.getNumParameters()];
			double fctValueTrain = evaluateApp(training,gradientTrain,null);
			log.debug("Gradient Train {}",Arrays.toString(gradientTrain));
			log.debug("Fct Value Train {}",fctValueTrain);

			
			//Update weights
			for (int i=0;i<paras.length;i++) {
				 double w= weights[iteration-1][i] - configuration.getPerceptronUpdateFactor() * (gradientTruth[i] - gradientTrain[i] + 2 * prior * weights[iteration-1][i]);
				 double[] bounds = parameters.getBounds(i);
				 w = Math.max(w, bounds[0]);
				 w = Math.min(w, bounds[1]);
				 weights[iteration][i]=w;
			}
			
		}
		
		//Average weights
		for (int i=0;i<paras.length;i++) {
			double total = 0.0;
			for (int t=1;t<=configuration.getPerceptronIterations();t++) {
				total += weights[t][i];
			}
			paras[i]=total/configuration.getPerceptronIterations();
		}
		parameters.setAllParameters(paras);
		log.debug("Learned parameters: {}",Arrays.toString(paras));

	}

	
}
