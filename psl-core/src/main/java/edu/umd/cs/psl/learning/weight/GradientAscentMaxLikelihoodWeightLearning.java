package edu.umd.cs.psl.learning.weight;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;

public class GradientAscentMaxLikelihoodWeightLearning extends AbstractMaxLikelihoodWeightLearning {
	
	private static final Logger log = LoggerFactory.getLogger(GradientAscentMaxLikelihoodWeightLearning.class);

	public GradientAscentMaxLikelihoodWeightLearning(Model m,
			Database givenData, Database groundTruth, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		super(m, givenData, groundTruth, config);
	}

	@Override
	public void learn() {
		initialize();
		double[] weights = parameters.getAllParameterValues();
		
		boolean converged = false;
		int itr = 0;
		while (!converged && (itr < maxRounds || maxRounds == 0)) {
			log.debug("Current point: {}",Arrays.toString(weights));
			parameters.setAllParameters(weights);
			
			groundTruth.runInference();
			givenData.runInference();
			double[] gradientTruth = new double[parameters.getNumParameters()];
			double fctValueTruth = evaluateApp(groundTruth,gradientTruth);
			log.debug("Gradient Truth {}",Arrays.toString(gradientTruth));
			log.debug("Fct Value Truth {}",fctValueTruth);
			
			double[] gradientTrain = new double[parameters.getNumParameters()];
			double fctValueTrain = evaluateApp(groundTruth,gradientTrain);
			log.debug("Gradient Train {}",Arrays.toString(gradientTrain));
			log.debug("Fct Value Train {}",fctValueTrain);
			
			//Update weights
			for (int i=0; i < weights.length; i++) {
				 double w= weights[iteration-1][i] - configuration.getPerceptronUpdateFactor() * (gradientTruth[i] - gradientTrain[i] + 2 * prior * weights[iteration-1][i]);
				 double[] bounds = parameters.getBounds(i);
				 w = Math.max(w, bounds[0]);
				 w = Math.min(w, bounds[1]);
				 weights[iteration][i]=w;
			}
			
			parameters.setAllParameters(paras);
			log.debug("Learned parameters: {}",Arrays.toString(paras));
		}
	}

}
