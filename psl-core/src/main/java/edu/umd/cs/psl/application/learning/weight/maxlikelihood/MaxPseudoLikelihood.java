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
package edu.umd.cs.psl.application.learning.weight.maxlikelihood;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.NumericUtilities;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.FunctionVariable;

/**
 * Learns weights by optimizing the pseudo-log-likelihood of the data using
 * the voted perceptron algorithm.
 * 
 * @author Ben London <blondon@cs.umd.edu>
 */
public class MaxPseudoLikelihood extends VotedPerceptron {

	private static final Logger log = LoggerFactory.getLogger(MaxPseudoLikelihood.class);
	
	/**
	 * Key for positive integer property.
	 * MaxPseudoLikelihood will sample this many values to approximate
	 * the integrals in the marginal computation.
	 */
	public static final String NUM_SAMPLES_KEY = CONFIG_PREFIX + ".numsamples";
	/** Default value for NUM_SAMPLES_KEY */
	public static final int NUM_SAMPLES_DEFAULT = 10;
	
	/**
	 * Key for constraint violation tolerance
	 */
	public static final String CONSTRAINT_TOLERANCE_KEY = CONFIG_PREFIX + ".constrainttolerance";
	/** Default value for CONSTRAINT_TOLERANCE **/
	public static final double CONSTRAINT_TOLERANCE_DEFAULT = 1e-5;
	
	/**
	 * Key for positive double property.
	 * Used as minimum width for bounds of integration.
	 */
	public static final String MIN_WIDTH_KEY = CONFIG_PREFIX + ".minwidth";
	/** Default value for MIN_WIDTH_KEY */
	public static final double MIN_WIDTH_DEFAULT = 1e-2;
	
	private HashMap<GroundAtom,double[]> bounds;
	private final int numSamples;
	private final double minWidth;
	private final double constraintTol;
	
	/**
	 * Constructor
	 * @param model
	 * @param rvDB
	 * @param observedDB
	 * @param config
	 */
	public MaxPseudoLikelihood(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);

		numSamples = config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
		if (numSamples <= 0)
			throw new IllegalArgumentException("Number of samples must be positive integer.");
		minWidth = config.getDouble(MIN_WIDTH_KEY, MIN_WIDTH_DEFAULT);
		if (minWidth <= 0)
			throw new IllegalArgumentException("Minimum width must be positive double.");
		constraintTol = config.getDouble(CONSTRAINT_TOLERANCE_KEY, CONSTRAINT_TOLERANCE_DEFAULT);
		if (constraintTol <= 0)
			throw new IllegalArgumentException("Minimum width must be positive double.");
	}
	
	/**
	 * Initializes the domain of integration for each ground atom.
	 * Note: calls super.initGroundModel() first, in order to ground model. 
	 */
	@Override
	public void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		/* Invoke method in the parent class to setup ground model */
		super.initGroundModel();
		
		/* Determine the bounds of integration */
		bounds = new HashMap<GroundAtom,double[]>();
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			RandomVariableAtom atom = e.getKey();
			double min = 0.0;
			double max = 1.0;
			for (GroundKernel gk : atom.getRegisteredGroundKernels()) {
				if (!(gk instanceof GroundConstraintKernel)) {
					continue;
				}
				ConstraintTerm ct = ((GroundConstraintKernel) gk).getConstraintDefinition();
				FunctionTerm ft = ct.getFunction();
				if (!(ft instanceof FunctionSum)) {
					throw new IllegalStateException("Can only have FunctionSum constraints in MPLE");
				}
				/* Create a map containing the Markov blanket and associated values */
				HashMap<FunctionVariable,Double> mb = new HashMap<FunctionVariable,Double>();
				for (GroundAtom a : gk.getAtoms()) {
					if (!a.equals(atom) && trainingMap.getTrainingMap().containsKey(a)) {
						FunctionVariable var = a.getVariable();
						double val = trainingMap.getTrainingMap().get(a).getValue();
						mb.put(var, val);
					}
				}
				/* Compute the RHS of the (in)equality */
				double rhs = ct.getValue();
				//System.out.println("RHS before: " + rhs);
				double coef = 0.0;
				StringBuilder constStr = new StringBuilder(rhs + " - (");
				for (Iterator<FunctionSummand> iter = ((FunctionSum) ft).iterator(); iter.hasNext();) {
					FunctionSummand term = iter.next();
					FunctionVariable var = (FunctionVariable) term.getTerm();
					if (atom.getVariable().equals(var)) {
						coef = term.getCoefficient();
						//System.out.println(term.toString() + " coef: " + term.getCoefficient());
					}
					else if (mb.containsKey(var)) {
						//System.out.println(term.toString() + " value: " + mb.get(var).toString() + ", coef: " + term.getCoefficient());
						rhs -= mb.get(var) * term.getCoefficient();
						constStr.append(" +" + term.getCoefficient() + "*" + mb.get(var));
					}
				}
				constStr.append(" ) / " + coef);
				rhs /= coef;
				//System.out.println("RHS after: " + rhs);
				/* Update the bounds of integration */
				switch(ct.getComparator()) {
					case Equality:
						if (rhs < -constraintTol) {
							throw new IllegalStateException("There are negative equality constraints in the ground data. RHS=" + rhs);
//							System.out.println("neg eq constraint in " + atom.toString() + " : " + rhs + " = " + constStr);
//							System.out.println("Function sum = " + ct.getFunction());
						}
						min = rhs;
						max = rhs;
						break;
					case SmallerThan:
						if (coef < 0) {
							if (min < rhs)
								min = rhs;
							if (max < min)
								throw new IllegalStateException("Infeasible constraints; max is less than min.");
						}
						else {
							if (max > rhs)
								max = rhs;
							if (min - max > constraintTol)
								throw new IllegalStateException("Infeasible constraints; min is greater than max; min - max = " + (min-max));
						}
						break;
					case LargerThan:
						if (coef < 0) {
							if (max > rhs)
								max = rhs;
							if (min - max > constraintTol)
								throw new IllegalStateException("Infeasible constraints; min is greater than max.");
						}
						else {
							if (min < rhs)
								min = rhs;
							if (min - max > constraintTol)
								throw new IllegalStateException("Infeasible constraints; max is less than min.");
						}
						break;
				}
			}
			/* Ensure a minimum width */
			if (max - min < minWidth) {
				if (min - minWidth/2 < 0.0) {
					min = 0.0;
					max = minWidth;
				}
				else if (max + minWidth/2 > 1.0) {
					min = 1.0 - minWidth;
					max = 1.0;
				}
				else {
					min -= minWidth/2;
					max += minWidth/2;
				}
			}
			//System.out.println(atom.toString() + " min: " + min + " max: " + max);
			bounds.put(atom, new double[]{min,max});
		}
	}
	
	/**
	 * Computes the expected incompatibility using the pseudolikelihood.
	 * Uses Monte Carlo integration to approximate definite integrals,
	 * since they do not admit a closed-form antiderivative.
	 */
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
							inc[j] += ((GroundCompatibilityKernel) gk).getIncompatibility();
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
						val += exp * incompatibilities.get(k)[j];
						marg.put(k, val);
					}
				}
				/* Finally, we add to the marginals for each kernel */ 
				for (int i = 0; i < kernels.size(); i++) {
					CompatibilityKernel k = kernels.get(i);
					if (marg.containsKey(k))
						if (marg.get(k) > 0.0) 
							expIncomp[i] += marg.get(k) / Z;
				}
			}
		}
	
		return expIncomp;
	}
	
