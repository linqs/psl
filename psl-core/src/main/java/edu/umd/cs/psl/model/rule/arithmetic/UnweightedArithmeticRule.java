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

import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.rule.UnweightedRule;
import edu.umd.cs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariable;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

/**
 * A template for {@link UnweightedGroundArithmeticRule UnweightedGroundArithmeticRules}.
 * 
 * @author Stephen Bach
 */
public class UnweightedArithmeticRule extends AbstractArithmeticRule
		implements UnweightedRule {

	public UnweightedArithmeticRule(ArithmeticRuleExpression expression) {
		this(expression, new HashMap<SummationVariable, Formula>());
	}
	
	public UnweightedArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> selectStatements) {
		super(expression, selectStatements);
	}

	@Override
	protected UnweightedGroundArithmeticRule makeGroundRule(double[] coeffs, GroundAtom[] atoms,
			FunctionComparator comparator, double c) {
		return new UnweightedGroundArithmeticRule(this, coeffs, atoms, comparator, c);
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("{constraint}: ");
		s.append(expression);
		for (Map.Entry<SummationVariable, Formula> e : selects.entrySet()) {
			s.append("\n{");
			// Appends the corresponding Variable, not the SummationVariable, to leave out the '+'
			s.append(e.getKey().getVariable());
			s.append(" : ");
			s.append(e.getValue());
			s.append("}");
		}
		return s.toString();
	}

}
