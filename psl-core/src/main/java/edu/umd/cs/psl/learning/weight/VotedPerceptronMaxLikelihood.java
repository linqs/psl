package edu.umd.cs.psl.learning.weight;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;

public class VotedPerceptronMaxLikelihood extends AbstractMaxLikelihood {
	
	private static final Logger log = LoggerFactory.getLogger(VotedPerceptronMaxLikelihood.class);

	public VotedPerceptronMaxLikelihood(Model m,
			Database givenData, Database groundTruth, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		super(m, givenData, groundTruth, config);
	}

	@Override
	public void learn() {
//		initialize();
//		double[] paras = parameters.getAllParameterValues();
////		double prior = configuration.getParameterPrior();
//		double[][] weights = new double[configuration.getPerceptronIterations()+1][paras.length];
//		for (int i=0;i<paras.length;i++) weights[0][i]=paras[i];
//		
//		for (int iteration=1;iteration<=configuration.getPerceptronIterations();iteration++) {
//			double[] currentWeights = weights[iteration-1];
//			log.debug("Current point: {}",Arrays.toString(currentWeights));
//			parameters.setAllParameters(currentWeights);
//			
//			//Evalute for ground truth with current weights
//			//groundTruth.runInference();
//			double[] gradientTruth = new double[parameters.getNumParameters()];
//			double fctValueTruth = evaluateApp(groundTruth,gradientTruth,null);
//			log.debug("Gradient Truth {}",Arrays.toString(gradientTruth));
//			log.debug("Fct Value Truth {}",fctValueTruth);
//			
//			
//			
//			training.runInference();
//			double[] gradientTrain = new double[parameters.getNumParameters()];
//			double fctValueTrain = evaluateApp(training,gradientTrain,null);
//			log.debug("Gradient Train {}",Arrays.toString(gradientTrain));
//			log.debug("Fct Value Train {}",fctValueTrain);
//
//			
//			//Update weights
//			for (int i=0;i<paras.length;i++) {
//				 double w= weights[iteration-1][i] - configuration.getPerceptronUpdateFactor() * (gradientTruth[i] - gradientTrain[i] + 2 * prior * weights[iteration-1][i]);
//				 double[] bounds = parameters.getBounds(i);
//				 w = Math.max(w, bounds[0]);
//				 w = Math.min(w, bounds[1]);
//				 weights[iteration][i]=w;
//			}
//			
//		}
//		
//		//Average weights
//		for (int i=0;i<paras.length;i++) {
//			double total = 0.0;
//			for (int t=1;t<=configuration.getPerceptronIterations();t++) {
//				total += weights[t][i];
//			}
//			paras[i]=total/configuration.getPerceptronIterations();
//		}
//		parameters.setAllParameters(paras);
//		log.debug("Learned parameters: {}",Arrays.toString(paras));
//		}
	}

}
