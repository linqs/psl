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
package org.linqs.psl.model.formula.traversal;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Negation;

/**
 * Implements the depth-first traversal of a formula, but performs no actions
 * during traversal.
 * <p>
 * Derived classes can extend this class to implement desired outcomes from traversal.
 * <p>
 * Sub-Formulas contained in a {@link Conjunction} or a {@link Disjunction} are
 * traversed in order from lowest index to highest.
 * 
 * @author Matthias Broecheler
 */
public abstract class AbstractFormulaTraverser implements FormulaTraverser {
	
	public static<V extends FormulaTraverser> V traverse(Formula f, V traverser) {
		recursiveTraverse(f,traverser);		
		return traverser;
	}
	
	private static void recursiveTraverse(Formula f, FormulaTraverser traverser) {
		if (f instanceof Conjunction) {
			if (!traverser.beforeConjunction()) return;
			Conjunction c = (Conjunction)f;
			for (int i=0;i<c.length();i++) {
				recursiveTraverse(c.get(i),traverser);
			}
			traverser.afterConjunction(c.length());
		} else if (f instanceof Disjunction) {
			if (!traverser.beforeDisjunction()) return;
			Disjunction d = (Disjunction)f;
			for (int i=0;i<d.length();i++) {
				recursiveTraverse(d.get(i),traverser);
			}
			traverser.afterDisjunction(d.length());
		} else if (f instanceof Negation) {
			if (!traverser.beforeNegation()) return;
			recursiveTraverse(((Negation)f).getFormula(),traverser);
			traverser.afterNegation();
		} else if (f instanceof Atom) {
			traverser.visitAtom((Atom)f);
		} else throw new IllegalArgumentException("Unsupported Formula :" + f);
	}

	@Override
	public void afterConjunction(int noFormulas) {
	}

	@Override
	public void afterDisjunction(int noFormulas) {
	}

	@Override
	public void afterNegation() {
	}

	@Override
	public boolean beforeConjunction() {
		return true;
	}

	@Override
	public boolean beforeDisjunction() {
		return true;
	}

	@Override
	public boolean beforeNegation() {
		return true;
	}

	@Override
	public void visitAtom(Atom atom) {
		// Do nothing		
	}
	
}
