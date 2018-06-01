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
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.function.FunctionComparator;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for all ground arithmetic rules.
 *
 * @author Stephen Bach
 */
public abstract class AbstractGroundArithmeticRule implements GroundRule {
	protected final AbstractArithmeticRule rule;
	protected final double[] coeffs;
	protected final GroundAtom[] atoms;
	protected final FunctionComparator comparator;
	protected final double constant;

	protected AbstractGroundArithmeticRule(AbstractArithmeticRule rule,
			List<Double> coeffs, List<GroundAtom> atoms, FunctionComparator comparator, double constant) {
		this(rule, ArrayUtils.toPrimitive(coeffs.toArray(new Double[0])),
				atoms.toArray(new GroundAtom[0]), comparator, constant, false);
	}

	protected AbstractGroundArithmeticRule(AbstractArithmeticRule rule,
			double[] coeffs, GroundAtom[] atoms, FunctionComparator comparator, double constant) {
		this(rule, coeffs, atoms, comparator, constant, true);
	}

	protected AbstractGroundArithmeticRule(AbstractArithmeticRule rule,
			double[] coeffs, GroundAtom[] atoms, FunctionComparator comparator, double constant,
			boolean copy) {
		this.rule = rule;
		this.comparator = comparator;
		this.constant = constant;

		if (copy) {
			this.coeffs = Arrays.copyOf(coeffs, coeffs.length);
			this.atoms = Arrays.copyOf(atoms, atoms.length);
		} else {
			this.coeffs = coeffs;
			this.atoms = atoms;
		}
	}

	@Override
	public Rule getRule() {
		return rule;
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		Set<GroundAtom> atomSet = new HashSet<GroundAtom>();
		for (GroundAtom atom : atoms) {
			atomSet.add(atom);
		}
		return atomSet;
	}

	@Override
	public List<GroundRule> negate() {
		// TODO(eriq)
		throw new UnsupportedOperationException("Negating arithmetic rules not yet supported.");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		// If there are coefficients, print each one.
		if (coeffs.length > 0) {
			for (int i = 0; i < coeffs.length; i++) {
				if (i != 0) {
					sb.append(" + ");
				}

				sb.append(coeffs[i]);
				sb.append(" * ");
				sb.append(atoms[i]);
			}
		} else {
			// Otherwise, just put in a zero.
			sb.append("0.0");
		}

		sb.append(" ");

		switch (comparator) {
		case Equality:
			sb.append("=");
			break;
		case LargerThan:
			sb.append(">=");
			break;
		case SmallerThan:
			sb.append("<=");
			break;
		default:
			throw new IllegalStateException("Unrecognized comparator: " + comparator);

		}

		sb.append(" ");
		sb.append(constant);

		return sb.toString();
	}

	public double[] getCoefficients() {
		return coeffs;
	}

	public GroundAtom[] getOrderedAtoms() {
		return atoms;
	}

	public FunctionComparator getComparator() {
		return comparator;
	}

	public double getConstant() {
		return constant;
	}
}
