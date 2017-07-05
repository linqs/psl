/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.rule.arithmetic.expression;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

/**
 * Container for components of an arithmetic rule formula.
 *
 * @author Stephen Bach
 */
public class ArithmeticRuleExpression {
	protected final List<Coefficient> coeffs;
	protected final List<SummationAtomOrAtom> atoms;
	protected final FunctionComparator comparator;
	protected final Coefficient c;
	protected final Set<Variable> vars;
	protected final Set<SummationVariable> sumVars;

	public ArithmeticRuleExpression(List<Coefficient> coeffs, List<SummationAtomOrAtom> atoms,
			FunctionComparator comparator, Coefficient c) {
		this.coeffs = Collections.unmodifiableList(coeffs);
		this.atoms = Collections.unmodifiableList(atoms);
		this.comparator = comparator;
		this.c = c;

		Set<Variable> vars = new HashSet<Variable>();
		Set<SummationVariable> sumVars = new HashSet<SummationVariable>();
		Set<String> sumVarNames = new HashSet<String>();

		for (SummationAtomOrAtom saoa : getAtoms()) {
			if (saoa instanceof SummationAtom) {
				for (SummationVariableOrTerm svot : ((SummationAtom) saoa).getArguments()) {
					if (svot instanceof Variable) {
						vars.add((Variable) svot);
					} else if (svot instanceof SummationVariable) {
						if (sumVars.contains((SummationVariable) svot)) {
							throw new IllegalArgumentException(
									"Each summation variable in an ArithmeticRuleExpression must be unique.");
						}

						sumVars.add((SummationVariable) svot);
						sumVarNames.add(((SummationVariable)svot).getVariable().getName());
					}
				}
			} else {
				for (Term term : ((Atom) saoa).getArguments()) {
					if (term instanceof Variable) {
						vars.add((Variable) term);
					}
				}
			}
		}

		// Check for summation variables used as terms.
		for (Variable var : vars) {
			if (sumVarNames.contains(var.getName())) {
				throw new IllegalArgumentException(String.format(
						"Summation variable (+%s) cannot be used as a normal variable (%s).",
						var.getName(), var.getName()));
			}
		}

		// Check for cardinality being used on non-summation variables.
		for (Coefficient coefficient : coeffs) {
			if (coefficient instanceof Cardinality) {
				String name = ((Cardinality)coefficient).getSummationVariable().getVariable().getName();
				if (!sumVarNames.contains(name)) {
					throw new IllegalArgumentException(String.format(
							"Cannot use variable (%s) in cardinality. " +
							"Only summation variables can be used in cardinality.",
							name));
				}
			}
		}

		this.vars = Collections.unmodifiableSet(vars);
		this.sumVars = Collections.unmodifiableSet(sumVars);
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

	public Set<Variable> getVariables() {
		return vars;
	}

	public Set<SummationVariable> getSummationVariables() {
		return sumVars;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();

		// If there are coefficients, print each one.
		if (coeffs.size() > 0) {
			for (int i = 0; i < coeffs.size(); i++) {
				if (i != 0) {
					s.append(" + ");
				}

				s.append(coeffs.get(i));
				s.append(" * ");
				s.append(atoms.get(i));
			}
		} else {
			// Otherwise, just put in a zero.
			s.append("0.0");
		}

		s.append(" ");
		s.append(comparator);
		s.append(" ");
		s.append(c);
		return s.toString();
	}

}
