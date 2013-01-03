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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.Model;
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

abstract public class AbstractRuleKernel extends AbstractKernel {

	private static final Logger log = LoggerFactory.getLogger(AbstractRuleKernel.class);
	
	protected final Model model;
	protected Formula formula;
	protected final FormulaEventAnalysis formulaAnalysis;
	
	public AbstractRuleKernel(Model m, Formula f) {
		Preconditions.checkNotNull(m);
		model = m;
		formula = f;
		Formula notF = new Negation(f).getDNF();
		if (notF instanceof Conjunction)
			formulaAnalysis = new FormulaEventAnalysis((Conjunction) notF);
		else
			throw new IllegalArgumentException("Formula must be a disjunction of literals.");
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
		// TODO Not sure this if statement is necessary...
		if (event == AtomEvent.ActivatedRVAtom) {
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
		} else throw new UnsupportedOperationException("Only handles activation for now!");
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
