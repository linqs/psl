/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.application.groundrulestore.AtomRegisterGroundRuleStore;
import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.arithmetic.UnweightedGroundArithmeticRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.util.MathUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This is a hacky class to prepare a constraint blocker in our reasoner framework.
 * Typically we have terms that come from ground rules, but here we have structures
 * that are build up around the entire collection of ground rules.
 */
public class ConstraintBlockerTermGenerator implements TermGenerator<Term> {
	@Override
	public void generateTerms(GroundRuleStore ruleStore, TermStore<Term> termStore) {
		if (!(ruleStore instanceof AtomRegisterGroundRuleStore)) {
			throw new IllegalArgumentException("AtomRegisterGroundRuleStore required.");
		}

		if (!(termStore instanceof ConstraintBlockerTermStore)) {
			throw new IllegalArgumentException("ConstraintBlockerTermStore required.");
		}

		generateTermsInternal((AtomRegisterGroundRuleStore)ruleStore, (ConstraintBlockerTermStore)termStore);
	}

	@Override
	public void updateWeights(GroundRuleStore ruleStore, TermStore<Term> termStore) {
		// TODO(eriq): Since we don't keep internal repsentations of the weights, I don't think we need to do anything.
	}

	private void generateTermsInternal(AtomRegisterGroundRuleStore ruleStore, ConstraintBlockerTermStore termStore) {
		// Collects constraints.
		Set<UnweightedGroundArithmeticRule> constraintSet = new HashSet<UnweightedGroundArithmeticRule>();
		Map<RandomVariableAtom, GroundValueConstraint> valueConstraintMap = new HashMap<RandomVariableAtom, GroundValueConstraint>();
		buildConstraints(ruleStore, constraintSet, valueConstraintMap);

		Set<RandomVariableAtom> freeRVSet = buildFreeRVSet(ruleStore);

		Map<RandomVariableAtom, Integer> rvMap = new HashMap<RandomVariableAtom, Integer>();

		// Put RandomVariableAtoms in 2d array by block.
		RandomVariableAtom[][] rvBlocks = new RandomVariableAtom[constraintSet.size() + freeRVSet.size()][];

		// If true, exactly one Atom in the RV block must be 1.0. If false, at most one can.
		boolean[] exactlyOne = new boolean[rvBlocks.length];

		// False means that an ObservedAtom or constrained RandomVariableAtom
		// is 1.0, forcing others to 0.0
		boolean varsAreFree;

		// Index of the current block we are working with.
		int blockIndex = 0;

		// RVAs constrained by each functional constraint.
		Set<RandomVariableAtom> constrainedRVSet = new HashSet<RandomVariableAtom>();

		// Process constrained RVs first.
		for (UnweightedGroundArithmeticRule con : constraintSet) {
			constrainedRVSet.clear();
			varsAreFree = true;

			for (GroundAtom atom : con.getAtoms()) {
				if (atom instanceof ObservedAtom && atom.getValue() != 0.0) {
					varsAreFree = false;
				} else if (atom instanceof RandomVariableAtom) {
					GroundValueConstraint valueCon = valueConstraintMap.get(atom);
					if (valueCon != null) {
						if (valueCon.getConstraintDefinition().getValue() != 0.0) {
							varsAreFree = false;
						}
					} else {
						constrainedRVSet.add((RandomVariableAtom)atom);
					}
				}
			}

			if (varsAreFree) {
				rvBlocks[blockIndex] = new RandomVariableAtom[constrainedRVSet.size()];
				int j = 0;
				for (RandomVariableAtom atom : constrainedRVSet) {
					rvBlocks[blockIndex][j++] = atom;
					rvMap.put(atom, blockIndex);
				}

				exactlyOne[blockIndex] = con.getConstraintDefinition().getComparator().equals(FunctionComparator.Equality) || constrainedRVSet.size() == 0;
			} else {
				rvBlocks[blockIndex] = new RandomVariableAtom[0];
				// Sets to true regardless of constraint type to avoid extra processing steps
				// that would not work on empty blocks
				exactlyOne[blockIndex] = true;

				// Set all the RVs in this block to 0.0 since there is a observed/constrained value.
				for (RandomVariableAtom atom : constrainedRVSet) {
					atom.setValue(0.0);
				}
			}

			blockIndex++;
		}

		// Processes free RVs second.
		for (RandomVariableAtom atom : freeRVSet) {
			rvBlocks[blockIndex] = new RandomVariableAtom[] {atom};
			exactlyOne[blockIndex] = false;
			rvMap.put(atom, blockIndex);
			blockIndex++;
		}

		// Collects WeightedGroundRules incident on each block of RandomVariableAtoms.
		WeightedGroundRule[][] incidentGRs = collectIncidentWeightedGroundRules(ruleStore, rvBlocks);

		// Sets all value-constrained atoms.
		for (Map.Entry<RandomVariableAtom, GroundValueConstraint> e : valueConstraintMap.entrySet()) {
			e.getKey().setValue(e.getValue().getConstraintDefinition().getValue());
		}

		termStore.init(ruleStore, rvBlocks, incidentGRs, exactlyOne, rvMap);
	}

