/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.application.util;

import java.util.ArrayList;
import java.util.List;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;

/**
 * Static utilities for common {@link GroundKernel} tasks.
 */
public class GroundKernels {

	/**
	 * Sums the total weighted incompatibility of an iterable container of
	 * {@link GroundCompatibilityKernel GroundCompatibilityKernels}.
	 * 
	 * @param gks  the GroundCompatibilityKernels
	 * @return the total weighted incompatibility
	 * @see GroundCompatibilityKernel#getIncompatibility()
	 * @see GroundCompatibilityKernel#getWeight()
	 */
	public static double getTotalWeightedIncompatibility(Iterable<GroundCompatibilityKernel> gks) {
		double totalInc = 0.0;
		for (GroundCompatibilityKernel gk : gks)
			totalInc += gk.getIncompatibility() * gk.getWeight().getWeight();
		return totalInc;
	}
	
	/**
	 * Sums the total weighted compatibility (1 - incompatibility) of an iterable
	 * container of {@link GroundCompatibilityKernel GroundCompatibilityKernels}.
	 * 
	 * WARNING: This method does not account for GroundCompatibilityKernels that
	 * were not grounded because they are trivially satisfied.
	 * 
	 * @param gks  the GroundCompatibilityKernels
	 * @return the total weighted compatibility
	 * @see GroundCompatibilityKernel#getIncompatibility()
	 * @see GroundCompatibilityKernel#getWeight()
	 */
	public static double getTotalWeightedCompatibility(Iterable<GroundCompatibilityKernel> gks) {
		double totalInc = 0.0;
		for (GroundCompatibilityKernel gk : gks)
			totalInc += (1 - gk.getIncompatibility()) * gk.getWeight().getWeight();
		return totalInc;
	}
	
	/**
	 * Computes the expected total weighted incompatibility of an iterable 
	 * container of {@link GroundCompatibilityKernel GroundCompatibilityKernels}
	 * from independently rounding each {@link RandomVariableAtom} to 1.0 or 0.0
	 * with probability equal to its current truth value.
	 * 
	 * WARNING: the result of this function is incorrect if the RandomVariableAtoms
	 * are subject to any {@link GroundConstraintKernel GroundConstraintKernels}.
	 * 
	 * @param gks  the GroundCompatibilityKernels
	 * @return the expected total weighted incompatibility
	 * @see GroundCompatibilityKernel#getIncompatibility()
	 * @see GroundCompatibilityKernel#getWeight()
	 */
	public static double getExpectedTotalWeightedIncompatibility(Iterable<GroundCompatibilityKernel> gks) {
		double totalInc = 0.0;
		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();
		for (GroundCompatibilityKernel gck : gks) {
			double inc = 0.0;
			
			/* Collects RandomVariableAtoms */
			for (GroundAtom atom : gck.getAtoms())
				if (atom instanceof RandomVariableAtom)
					atoms.add((RandomVariableAtom) atom);
			
			/* Collects truth values */
			double[] truthValues = new double[atoms.size()];
			for (int i = 0; i < truthValues.length; i++)
				truthValues[i] = atoms.get(i).getValue();
			
			/* Sums over settings */
			for (int i = 0; i < Math.pow(2, atoms.size()); i++) {
				double assignmentProb = 1.0;
				
				/* Sets assignment and computes probability */
				for (int j = 0; j < atoms.size(); j++) {
					int assignment = ((i >> j) & 1);
					atoms.get(j).setValue(assignment);
					assignmentProb *= (assignment == 1) ? truthValues[j] : 1 - truthValues[j];
				}
				
				inc += assignmentProb * gck.getIncompatibility();
			}
			
			/* Restores truth values */
			for (int i = 0; i < atoms.size(); i++)
				atoms.get(i).setValue(truthValues[i]);
			
			/* Clears atom list */
			atoms.clear();
			
			/* Weights and adds to total */
			inc *= gck.getWeight().getWeight();
			totalInc += inc;
		}
		return totalInc;
	}
	
