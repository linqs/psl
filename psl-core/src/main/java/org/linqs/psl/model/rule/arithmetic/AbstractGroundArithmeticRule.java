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
package org.linqs.psl.model.rule.arithmetic;

import java.util.HashSet;
import java.util.Set;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.function.FunctionComparator;

/**
 * Base class for all ground arithmetic rules.
 * 
 * @author Stephen Bach
 */
public class AbstractGroundArithmeticRule implements GroundRule {
	
	protected final AbstractArithmeticRule rule;
	protected final double[] coeffs;
	protected final GroundAtom[] atoms;
	protected final FunctionComparator comparator;
	protected final double c;
	
	protected AbstractGroundArithmeticRule(AbstractArithmeticRule rule,
			double[] coeffs, GroundAtom[] atoms, FunctionComparator comparator, double c) {
		this.rule = rule;
		this.coeffs = coeffs;
		this.atoms = atoms;
		this.comparator = comparator;
		this.c = c;
	}

	@Override
	public Rule getRule() {
		return rule;
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		Set<GroundAtom> atoms = new HashSet<GroundAtom>();
		for (GroundAtom atom : atoms)
			atoms.add(atom);
		return atoms;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < coeffs.length; i++) {
			sb.append(coeffs[i]);
			sb.append(" ");
			sb.append(atoms[i]);
			sb.append(" ");
		}
		
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
		sb.append(c);
		
		return sb.toString();
	}

}
