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
package edu.umd.cs.psl.reasoner.conic;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.RotatedSecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.MaxFunction;
import edu.umd.cs.psl.reasoner.function.PowerOfTwo;

class FunctionConicProgramProxy extends ConicProgramProxy {

	/* Variables when using any cone */
	protected Variable featureVar;
	/* Variables when using a second-order cone */
	protected Variable squaredFeatureVar, innerFeatureVar, innerSquaredVar, outerSquaredVar;
	/* Variables when using a rotated second-order cone */
	protected Variable rotSquaredFeatureVar, otherOuterVar;
	/* Constraints when using a second-order cone */
	protected LinearConstraint innerFeatureCon, innerSquaredCon, outerSquaredCon;
	/* Constraints when using a rotated second-order cone */
	protected LinearConstraint holdOtherOuterVar;
	protected Vector<ConstraintConicProgramProxy> constraints;
	protected boolean initialized = false;
	protected boolean squared;
	
	protected static final Set<ConeType> RSOCType;
	static {
		RSOCType = new HashSet<ConeType>();
		RSOCType.add(ConeType.RotatedSecondOrderCone);
	}
	
	FunctionConicProgramProxy(ConicReasoner reasoner, GroundCompatibilityKernel gk) {
		super(reasoner, gk);
		updateGroundKernel(gk);
	}
	
	protected void initialize() {
		if (!initialized) {
			constraints = new Vector<ConstraintConicProgramProxy>(1);
			
			if (squared) {
				/* If the solver supports rotated second-order cones, uses them... */
				if (reasoner.solver.supportsConeTypes(RSOCType)) {
					RotatedSecondOrderCone rsoc = reasoner.program.createRotatedSecondOrderCone(3);
					for (Variable v : rsoc.getVariables()) {
						if (v.equals(rsoc.getNthVariable()))
							rotSquaredFeatureVar = v;
						else if (v.equals(rsoc.getNMinus1stVariable()))
							otherOuterVar = v;
						else
							featureVar = v;
					}

					featureVar.setObjectiveCoefficient(0.0);
					holdOtherOuterVar = reasoner.program.createConstraint();
					holdOtherOuterVar.setVariable(otherOuterVar, 1.0);
					holdOtherOuterVar.setConstrainedValue(0.5);
				}
				/* Else makes something equivalent using regular second-order cones... */
				else {
					featureVar = reasoner.program.createNonNegativeOrthantCone().getVariable();
					featureVar.setObjectiveCoefficient(0.0);
					squaredFeatureVar = reasoner.program.createNonNegativeOrthantCone().getVariable();
					SecondOrderCone soc = reasoner.program.createSecondOrderCone(3);
					outerSquaredVar = soc.getNthVariable();
					for (Variable v : soc.getVariables()) {
						if (!v.equals(outerSquaredVar))
							if (innerFeatureVar == null)
								innerFeatureVar = v;
							else
								innerSquaredVar = v;
					}
					
					innerFeatureCon = reasoner.program.createConstraint();
					innerFeatureCon.setVariable(featureVar, 1.0);
					innerFeatureCon.setVariable(innerFeatureVar, -1.0);
					innerFeatureCon.setConstrainedValue(0.0);
					
					innerSquaredCon = reasoner.program.createConstraint();
					innerSquaredCon.setVariable(innerSquaredVar, 1.0);
					innerSquaredCon.setVariable(squaredFeatureVar, 0.5);
					innerSquaredCon.setConstrainedValue(0.5);
					
					outerSquaredCon = reasoner.program.createConstraint();
					outerSquaredCon.setVariable(outerSquaredVar, 1.0);
					outerSquaredCon.setVariable(squaredFeatureVar, -0.5);
					outerSquaredCon.setConstrainedValue(0.5);
				}
			}
			else
				featureVar = reasoner.program.createNonNegativeOrthantCone().getVariable();
			
			initialized = true;
		}
		else {
			throw new IllegalStateException("ConicProgramProxy has already been initialized.");
		}
	}
	
