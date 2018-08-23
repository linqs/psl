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
import org.linqs.psl.database.QueryResultIterable;
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
	public int groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore) {
		QueryResultIterable queryResults = atomManager.executeGroundingQuery(negatedDNF.getQueryFormula());
		return groundAll(queryResults, atomManager, groundRuleStore);
	}

	public int groundAll(QueryResultIterable groundVariables, AtomManager atomManager, GroundRuleStore groundRuleStore) {
		// We will manually handle these in the grounding process.
		// We do not want to throw too early because the ground rule may turn out to be trivial in the end.
		boolean oldAccessExceptionState = atomManager.enableAccessExceptions(false);

		int initialCount = groundRuleStore.count(this);
		Parallel.foreach(groundVariables, new GroundWorker(atomManager, groundRuleStore, groundVariables.getVariableMap()));
		int groundCount = groundRuleStore.count(this) - initialCount;

		atomManager.enableAccessExceptions(oldAccessExceptionState);

		log.debug("Grounded {} instances of rule {}", groundCount, this);
		return groundCount;
	}

	private class GroundWorker extends Parallel.Worker<Constant[]> {
		private static final int ERROR_TRIVIAL = -1;

		// Remember that these are positive/negative in the CNF.
		private List<GroundAtom> positiveAtoms;
		private List<GroundAtom> negativeAtoms;

		// Atoms that cause trouble for the atom manager.
		Set<GroundAtom> accessExceptionAtoms;

		private AtomManager atomManager;
		private GroundRuleStore groundRuleStore;
		private Map<Variable, Integer> variableMap;

		// Allocate up-front some buffers for grounding QueryAtoms into.
		private Constant[][] positiveAtomArgs;
		private Constant[][] negativeAtomArgs;

		public GroundWorker(AtomManager atomManager, GroundRuleStore groundRuleStore, Map<Variable, Integer> variableMap) {
			this.atomManager = atomManager;
			this.variableMap = variableMap;
			this.groundRuleStore = groundRuleStore;
		}

		@Override
		public void init(int id) {
			super.init(id);

			positiveAtoms = new ArrayList<GroundAtom>(4);
			negativeAtoms = new ArrayList<GroundAtom>(4);
			accessExceptionAtoms = new HashSet<GroundAtom>(4);

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
			return new GroundWorker(atomManager, groundRuleStore, variableMap);
		}

		@Override
		public void work(int index, Constant[] row) {
			positiveAtoms.clear();
			negativeAtoms.clear();
			accessExceptionAtoms.clear();

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

			int positiveRVACount = createAtoms(negatedDNF.getPosLiterals(), row, positiveAtomArgs, positiveAtoms, 0.0);
			if (positiveRVACount == ERROR_TRIVIAL) {
				// Trivial.
				return;
			}
			rvaCount += positiveRVACount;

			int negativeRVACount = createAtoms(negatedDNF.getNegLiterals(), row, negativeAtomArgs, negativeAtoms, 1.0);
			if (negativeRVACount == ERROR_TRIVIAL) {
				// Trivial.
				return;
			}
			rvaCount += negativeRVACount;

			// We got an access error and the ground rule was not trivial.
			if (accessExceptionAtoms.size() != 0) {
				RuntimeException ex = new RuntimeException(String.format(
						"Found one or more RandomVariableAtoms (target ground atom)" +
						" that were not explicitly specified in the targets." +
						" Offending atom(s): %s." +
						" This typically means that your specified target set is insufficient." +
						" This was encountered during the grounding of the rule: [%s].",
						accessExceptionAtoms,
						AbstractLogicalRule.this));
				atomManager.reportAccessException(ex, accessExceptionAtoms.iterator().next());
			}

			AbstractGroundLogicalRule groundRule = groundFormulaInstance(positiveAtoms, negativeAtoms, rvaCount);
			groundRuleStore.addGroundRule(groundRule);
		}


		private int createAtoms(List<Atom> literals, Constant[] row, Constant[][] argumentBuffer, List<GroundAtom> groundAtoms, double trivialValue) {
			GroundAtom atom = null;
			int rvaCount = 0;

			for (int j = 0; j < literals.size(); j++) {
				atom = ((QueryAtom)literals.get(j)).ground(atomManager, row, variableMap, argumentBuffer[j]);
				if (atom instanceof RandomVariableAtom) {
					// If we got an atom that is in violation of an access policy, then we may need to throw an exception.
					// First we will check to see if the ground rule is trivial,
					// then only throw if if it is not.
					if (((RandomVariableAtom)atom).getAccessException()) {
						accessExceptionAtoms.add((RandomVariableAtom)atom);
					}

					rvaCount++;
				} else if (MathUtils.equals(atom.getValue(), trivialValue)) {
					// This rule is trivially satisfied by a constant, do not ground it.
					return ERROR_TRIVIAL;
				}

				groundAtoms.add(atom);
			}

			return rvaCount;
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

	protected abstract AbstractGroundLogicalRule groundFormulaInstance(List<GroundAtom> positiveAtoms, List<GroundAtom> negativeAtoms, int rvaCount);
}
