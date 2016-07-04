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
package edu.umd.cs.psl.model.rule.arithmetic.expression;

import java.util.Collections;
import java.util.List;

import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

/**
 * Container for components of an arithmetic rule formula.
 * 
 * @author Stephen Bach
 */
public class ArithmeticRuleExpression {
	private final List<Coefficient> coeffs;
	private final List<SummationAtomOrAtom> atoms;
	private final FunctionComparator comparator;
	private final Coefficient c;

	public ArithmeticRuleExpression(List<Coefficient> coeffs, List<SummationAtomOrAtom> atoms,
			FunctionComparator comparator, Coefficient c) {
		this.coeffs = Collections.unmodifiableList(coeffs);
		this.atoms = Collections.unmodifiableList(atoms);
		this.comparator = comparator;
		this.c = c;
	}
	
	public List<Coefficient> getAtomCoefficients() {
		return coeffs;
	}
	
	public List<SummationAtomOrAtom> getAtoms() {
		return atoms;
	}
	
	public FunctionComparator getComparator() {
		return comparator;
	}
	
	public Coefficient getFinalCoefficient() {
		return c;
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < coeffs.size(); i++) {
			s.append(coeffs.get(i));
			s.append(" * ");
			s.append(coeffs.get(i));
		}
		s.append(" ");
		s.append(comparator);
		s.append(" ");
		s.append(c);
		return s.toString();
	}

}
