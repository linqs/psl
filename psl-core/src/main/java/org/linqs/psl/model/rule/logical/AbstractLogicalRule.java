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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.NumericUtilities;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.AtomEvent;
import org.linqs.psl.model.atom.AtomEventFramework;
import org.linqs.psl.model.atom.AtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.atom.VariableAssignment;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.formula.FormulaAnalysis.DNFClause;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.FunctionVariable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all (first order, i.e., not ground) logical rules.
 */
abstract public class AbstractLogicalRule extends AbstractRule {
	private static final Logger log = LoggerFactory.getLogger(AbstractLogicalRule.class);

	protected Formula formula;
	protected final DNFClause clause;

	public AbstractLogicalRule(Formula f) {
		super();
		formula = f;
		FormulaAnalysis analysis = new FormulaAnalysis(new Negation(formula));

		if (analysis.getNumDNFClauses() > 1) {
			throw new IllegalArgumentException("Formula must be a disjunction of literals (or a negative literal).");
		} else {
			clause = analysis.getDNFClause(0);
		}

		Set<Variable> unboundVariables = clause.getUnboundVariables();
		if (unboundVariables.size() > 0) {
			Variable[] sortedVariables = unboundVariables.toArray(new Variable[unboundVariables.size()]);
			Arrays.sort(sortedVariables);

			throw new IllegalArgumentException(
					"Any variable used in a negated (non-functional) predicate must also participate" +
					" in a positive (non-functional) predicate." +
					" The following variables do not meet this requirement: [" + StringUtils.join(sortedVariables, ", ") + "]."
			);
		}

		if (clause.isGround()) {
			throw new IllegalArgumentException("Formula has no Variables.");
		}

		if (!clause.isQueriable()) {
			throw new IllegalArgumentException("Formula is not a valid rule for unknown reason.");
		}
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundRuleStore grs) {
		ResultList res = atomManager.executeQuery(new DatabaseQuery(clause.getQueryFormula()));
		int numGrounded = groundFormula(atomManager, grs, res, null);
		log.debug("Grounded {} instances of rule {}", numGrounded, this);
	}

	protected int groundFormula(AtomManager atomManager, GroundRuleStore grs, ResultList res,  VariableAssignment var) {
		int numGroundingsAdded = 0;
		List<GroundAtom> posLiterals = new ArrayList<GroundAtom>(4);
		List<GroundAtom> negLiterals = new ArrayList<GroundAtom>(4);

		/* Uses these to check worst-case truth value */
		Map<FunctionVariable, Double> worstCaseValues = new HashMap<FunctionVariable, Double>(8);
		double worstCaseValue;

		GroundAtom atom;
		for (int i = 0; i < res.size(); i++) {

			for (int j = 0; j < clause.getPosLiterals().size(); j++) {
				atom = groundAtom(atomManager, clause.getPosLiterals().get(j), res, i, var);
				if (atom instanceof RandomVariableAtom)
					worstCaseValues.put(atom.getVariable(), 1.0);
				else
					worstCaseValues.put(atom.getVariable(), atom.getValue());
				posLiterals.add(atom);
			}

			for (int j = 0; j < clause.getNegLiterals().size(); j++) {
				atom = groundAtom(atomManager, clause.getNegLiterals().get(j), res, i, var);
				if (atom instanceof RandomVariableAtom)
					worstCaseValues.put(atom.getVariable(), 0.0);
				else
					worstCaseValues.put(atom.getVariable(), atom.getValue());
				negLiterals.add(atom);
			}

			AbstractGroundLogicalRule groundRule = groundFormulaInstance(posLiterals, negLiterals);
			FunctionTerm function = groundRule.getFunction();
			worstCaseValue = function.getValue(worstCaseValues, false);
			if (worstCaseValue > NumericUtilities.strictEpsilon
					&& (!function.isConstant() || !(groundRule instanceof WeightedGroundRule))
					&& !grs.containsGroundRule(groundRule)) {
				grs.addGroundRule(groundRule);
				numGroundingsAdded++;
			/* If the ground rule is not actually added, unregisters it from atoms */
			} else {
				for (GroundAtom incidentAtom : groundRule.getAtoms()) {
					incidentAtom.unregisterGroundRule(groundRule);
				}
			}

			posLiterals.clear();
			negLiterals.clear();
			worstCaseValues.clear();
		}

		return numGroundingsAdded;
	}

	protected GroundAtom groundAtom(AtomManager atomManager, Atom atom, ResultList res, int resultIndex, VariableAssignment var) {
		Term[] oldArgs = atom.getArguments();
		Constant[] newArgs = new Constant[atom.getArity()];
		for (int i = 0; i < oldArgs.length; i++)
			if (oldArgs[i] instanceof Variable) {
				Variable v = (Variable) oldArgs[i];
				if (var != null && var.hasVariable(v))
					newArgs[i] = var.getVariable(v);
				else
					newArgs[i] = res.get(resultIndex, (Variable) oldArgs[i]);
			}
			else if (oldArgs[i] instanceof Constant)
				newArgs[i] = (Constant) oldArgs[i];
			else
				throw new IllegalArgumentException("Unrecognized type of Term.");

		return atomManager.getAtom(atom.getPredicate(), newArgs);
	}

	abstract protected AbstractGroundLogicalRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals);

	@Override
	public void notifyAtomEvent(AtomEvent event, GroundRuleStore grs) {
		List<VariableAssignment> vars = clause.traceAtomEvent(event.getAtom());
		if (!vars.isEmpty()) {
			for (VariableAssignment var : vars) {
				DatabaseQuery dbQuery = new DatabaseQuery(clause.getQueryFormula());
				dbQuery.getPartialGrounding().putAll(var);
				ResultList res = event.getEventFramework().executeQuery(dbQuery);
				groundFormula(event.getEventFramework(), grs, res, var);
			}
		}
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework manager) {
		clause.registerClauseForEvents(manager, AtomEvent.ActivatedEventTypeSet, this);
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework manager) {
		clause.unregisterClauseForEvents(manager, AtomEvent.ActivatedEventTypeSet, this);
	}

	@Override
	public Rule clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}
}