	/**
	 * Computes the expected total weighted compatibility (1 - incompatibility)
	 * of an iterable container of {@link GroundCompatibilityKernel GroundCompatibilityKernels}
	 * from independently rounding each {@link RandomVariableAtom} to 1.0 or 0.0
	 * with probability equal to its current truth value.
	 * 
	 * WARNING: the result of this function is incorrect if the RandomVariableAtoms
	 * are subject to any {@link GroundConstraintKernel GroundConstraintKernels}.
	 * 
	 * WARNING: This method does not account for GroundCompatibilityKernels that
	 * were not grounded because they are trivially satisfied.
	 * 
	 * @param gks  the GroundCompatibilityKernels
	 * @return the expected total weighted incompatibility
	 * @see GroundKernels#getExpectedWeightedCompatibility(GroundCompatibilityKernel)
	 */
	public static double getExpectedTotalWeightedCompatibility(Iterable<GroundCompatibilityKernel> gks) {
		double totalInc = 0.0;
		for (GroundCompatibilityKernel gck : gks)
			totalInc += getExpectedWeightedCompatibility(gck);
		return totalInc;
	}
	
	/**
	 * Computes the expected weighted compatibility (1 - incompatibility)
	 * of a {@link GroundCompatibilityKernel}
	 * from independently rounding each {@link RandomVariableAtom} to 1.0 or 0.0
	 * with probability equal to its current truth value.
	 * 
	 * WARNING: the result of this function is incorrect if the RandomVariableAtoms
	 * are subject to any {@link GroundConstraintKernel GroundConstraintKernels}.
	 * 
	 * @param gks  the GroundCompatibilityKernels
	 * @return the expected weighted compatibility
	 * @see GroundCompatibilityKernel#getIncompatibility()
	 * @see GroundCompatibilityKernel#getWeight()
	 */
	public static double getExpectedWeightedCompatibility(GroundCompatibilityKernel gck) {
		double inc = 0.0;
		List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();
		
		/* Collects RandomVariableAtoms */
		for (GroundAtom atom : gck.getAtoms())
			if (atom instanceof RandomVariableAtom)
				atoms.add((RandomVariableAtom) atom);
		
		/* Collects truth values */
		double[] truthValues = new double[atoms.size()];
		for (int i = 0; i < truthValues.length; i++)
			truthValues[i] = atoms.get(i).getValue();
		
		/* Sums over settings */
		for (int i = 0; i < Math.pow(2, atoms.size()); i++) {
			double assignmentProb = 1.0;
			
			/* Sets assignment and computes probability */
			for (int j = 0; j < atoms.size(); j++) {
				int assignment = ((i >> j) & 1);
				atoms.get(j).setValue(assignment);
				assignmentProb *= (assignment == 1) ? truthValues[j] : 1 - truthValues[j];
			}
			
			inc += assignmentProb * (1 - gck.getIncompatibility());
		}
		
		/* Restores truth values */
		for (int i = 0; i < atoms.size(); i++)
			atoms.get(i).setValue(truthValues[i]);
		
		/* Weights and returns */
		return inc * gck.getWeight().getWeight();
	}
	
	/**
	 * Computes the Euclidean norm of the infeasibilities of an iterable container
	 * of {@link GroundConstraintKernel GroundConstraintKernels}.
	 * 
	 * @param gks  the GroundConstraintKernels
	 * @return the Euclidean norm of the infeasibilities
	 * @see GroundConstraintKernel#getInfeasibility()
	 */
	public static double getInfeasibilityNorm(Iterable<GroundConstraintKernel> gks) {
		double inf, norm = 0.0;
		for (GroundConstraintKernel gk : gks) {
			inf = gk.getInfeasibility();
			norm += inf * inf;
		}
		return Math.sqrt(norm);
	}
}