	private WeightedGroundRule[][] collectIncidentWeightedGroundRules(
			AtomRegisterGroundRuleStore ruleStore, RandomVariableAtom[][] rvBlocks) {
		WeightedGroundRule[][] incidentGRs = new WeightedGroundRule[rvBlocks.length][];

		Set<WeightedGroundRule> incidentGKSet = new HashSet<WeightedGroundRule>();
		for (int blockIndex = 0; blockIndex < rvBlocks.length; blockIndex++) {
			incidentGKSet.clear();
			for (RandomVariableAtom atom : rvBlocks[blockIndex]) {
				for (GroundRule incidentGK : ruleStore.getRegisteredGroundRules(atom)) {
					if (incidentGK instanceof WeightedGroundRule) {
						incidentGKSet.add((WeightedGroundRule) incidentGK);
					}
				}
			}

			incidentGRs[blockIndex] = new WeightedGroundRule[incidentGKSet.size()];
			int j = 0;
			for (WeightedGroundRule incidentGK : incidentGKSet) {
				incidentGRs[blockIndex][j++] = incidentGK;
			}
		}

		return incidentGRs;
	}

	private Set<RandomVariableAtom> buildFreeRVSet(AtomRegisterGroundRuleStore ruleStore) {
		// Collects the free RandomVariableAtoms that remain.
		Set<RandomVariableAtom> freeRVSet = new HashSet<RandomVariableAtom>();
		for (GroundRule groundRule : ruleStore.getGroundRules()) {
			for (GroundAtom atom : groundRule.getAtoms()) {
				if (!(atom instanceof RandomVariableAtom)) {
					continue;
				}

				int numDRConstraints = 0;
				int numValueConstraints = 0;

				for (GroundRule incidentGR : ruleStore.getRegisteredGroundRules(atom)) {
					if (incidentGR instanceof UnweightedGroundArithmeticRule) {
						numDRConstraints++;
					} else if (incidentGR instanceof GroundValueConstraint) {
						numValueConstraints++;
					}
				}

				if (numDRConstraints == 0 && numValueConstraints == 0) {
					freeRVSet.add(((RandomVariableAtom) atom));
				} else if (numDRConstraints >= 2 || numValueConstraints >= 2) {
					throw new IllegalStateException(
							"RandomVariableAtoms may only participate in one (at-least) 1-of-k" +
							" and/or GroundValueConstraint.");
				}
			}
		}

		return freeRVSet;
	}

	private void buildConstraints(GroundRuleStore ruleStore,
			Set<UnweightedGroundArithmeticRule> constraintSet, Map<RandomVariableAtom, GroundValueConstraint> valueConstraintMap) {
		for (UnweightedGroundRule groundRule : ruleStore.getConstraintRules()) {
			if (groundRule instanceof GroundValueConstraint) {
				valueConstraintMap.put(((GroundValueConstraint)groundRule).getAtom(), (GroundValueConstraint)groundRule);
				continue;
			}

			if (!(groundRule instanceof UnweightedGroundArithmeticRule)) {
				throw new IllegalStateException(
						"Unsupported ground rule: [" + groundRule + "]." +
						" Only categorical (functional) arithmetic constraints are supported.");
			}

			// If the ground rule is an UnweightedGroundArithmeticRule, checks if it
			// is a categorical, i.e., at-least-1-of-k (partial functional) or 1-of-k (functional), constraint.
			UnweightedGroundArithmeticRule gar = (UnweightedGroundArithmeticRule)groundRule;
			boolean categorical = true;

			FunctionComparator comparator = gar.getConstraintDefinition().getComparator();
			double rhsValue = gar.getConstraintDefinition().getValue();

			if (!(
					// Foo(A, +B) = 1.0 .
					(comparator == FunctionComparator.Equality && MathUtils.equals(rhsValue, 1.0))
					// Foo(A, +B) <= 1.0 .
					|| (comparator == FunctionComparator.SmallerThan && MathUtils.equals(rhsValue, 1.0))
					// -Foo(A, +B) >= -1.0 .
					|| (comparator == FunctionComparator.LargerThan && MathUtils.equals(rhsValue, -1.0)))) {
				categorical = false;
			} else if (gar.getConstraintDefinition().getFunction() instanceof FunctionSum) {
				FunctionSum sum = (FunctionSum) gar.getConstraintDefinition().getFunction();
				for (int i = 0; i < sum.size(); i++) {
					if (Math.abs(sum.get(i).getCoefficient() - gar.getConstraintDefinition().getValue()) > 1e-8) {
						categorical = false;
						break;
					}
				}
			} else {
				categorical = false;
			}

			if (!categorical) {
				throw new IllegalStateException(
						"Unsupported ground rule: [" + groundRule + "]." +
						" The only supported constraints are 1-of-k constraints" +
						" and at-least-1-of-k constraints and value constraints.");
			}

			constraintSet.add(gar);
		}
	}
}
