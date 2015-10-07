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
package edu.umd.cs.psl.model.kernel.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.NumericUtilities;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.AvgConjRule;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.FunctionVariable;

/**
 * A CompatibilityRuleKernel for averaging conjunction rules.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class CompatibilityAveragingRuleKernel extends CompatibilityRuleKernel {
	
	protected PositiveWeight weight;
	protected boolean squared;
	
	protected final List<Atom> posLiterals;
	protected final List<Atom> negLiterals;
	protected final List<Double> posLiteralsWeights; 
	protected final List<Double> negLiteralsWeights;

	public CompatibilityAveragingRuleKernel(AvgConjRule f, double w, boolean squared) {
		super(f, w, squared); //create DNF, etc as required by the superclass.
		weight = new PositiveWeight(w);
		this.squared = squared;
		AvgConjRule acr = f;
		
		/*
		 * At this point, we negate the rule to create the hinge function for
		 * its distance to satisfaction. The super class has also negated it at
		 * the creation of the object, when creating its FormulaAnalysis object.
		 * Negative and positive are therefore reversed below.
		 */
		posLiterals = acr.getNegLiterals();
		negLiterals = acr.getPosLiterals();
		posLiteralsWeights = acr.getNegLiteralsWeights();
		negLiteralsWeights = acr.getPosLiteralsWeights();
	}

	@Override
	protected GroundCompatibilityRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
		throw new IllegalStateException("groundFormulaInstance should not be called for a CompatibilityAveragingRuleKernel, as it does not handle literal weights!");
	}
	
	protected GroundCompatibilityRule groundWeightedFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals, List<Double> posLiteralsWeights, List<Double> negLiteralsWeights) {
		return new GroundWeightedCompatibilityRule(this, posLiterals, negLiterals, squared, posLiteralsWeights, negLiteralsWeights);
	}
			
	@Override
	public Kernel clone() {
		return new CompatibilityAveragingRuleKernel((AvgConjRule) formula, weight.getWeight(), squared);
	}
	
	@Override
	protected int groundFormula(AtomManager atomManager, GroundKernelStore gks, ResultList res,  VariableAssignment var) {
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
				//we assume that the DNF literals are aligned with this object's literals, and in particular, their weights.
				assert clause.getPosLiterals().get(j).equals(this.posLiterals.get(j));
			}
			
			for (int j = 0; j < clause.getNegLiterals().size(); j++) {
				atom = groundAtom(atomManager, clause.getNegLiterals().get(j), res, i, var);
				if (atom instanceof RandomVariableAtom)
					worstCaseValues.put(atom.getVariable(), 0.0);
				else
					worstCaseValues.put(atom.getVariable(), atom.getValue());
				negLiterals.add(atom);
				//we assume that the DNF literals are aligned with this object's literals, and in particular, their weights.
				assert clause.getNegLiterals().get(j).equals(this.negLiterals.get(j));
			}
			
			AbstractGroundRule groundRule = groundWeightedFormulaInstance(posLiterals, negLiterals, posLiteralsWeights, negLiteralsWeights);
			FunctionTerm function = groundRule.getFunction();
			worstCaseValue = function.getValue(worstCaseValues, false);
			if (worstCaseValue > NumericUtilities.strictEpsilon
					&& (!function.isConstant() || !(groundRule instanceof GroundCompatibilityKernel))
					&& !gks.containsGroundKernel(groundRule)) {
				gks.addGroundKernel(groundRule);
				numGroundingsAdded++;
			}
			/* If the ground kernel is not actually added, unregisters it from atoms */
			else
				for (GroundAtom incidentAtom : groundRule.getAtoms())
					incidentAtom.unregisterGroundKernel(groundRule);
			
			posLiterals.clear();
			negLiterals.clear();
			worstCaseValues.clear();
		}
		
		return numGroundingsAdded;
	}
}
