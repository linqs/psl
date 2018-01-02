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
package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.reasoner.function.FunctionSummand;

import java.util.List;

/**
 * An {@link AbstractGroundArithmeticRule} that is unweighted, i.e., it is a hard
 * constraint that must always hold.
 *
 * @author Stephen Bach
 */
public class UnweightedGroundArithmeticRule extends AbstractGroundArithmeticRule
		implements UnweightedGroundRule {

	protected UnweightedGroundArithmeticRule(UnweightedArithmeticRule rule, List<Double> coeffs,
			List<GroundAtom> atoms, FunctionComparator comparator, double c) {
		super(rule, coeffs, atoms, comparator, c);
	}

	protected UnweightedGroundArithmeticRule(UnweightedArithmeticRule rule, double[] coeffs,
			GroundAtom[] atoms, FunctionComparator comparator, double c) {
		super(rule, coeffs, atoms, comparator, c);
	}

	@Override
	public UnweightedRule getRule() {
		return (UnweightedRule) rule;
	}

	@Override
	public double getInfeasibility() {
		double sum = 0.0;
		for (int i = 0; i < coeffs.length; i++) {
			// Skip any special predicates.
			if (atoms[i].getPredicate() instanceof SpecialPredicate) {
				continue;
			}

			sum += coeffs[i] * atoms[i].getValue();
		}

		switch (comparator) {
		case Equality:
			return Math.abs(sum - c);
		case LargerThan:
			return -1 * Math.min(sum - c, 0);
		case SmallerThan:
			return Math.max(sum - c, 0);
		default:
			throw new IllegalStateException("Unrecognized comparator: " + comparator);
		}
	}

	@Override
	public ConstraintTerm getConstraintDefinition() {
		FunctionSum sum = new FunctionSum();
		for (int i = 0; i < coeffs.length; i++) {
			// Skip any special predicates.
			if (atoms[i].getPredicate() instanceof SpecialPredicate) {
				continue;
			}

			sum.add(new FunctionSummand(coeffs[i], atoms[i].getVariable()));
		}
		return new ConstraintTerm(sum, comparator, c);
	}

	@Override
	public String toString() {
		return super.toString() + " .";
	}

}
