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

import edu.umd.cs.psl.model.DistanceNorm;
import edu.umd.cs.psl.util.config.PSLConfiguration;

public class PSLCoreConfiguration extends PSLConfiguration {

	private static final String defaultConfigFilename = "pslcore.config";
	
	public static final String configDistanceNorm = "norm";
	public static final String defaultDistanceNorm = "l1";
	
	private static final String configContinuousVars = "continuousVariables";
	private static final boolean defaultContinuousVars = true;
	
	private static final String configActivationThreshold = "activationThreshold";
	private static final double defaultActivationThreshold = 0.1; 
	
	private static final String configMaxNoInferenceSteps = "maxNoSteps";
	private static final int defaultMaxNoInferenceSteps = 500;
	
	private static final String configActivationTerminationFactor = "activationTermination";
	private static final double defaultActivationTerminationFactor = 0.002;
	
	private static final String configNumericProgramSizeBoundsLower = "programSizeBoundsLower";
	private static final String configNumericProgramSizeBoundsUpper = "programSizeBoundsUpper";
	private static final int[] defaultNumericProgramSizeBounds = {25000,60000};
	
	private static final String configSamplingSteps = "samplingsteps";
	private static final int defaultSamplingSteps = 100000;
	
	private static final String configNoThreads = "noThreads";
	private static final int defaultNoThreads = 32;
	
	private DistanceNorm norm;
	private boolean continuousVariables;
	private double activationThreshold;
	private int maxNoInferenceSteps;
	private int noThreads;
	private double activationTerminationFactor;
	private int[] numericProgramSizeBounds;
	private int samplingSteps;
	
	private boolean partitionedOptimizer;

	
	public PSLCoreConfiguration(String dir, String configFile) {
		super(dir,configFile,null);
		defaultSetup();
	}
	
	public PSLCoreConfiguration(String configFile) {
		super(configFile,null);
		defaultSetup();
	}
	
	public PSLCoreConfiguration() {
		this(defaultConfigFilename);
	}

	private void defaultSetup() {
		setNorm(DistanceNorm.parse(getString(configDistanceNorm, defaultDistanceNorm)));
		setContinuousVariables(getBoolean(configContinuousVars, defaultContinuousVars));
		setActivationThreshold(getDouble(configActivationThreshold,defaultActivationThreshold));
		setMaxNoInferenceSteps(getInt(configMaxNoInferenceSteps,defaultMaxNoInferenceSteps));
		setNoThreads(getInt(configNoThreads,defaultNoThreads));
		setActivationTerminationFactor(getDouble(configActivationTerminationFactor,defaultActivationTerminationFactor));
		setNumericProgramSizeBounds(getInt(configNumericProgramSizeBoundsLower,defaultNumericProgramSizeBounds[0]),
				getInt(configNumericProgramSizeBoundsUpper,defaultNumericProgramSizeBounds[1]));
		setSamplingSteps(getInt(configSamplingSteps,defaultSamplingSteps));
	}
	
	public void setNorm(DistanceNorm norm) {
		this.norm = norm;
	}

	public DistanceNorm getNorm() {
		return norm;
	}

	public void setContinuousVariables(boolean continuousVariables) {
		this.continuousVariables = continuousVariables;
	}

	public boolean hasContinuousVariables() {
		return continuousVariables;
	}

	public void setActivationThreshold(double activationThreshold) {
		this.activationThreshold = activationThreshold;
	}

	public double getActivationThreshold() {
		return activationThreshold;
	}

	public void setMaxNoInferenceSteps(int maxNoInferenceSteps) {
		this.maxNoInferenceSteps = maxNoInferenceSteps;
	}

	public int getMaxNoInferenceSteps() {
		return maxNoInferenceSteps;
	}

	public void setNoThreads(int noThreads) {
		this.noThreads = noThreads;
	}

	public int getNoThreads() {
		return noThreads;
	}

	public void setActivationTerminationFactor(double activationTerminationFactor) {
		this.activationTerminationFactor = activationTerminationFactor;
	}

	public double getActivationTerminationFactor() {
		return activationTerminationFactor;
	}

	public void setNumericProgramSizeBounds(int lowerbound, int upperBound) {
		if (lowerbound>=upperBound) throw new IllegalArgumentException("Lower bound must be smaller than the upper bound!");
		this.numericProgramSizeBounds = new int[]{lowerbound,upperBound};
	}

	public int[] getNumericProgramSizeBounds() {
		return numericProgramSizeBounds;
	}

	public void setSamplingSteps(int samplingSteps) {
		this.samplingSteps = samplingSteps;
	}

	public int getSamplingSteps() {
		return samplingSteps;
	}
}
