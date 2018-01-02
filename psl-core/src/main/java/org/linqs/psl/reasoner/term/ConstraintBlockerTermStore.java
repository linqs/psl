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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.arithmetic.UnweightedGroundArithmeticRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * This class blocks free {@link RandomVariableAtom RandomVariableAtoms}
 * and RandomVariableAtoms that are each constrained by a single
 * UnweightedGroundArithmeticRule into individual categorical variables.
 * GroundValueConstraint are also supported.
 *
 * It also assumes that all ObservedAtoms and value-constrained atoms have
 * values in {0.0, 1.0}. Its behavior is not defined otherwise.
 *
 * This is hackily implemented as a TermStore so that it fits into the reasoner framework.
 * Even though the TermStore methods are not supported.
 */
public class ConstraintBlockerTermStore implements TermStore<Term> {
	private RandomVariableAtom[][] rvBlocks;
	private WeightedGroundRule[][] incidentGRs;
	private boolean[] exactlyOne;
	private Map<RandomVariableAtom, Integer> rvMap;
	private GroundRuleStore groundRuleStore;

	public void init(GroundRuleStore groundRuleStore,
			RandomVariableAtom[][] rvBlocks, WeightedGroundRule[][] incidentGRs,
			boolean[] exactlyOne, Map<RandomVariableAtom, Integer> rvMap) {
		this.groundRuleStore = groundRuleStore;
		this.rvBlocks = rvBlocks;
		this.incidentGRs = incidentGRs;
		this.exactlyOne = exactlyOne;
		this.rvMap = rvMap;
	}

	/**
	 * Extremely hacky way to allow methods that require this to get ahold of the GroundRuleStore.
	 */
	public GroundRuleStore getGroundRuleStore() {
		return groundRuleStore;
	}

	public RandomVariableAtom[][] getRVBlocks() {
		return rvBlocks;
	}

	public Map<RandomVariableAtom, Integer> getRVMap() {
		return rvMap;
	}

	public WeightedGroundRule[][] getIncidentGKs() {
		return incidentGRs;
	}

	public boolean[] getExactlyOne() {
		return exactlyOne;
	}

	public double[][] getEmptyDouble2DArray() {
		double[][] totals = new double[rvBlocks.length][];
		for (int i = 0; i < rvBlocks.length; i++) {
			totals[i] = new double[rvBlocks[i].length];
		}

		return totals;
	}

	/**
	 * Randomly initializes the RandomVariableAtoms to a feasible state.
	 */
	public void randomlyInitializeRVs() {
		Random rand = new Random();
		for (int i = 0; i < rvBlocks.length; i++) {
			for (int j = 0; j < rvBlocks[i].length; j++) {
				rvBlocks[i][j].setValue(0.0);
			}

			if (rvBlocks[i].length > 0 && exactlyOne[i]) {
				rvBlocks[i][rand.nextInt(rvBlocks[i].length)].setValue(1.0);
			}
		}
	}

	// Standard TermStore operations are not supported.

	@Override
	public void add(GroundRule rule, Term term) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
	}

	@Override
	public void close() {
	}

	@Override
	public Term get(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return -1;
	}

	@Override
	public void updateWeight(WeightedGroundRule rule) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Integer> getTermIndices(WeightedGroundRule rule) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Term> iterator() {
		throw new UnsupportedOperationException();
	}
}
