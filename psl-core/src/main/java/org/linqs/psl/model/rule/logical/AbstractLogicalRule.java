/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.formula.FormulaAnalysis.DNFClause;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionVariable;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all (first order, i.e., not ground) logical rules.
 */
public abstract class AbstractLogicalRule extends AbstractRule {
	private static final Logger log = LoggerFactory.getLogger(AbstractLogicalRule.class);

	protected Formula formula;
	protected final DNFClause negatedDNF;
	private int hash;

	public AbstractLogicalRule(Formula formula, String name) {
		super(name);

		this.formula = formula;

		// Do the formula analysis so we know what atoms to query for grounding.
		// We will query for all positive atoms in the negated DNF.
		FormulaAnalysis analysis = new FormulaAnalysis(new Negation(formula));

		if (analysis.getNumDNFClauses() > 1) {
			throw new IllegalArgumentException("Formula must be a disjunction of literals (or a negative literal).");
		} else {
			negatedDNF = analysis.getDNFClause(0);
		}

		Set<Variable> unboundVariables = negatedDNF.getUnboundVariables();
		if (unboundVariables.size() > 0) {
			Variable[] sortedVariables = unboundVariables.toArray(new Variable[unboundVariables.size()]);
			Arrays.sort(sortedVariables);

			throw new IllegalArgumentException(
					"Any variable used in a negated (non-functional) predicate must also participate" +
					" in a positive (non-functional) predicate." +
					" The following variables do not meet this requirement: [" + StringUtils.join(sortedVariables, ", ") + "]."
			);
		}

		if (negatedDNF.isGround()) {
			throw new IllegalArgumentException("Formula has no Variables.");
		}

		if (!negatedDNF.isQueriable()) {
			throw new IllegalArgumentException("Formula is not a valid rule for unknown reason.");
		}

		// Build up the hash code from positive and negative literals.
		hash = HashCode.DEFAULT_INITIAL_NUMBER;

		for (Atom atom : negatedDNF.getPosLiterals()) {
			hash = HashCode.build(atom);
		}

		for (Atom atom : negatedDNF.getNegLiterals()) {
			hash = HashCode.build(atom);
		}
	}

	public Formula getFormula() {
		return formula;
	}

	public DNFClause getDNF() {
		return negatedDNF;
	}

	@Override
	public int groundAll(AtomManager atomManager, GroundRuleStore grs) {
		ResultList res = atomManager.executeGroundingQuery(negatedDNF.getQueryFormula());
		return groundAll(res, atomManager, grs);
	}

	public int groundAll(ResultList groundVariables, AtomManager atomManager, GroundRuleStore grs) {
		int initialCount = grs.count(this);
		Parallel.count(groundVariables.size(), new GroundWorker(atomManager, grs, groundVariables));
		int groundCount = grs.count(this) - initialCount;

		log.debug("Grounded {} instances of rule {}", groundCount, this);
		return groundCount;
	}

	private class GroundWorker extends Parallel.Worker<Integer> {
		// Remember that these are positive/negative in the CNF.
		private List<GroundAtom> posLiterals;
		private List<GroundAtom> negLiterals;

		private AtomManager atomManager;
		private GroundRuleStore grs;
		private ResultList res;

		// Allocate up-front some buffers for grounding QueryAtoms into.
		private Constant[][] positiveAtomArgs;
		private Constant[][] negativeAtomArgs;

		public GroundWorker(AtomManager atomManager, GroundRuleStore grs, ResultList res) {
			this.atomManager = atomManager;
			this.grs = grs;
			this.res = res;
		}

		@Override
		public void init(int id) {
			super.init(id);

			posLiterals = new ArrayList<GroundAtom>(4);
			negLiterals = new ArrayList<GroundAtom>(4);

			int numLiterals = negatedDNF.getPosLiterals().size() + negatedDNF.getNegLiterals().size();

			positiveAtomArgs = new Constant[negatedDNF.getPosLiterals().size()][];
			for (int i = 0; i < negatedDNF.getPosLiterals().size(); i++) {
				positiveAtomArgs[i] = new Constant[negatedDNF.getPosLiterals().get(i).getArity()];
			}

			negativeAtomArgs = new Constant[negatedDNF.getNegLiterals().size()][];
			for (int i = 0; i < negatedDNF.getNegLiterals().size(); i++) {
				negativeAtomArgs[i] = new Constant[negatedDNF.getNegLiterals().get(i).getArity()];
			}
		}

		@Override
		public Object clone() {
			return new GroundWorker(atomManager, grs, res);
		}

		@Override
		public void work(int index, Integer ignore) {
			GroundAtom atom = null;

			int rvaCount = 0;

			// Note that there is a class of trivial groundings that we choose not to remove at this point for
			// computational reasons.
			// It is possible for both a ground atoms and it's negation to appear in the DNF.
			// This obviously causes a tautology.
			// Removing it here would require checking the positive atoms against the negative ones.
			// Even if we already had a mapping of possiblities (perhaps created in FormulaAnalysis),
			// it would still be non-trivial (and complex rules can cause the mapping to blow up).
			// Instead they will be removed as they are turned into hyperplane terms,
			// since we will have to keep track of variables there anyway.

			for (int j = 0; j < negatedDNF.getPosLiterals().size(); j++) {
				atom = ((QueryAtom)negatedDNF.getPosLiterals().get(j)).ground(atomManager, res, index, positiveAtomArgs[j]);
				if (atom instanceof RandomVariableAtom) {
					rvaCount++;
				} else if (MathUtils.equals(atom.getValue(), 0.0)) {
					// This rule is trivially satisfied by a constant, do not ground it.
					posLiterals.clear();
					negLiterals.clear();
					return;
				}

				posLiterals.add(atom);
			}

			for (int j = 0; j < negatedDNF.getNegLiterals().size(); j++) {
				atom = ((QueryAtom)negatedDNF.getNegLiterals().get(j)).ground(atomManager, res, index, negativeAtomArgs[j]);
				if (atom instanceof RandomVariableAtom) {
					rvaCount++;
				} else if (MathUtils.equals(atom.getValue(), 1.0)) {
					// This rule is trivially satisfied by a constant, do not ground it.
					posLiterals.clear();
					negLiterals.clear();
					return;
				}

				negLiterals.add(atom);
			}

			AbstractGroundLogicalRule groundRule = groundFormulaInstance(posLiterals, negLiterals, rvaCount);
			grs.addGroundRule(groundRule);

			posLiterals.clear();
			negLiterals.clear();
		}
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}

		if (other == null || !(other instanceof AbstractLogicalRule)) {
			return false;
		}

		AbstractLogicalRule otherRule = (AbstractLogicalRule)other;

		if (this.hash != otherRule.hash) {
			return false;
		}

		// Final deep equality check.
		List<Atom> thisPosLiterals = this.negatedDNF.getPosLiterals();
		List<Atom> otherPosLiterals = otherRule.negatedDNF.getPosLiterals();
		if (thisPosLiterals.size() != otherPosLiterals.size()) {
			return false;
		}

		List<Atom> thisNegLiterals = this.negatedDNF.getNegLiterals();
		List<Atom> otherNegLiterals = otherRule.negatedDNF.getNegLiterals();
		if (thisNegLiterals.size() != otherNegLiterals.size()) {
			return false;
		}

		return
				(new HashSet<Atom>(thisPosLiterals)).equals(new HashSet<Atom>(otherPosLiterals)) &&
				(new HashSet<Atom>(thisNegLiterals)).equals(new HashSet<Atom>(otherNegLiterals));
	}

	protected abstract AbstractGroundLogicalRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals, int rvaCount);
}
