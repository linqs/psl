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
package edu.umd.cs.psl.config;

public class WeightLearningConfiguration extends PSLCoreConfiguration {

	public enum Type { LBFGSB, Perceptron;
		public static Type parseType(String type) {
			type = type.toLowerCase();
			if (type.equals("lbfgsb")) return LBFGSB;
			else if (type.equals("perceptron")) return Perceptron;
			else throw new IllegalArgumentException("Unknown learning type: " + type);
		}
	};
		
	private static final String configInitialParameter = "initialweight";
	private static final double defaultInitialParameter = 10.0;
	
	private static final String configLearningType = "learningtype";
	private static final String defaultLearningType = "perceptron";

	private static final String configParameterPrior = "prior";
	private static final double defaultParameterPrior = 0.0;
	private static final String configMaxOptIterations = "maxiterations";
	private static final int defaultMaxOptIterations = 100;
	private static final String configPointMoveConvergenceThres = "movepointthreshold";
	private static final double defaultPointMoveConvergenceThres = -1.0;

	private static final String configPerceptronIterations = "perceptroniterations";
	private static final int defaultPerceptronIterations = 10;
	private static final String configUpdateFactor = "updatefactor";
	private static final double defaultUpdateFactor = 1.0;

  //CHANGED:
  private static final String configRuleMean      = "rulemean";
  private static final double defaultRuleMean     = 0.0;
  private static final String configUnitRuleMean  = "unitrulemean";
  private static final double defaultUnitRuleMean = 1.0;
	
	private double parameterPrior;
	private int maxOptIterations;
	private double pointMoveConvergenceThres;
	private double initialParameter;
	private Type learningType;
	
	private int perceptronIterations;
	private double perceptronUpdateFactor;


  //CHANGED:
  private double ruleMean;
  private double unitRuleMean;

	
	public WeightLearningConfiguration(String dir, String configFile) {
		super(dir,configFile);
		defaultSetup();
	}
	
	public WeightLearningConfiguration(String configFile) {
		super(configFile);
		defaultSetup();
	}
	
	public WeightLearningConfiguration() {
		super();
		defaultSetup();
	}
	
	private void defaultSetup() {
		setParameterPrior(getDouble(configParameterPrior, defaultParameterPrior));
		setInitialParameter(getDouble(configInitialParameter, defaultInitialParameter));
		setLearningType(Type.parseType(getString(configLearningType, defaultLearningType)));
		setPerceptronIterations(getInt(configPerceptronIterations, defaultPerceptronIterations));
		setPerceptronUpdateFactor(getDouble(configUpdateFactor, defaultUpdateFactor));
		setMaxOptIterations(getInt(configMaxOptIterations,defaultMaxOptIterations));
		setPointMoveConvergenceThres(getDouble(configPointMoveConvergenceThres,defaultPointMoveConvergenceThres));

    //CHANGED:
    setRuleMean(getDouble(configRuleMean, defaultRuleMean));
    setUnitRuleMean(getDouble(configUnitRuleMean, defaultUnitRuleMean));
	}

	public void setPerceptronUpdateFactor(double updateFactor) {
		this.perceptronUpdateFactor = updateFactor;
	}

	public double getPerceptronUpdateFactor() {
		return perceptronUpdateFactor;
	}

	public void setPerceptronIterations(int perceptronIterations) {
		this.perceptronIterations = perceptronIterations;
	}

	public int getPerceptronIterations() {
		return perceptronIterations;
	}

	public void setLearningType(Type learningType) {
		this.learningType = learningType;
	}

	public Type getLearningType() {
		return learningType;
	}

	public void setInitialParameter(double initialParameter) {
		this.initialParameter = initialParameter;
	}

	public double getInitialParameter() {
		return initialParameter;
	}

	public void setPointMoveConvergenceThres(double pointMoveConvergenceThres) {
		this.pointMoveConvergenceThres = pointMoveConvergenceThres;
	}

	public double getPointMoveConvergenceThres() {
		return pointMoveConvergenceThres;
	}

	public void setMaxOptIterations(int maxOptIterations) {
		this.maxOptIterations = maxOptIterations;
	}

	public int getMaxOptIterations() {
		return maxOptIterations;
	}

	public void setParameterPrior(double parameterPrior) {
		this.parameterPrior = parameterPrior;
	}

	public double getParameterPrior() {
		return parameterPrior;
	}

	public void setRuleMean(double ruleMean) {
		this.ruleMean = ruleMean;
	}

	public double getRuleMean() {
		return ruleMean;
	}

	public void setUnitRuleMean(double unitRuleMean) {
		this.unitRuleMean = unitRuleMean;
	}

	public double getUnitRuleMean() {
		return unitRuleMean;
	}	
	
}
