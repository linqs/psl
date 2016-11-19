/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight.random;

import java.util.Iterator;
import java.util.Map;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;

/**
 * A {@link FirstOrderMetropolisRandOM} learning algorithm which scores the likelihood
 * of a sample using the distance in total (unweighted) incompatibility space grouped
 * by {@link WeightedRule} between the sample and the observations.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class IncompatibilityMetropolisRandOM extends FirstOrderMetropolisRandOM {
	
	protected double[] obsvIncompatibilities;
	protected int[] numGroundings;

	public IncompatibilityMetropolisRandOM(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
	}
	
	@Override
	protected void doLearn() {
		/* Computes the observed incompatibilities */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}
		obsvIncompatibilities = computeIncompatibilities();
		
		/* Counts the numbers of groundings */
		numGroundings = new int[kernels.size()];
		for (int i = 0; i < kernels.size(); i++) {
			Iterator<GroundRule> itr = reasoner.getGroundKernels(kernels.get(i)).iterator();
			while(itr.hasNext()) {
				itr.next();
				numGroundings[i]++;
			}
			
			if (numGroundings[i] == 0.0)
				numGroundings[i]++;
		}
		
		/* Begins learning */
		super.doLearn();
	}

	@Override
	public double getLogLikelihoodObservations() {
		double likelihood = 0.0;
		double[] sampleIncompatibilities = computeIncompatibilities();
		for (int i = 0; i < kernels.size(); i++)
//			likelihood -= Math.abs((sampleIncompatibilities[i] - obsvIncompatibilities[i])) / observationScale;
			likelihood -= Math.pow((sampleIncompatibilities[i] - obsvIncompatibilities[i]), 2);
//			likelihood -= Math.abs((sampleIncompatibilities[i] - obsvIncompatibilities[i])) / (numGroundings[i] * observationScale);
		return likelihood;
	}
	
	protected double[] computeIncompatibilities() {
		double[] incompatibility = new double[kernels.size()];
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundRule gk : reasoner.getGroundKernels(kernels.get(i)))
				incompatibility[i] += ((WeightedGroundRule) gk).getIncompatibility();
		}
		return incompatibility;
	}

}
