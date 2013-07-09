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
package edu.umd.cs.psl.optimizer.conic.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramEvent;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramListener;
import edu.umd.cs.psl.optimizer.conic.program.Entity;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class Dualizer implements ConicProgramListener {
	
	private boolean checkedOut;
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
	}
	
	private ConicProgram primalProgram, dualProgram;
	private Map<Variable, LinearConstraint> primalVarsToDualCons;
	private Map<Variable, Variable> primalVarsToDualVars;
	private Map<LinearConstraint, Variable> primalConsToDualVars;
	private Map<LinearConstraint, SOCVariablePair> varPairs;
	
	private Set<Cone> conesToDelete;
	private Set<LinearConstraint> constraintsToDelete;
	
	private Set<Cone> newCones;
	private Set<LinearConstraint> newConstraints;
	
	
	public Dualizer(ConicProgram program) {
		primalProgram = program;
		
		dualProgram = new ConicProgram();
		primalVarsToDualCons = new HashMap<Variable, LinearConstraint>();
		primalVarsToDualVars = new HashMap<Variable, Variable>();
		primalConsToDualVars = new HashMap<LinearConstraint, Variable>();
		varPairs = new HashMap<LinearConstraint, SOCVariablePair>();
		
		conesToDelete = new HashSet<Cone>();
		constraintsToDelete = new HashSet<LinearConstraint>();
		
		newCones = new HashSet<Cone>(program.getCones());
		newConstraints = new HashSet<LinearConstraint>(program.getConstraints());
		
		checkedOut = false;
		
		primalProgram.registerForConicProgramEvents(this);
		dualProgram.registerForConicProgramEvents(this);
	}
	
	public static boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}
	
	public ConicProgram getDualProgram() {
		return dualProgram;
	}
	
	public void verifyCheckedOut() {
		if (!checkedOut)
			throw new IllegalStateException("Dual program is not checked out.");
	}
	
	public void verifyCheckedIn() {
		if (checkedOut)
			throw new IllegalStateException("Dual program is not checked in.");
	}

	@Override
	public void notify(ConicProgram sender, ConicProgramEvent event, Entity entity, Object... data) {
		Variable primalVar, dualVar;
		LinearConstraint primalCon, dualCon;
		SOCVariablePair socPair;
		
		if (primalProgram.equals(sender)) {
			switch (event) {
			case MatricesCheckedIn:
				verifyCheckedIn();
				break;
			case ConCreated:
				newConstraints.add((LinearConstraint) entity);
				break;
			case ConDeleted:
				if (!newConstraints.remove(entity)) {
					dualVar = primalConsToDualVars.get(entity);
					if (dualVar != null) {
						conesToDelete.add(dualVar.getCone());
						primalConsToDualVars.remove(entity);
					}
					else {
						dualVar = varPairs.get(entity).inner;
						conesToDelete.add(dualVar.getCone());
						varPairs.remove(entity);
					}
					
					/*
					 * Checks whether the linear constraint had a (primal) slack variable
					 * (marked as such in the current dualization).
					 * If it did, marks the dual variable corresponding to the slack
					 * variable for deletion and marks the slack variable as a new
					 * variable
					 */
					for (Variable v : ((Set<Variable>) data[0])) {
						if (primalVarsToDualVars.get(v) != null) {
							conesToDelete.add(primalVarsToDualVars.get(v).getCone());
							primalVarsToDualVars.remove(v);
							newCones.add(v.getCone());
						}
					}
				}
				break;
			case NNOCCreated:
				newCones.add((NonNegativeOrthantCone) entity);
				break;
			case NNOCDeleted:
				if (!newCones.remove(entity)) {
					primalVar = ((NonNegativeOrthantCone) entity).getVariable();
					dualVar = primalVarsToDualVars.get(primalVar);
					
					/*
					 * If it's a slack variable, needs to ensure that the corresponding
					 * primal constraint will be reprocessed on next check out
					 */
					if (dualVar != null) {
						primalCon = primalVar.getLinearConstraints().iterator().next();
						conesToDelete.add(primalConsToDualVars.get(primalCon).getCone());
						primalConsToDualVars.remove(primalCon);
						newConstraints.add(primalCon);
						
						primalVarsToDualVars.remove(primalVar);
						conesToDelete.remove(dualVar);
					}
					/* Otherwise, marks the dual constraint and the lower bound on the Lagrange multiplier for deletion */
					else {
						dualCon = primalVarsToDualCons.get(primalVar);
						for (Variable var : dualCon.getVariables().keySet()) {
							boolean mappedTo = false;
							for (LinearConstraint con : primalVar.getLinearConstraints()) {
								socPair = varPairs.get(con);
								if (var.equals(primalConsToDualVars.get(con)) || (socPair != null && var.equals(socPair.inner))) {
									mappedTo = true;
									break;
								}
							}
							
							if (!mappedTo) {
								conesToDelete.add(var.getCone());
							}
						}
						constraintsToDelete.add(dualCon);
						primalVarsToDualCons.remove(primalVar);
					}
				}
				else {
					if (primalVarsToDualCons.get(((NonNegativeOrthantCone) entity).getVariable()) != null) {
						throw new IllegalStateException();
					}
				}
				break;
			case ObjCoeffChanged:
				primalVar = (Variable) entity;
				if (!newCones.remove(primalVar.getCone())) {
					dualVar = primalVarsToDualVars.get(primalVar);
					
					/*
					 * If it's a slack variable, needs to ensure that the corresponding
					 * primal constraint will be reprocessed on next check out
					 */
					if (dualVar != null) {
						primalCon = primalVar.getLinearConstraints().iterator().next();
						conesToDelete.add(primalConsToDualVars.get(primalCon).getCone());
						primalConsToDualVars.remove(primalCon);
						newConstraints.add(primalCon);
						
						primalVarsToDualVars.remove(primalVar);
						conesToDelete.remove(dualVar);
					}
					/* Otherwise, marks the dual constraint and the lower bound on the Lagrange multiplier for deletion */
					else {
						dualCon = primalVarsToDualCons.get(primalVar);
						for (Variable var : dualCon.getVariables().keySet()) {
							boolean mappedTo = false;
							for (LinearConstraint con : primalVar.getLinearConstraints()) {
								socPair = varPairs.get(con);
								if (var.equals(primalConsToDualVars.get(con)) || (socPair != null && var.equals(socPair.inner))) {
									mappedTo = true;
									break;
								}
							}
							
							if (!mappedTo) {
								conesToDelete.add(var.getCone());
							}
						}
						constraintsToDelete.add(dualCon);
						primalVarsToDualCons.remove(primalVar);
					}
				}
				else {
					if (primalVarsToDualCons.get(entity) != null) {
						throw new IllegalStateException();
					}
				}
				newCones.add(primalVar.getCone());
				break;
			}
		}
		else if (dualProgram.equals(sender)) {
			switch (event) {
			case MatricesCheckedIn:
				break;
			case MatricesCheckedOut:
				verifyCheckedOut();
				break;
			default:
				throw new UnsupportedOperationException("Dual program cannot be modified directly.");
			}
		}
		else
			throw new IllegalArgumentException("Unknown sender.");
	}
	
	public void checkOutProgram() {
		verifyCheckedIn();
		primalProgram.verifyCheckedOut();
		
		Variable slack, primalVar, dualVar;
		LinearConstraint primalCon, dualCon;
		Double coeff;
		
		dualProgram.unregisterForConicProgramEvents(this);
		
		/* Deletes cones marked for deletion */
		for (Cone cone : conesToDelete)
			cone.delete();
				
		/* Deletes constraints marked for deletion */
		for (LinearConstraint con : constraintsToDelete)
			con.delete();
		
		/* Processes new constraints */
		for (LinearConstraint con : newConstraints) {
			slack = null;
			for (Variable slackCandidate : con.getVariables().keySet()) {
				if (slackCandidate.getLinearConstraints().size() == 1
						&& slackCandidate.getCone() instanceof NonNegativeOrthantCone
						&& slackCandidate.getObjectiveCoefficient() == 0.0) {
					slack = slackCandidate;
					break;
				}
			}
			
			/* If a slack variable is found, it will be represented as a dual slack variable */
			if (slack != null) {
				dualVar = dualProgram.createNonNegativeOrthantCone().getVariable();
				dualVar.setObjectiveCoefficient(con.getConstrainedValue());
				primalVarsToDualVars.put(slack, dualVar);
				primalConsToDualVars.put(con, dualVar);
			}
			/*
			 * Otherwise, the Lagrange multiplier of the primal constraint cannot, in
			 * general, be bounded at zero. Therefore, its value will be represented
			 * by the inner variable of a second-order cone.
			 */
			else {
				SOCVariablePair pair = new SOCVariablePair();
				SecondOrderCone cone = dualProgram.createSecondOrderCone(2);
				for (Variable v : cone.getVariables()) {
					if (!cone.getNthVariable().equals(v)) {
						pair.inner = v;
						break;
					}
				}
				pair.inner.setObjectiveCoefficient(con.getConstrainedValue());
				varPairs.put(con, pair);
			}
		}
		
		/* Processes new cones */
		for (Cone cone : newCones) {
			if (cone instanceof NonNegativeOrthantCone) {
				primalVar = ((NonNegativeOrthantCone) cone).getVariable();
				/*
				 * If the variable isn't marked as a slack variable, makes a constraint
				 * in the dualized program for it. Immediately creates a new variable
				 * to keep the new constraint's Lagrange multiplier non-negative (to
				 * match the primal variable in the primal program).
				 */
				if (primalVarsToDualVars.get(primalVar) == null) {
					dualCon = dualProgram.createConstraint();
					dualCon.setConstrainedValue(-1 * primalVar.getObjectiveCoefficient());
					primalVarsToDualCons.put(primalVar, dualCon);
					dualVar = dualProgram.createNonNegativeOrthantCone().getVariable();
					dualCon.setVariable(dualVar, -1.0);
					dualVar.setObjectiveCoefficient(0.0);
				}
			}
			else
				throw new IllegalStateException("Unsupported cone type." +
					"Only NonNegativeOrthantCone is supported.");
		}
		
		/* Puts it all together */
		for (LinearConstraint pCon : primalProgram.getConstraints()) {
			dualVar = primalConsToDualVars.get(pCon);
			if (dualVar == null)
				dualVar = varPairs.get(pCon).inner;
			
			for (Map.Entry<Variable, Double> e : pCon.getVariables().entrySet()) {
				dualCon = primalVarsToDualCons.get(e.getKey());
				if (dualCon != null)
					dualCon.setVariable(dualVar, e.getValue());
			}
		}
		
		/*
		 * Scales and flips constraints in dual program to make slacks have
		 * coefficients of 1.
		 */
		for (Map.Entry<Variable, Variable> e : primalVarsToDualVars.entrySet()) {
			primalCon = e.getKey().getLinearConstraints().iterator().next();
			coeff = primalCon.getVariables().get(e.getKey());
			if (coeff != 1.0) {
				dualVar = e.getValue();
				dualVar.setObjectiveCoefficient(primalCon.getConstrainedValue() / coeff);
				for (Variable pVar : primalCon.getVariables().keySet()) {
					dualCon = primalVarsToDualCons.get(pVar);
					if (dualCon != null)
						dualCon.setVariable(dualVar, primalCon.getVariables().get(pVar) / coeff);
				}
			}
		}
		
		dualProgram.registerForConicProgramEvents(this);

		conesToDelete.clear();
		constraintsToDelete.clear();
		newCones.clear();
		newConstraints.clear();
		
		checkedOut = true;
	}
	
	public void checkInProgram() {
		verifyCheckedOut();
		dualProgram.verifyCheckedIn();
		
		DoubleMatrix1D x = primalProgram.getX();
		
		for (Map.Entry<Variable, LinearConstraint> e : primalVarsToDualCons.entrySet()) {
			x.set(primalProgram.getIndex(e.getKey()), e.getValue().getLagrange());
		}
		
		for (Map.Entry<Variable, Variable> e : primalVarsToDualVars.entrySet()) {
			x.set(primalProgram.getIndex(e.getKey()), e.getValue().getDualValue());
		}
		
		checkedOut = false;
	}
	
	private class SOCVariablePair {
		private Variable inner;
	}
}