	protected void setWeight(double weight) {
		if (squared) {
			if (reasoner.solver.supportsConeTypes(RSOCType))
				rotSquaredFeatureVar.setObjectiveCoefficient(weight);
			else
				squaredFeatureVar.setObjectiveCoefficient(weight);
		}
		else
			featureVar.setObjectiveCoefficient(weight);
	}
	
	void updateGroundKernelWeight(GroundCompatibilityKernel gk) {
		if (gk.getWeight().getWeight() == 0) {
			if (initialized)
				remove();
		}
		else {
			if (!initialized)
				updateGroundKernel(gk);
			else
				setWeight(gk.getWeight().getWeight());
		}
	}
	
	void updateGroundKernel(GroundCompatibilityKernel gk) {
		if (gk.getWeight().getWeight() == 0) {
			if (initialized)
				remove();
		}
		else {
			FunctionTerm function = gk.getFunctionDefinition();
			boolean nowSquared;
			if (function instanceof PowerOfTwo) {
				nowSquared = true;
				function = ((PowerOfTwo) function).getInnerFunction();
			}
			else
				nowSquared = false;
			
			if (squared != nowSquared)
				remove();
			
			squared = nowSquared;
			
			if (!initialized) {
				initialize();
			}
			else {
				deleteConstraints();
			}
			addFunctionTerm(function);
			setWeight(gk.getWeight().getWeight());
		}
	}
	
	protected void addFunctionTerm(FunctionTerm fun) {
		if (fun instanceof MaxFunction) {
			for (FunctionTerm t : (MaxFunction)fun)
				addFunctionTerm(t);
		}
		else if (!fun.isConstant() || fun.getValue() != 0.0) {
			ConstraintTerm con;
			FunctionSummand featureSummand;
			
			featureSummand = new FunctionSummand(-1.0, new ConicReasonerSingleton(featureVar));
			
			if (fun.isConstant()) {
				con = new ConstraintTerm(featureSummand, FunctionComparator.SmallerThan, -1*fun.getValue());
			}
			else if (fun instanceof FunctionSum) {
				((FunctionSum)fun).add(featureSummand);
				con = new ConstraintTerm(fun, FunctionComparator.SmallerThan, 0.0);
			}
			else if (fun instanceof FunctionSummand) {
				FunctionSum sum = new FunctionSum();
				sum.add((FunctionSummand)fun);
				sum.add(featureSummand);
				con = new ConstraintTerm(sum, FunctionComparator.SmallerThan, 0.0);
			}
			else
				throw new IllegalArgumentException("Unsupported FunctionTerm: " + fun);
			
			constraints.add(new ConstraintConicProgramProxy(reasoner, con, kernel));
		}
	}

	@Override
	void remove() {
		if (initialized) {
			deleteConstraints();
			if (squared) {
				if (reasoner.solver.supportsConeTypes(RSOCType)) {
					holdOtherOuterVar.delete();
					rotSquaredFeatureVar.getCone().delete();
					rotSquaredFeatureVar = null;
					otherOuterVar = null;
					featureVar = null;
				}
				else {
					featureVar.getCone().delete();
					featureVar = null;
					innerFeatureCon.delete();
					innerFeatureCon = null;
					innerSquaredCon.delete();
					innerSquaredCon = null;
					outerSquaredCon.delete();
					outerSquaredCon = null;
					squaredFeatureVar.getCone().delete();
					squaredFeatureVar = null;
					outerSquaredVar.getCone().delete();
					outerSquaredVar = null;
				}
			}
			else {
				featureVar.getCone().delete();
				featureVar = null;
			}
			
			initialized = false;
		}
	}
	
	protected void deleteConstraints() {
		for (ConstraintConicProgramProxy con : constraints)
			con.remove();
		constraints.clear();
	}
}
