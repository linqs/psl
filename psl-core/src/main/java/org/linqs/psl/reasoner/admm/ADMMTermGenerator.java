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
package org.linqs.psl.reasoner.admm;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.admm.HingeLossTerm;
import org.linqs.psl.reasoner.admm.LinearLossTerm;
import org.linqs.psl.reasoner.admm.SquaredHingeLossTerm;
import org.linqs.psl.reasoner.admm.SquaredLinearLossTerm;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;
import org.linqs.psl.reasoner.function.ConstantNumber;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionSingleton;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.reasoner.function.FunctionSummand;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.MaxFunction;
import org.linqs.psl.reasoner.function.PowerOfTwo;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A TermGenerator for ADMM objective terms.
 */
public class ADMMTermGenerator implements TermGenerator<ADMMObjectiveTerm> {
	private ADMMReasoner reasoner;

	public ADMMTermGenerator(ADMMReasoner reasoner) {
		this.reasoner = reasoner;
	}

	public void generateTerms(GroundRuleStore ruleStore, TermStore<ADMMObjectiveTerm> termStore) {
		for (GroundRule groundRule : ruleStore.getGroundRules()) {
			ADMMObjectiveTerm term = createTerm(groundRule);
			if (term.x.length > 0) {
				termStore.add(term);
			}
		}
	}

	/**
	 * Processes a {@link GroundRule} to create a corresponding
	 * {@link ADMMObjectiveTerm}
	 *
	 * @param groundRule  the GroundRule to be added to the ADMM objective
	 * @return  the created ADMMObjectiveTerm
	 */
	private ADMMObjectiveTerm createTerm(GroundRule groundRule) {
		boolean squared;
		FunctionTerm function, innerFunction, zeroTerm, innerFunctionA, innerFunctionB;
		ADMMObjectiveTerm term;

		if (groundRule instanceof WeightedGroundRule) {
			function = ((WeightedGroundRule)groundRule).getFunctionDefinition();

			/* Checks if the function is wrapped in a PowerOfTwo */
			if (function instanceof PowerOfTwo) {
				squared = true;
				function = ((PowerOfTwo)function).getInnerFunction();
			} else {
				squared = false;
			}

			/*
			 * If the FunctionTerm is a MaxFunction, ensures that it has two arguments, a linear
			 * function and zero, and constructs the objective term (a hinge loss)
			 */
			if (function instanceof MaxFunction) {
				if (((MaxFunction)function).size() != 2) {
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");
				}

				innerFunction = null;
				zeroTerm = null;
				innerFunctionA = ((MaxFunction)function).get(0);
				innerFunctionB = ((MaxFunction)function).get(1);

				if (innerFunctionA instanceof ConstantNumber && innerFunctionA.getValue() == 0.0) {
					zeroTerm = innerFunctionA;
					innerFunction = innerFunctionB;
				} else if (innerFunctionB instanceof ConstantNumber && innerFunctionB.getValue() == 0.0) {
					zeroTerm = innerFunctionB;
					innerFunction = innerFunctionA;
				}

				if (zeroTerm == null) {
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");
				}

				if (innerFunction instanceof FunctionSum) {
					Hyperplane hp = processHyperplane((FunctionSum) innerFunction);
					if (squared) {
						term = new SquaredHingeLossTerm(reasoner, hp.zIndices, hp.coeffs, hp.constant,
								((WeightedGroundRule)groundRule).getWeight().getWeight());
					} else {
						term = new HingeLossTerm(reasoner, hp.zIndices, hp.coeffs, hp.constant,
								((WeightedGroundRule)groundRule).getWeight().getWeight());
					}
				} else {
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");
				}
			/* Else, if it's a FunctionSum, constructs the objective term (a linear loss) */
			} else if (function instanceof FunctionSum) {
				Hyperplane hp = processHyperplane((FunctionSum) function);
				if (squared) {
					term = new SquaredLinearLossTerm(reasoner, hp.zIndices, hp.coeffs, 0.0,
							((WeightedGroundRule)groundRule).getWeight().getWeight());
				} else {
					term = new LinearLossTerm(reasoner, hp.zIndices, hp.coeffs,
							((WeightedGroundRule)groundRule).getWeight().getWeight());
				}
			} else {
				throw new IllegalArgumentException("Unrecognized function: " + ((WeightedGroundRule) groundRule).getFunctionDefinition());
			}
		} else if (groundRule instanceof UnweightedGroundRule) {
			ConstraintTerm constraint = ((UnweightedGroundRule)groundRule).getConstraintDefinition();
			function = constraint.getFunction();
			if (function instanceof FunctionSum) {
				Hyperplane hp = processHyperplane((FunctionSum)function);
				term = new LinearConstraintTerm(reasoner, hp.zIndices, hp.coeffs,
						constraint.getValue() + hp.constant, constraint.getComparator());
			} else {
				throw new IllegalArgumentException("Unrecognized constraint: " + constraint);
			}
		} else {
			throw new IllegalArgumentException("Unsupported ground rule: " + groundRule);
		}

		return term;
	}

	private Hyperplane processHyperplane(FunctionSum sum) {
		Hyperplane hp = new Hyperplane();
		Map<AtomFunctionVariable, Integer> localVarLocations = new HashMap<AtomFunctionVariable, Integer>();
		List<Integer> tempZIndices = new ArrayList<Integer>(sum.size());
		List<Double> tempCoeffs = new ArrayList<Double>(sum.size());

		for (FunctionSummand summand : sum) {
			FunctionSingleton singleton = summand.getTerm();
			if (singleton instanceof AtomFunctionVariable && !singleton.isConstant()) {
				// If this variable has been encountered before in any hyperplane.
				int zIndex = reasoner.getConsensusIndex((AtomFunctionVariable)singleton);
				if (zIndex != -1) {
					// Checks if the variable has already been encountered in THIS hyperplane.
					Integer localIndex = localVarLocations.get(singleton);
					// If it has, just adds the coefficient.
					if (localIndex != null) {
						tempCoeffs.set(localIndex, tempCoeffs.get(localIndex) + summand.getCoefficient());
					// Else, creates a new local variable.
					} else {
						tempZIndices.add(zIndex);
						tempCoeffs.add(summand.getCoefficient());
						localVarLocations.put((AtomFunctionVariable)singleton, tempZIndices.size() - 1);

						reasoner.addLocalVariable();
					}
				// Else, creates a new global variable and a local variable.
				} else {
					// Create the global variable.
					zIndex = reasoner.addGlobalVariable((AtomFunctionVariable)singleton);

					// Creates the local variable.
					tempZIndices.add(zIndex);
					tempCoeffs.add(summand.getCoefficient());
					localVarLocations.put((AtomFunctionVariable)singleton, tempZIndices.size() - 1);
				}
			} else if (singleton.isConstant()) {
				// Subtracts because hyperplane is stored as coeffs^T * x = constant.
				hp.constant -= summand.getValue();
			} else {
				throw new IllegalArgumentException("Unexpected summand.");
			}
		}

		hp.zIndices = new int[tempZIndices.size()];
		hp.coeffs = new double[tempCoeffs.size()];

		for (int i = 0; i < tempZIndices.size(); i++) {
			hp.zIndices[i] = tempZIndices.get(i);
			hp.coeffs[i] = tempCoeffs.get(i);
		}

		return hp;
	}

	private static class Hyperplane {
		public int[] zIndices;
		public double[] coeffs;
		public double constant;
	}

}
