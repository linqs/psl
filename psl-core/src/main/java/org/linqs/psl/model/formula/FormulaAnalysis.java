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
package org.linqs.psl.model.formula;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.AtomEvent;
import org.linqs.psl.model.atom.AtomEventFramework;
import org.linqs.psl.model.atom.VariableAssignment;
import org.linqs.psl.model.atom.AtomEvent.Type;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;

import org.apache.commons.lang.StringUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * Converts a {@link Formula} to a simplified Disjunctive Normal Form view
 * and makes the clauses available.
 * <p>
 * Each clause reports properties and helps {@link Rule Rules} with registering
 * for the appropriate {@link AtomEvent AtomEvents} and running the appropriate
 * {@link DatabaseQuery DatabaseQueries} to identify true groundings.
 *
 * @author Matthias Broecheler
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class FormulaAnalysis {

	protected final Formula f;
	protected final List<DNFClause> clauses;

	public FormulaAnalysis(Formula formula) {
		f = formula;

		/*
		 * Converts the Formula to Disjunctive Normal Form and collects the clauses
		 */
		formula = formula.getDNF();
		Formula[] rawClauses;
		if (formula instanceof Disjunction) {
			Disjunction disj = ((Disjunction) formula).flatten();
			rawClauses = new Formula[disj.length()];
			for (int i = 0; i < rawClauses.length; i++)
				rawClauses[i] = disj.get(i);
		}
		else {
			rawClauses = new Formula[] {formula};
		}

		/*
		 * Processes each clause
		 */
		clauses = new ArrayList<DNFClause>(rawClauses.length);

		List<Atom> posLiterals = new ArrayList<Atom>(4);
		List<Atom> negLiterals = new ArrayList<Atom>(4);

		for (int i = 0; i < rawClauses.length; i++) {
			/*
			 * Extracts the positive and negative literals from the clause
			 */
			if (rawClauses[i] instanceof Conjunction) {
				Conjunction c = ((Conjunction) rawClauses[i]).flatten();
				for (int j = 0; j < c.length(); j++) {
					if (c.get(j) instanceof Atom) {
						posLiterals.add((Atom) c.get(j));
					}
					else if (c.get(j) instanceof Negation) {
						Negation n = (Negation) c.get(j);
						if (n.getFormula() instanceof Atom) {
							negLiterals.add((Atom) n.getFormula());
						}
						else {
							throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
						}
					}
					else {
						throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
					}
				}
			}
			else if (rawClauses[i] instanceof Atom) {
				posLiterals.add((Atom) rawClauses[i]);
			}
			else if (rawClauses[i] instanceof Negation) {
				Negation n = (Negation) rawClauses[i];
				if (n.getFormula() instanceof Atom) {
					negLiterals.add((Atom) n.getFormula());
				}
				else {
					throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
				}
			}
			else {
				throw new IllegalStateException("Unexpected sub-Formula. Formula was not in flattened Disjunctive Normal Form.");
			}

			/*
			 * Stores the DNFClause
			 */
			clauses.add(new DNFClause(posLiterals, negLiterals));
			posLiterals.clear();
			negLiterals.clear();
		}
	}

	/**
	 * @return the original Formula that was analyzed
	 */
	public Formula getFormula() {
		return f;
	}

	/**
	 * @return the number of clauses in the Formula after it has been converted to Disjunctive Normal Form.
	 */
	public int getNumDNFClauses() {
		return clauses.size();
	}

	/**
	 * Returns the specified clause of the Formula after it has been converted
	 * to Disjunctive Normal Form.
	 *
	 * @param index  the clause's index
	 * @return the DNF clause
	 */
	public DNFClause getDNFClause(int index) {
		return clauses.get(index);
	}

	public class DNFClause {
		protected final List<Atom> posLiterals;
		protected final List<Atom> negLiterals;
		protected final Multimap<Predicate,Atom> dependence;
		protected final Formula query;
		protected final boolean allVariablesBound;
		protected final boolean isGround;

		public DNFClause(List<Atom> posLiterals, List<Atom> negLiterals) {
			this.posLiterals = new ArrayList<Atom>(posLiterals);
			this.negLiterals = new ArrayList<Atom>(negLiterals);
			dependence = ArrayListMultimap.create();
			Set<Variable> allowedVariables = new HashSet<Variable>();
			Set<Variable> variablesToCheck = new HashSet<Variable>();
			boolean tempAllVariablesBound = true;

			/*
			 * Checks if all Variables in the clause appear in a positive literal
			 * with a StandardPredicate.
			 */
			Set<Variable> setToAdd;

			for (Atom atom : posLiterals) {
				if (atom.getPredicate() instanceof StandardPredicate)
					setToAdd = allowedVariables;
				else
					setToAdd = variablesToCheck;

				for (Term t : atom.getArguments()) {
					if (t instanceof Variable)
						setToAdd.add((Variable) t);
				}
			}

			for (Atom atom : negLiterals)
				for (Term t : atom.getArguments())
					if (t instanceof Variable)
						variablesToCheck.add((Variable) t);

			isGround = (allowedVariables.size() + variablesToCheck.size() == 0) ? true : false;

			for (Variable v : variablesToCheck)
				if (!allowedVariables.contains(v))
					tempAllVariablesBound = false;

			allVariablesBound = tempAllVariablesBound;

			/*
			 * Processes the positive literals with StandardPredicates further
			 */
			for (int i = 0; i < posLiterals.size(); i++)
				if (posLiterals.get(i).getPredicate() instanceof StandardPredicate)
					dependence.put(posLiterals.get(i).getPredicate(), posLiterals.get(i));

			if (posLiterals.size() == 0)
				query = null;
			else if (posLiterals.size() == 1)
				query = (allVariablesBound) ? posLiterals.get(0) : null;
			else
				query = (allVariablesBound) ? new Conjunction(posLiterals.toArray(new Formula[posLiterals.size()])) : null;
		}

		/**
		 * @return the positive literals, i.e., Atoms not negated, in the clause
		 */
		public List<Atom> getPosLiterals() {
			return Collections.unmodifiableList(posLiterals);
		}

		/**
		 * @return the negative literals, i.e., negated Atoms, in the clause
		 */
		public List<Atom> getNegLiterals() {
			return Collections.unmodifiableList(negLiterals);
		}

		/**
		 * Returns whether all Variables in the clause appear at least once in a
		 * positive literal with a {@link StandardPredicate}.
		 * <p>
		 * If all Variables are bound, then {@link DatabaseQuery DatabaseQueries}
		 * can identify all groundings of the clause with possibly non-zero truth
		 * values in a {@link Database}.
		 *
		 * @return whether all Variables are bound
		 */
		public boolean getAllVariablesBound() {
			return allVariablesBound;
		}

		public boolean isGround() {
			return isGround;
		}

		public boolean isQueriable() {
			return (query != null);
		}

		public Formula getQueryFormula() {
			if (query != null)
				return query;
			else
				throw new IllegalStateException("Clause is not queriable.");
		}

		public List<VariableAssignment> traceAtomEvent(Atom atom) {
			Collection<Atom> atoms = dependence.get(atom.getPredicate());
			List<VariableAssignment> vars = new ArrayList<VariableAssignment>(atoms.size());
			for (Atom entry : atoms) {
				//Check whether arguments match
				VariableAssignment var = new VariableAssignment();
				Term[] argsGround = atom.getArguments();
				Term[] argsTemplate = entry.getArguments();
				assert argsGround.length==argsTemplate.length;
				for (int i=0;i<argsGround.length;i++) {
					if (argsTemplate[i] instanceof Variable) {
						//Add mapping
						assert argsGround[i] instanceof Constant;
						var.assign((Variable)argsTemplate[i], (Constant)argsGround[i]);
					} else {
						//They must be the same
						if (!argsTemplate[i].equals(argsGround[i])) {
							var = null;
							break;
						}
					}
				}
				if (var!=null) vars.add(var);
			}
			return vars;
		}

		public void registerClauseForEvents(AtomEventFramework eventFramework, Set<Type> eventTypes, Rule k) {
			for (Predicate p : dependence.keySet()) {
				if (!eventFramework.isClosed((StandardPredicate) p)) {
					eventFramework.registerAtomEventListener(eventTypes, (StandardPredicate) p, k);
				}
			}
		}

		public void unregisterClauseForEvents(AtomEventFramework eventFramework, Set<Type> eventTypes, Rule k) {
			for (Predicate p : dependence.keySet()) {
				if (!eventFramework.isClosed((StandardPredicate) p)) {
					eventFramework.unregisterAtomEventListener(eventTypes, (StandardPredicate) p, k);
				}
			}
		}

		public String toString() {
			List<String> allLiterals = new ArrayList<>();

			for (Atom posLit : getPosLiterals()) {
				allLiterals.add(posLit.toString());
			}

			for (Atom negLit : getNegLiterals()) {
				allLiterals.add("~" + negLit.toString());
			}

			return StringUtils.join(allLiterals, " & ");
		}
	}
}
