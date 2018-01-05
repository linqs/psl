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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
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
import java.util.List;

/**
 * A TermGenerator for ADMM objective terms.
 */
public class ADMMTermGenerator implements TermGenerator<ADMMObjectiveTerm> {
	public ADMMTermGenerator() {}
	public ADMMTermGenerator(ConfigBundle config) {}

	@Override
	public void generateTerms(GroundRuleStore ruleStore, TermStore<ADMMObjectiveTerm> termStore) {
		if (!(termStore instanceof ADMMTermStore)) {
			throw new IllegalArgumentException("ADMMTermGenerator requires an ADMMTermStore");
		}

		for (GroundRule groundRule : ruleStore.getGroundRules()) {
			ADMMObjectiveTerm term = createTerm(groundRule, (ADMMTermStore)termStore);
			if (term.variables.size() > 0) {
				termStore.add(groundRule, term);
			}
		}
	}

	@Override
	public void updateWeights(GroundRuleStore ruleStore, TermStore<ADMMObjectiveTerm> termStore) {
		for (GroundRule groundRule : ruleStore.getGroundRules()) {
			if (groundRule instanceof WeightedGroundRule) {
				termStore.updateWeight((WeightedGroundRule)groundRule);
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
	private ADMMObjectiveTerm createTerm(GroundRule groundRule, ADMMTermStore termStore) {
		ADMMObjectiveTerm term;

		if (groundRule instanceof WeightedGroundRule) {
			boolean squared;
			float weight = (float)((WeightedGroundRule)groundRule).getWeight();
			FunctionTerm function = ((WeightedGroundRule)groundRule).getFunctionDefinition();

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

				FunctionTerm innerFunction = null;
				FunctionTerm zeroTerm = null;

				FunctionTerm innerFunction0 = ((MaxFunction)function).get(0);
				FunctionTerm innerFunction1 = ((MaxFunction)function).get(1);

				if (innerFunction0 instanceof ConstantNumber && innerFunction0.getValue() == 0.0) {
					zeroTerm = innerFunction0;
					innerFunction = innerFunction1;
				} else if (innerFunction1 instanceof ConstantNumber && innerFunction1.getValue() == 0.0) {
					zeroTerm = innerFunction1;
					innerFunction = innerFunction0;
				}

				if (zeroTerm == null) {
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");
				}

				if (innerFunction instanceof FunctionSum) {
					Hyperplane hyperplane = processHyperplane((FunctionSum) innerFunction, termStore);
					if (squared) {
						term = new SquaredHingeLossTerm(hyperplane.variables, hyperplane.coeffs, hyperplane.constant, weight);
					} else {
						term = new HingeLossTerm(hyperplane.variables, hyperplane.coeffs, hyperplane.constant, weight);
					}
				} else {
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");
				}
			/* Else, if it's a FunctionSum, constructs the objective term (a linear loss) */
			} else if (function instanceof FunctionSum) {
				Hyperplane hyperplane = processHyperplane((FunctionSum) function, termStore);
				if (squared) {
					term = new SquaredLinearLossTerm(hyperplane.variables, hyperplane.coeffs, 0.0f, weight);
				} else {
					term = new LinearLossTerm(hyperplane.variables, hyperplane.coeffs, weight);
				}
			} else {
				throw new IllegalArgumentException("Unrecognized function: " + function);
			}
		} else if (groundRule instanceof UnweightedGroundRule) {
			ConstraintTerm constraint = ((UnweightedGroundRule)groundRule).getConstraintDefinition();
			FunctionTerm function = constraint.getFunction();
			if (function instanceof FunctionSum) {
				Hyperplane hyperplane = processHyperplane((FunctionSum)function, termStore);
				term = new LinearConstraintTerm(hyperplane.variables, hyperplane.coeffs,
						(float)(constraint.getValue() + hyperplane.constant), constraint.getComparator());
			} else {
				throw new IllegalArgumentException("Unrecognized constraint: " + constraint);
			}
		} else {
			throw new IllegalArgumentException("Unsupported ground rule: " + groundRule);
		}

		return term;
	}

	private Hyperplane processHyperplane(FunctionSum sum, ADMMTermStore termStore) {
		Hyperplane hyperplane = new Hyperplane();

		for (FunctionSummand summand : sum) {
			FunctionSingleton singleton = summand.getTerm();

			if (singleton instanceof AtomFunctionVariable && !singleton.isConstant()) {
				LocalVariable variable = termStore.createLocalVariable((AtomFunctionVariable)singleton);

				// Check to see if we have seen this variable before in this hyperplane.
				// Note that we checking for existance in a List (O(n)), but there are usually a small number of
				// variables per hyperplane.
				int localIndex = hyperplane.variables.indexOf(variable);
				if (localIndex != -1) {
					// If it has, just adds the coefficient.
					hyperplane.coeffs.set(localIndex, new Float(hyperplane.coeffs.get(localIndex) + summand.getCoefficient()));
				} else {
					hyperplane.variables.add(variable);
					hyperplane.coeffs.add(new Float(summand.getCoefficient()));
				}
			} else if (singleton.isConstant()) {
				// Subtracts because hyperplane is stored as coeffs^T * x = constant.
				hyperplane.constant -= summand.getValue();
			} else {
				throw new IllegalArgumentException("Unexpected summand.");
			}
		}

		return hyperplane;
	}

	private static class Hyperplane {
		public List<LocalVariable> variables;
		public List<Float> coeffs;
		public float constant;

		public Hyperplane() {
			variables = new ArrayList<LocalVariable>();
			coeffs = new ArrayList<Float>();
			constant = 0.0f;
		}
	}

}
