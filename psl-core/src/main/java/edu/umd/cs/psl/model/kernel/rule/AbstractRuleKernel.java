/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.model.kernel.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.FormulaEventAnalysis;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.formula.traversal.FormulaGrounder;
import edu.umd.cs.psl.model.kernel.AbstractKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

abstract public class AbstractRuleKernel extends AbstractKernel {
	private static final Logger log = LoggerFactory.getLogger(AbstractRuleKernel.class);
	
	protected Formula formula;
	protected final List<Atom> posLiterals, negLiterals;
	protected final FormulaEventAnalysis formulaAnalysis;
	
	public AbstractRuleKernel(Formula f) {
		super();
		formula = f;
		posLiterals = new ArrayList<Atom>(4);
		negLiterals = new ArrayList<Atom>(4);
		Formula notF = new Negation(f).getDNF();
		
		/*
		 * Extracts the positive and negative Atoms from the negated Formula
		 */
		boolean validFormula = true;
		if (notF instanceof Conjunction) {
			Conjunction c = ((Conjunction) notF).flatten();
			for (int i = 0; i < c.getNoFormulas(); i++) {
				if (c.get(i) instanceof Atom) {
					posLiterals.add((Atom) c.get(i));
				}
				else if (c.get(i) instanceof Negation) {
					Negation n = (Negation) c.get(i);
					if (n.getFormula() instanceof Atom) {
						negLiterals.add((Atom) n.getFormula());
					}
					else {
						validFormula = false;
					}
				}
				else {
					validFormula = false;
				}
			}
		}
		else if (notF instanceof Atom) {
			posLiterals.add((Atom) notF);
		}
		else {
			validFormula = false;
		}
		
		if (!validFormula) {
			throw new IllegalArgumentException("Formula must be a disjunction of literals (or a negative literal).");
		}
		
		/*
		 * Checks that all Variables in the negated Formula appear in a positive
		 * literal with a StandardPredicate.
		 */
		Set<Variable> allowedVariables = new HashSet<Variable>();
		Set<Variable> variablesToCheck = new HashSet<Variable>();
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
		
		for (Atom atom : negLiterals) {
			for (Term t : atom.getArguments()) {
				if (t instanceof Variable)
					variablesToCheck.add((Variable) t);
			}
		}
		
		for (Variable v : variablesToCheck)
			if (!allowedVariables.contains(v))
				throw new IllegalArgumentException("All Variables must be used at " +
						"least once as an argument for a negative literal with a " +
						"StandardPredicate.");
		
		/* Analyzes the positive literals to determine which queries to run */
		formulaAnalysis = new FormulaEventAnalysis(posLiterals);
	}
	
	protected void groundFormula(AtomManager atomManager, GroundKernelStore gks, ResultList res,  VariableAssignment var) {
		log.trace("Grounding {} instances of rule {}", res.size(), formula);
		FormulaGrounder grounder = new FormulaGrounder(atomManager, res, var);
		while (grounder.hasNext()) {
			AbstractGroundRule groundRule = groundFormulaInstance(grounder.ground(formulaAnalysis.getFormula()));
			GroundKernel oldrule = gks.getGroundKernel(groundRule);
			if (oldrule != null) {
				((AbstractGroundRule)oldrule).increaseGroundings();
				gks.changedGroundKernel(oldrule);
			} else {
				gks.addGroundKernel(groundRule);
			}
			grounder.next();
		}
	}
	
	abstract protected AbstractGroundRule groundFormulaInstance(Formula f);
	
	@Override
	public void groundAll(AtomManager atomManager, GroundKernelStore gks) {
		for (Formula query : formulaAnalysis.getQueryFormulas()) {
			ResultList res = atomManager.getDatabase().executeQuery(new DatabaseQuery(query));
			groundFormula(atomManager, gks, res, null);
		}
	}

	@Override
	public void notifyAtomEvent(AtomEvent event, GroundKernelStore gks) {
		List<VariableAssignment> vars = formulaAnalysis.traceAtomEvent(event.getAtom());
		if (!vars.isEmpty()) {
			for (VariableAssignment var : vars) {
				for (Formula query : formulaAnalysis.getQueryFormulas()) {
					// TODO fix me: ResultList res = app.getAtomManager().getActiveGroundings(query, var);
					DatabaseQuery dbQuery = new DatabaseQuery(query);
					dbQuery.getPartialGrounding().putAll(var);
					ResultList res = event.getEventFramework().getDatabase().executeQuery(dbQuery);
					groundFormula(event.getEventFramework(), gks, res, var);
				}
			}
		}
	}
	
	@Override
	public void registerForAtomEvents(AtomEventFramework manager) {
		formulaAnalysis.registerFormulaForEvents(manager, this, ActivatedEventSet);
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework manager) {
		formulaAnalysis.unregisterFormulaForEvents(manager, this, ActivatedEventSet);
	}

	@Override
	public Kernel clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
}
