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
import edu.umd.cs.psl.config.ConfigManager;
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
public class MaxPseudoLikelihoodDiscrete extends VotedPerceptron {

	private static final Logger log = LoggerFactory.getLogger(MaxPseudoLikelihoodDiscrete.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "maxspeudolikelihood";
	
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
	
	private HashMap<GroundAtom,double[]> constraints;
	private final double minWidth;
	private final double constraintTol;
	
	/**
	 * Constructor
	 * @param model
	 * @param rvDB
	 * @param observedDB
	 * @param config
	 */
	public MaxPseudoLikelihoodDiscrete(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);

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
		constraints = new HashMap<GroundAtom,double[]>();
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
				double coef = 0.0;
				//StringBuilder constStr = new StringBuilder(rhs + " - (");
				for (Iterator<FunctionSummand> iter = ((FunctionSum) ft).iterator(); iter.hasNext();) {
					FunctionSummand term = iter.next();
					FunctionVariable var = (FunctionVariable) term.getTerm();
					if (atom.getVariable().equals(var)) {
						coef = term.getCoefficient();
					}
					else if (mb.containsKey(var)) {
						rhs -= mb.get(var) * term.getCoefficient();
						//constStr.append(" +" + term.getCoefficient() + "*" + mb.get(var));
					}
				}
				//constStr.append(" ) / " + coef);
				rhs /= coef;
				/* Update the bounds of integration */
				switch(ct.getComparator()) {
					case Equality:
						if (rhs < -constraintTol || rhs > (1+constraintTol))
							throw new IllegalStateException("Infeasible Equality constraint: RHS=" + rhs);
						min = Math.min(1.0, Math.max(0.0, rhs));
						max = Math.min(1.0, Math.max(0.0, rhs));
						break;
					case SmallerThan:
						if (coef < 0) {
							if (min < rhs)
								min = rhs;
							if (max < min - constraintTol)
								throw new IllegalStateException("Infeasible LessThan constraint: max < min - tol.");
							max = Math.max(min, max);
						}
						else {
							if (max > rhs)
								max = rhs;
							if (min > max + constraintTol)
								throw new IllegalStateException("Infeasible LessThan constraint: min > max + tol");
							min = Math.min(min, max);
						}
						break;
					case LargerThan:
						if (coef < 0) {
							if (max > rhs)
								max = rhs;
							if (min > max + constraintTol)
								throw new IllegalStateException("Infeasible LargerThan constraint: min > max + tol");
							min = Math.min(min, max);
						}
						else {
							if (min < rhs)
								min = rhs;
							if (max < min - constraintTol)
								throw new IllegalStateException("Infeasible LargerThan constraint: max < min - tol.");
							max = Math.max(min, max);
						}
						break;
				}
			}
			//System.out.println(atom.toString() + " min: " + min + " max: " + max);
			constraints.put(atom, new double[]{1,1});
		}
	}
	
	/**
	 * Computes the expected incompatibility using the pseudolikelihood.
	 * Since the values are assumed to be discrete, we can compute the
	 * expectation exactly.
	 */
	@Override
	protected double[] computeExpectedIncomp() {
		double[] expInc = new double[kernels.size()];
		
		/* Accumulate the marginals over all atoms */
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			RandomVariableAtom atom = e.getKey();
			/* Save original atom state */
			double originalValue = atom.getValue();
			/* Compute the incompatibility for each kernel containing atom */
			HashMap<CompatibilityKernel,double[]> incompatibilities = new HashMap<CompatibilityKernel,double[]>();
			for (GroundKernel gk : atom.getRegisteredGroundKernels()) {
				/* Only care about compatibility kernels */
				if (gk instanceof GroundCompatibilityKernel) {
					CompatibilityKernel k = (CompatibilityKernel) gk.getKernel();
					if (!incompatibilities.containsKey(k))
						incompatibilities.put(k, new double[2]);
					double[] inc = incompatibilities.get(k);
					/* False groundings */
					if (constraints.get(atom)[0] == 1) {
						atom.setValue(0.0);
						inc[0] += ((GroundCompatibilityKernel) gk).getIncompatibility();
					}
					/* True groundings */
					if (constraints.get(atom)[1] == 1) {
						atom.setValue(1.0);
						inc[1] += ((GroundCompatibilityKernel) gk).getIncompatibility();
					}
				}
			}
			/* Remember to return the atom to its original state! */
			atom.setValue(originalValue);
			/* Compute the exp incomp and accumulate the partition for the current atom. */
			HashMap<CompatibilityKernel,Double> expIncAtom = new HashMap<CompatibilityKernel,Double>();
			double Z = 0.0;
			for (int j = 0; j < 2; j++) {
				/* Check constraints */
				if (constraints.get(atom)[j] == 0)
					continue;
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
				/* Compute the exp incomp for current atom */
				for (Map.Entry<CompatibilityKernel,double[]> e2 : incompatibilities.entrySet()) {
					CompatibilityKernel k = e2.getKey();
					if (!expIncAtom.containsKey(k))
						expIncAtom.put(k, 0.0);
					double val = expIncAtom.get(k).doubleValue();
					val += exp * incompatibilities.get(k)[j];
					expIncAtom.put(k, val);
				}
			}
			/* Finally, we add to the exp incomp for each kernel */ 
			for (int i = 0; i < kernels.size(); i++) {
				CompatibilityKernel k = kernels.get(i);
				if (expIncAtom.containsKey(k))
					if (expIncAtom.get(k) > 0.0) 
						expInc[i] += expIncAtom.get(k) / Z;
			}
		}
	
		return expInc;
	}

}
