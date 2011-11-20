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

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.formula.traversal.FormulaEventAnalysis;
import edu.umd.cs.psl.model.formula.traversal.FormulaGrounder;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;

abstract public class AbstractRuleKernel implements Kernel {

	private static final Logger log = LoggerFactory.getLogger(AbstractRuleKernel.class);
	
	protected final Model model;
	protected Formula formula;
	protected final FormulaEventAnalysis rule;
	
	public AbstractRuleKernel(Model m, Formula f) {
		Preconditions.checkNotNull(m);
		model = m;
		formula = f;
		Formula notF = new Negation(f).dnf();
		if (notF instanceof Conjunction)
			rule = new FormulaEventAnalysis((Conjunction) notF);
		else
			throw new IllegalArgumentException("Formula must be a disjunction of literals.");
	}
	
	protected void groundFormula(ResultList res, ModelApplication app, VariableAssignment var) {
		log.trace("Grounding {} instances of rule {}", res.size(), formula);
		FormulaGrounder grounder = new FormulaGrounder(app.getAtomManager(),res, var);
		while (grounder.hasNext()) {
			AbstractGroundRule groundRule = groundFormulaInstance(grounder.ground(rule.getFormula()));
			GroundKernel oldrule = app.getGroundKernel(groundRule);
			if (oldrule!=null) {
				((AbstractGroundRule)oldrule).increaseGroundings();
				app.changedGroundKernel(oldrule);
			} else {
				app.addGroundKernel(groundRule);
			}
			grounder.next();
		}
	}
	
	abstract protected AbstractGroundRule groundFormulaInstance(Formula f);
	
	@Override
	public void groundAll(ModelApplication app) {
		for (Formula query : rule.getQueryFormulas()) {
			ResultList res = app.getDatabase().query(query);
			groundFormula(res,app,null);
		}
	}

	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode,	ModelApplication app) {
		if (mode==GroundingMode.Forward || mode==GroundingMode.ForwardInitial) {
			if (AtomEventSets.ActivationEvent.subsumes(event)) {
				List<VariableAssignment> vars = rule.traceAtomEvent(atom);
				if (!vars.isEmpty()) {
					for (VariableAssignment var : vars) {
						for (Formula query : rule.getQueryFormulas()) {
							ResultList res = app.getDatabase().query(query, var);
							groundFormula(res,app,var);
						}
					}
				}
			} else throw new UnsupportedOperationException("Only handles activation for now! " + event);
		} else if (mode==GroundingMode.Backward) {
			if (AtomEventSets.ActivationEvent.subsumes(event)) {
				List<VariableAssignment> vars = rule.traceAtomEvent(atom);
				if (!vars.isEmpty()) {
					for (VariableAssignment var : vars) {
						for (Formula query : rule.getQueryFormulas()) {
							ResultList res = app.getDatabase().query(query, var);
							groundFormula(res,app,var);
						}
					}
				}
			} else throw new UnsupportedOperationException("Only handles activation for now!");
		} else  throw new UnsupportedOperationException("Unsupported grounding mode: " + mode);
	}
	
	@Override
	public void registerForAtomEvents(AtomEventFramework framework,
			DatabaseAtomStoreQuery db) {
		rule.registerFormulaForEvents(framework, this, AtomEventSets.DeOrActivationEvent, db);
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework framework,
			DatabaseAtomStoreQuery db) {
		rule.unregisterFormulaForEvents(framework, this, AtomEventSets.DeOrActivationEvent, db);
	}

	@Override
	public Kernel clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
}
