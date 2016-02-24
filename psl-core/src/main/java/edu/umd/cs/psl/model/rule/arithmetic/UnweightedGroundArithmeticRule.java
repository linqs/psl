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
package edu.umd.cs.psl.model.rule.arithmetic;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.rule.UnweightedGroundRule;
import edu.umd.cs.psl.model.rule.UnweightedRule;
import edu.umd.cs.psl.model.rule.arithmetic.AbstractArithmeticRule.Comparator;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

/**
 * An {@link AbstractGroundArithmeticRule} that is unweighted, i.e., it is a hard
 * constraint that must always hold.
 * 
 * @author Stephen Bach
 */
public class UnweightedGroundArithmeticRule extends AbstractGroundArithmeticRule 
		implements UnweightedGroundRule {

	protected UnweightedGroundArithmeticRule(UnweightedArithmeticRule rule, double[] coeffs,
			GroundAtom[] atoms, Comparator comparator, double c) {
		super(rule, coeffs, atoms, comparator, c);
	}
	
	@Override
	public UnweightedRule getRule() {
		return (UnweightedRule) rule;
	}
	
	@Override
	public double getInfeasibility() {
		double sum = 0.0;
		for (int i = 0; i < coeffs.length; i++)
			sum += coeffs[i] * atoms[i].getValue();
		switch (comparator) {
		case EQUAL:
			return Math.abs(sum - c);
		case GREATER_EQUAL:
			return -1 * Math.min(sum - c, 0);
		case LESS_EQUAL:
			return Math.max(sum - c, 0);
		default:
			throw new IllegalStateException("Unrecognized comparator: " + comparator);
		}
	}

	@Override
	public ConstraintTerm getConstraintDefinition() {
		FunctionSum sum = new FunctionSum();
		for (int i = 0; i < coeffs.length; i++)
			sum.add(new FunctionSummand(coeffs[i], atoms[i].getVariable()));
		FunctionComparator fc;
		switch (comparator) {
		case EQUAL:
			fc = FunctionComparator.Equality;
			break;
		case GREATER_EQUAL:
			fc = FunctionComparator.LargerThan;
			break;
		case LESS_EQUAL:
			fc = FunctionComparator.SmallerThan;
			break;
		default:
			throw new IllegalStateException("Unrecognized comparator: " + comparator);
		}
		return new ConstraintTerm(sum, fc, c);
	}
	
	@Override
	public String toString() {
		return super.toString() + " ."; 
	}

}
