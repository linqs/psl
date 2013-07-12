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
package edu.umd.cs.psl.database;

import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.FormulaAnalysis;
import edu.umd.cs.psl.model.formula.traversal.AbstractFormulaTraverser;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.util.collection.HashList;

/**
 * A query to select groundings from a {@link Database}.
 * <p>
 * Groundings that match the query are returned in the form of a {@link ResultList}.
 * 
 * <h2>Semantics</h2>
 * 
 * A DatabaseQuery has three components: a Formula, a partial grounding,
 * and a set of {@link Variable Variables} onto which the results will be
 * projected.
 * <p>
 * The Formula is given upon initialization and is fixed. It must be
 * a {@link Conjunction} of Atoms or a single Atom. Any {@link Variable}
 * in the Formula must be used in an Atom with a {@link StandardPredicate}.
 * (Then it can be used in others as well.)
 * The query will return any grounding such that each GroundAtom
 * with a {@link StandardPredicate} in the ground Formula is persisted in the
 * Database and each GroundAtom with a {@link FunctionalPredicate}
 * in the ground Formula has a non-zero truth value (regardless of whether
 * it is instantiated in memory).
 * <p>
 * The partial grounding is a {@link VariableAssignment} which all returned
 * groundings must match. Use {@link #getPartialGrounding()} to modify the partial
 * grounding. It is initially empty.
 * <p>
 * The projection subset is a subset of the Variables in the Formula onto
 * which the returned groundings will be projected. An empty subset is
 * the same as including all Variables in the Formula in the subset except those
 * with assignments in the partial grounding. Use {@link #getProjectionSubset()}
 * to modify the subset. It is initially empty.
 */
public class DatabaseQuery {

	private final Formula formula;
	private final VariableAssignment partialGrounding;
	private final Set<Variable> projectTo;
	private final HashList<Variable> ordering;
	
	public DatabaseQuery(Formula formula) {
		this.formula = formula;
		partialGrounding = new VariableAssignment();
		projectTo = new HashSet<Variable>();
		
		FormulaAnalysis analysis = new FormulaAnalysis(formula);
		if (analysis.getNumDNFClauses() > 1 || analysis.getDNFClause(0).getNegLiterals().size() > 0)
			throw new IllegalArgumentException("Illegal query formula. " +
					"Must be a conjunction of atoms or a single atom. " +
					"Formula: " + formula);
		
		if (!analysis.getDNFClause(0).getAllVariablesBound())
			throw new IllegalArgumentException("Illegal query formula. " +
					"All variables must appear at least once in an atom " +
					"with a StandardPredicate. " +
					"Formula: " + formula);
		
		ordering = new HashList<Variable>();
		
		AbstractFormulaTraverser.traverse(formula, new VariableOrderer());
	}
	
	public Formula getFormula() {
		return formula;
	}
	
	public VariableAssignment getPartialGrounding() {
		return partialGrounding;
	}
	
	public Set<Variable> getProjectionSubset() {
		return projectTo;
	}
	
	/**
	 * @return the number of Variables in this query's Formula
	 */
	public int getNumVariables() {
		return ordering.size();
	}
	
	/**
	 * Returns the Variable at a given index in this Query's formula according
	 * to a depth-first, left-to-right traversal (starting with 0).
	 * 
	 * @param index  the index of the Variable to return
	 * @return the Variable with the given index
	 */
	public Variable getVariable(int index) {
		return ordering.get(index);
	}
	
	/**
	 * Returns the index of a Variable in this Query's formula according to a
	 * depth-first, left-to-right traversal (starting with 0).
	 * 
	 * @param var  the Variable in the formula
	 * @return the Variable's index, or -1 if it is not in the formula
	 */
	public int getVariableIndex(Variable var) {
		return ordering.indexOf(var);
	}
	
	/**
	 * Places the Variables in the query Formula in ordering in order
	 * of their first appearances in a depth-first, left-to-right traversal. 
	 */
	private class VariableOrderer extends AbstractFormulaTraverser {
		@Override
		public void visitAtom(Atom atom) {
			for (Term term : atom.getArguments())
				if (term instanceof Variable)
					if (!ordering.contains(term))
						ordering.add((Variable) term);
		}
	}
	
}
