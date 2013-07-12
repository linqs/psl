/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.model.formula.traversal;

import cern.colt.list.tdouble.AbstractDoubleList;
import cern.colt.list.tdouble.DoubleArrayList;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.formula.*;

public class FormulaEvaluator extends AbstractFormulaTraverser {
	
	public final static FormulaEvaluator LUKASIEWICZ = new FormulaEvaluator(Tnorm.LUKASIEWICZ);
	public final static FormulaEvaluator GOEDEL = new FormulaEvaluator(Tnorm.GOEDEL);
	public final static FormulaEvaluator PRODUCT = new FormulaEvaluator(Tnorm.PRODUCT);

	private final AbstractDoubleList stack;
	private final Tnorm tnorm;
	
	public FormulaEvaluator(Tnorm t) {
		tnorm = t;
		stack = new DoubleArrayList();
	}
	
	public double getTruthValue(Formula f) {
		reset();
		AbstractFormulaTraverser.traverse(f, this);
		return pop();
	}
	
	public void push(double val) {
		stack.add(val);
	}
	
	public double pop() {
		if (stack.isEmpty()) throw new ArrayIndexOutOfBoundsException();
		double val = stack.get(stack.size()-1);
		stack.remove(stack.size()-1);
		return val;
	}
	
	public void reset() {
		if (!stack.isEmpty()) throw new IllegalStateException("Value on stack!");
	}
	
	@Override
	public void afterConjunction(int noFormulas) {
		double v=pop();
		for (int i=1;i<noFormulas;i++)
			v = tnorm.conjunction(v, pop());
		push(v);
	}

	@Override
	public void afterDisjunction(int noFormulas) {
		double v=pop();
		for (int i=1;i<noFormulas;i++)
			v = tnorm.disjunction(v, pop());
		push(v);
	}


	@Override
	public void afterNegation() {
		push(tnorm.negation(pop()));
	}

	
	@Override
	public void visitAtom(Atom atom) {
		if (atom instanceof GroundAtom)
			push(((GroundAtom) atom).getValue());
		else
			throw new IllegalArgumentException("Atom is not ground.");
	}
	
}
