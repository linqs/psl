/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.util.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.predicateconstraint.GroundDomainRangeConstraint;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

/**
 * This class blocks free {@link RandomVariableAtom RandomVariableAtoms}
 * and RandomVariableAtoms that are each constrained by a single
 * {@link GroundDomainRangeConstraint} into logically individual categorical variables.
 * <p>
 * It also assumes that all ObservedAtoms have values in {0.0, 1.0}.
 * Its behavior is not defined otherwise.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConstraintBlocker {
	
	private RandomVariableAtom[][] rvBlocks;
	private GroundCompatibilityKernel[][] incidentGKs;
	private boolean[] exactlyOne;
	private Map<RandomVariableAtom, Integer> rvMap;
	
	private final GroundKernelStore store;
	
	public ConstraintBlocker(GroundKernelStore store) {
		this.store = store;
	}
	
	public void prepareBlocks(boolean prepareRVMap) {
		rvMap = (prepareRVMap) ? new HashMap<RandomVariableAtom, Integer>() : null;
		
		/* Collects GroundDomainRangeConstraints */
		Set<GroundDomainRangeConstraint> constraintSet = new HashSet<GroundDomainRangeConstraint>();
		for (GroundConstraintKernel gk : store.getConstraintKernels()) {
			if (gk instanceof GroundDomainRangeConstraint)
				constraintSet.add((GroundDomainRangeConstraint) gk);
			else
				throw new IllegalStateException("The only supported ConstraintKernels are DomainRangeConstraintKernels.");
		}
		
		/* Collects the free RandomVariableAtoms that remain */
		Set<RandomVariableAtom> freeRVSet = new HashSet<RandomVariableAtom>();
		for (GroundKernel gk : store.getGroundKernels()) {
			for (GroundAtom atom : gk.getAtoms()) {
				if (atom instanceof RandomVariableAtom) {
					int numConstraints = 0;
					for (GroundKernel incidentGK : atom.getRegisteredGroundKernels())
						if (incidentGK instanceof GroundConstraintKernel)
							numConstraints++;
					if (numConstraints == 0)
						freeRVSet.add(((RandomVariableAtom) atom));
					else if (numConstraints >= 2)
						throw new IllegalStateException("RandomVariableAtoms may only participate in one GroundDomainRangeConstraint.");
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
		boolean varsAreFree; /* False means that an ObservedAtom is 1.0, forcing others to 0.0 */
		i = 0;
		for (GroundDomainRangeConstraint con : constraintSet) {
			constrainedRVSet.clear();
			varsAreFree = true;
			for (GroundAtom atom : con.getAtoms())
				if (atom instanceof ObservedAtom && atom.getValue() != 0.0)
					varsAreFree = false;
				else if (atom instanceof RandomVariableAtom)
					constrainedRVSet.add((RandomVariableAtom) atom);
			
			if (varsAreFree) {
				rvBlocks[i] = new RandomVariableAtom[constrainedRVSet.size()];
				int j = 0;
				for (RandomVariableAtom atom : constrainedRVSet) {
					rvBlocks[i][j++] = atom;
					if (prepareRVMap)
						rvMap.put(atom, i);
				}
				
				exactlyOne[i] = con.getConstraintDefinition().getComparator().equals(FunctionComparator.Equality);
			}
			else {
				rvBlocks[i] = new RandomVariableAtom[0];
				/*
				 * Sets to true regardless of constraint type to avoid extra steps
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
		
		/* Collects GroundCompatibilityKernels incident on each block of RandomVariableAtoms */
		incidentGKs = new GroundCompatibilityKernel[rvBlocks.length][];
		Set<GroundCompatibilityKernel> incidentGKSet = new HashSet<GroundCompatibilityKernel>();
		for (i = 0; i < rvBlocks.length; i++) {
			incidentGKSet.clear();
			for (RandomVariableAtom atom : rvBlocks[i])
				for (GroundKernel incidentGK : atom.getRegisteredGroundKernels())
					if (incidentGK instanceof GroundCompatibilityKernel)
						incidentGKSet.add((GroundCompatibilityKernel) incidentGK);
			
			incidentGKs[i] = new GroundCompatibilityKernel[incidentGKSet.size()];
			int j = 0;
			for (GroundCompatibilityKernel incidentGK : incidentGKSet)
				incidentGKs[i][j++] = incidentGK;
		}
	}
	
	public RandomVariableAtom[][] getRVBlocks() {
		return rvBlocks;
	}
	
	public Map<RandomVariableAtom, Integer> getRVMap() {
		return rvMap;
	}
	
	public GroundCompatibilityKernel[][] getIncidentGKs() {
		return incidentGKs;
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
