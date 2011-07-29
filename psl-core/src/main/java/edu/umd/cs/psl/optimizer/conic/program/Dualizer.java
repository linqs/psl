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
package edu.umd.cs.psl.optimizer.conic.program;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dualizer {
	
	private static final Logger log = LoggerFactory.getLogger(Dualizer.class);
	
	private ConicProgram primalData, dualData;
	private Map<Variable, LinearConstraint> primalVarsToDualCons;
	private Map<LinearConstraint, Variable> primalConsToDualVars;
	private Map<Variable, Double> lowerBounds, upperBounds;
	private Map<LinearConstraint, VariablePair> varPairs;

	public Dualizer(ConicProgram data) {
		primalData = data;
		dualData = new ConicProgram();
		primalVarsToDualCons = new HashMap<Variable, LinearConstraint>();
		primalConsToDualVars = new HashMap<LinearConstraint, Variable>();
		lowerBounds = new HashMap<Variable, Double>();
		upperBounds = new HashMap<Variable, Double>();
		varPairs = new HashMap<LinearConstraint, VariablePair>();
		
		Variable dualVar;
		LinearConstraint dualCon;
		Double lowerBound, upperBound, newBound, coeff;
		int numConstraints;
		
		log.trace("Starting first pass.");
		int i = 0;
		
		for (Cone c : primalData.getNonNegativeOrthantCones()) {
			Variable primalVar = ((NonNegativeOrthantCone) c).getVariable();
			numConstraints = primalVar.getLinearConstraints().size();
			if (numConstraints > 1) {
				dualCon = new LinearConstraint(dualData);
				dualCon.setConstrainedValue(primalVar.getObjectiveCoefficient());
				dualCon.addVariable(new NonNegativeOrthantCone(dualData).getVariable(), 1.0);
				primalVarsToDualCons.put(primalVar, dualCon);
			}
			else if (numConstraints == 1) {
				LinearConstraint primalCon = primalVar.getLinearConstraints().iterator().next();
				dualVar = primalConsToDualVars.get(primalCon);
				if (dualVar == null) {
					dualVar = new NonNegativeOrthantCone(dualData).getVariable();
					primalConsToDualVars.put(primalCon, dualVar);
				}
				
				coeff = primalCon.getVariables().get(primalVar);

				newBound = primalVar.getObjectiveCoefficient() / coeff;
				
				if (coeff > 0) {
					upperBound = upperBounds.get(dualVar);
					if (upperBound == null) {
						upperBound = Double.POSITIVE_INFINITY;
					}
					if (newBound < upperBound) {
						upperBounds.put(dualVar, newBound);
					}
				}
				else if (coeff < 0) {
					lowerBound = lowerBounds.get(dualVar);
					if (lowerBound == null) {
						lowerBound = Double.NEGATIVE_INFINITY;
					}
					if (newBound > lowerBound) {
						lowerBounds.put(dualVar, newBound);
					}
				}
				else
					throw new IllegalStateException("Unexpected state.");
			}
			if (++i % 1000 == 0)
				log.trace("Finished {} variables.", i);
		}
		
		log.trace("Starting second pass.");
		
		for (LinearConstraint con : data.getConstraints()) {
			LinearConstraint primalCon = (LinearConstraint) con;
			dualVar = primalConsToDualVars.get(primalCon);
			/* If Lagrange multiplier is unbounded */
			if (dualVar == null) {
				/* Checks for existing pair of non-negative variables for this multiplier */
				VariablePair pair = varPairs.get(con);
				if (pair == null) {
					pair = new VariablePair();
					pair.positive = new NonNegativeOrthantCone(dualData).getVariable();
					pair.positive.setObjectiveCoefficient(-1 * primalCon.getConstrainedValue());
					pair.negative = new NonNegativeOrthantCone(dualData).getVariable();
					pair.negative.setObjectiveCoefficient(-1 * primalCon.getConstrainedValue());
					varPairs.put(con, pair);
				}
				/* Adds two variables with opposite multipliers to match unbounded variable */
				for (Variable primalVar : primalCon.getVariables().keySet()) {
					dualCon = primalVarsToDualCons.get(primalVar);
					if (dualCon != null) {
						coeff = primalCon.getVariables().get(primalVar);
						dualCon.addVariable(pair.positive, coeff);
						dualCon.addVariable(pair.negative, -1 * coeff);
					}
				}
			}
			else {
				lowerBound = lowerBounds.get(dualVar);
				/* If x only has an upper bound, flip it and shift it to be x' >= 0 */
				if (lowerBound == null) {
					dualVar.setObjectiveCoefficient(primalCon.getConstrainedValue());
					upperBound = upperBounds.get(dualVar);
					for (Variable primalVar : primalCon.getVariables().keySet()) {
						dualCon = primalVarsToDualCons.get(primalVar);
						if (dualCon != null) {
							coeff = primalCon.getVariables().get(primalVar);
							dualCon.addVariable(dualVar, -1*coeff);
							dualCon.setConstrainedValue(dualCon.getConstrainedValue() - coeff * upperBound);
						}
					}
				}
				else {
					dualVar.setObjectiveCoefficient(-1 * primalCon.getConstrainedValue());
					for (Variable primalVar : primalCon.getVariables().keySet()) {
						dualCon = primalVarsToDualCons.get(primalVar);
						if (dualCon != null) {
							coeff = primalCon.getVariables().get(primalVar);
							dualCon.addVariable(dualVar, coeff);
							if (lowerBound != 0) {
								dualCon.setConstrainedValue(dualCon.getConstrainedValue() - coeff * lowerBound);
							}
						}
					}
					
					upperBound = upperBounds.get(dualVar);
					if (upperBound != null) {
						upperBound -= lowerBound;
						dualCon = new LinearConstraint(dualData);
						dualCon.addVariable(dualVar, 1.0);
						dualVar = new NonNegativeOrthantCone(dualData).getVariable();
						dualVar.setObjectiveCoefficient(0.0);
						dualCon.addVariable(dualVar, 1.0);
						dualCon.setConstrainedValue(upperBound);
					}
				}
			}
		}
	}
	
	public ConicProgram getData() {
		return dualData;
	}
	
	public void updateData() {
		// TODO: Fill in implicit values.
		Variable primalVar, dualVar;
		LinearConstraint dualCon;
		
		for (Cone c : primalData.getNonNegativeOrthantCones()) {
			primalVar = ((NonNegativeOrthantCone) c).getVariable();
			dualCon = primalVarsToDualCons.get(primalVar);
			if (dualCon != null)
				primalVar.setValue(-1.0*dualCon.getLagrange());
		}
	}
	
	private class VariablePair {
		private Variable positive;
		private Variable negative;
	}
}
