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
package org.linqs.psl.model.formula.traversal;

import java.util.ArrayList;
import java.util.List;

import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.AtomManager;
import org.linqs.psl.model.atom.VariableAssignment;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;

/**
 * FormulaGrounder is an object capable of traversing a {@link Formula},
 * replacing {@link Variable Variables} with values specified in the
 * {@link VariableAssignment} or {@link ResultList} passed to the constructor.
 */

public class FormulaGrounder extends AbstractFormulaTraverser {

	protected final AtomManager atommanger;
	protected final ArrayList<Formula> stack;
	protected final ResultList results;
	protected final VariableAssignment varAssign;

	protected int position;

	public FormulaGrounder(AtomManager m, ResultList res, VariableAssignment var) {
		assert m != null && res != null;
		atommanger = m;
		results = res;
		position = 0;
		varAssign = var;
		stack = new ArrayList<Formula>(5);
	}

	public FormulaGrounder(AtomManager m, ResultList res) {
		this(m, res, null);
	}

	public Formula ground(Formula f) {
		reset();
		AbstractFormulaTraverser.traverse(f, this);
		return pop();
	}

	public boolean hasNext() {
		return position < results.size();
	}

	public void next() {
		position++;
		reset();
	}

	public Constant getResultVariable(Variable var) {
		return results.get(position, var);
	}

	public void push(Formula f) {
		stack.add(f);
	}

	public Formula pop() {
		if (stack.isEmpty())
			throw new ArrayIndexOutOfBoundsException();
		return stack.remove(stack.size() - 1);
	}

	public void reset() {
		if (!stack.isEmpty())
			throw new IllegalStateException("Element on stack!");
	}

	@Override
	public void afterConjunction(int noFormulas) {
		Formula[] f = new Formula[noFormulas];
		for (int i = 0; i < noFormulas; i++)
			f[i] = pop();
		push(new Conjunction(f));
	}

	@Override
	public void afterDisjunction(int noFormulas) {
		Formula[] f = new Formula[noFormulas];
		for (int i = 0; i < noFormulas; i++)
			f[i] = pop();
		push(new Disjunction(f));
	}

	@Override
	public void afterNegation() {
		push(new Negation(pop()));
	}

	/**
	 * Visits an {@link org.linqs.psl.model.atom.Atom Atom} in the
	 * {@link Formula} and replaces all
	 * {@link org.linqs.psl.model.term.Variable Variables} in the
	 * {@link Atom Atom's} arguments with assignments from the
	 * {@link org.linqs.psl.model.atom.VariableAssignment VariableAssignment}
	 * or {@link org.linqs.psl.database.ResultList ResultsList} passed into the
	 * constructor, while preserving all
	 * {@link org.linqs.psl.model.term.Constant GroundTerms}. The new
	 * arguments are included in an {@link org.linqs.psl.model.atom.Atom Atom}
	 * that will be included in the ground
	 * {@link org.linqs.psl.model.formula.Formula Formula}
	 * 
	 * 
	 */
	@Override
	public void visitAtom(Atom atom) {
		Constant[] args = new Constant[atom.getPredicate().getArity()];
		Term[] atomArgs = atom.getArguments();
		assert args.length == atomArgs.length;
		for (int i = 0; i < args.length; i++) {
			if (atomArgs[i] instanceof Variable) {
				Variable v = (Variable) atomArgs[i];
				if (varAssign != null && varAssign.hasVariable(v)) {
					args[i] = varAssign.getVariable(v);
				} else {
					args[i] = results.get(position, v);
				}

			} else {
				assert atomArgs[i] instanceof Constant;
				args[i] = (Constant) atomArgs[i];
			}
		}
		push(atommanger.getAtom(atom.getPredicate(), args));
	}

	public static List<Formula> ground(Formula f, AtomManager atomManager,
			ResultList results) {
		List<Formula> formulas = new ArrayList<Formula>(results.size());
		FormulaGrounder grounder = new FormulaGrounder(atomManager, results);
		while (grounder.hasNext()) {
			AbstractFormulaTraverser.traverse(f, grounder);
			formulas.add(grounder.pop());
			grounder.next();
		}
		return formulas;
	}

}