//	private double[] getExpectedInc2() {
//		double[] expIncomp = new double[kernels.size()];
//		
//		/* Let's create/seed the random number generator */
//		Random rand = new Random();
//		/* Accumulate the marginals over all atoms */
//		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
//			RandomVariableAtom atom = e.getKey();
//			/* Check the range of the variable to see if we can integrate it */
//			double range = bounds.get(atom)[1] - bounds.get(atom)[0];
//			if (range != 0.0) {
//				/* Sample numSamples random numbers in the range of integration */
//				//double[] s = new double[numSamples];
//				HashMap<CompatibilityKernel,Double> marg = new HashMap<CompatibilityKernel,Double>();
//				double Z = 0.0;
//				double originalValue = atom.getValue();
//				for (int j = 0; j < numSamples; j++) {
//					HashMap<CompatibilityKernel,Double> incompatibilities = new HashMap<CompatibilityKernel,Double>();
//					double s = rand.nextDouble() * range + bounds.get(atom)[0];
//					atom.setValue(s);
//					/* Compute the incompatibility of each sample for each kernel */
//					for (GroundKernel gk : atom.getRegisteredGroundKernels()) {
//						if (gk instanceof GroundCompatibilityKernel) {
//							CompatibilityKernel k = (CompatibilityKernel) gk.getKernel();
//							if (!incompatibilities.containsKey(k))
//								incompatibilities.put(k, 0.0);
//							double inc = incompatibilities.get(k);
//							inc += ((GroundCompatibilityKernel) gk).getIncompatibility();
//							incompatibilities.put(k, inc);
//						}
//					}
//					/* Compute the exponent */
//					double sum = 0.0;
//					for (Map.Entry<CompatibilityKernel,Double> e2 : incompatibilities.entrySet()) {
//						CompatibilityKernel k = e2.getKey();
//						double inc = e2.getValue();
//						sum -= k.getWeight().getWeight() * inc;
//					}
//					double exp = Math.exp(sum);
//					/* Add to partition */
//					Z += exp;
//					/* Compute the marginals for current atom */
//					for (Map.Entry<CompatibilityKernel,Double> e2 : incompatibilities.entrySet()) {
//						CompatibilityKernel k = e2.getKey();
//						if (!marg.containsKey(k))
//							marg.put(k, 0.0);
//						double val = marg.get(k);
//						val += exp * incompatibilities.get(k);
//						marg.put(k, val);
//					}
//				}
//				/* Remember to return the atom to its original state! */
//				atom.setValue(originalValue);
//				/* Compute the unnormalized marginals and accumulate the partition for the current atom. */
//				for (int j = 0; j < numSamples; j++) {
//				}
//				/* Finally, we add to the marginals for each kernel */ 
//				for (int i = 0; i < kernels.size(); i++) {
//					CompatibilityKernel k = kernels.get(i);
//					if (marg.containsKey(k))
//						expIncomp[i] += marg.get(k) / Z;
//				}
//			}
//		}
//		
//		return expIncomp;
//	}
}
