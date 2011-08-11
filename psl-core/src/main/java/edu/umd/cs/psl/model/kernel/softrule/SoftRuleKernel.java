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
package edu.umd.cs.psl.model.kernel.softrule;

import java.util.List;

import org.apache.commons.lang.builder.HashCodeBuilder;
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
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.formula.traversal.FormulaEventAnalysis;
import edu.umd.cs.psl.model.formula.traversal.FormulaGrounder;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;

public class SoftRuleKernel implements Kernel {

	private static final Logger log = LoggerFactory.getLogger(SoftRuleKernel.class);
	
	private final FormulaEventAnalysis rule;

	private final Model model;
	
	private PositiveWeight weight;
	
	private final int hashcode;
	
	public SoftRuleKernel(Model m, Formula r, double mult, double w) {
		this(m, r, w);
	}
	
	public SoftRuleKernel(Model m, Formula r, double w) {
		Preconditions.checkNotNull(m);
		model = m;
		rule = new FormulaEventAnalysis(new Negation(r).dnf());
		weight = new PositiveWeight(w);
		hashcode = new HashCodeBuilder().append(rule.getFormula()).toHashCode();
	}
	
	@Override
	public Kernel clone() {
		return new SoftRuleKernel(model,rule.getFormula(),weight.getWeight());
	}
	
	public SoftRuleKernel(Model m, Formula r) {
		this(m, r, Double.NaN);
	}
	
	public Weight getWeight() {
		return weight;
	}
	
	private void groundFormula(ResultList res, ModelApplication app, VariableAssignment var) {
		log.trace("Grounding {} rules",res.size());
		FormulaGrounder grounder = new FormulaGrounder(app.getAtomManager(),res, var);
		while (grounder.hasNext()) {
			GroundSoftRule groundRule = new GroundSoftRule(this,grounder.ground(rule.getFormula()));
			GroundKernel oldrule = app.getGroundKernel(groundRule);
			if (oldrule!=null) {
				((GroundSoftRule)oldrule).increaseGroundings();
				app.changedGroundKernel(oldrule);
			} else {
				app.addGroundKernel(groundRule);
			}
			grounder.next();
		}
	}
	
	@Override
	public void groundAll(ModelApplication app) {
		for (Formula query : rule.getQueryFormulas()) {
			ResultList res = app.getDatabase().query(query);
			log.debug("Grounding {} instances of rule {}", res.size(), rule.getFormula());
			groundFormula(res,app,null);
		}
	}

	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode,	ModelApplication app) {
		if (mode==GroundingMode.Forward) {
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
	public Parameters getParameters() {
		return weight.duplicate();
	}

	@Override
	public void setParameters(Parameters para) {
		if (!(para instanceof Weight)) throw new IllegalArgumentException("Expected weight parameter!");
		PositiveWeight newweight = (PositiveWeight)para;
		if (!newweight.equals(weight)) {
			weight = newweight;
			model.changedKernelParameters(this);
		}
	}
	
	@Override
	public boolean isCompatibilityKernel() {
		return true;
	}
	
	
	@Override
	public String toString() {
		return weight.getWeight() + " : " + rule.getFormula();
	}
	
	@Override
	public int hashCode() {
		return super.hashCode();
		//return hashcode;
	}
	
	@Override
	public boolean equals(Object oth) {
		return super.equals(oth);
//		if (oth==this) return true;
//		if (oth==null || !(getClass().isInstance(oth)) ) return false;
//		SoftRuleKernel r = (SoftRuleKernel)oth;
//		return body.getFormula().equals(r.body.getFormula()) && head.getFormula().equals(r.head.getFormula()) && multiplier==r.multiplier;
	}


}
