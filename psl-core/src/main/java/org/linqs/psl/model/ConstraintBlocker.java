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
package org.linqs.psl.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.arithmetic.UnweightedGroundArithmeticRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.FunctionSum;

/**
 * This class blocks free {@link RandomVariableAtom RandomVariableAtoms}
 * and RandomVariableAtoms that are each constrained by a single
 * {@link UnweightedGroundArithmeticRule} into individual categorical variables.
 * {@link GroundValueConstraint GroundValueConstraints} are also supported.
 * <p>
 * It also assumes that all ObservedAtoms and value-constrained atoms have
 * values in {0.0, 1.0}. Its behavior is not defined otherwise.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConstraintBlocker {
	
	private RandomVariableAtom[][] rvBlocks;
	private WeightedGroundRule[][] incidentGRs;
	private boolean[] exactlyOne;
	private Map<RandomVariableAtom, Integer> rvMap;
	
	private final GroundRuleStore store;
	
	public ConstraintBlocker(GroundRuleStore store) {
		this.store = store;
	}
	
	public void prepareBlocks(boolean prepareRVMap) {
		rvMap = (prepareRVMap) ? new HashMap<RandomVariableAtom, Integer>() : null;
		
		/* Collects constraints */
		Set<UnweightedGroundArithmeticRule> constraintSet = new HashSet<UnweightedGroundArithmeticRule>();
		Map<RandomVariableAtom, GroundValueConstraint> valueConstraintMap = new HashMap<RandomVariableAtom, GroundValueConstraint>();
		for (UnweightedGroundRule groundRule : store.getConstraintRules()) {
			if (groundRule instanceof UnweightedGroundArithmeticRule) {
				/* 
				 * If the ground rule is an UnweightedGroundArithmeticRule, checks if it
				 * is a categorical, i.e., at-least-1-of-k or 1-of-k, constraint 
				 */
				UnweightedGroundArithmeticRule gar = (UnweightedGroundArithmeticRule) groundRule;
				boolean categorical = true;
				
				if (!(
						FunctionComparator.Equality.equals(gar.getConstraintDefinition().getComparator())
						||
						(FunctionComparator.SmallerThan.equals(gar.getConstraintDefinition().getComparator()) && gar.getConstraintDefinition().getValue() > 0)
						||
						(FunctionComparator.LargerThan.equals(gar.getConstraintDefinition().getComparator()) && gar.getConstraintDefinition().getValue() < 0)
						)){
					categorical = false;
				}
				
				if (gar.getConstraintDefinition().getFunction() instanceof FunctionSum) {
					FunctionSum sum = (FunctionSum) gar.getConstraintDefinition().getFunction();
					for (int i = 0; i < sum.size(); i++) {
						if (Math.abs(sum.get(i).getCoefficient() - gar.getConstraintDefinition().getValue()) > 1e-8) {
							categorical = false;
							break;
						}
					}
				}
				else
					categorical = false;
				
				if (categorical) {
					constraintSet.add(gar);
				}
				else {
					throw new IllegalStateException("The only supported constraints are 1-of-k constraints "
							+ "and at-least-1-of-k constraints and value constraints.");
				}
			}
			else if (groundRule instanceof GroundValueConstraint)
				valueConstraintMap.put((RandomVariableAtom) groundRule.getAtoms().iterator().next(), (GroundValueConstraint) groundRule);
			else
				throw new IllegalStateException("The only supported constraints are 1-of-k constraints "
						+ "and at-least-1-of-k constraints and value constraints.");
		}
		
		/* Collects the free RandomVariableAtoms that remain */
		Set<RandomVariableAtom> freeRVSet = new HashSet<RandomVariableAtom>();
		for (GroundRule groundRule : store.getGroundRules()) {
			for (GroundAtom atom : groundRule.getAtoms()) {
				if (atom instanceof RandomVariableAtom) {
					int numDRConstraints = 0;
					int numValueConstraints = 0;
					for (GroundRule incidentGR : atom.getRegisteredGroundRules())
						if (incidentGR instanceof UnweightedGroundArithmeticRule)
							numDRConstraints++;
						else if (incidentGR instanceof GroundValueConstraint)
							numValueConstraints++;
					if (numDRConstraints == 0 && numValueConstraints == 0)
						freeRVSet.add(((RandomVariableAtom) atom));
					else if (numDRConstraints >= 2 || numValueConstraints >= 2)
						throw new IllegalStateException("RandomVariableAtoms may " +
								"only participate in one (at-least) 1-of-k " +
								"and/or GroundValueConstraint.");
				}
			}
		}
		
		int i;
		
		/* Puts RandomVariableAtoms in 2d array by block */
		rvBlocks = new RandomVariableAtom[constraintSet.size() + freeRVSet.size()][];
		/* If true, exactly one Atom in the RV block must be 1.0. If false, at most one can. */
		exactlyOne = new boolean[rvBlocks.length];
		
		/* Processes constrained RVs first */
		Set<RandomVariableAtom> constrainedRVSet = new HashSet<RandomVariableAtom>();
		/*
		 * False means that an ObservedAtom or constrained RandomVariableAtom
		 * is 1.0, forcing others to 0.0
		 */
		boolean varsAreFree;
		i = 0;
		for (UnweightedGroundArithmeticRule con : constraintSet) {
			constrainedRVSet.clear();
			varsAreFree = true;
			for (GroundAtom atom : con.getAtoms()) {
				if (atom instanceof ObservedAtom && atom.getValue() != 0.0)
					varsAreFree = false;
				else if (atom instanceof RandomVariableAtom) {
					GroundValueConstraint valueCon = valueConstraintMap.get(atom);
					if (valueCon != null) {
						if (valueCon.getConstraintDefinition().getValue() != 0.0)
							varsAreFree = false;
					}
					else
						constrainedRVSet.add((RandomVariableAtom) atom);
				}
			}
			
			if (varsAreFree) {
				rvBlocks[i] = new RandomVariableAtom[constrainedRVSet.size()];
				int j = 0;
				for (RandomVariableAtom atom : constrainedRVSet) {
					rvBlocks[i][j++] = atom;
					if (prepareRVMap)
						rvMap.put(atom, i);
				}
				
				exactlyOne[i] = con.getConstraintDefinition().getComparator().equals(FunctionComparator.Equality) || constrainedRVSet.size() == 0;
			}
			else {
				rvBlocks[i] = new RandomVariableAtom[0];
				/*
				 * Sets to true regardless of constraint type to avoid extra processing steps
				 * that would not work on empty blocks 
				 */
				exactlyOne[i] = true;
			}
			
			i++;
		}
		
		/* Processes free RVs second */
		for (RandomVariableAtom atom : freeRVSet) {
			rvBlocks[i] = new RandomVariableAtom[] {atom};
			exactlyOne[i] = false;
			if (prepareRVMap)
				rvMap.put(atom, i);
			i++;
		}
		
		/* Collects WeightedGroundRules incident on each block of RandomVariableAtoms */
		incidentGRs = new WeightedGroundRule[rvBlocks.length][];
		Set<WeightedGroundRule> incidentGKSet = new HashSet<WeightedGroundRule>();
		for (i = 0; i < rvBlocks.length; i++) {
			incidentGKSet.clear();
			for (RandomVariableAtom atom : rvBlocks[i])
				for (GroundRule incidentGK : atom.getRegisteredGroundRules())
					if (incidentGK instanceof WeightedGroundRule)
						incidentGKSet.add((WeightedGroundRule) incidentGK);
			
			incidentGRs[i] = new WeightedGroundRule[incidentGKSet.size()];
			int j = 0;
			for (WeightedGroundRule incidentGK : incidentGKSet)
				incidentGRs[i][j++] = incidentGK;
		}
		
		/* Sets all value-constrained atoms */
		for (Map.Entry<RandomVariableAtom, GroundValueConstraint> e : valueConstraintMap.entrySet())
			e.getKey().setValue(e.getValue().getConstraintDefinition().getValue());
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
		for (int i = 0; i < rvBlocks.length; i++)
			totals[i] = new double[rvBlocks[i].length];
		
		return totals;
	}
	
	/** Randomly initializes the RandomVariableAtoms to a feasible state. */
	public void randomlyInitializeRVs() {
		Random rand = new Random();
		for (int i = 0; i < rvBlocks.length; i++) {
			for (int j = 0; j < rvBlocks[i].length; j++) {
				rvBlocks[i][j].setValue(0.0);
			}
			if (rvBlocks[i].length > 0 && exactlyOne[i])
				rvBlocks[i][rand.nextInt(rvBlocks[i].length)].setValue(1.0);
		}
	}
}
