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
package edu.umd.cs.psl.application.learning.weight;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionVariable;

/**
 * Learns weights by optimizing the pseudo-log-likelihood of the data using
 * the voted perceptron algorithm.
 * 
 * @author Ben London <blondon@cs.umd.edu>
 */
public class MaxPseudoLikelihood extends VotedPerceptron {

	/**
	 * Key for positive integer property.
	 * MaxPseudoLikelihood will sample this many values to approximate
	 * the integrals in the marginal computation.
	 */
	public static final String NUM_SAMPLES_KEY = CONFIG_PREFIX + ".numsamples";
	/** Default value for NUM_SAMPLES_KEY */
	public static final int NUM_SAMPLES_DEFAULT = 10;
	
	private HashMap<GroundAtom,double[]> bounds;
	private final int numSamples;
	
	public MaxPseudoLikelihood(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);

		numSamples = config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
		if (numSamples <= 0)
			throw new IllegalArgumentException("Number of samples must be positive integer.");
	}
	
	@Override
	public void initGroundModel() {
		/* Invoke method in the parent class to setup ground model */
		super.initGroundModel();
		
		/* Determine the bounds of integration */
		bounds = new HashMap<GroundAtom,double[]>();
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			RandomVariableAtom atom = e.getKey();
			double min = 0.0;
			double max = 1.0;
			for (GroundKernel gk : atom.getRegisteredGroundKernels()) {
				if (gk instanceof GroundConstraintKernel) {
					ConstraintTerm ct = ((GroundConstraintKernel) gk).getConstraintDefinition();
					/* Create a map containing the Markov blanket and associated values */
					HashMap<FunctionVariable,Double> mb = new HashMap<FunctionVariable,Double>();
					for (GroundAtom a : gk.getAtoms()) {
						if (a != atom)
							mb.put(a.getVariable(), a.getValue());
					}
					/* Compute bounds of integration */
					double rhs = ct.getValue() - ct.getFunction().getValue(mb, false);
					switch(ct.getComparator()) {
						case Equality:
							min = rhs;
							max = rhs;
							break;
						case SmallerThan:
							if (max > rhs)
								max = rhs;
							if (min > max)
								min = max;
							break;
						case LargerThan:
							if (min < rhs)
								min = rhs;
							if (max < min)
								max = min;
							break;
					}
				}
			}
			bounds.put(atom, new double[]{min,max});
		}
	}
	
	@Override
	protected double[] computeExpectedIncomp() {
		double[] expIncomp = new double[kernels.size()];
		
		/* Let's create/seed the random number generator */
		Random rand = new Random();
		/* Accumulate the marginals over all atoms */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			RandomVariableAtom atom = e.getKey();
			/* Check the range of the variable to see if we can integrate it */
			double range = bounds.get(atom)[1] - bounds.get(atom)[0];
			if (range != 0.0) {
				/* Sample numSamples random numbers in the range of integration */
				double[] s = new double[numSamples];
				for (int j = 0; j < numSamples; j++) {
					s[j] = rand.nextDouble() * range + bounds.get(atom)[0];
				}
				/* Compute the incompatibility of each sample for each kernel */
				HashMap<CompatibilityKernel,double[]> incompatibilities = new HashMap<CompatibilityKernel,double[]>();
				double originalValue = atom.getValue();
				for (GroundKernel gk : atom.getRegisteredGroundKernels()) {
					if (gk instanceof GroundCompatibilityKernel) {
						CompatibilityKernel k = (CompatibilityKernel) gk.getKernel();
						if (!incompatibilities.containsKey(k))
							incompatibilities.put(k, new double[numSamples]);
						double[] inc = incompatibilities.get(k);
						for (int j = 0; j < numSamples; j++) {
							atom.setValue(s[j]);
							inc[j] += gk.getIncompatibility();
						}
					}
				}
				/* Remember to return the atom to its original state! */
				atom.setValue(originalValue);
				/* Compute the unnormalized marginals and accumulate the partition for the current atom. */
				HashMap<CompatibilityKernel,Double> marg = new HashMap<CompatibilityKernel,Double>();
				double Z = 0.0;
				for (int j = 0; j < numSamples; j++) {
					/* Compute the exponent */
					double sum = 0.0;
					for (Map.Entry<CompatibilityKernel,double[]> e2 : incompatibilities.entrySet()) {
						CompatibilityKernel k = e2.getKey();
						double[] inc = e2.getValue();
						sum -= k.getWeight().getWeight() * inc[j];
					}
					double exp = Math.exp(sum);
					/* Add to partition */
					Z += exp;
					/* Compute the marginals for current atom */
					for (Map.Entry<CompatibilityKernel,double[]> e2 : incompatibilities.entrySet()) {
						CompatibilityKernel k = e2.getKey();
						if (!marg.containsKey(k))
							marg.put(k, 0.0);
						double val = marg.get(k).doubleValue();
						marg.put(k, val + exp * incompatibilities.get(k)[j]);
					}
				}
				/* Do we need to normalize by the (range / numSamples) ? */
				//Z *= bounds.get(atom)[1] - bounds.get(atom)[0] / numSamples;
				/* Finally, we add to the marginals for each kernel */ 
				for (int i = 0; i < kernels.size(); i++) {
					expIncomp[i] += marg.get(kernels.get(i)) / Z;
				}
			}
		}
		
		return expIncomp;
	}

}
