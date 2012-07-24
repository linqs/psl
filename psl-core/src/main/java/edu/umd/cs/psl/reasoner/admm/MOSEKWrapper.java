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
package edu.umd.cs.psl.reasoner.admm;

import java.util.Iterator;
import java.util.Vector;

import mosek.Env;
import mosek.Task;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.reasoner.Reasoner.DistributionType;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.reasoner.function.FunctionSingleton;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.MaxFunction;

public class MOSEKWrapper extends GroundKernelWrapper {
	
	static final Env env = new Env();
	static {
		env.init();
	}
	
	protected final double weight;
	protected final Vector<Double> coeffs;
	protected double constant;
	
	protected final boolean alwaysIterative;
	protected final boolean pessimistic;
	
	protected int[] xVar;
	protected int[] xSlackVar;
	protected int featureVar;
	protected int hingeSlackVar;
	protected int[] augLagVar;
	protected int[] sqFeatureVar;
	
	protected int[] xUpperBoundCon;
	/* augLagCon[0] sets augLagVar[1] = 0.5 */
	protected int[] augLagCon;
	protected int hingeCon;
	/* Sets sqFeatureVar[1] = 0.5 */
	protected int sqFeatureCon;
	
	protected Task task;
	
	protected MOSEKWrapper(ADMMReasoner reasoner, GroundCompatibilityKernel groundKernel, boolean alwaysIterative, boolean pessimistic) {
		super(reasoner, groundKernel);
		
		weight = groundKernel.getWeight().getWeight();
		coeffs = new Vector<Double>(x.size());
		constant = 0.0;
		
		this.alwaysIterative = alwaysIterative;
		this.pessimistic = pessimistic;
		
		FunctionTerm function = groundKernel.getFunctionDefinition();
		FunctionTerm innerFunction;
		FunctionTerm constantTerm;
		
		if (function instanceof MaxFunction) {
			if (((MaxFunction) function).size() != 2)
				throw new IllegalArgumentException("Max function must have two arguments.");
			innerFunction = ((MaxFunction) function).get(0);
			if (innerFunction.isConstant()) {
				constantTerm = innerFunction;
				innerFunction = ((MaxFunction) function).get(1);
			}
			else {
				constantTerm = ((MaxFunction) function).get(1);
			}
			
			if (!constantTerm.isConstant() || constantTerm.getValue() != 0)
				throw new IllegalArgumentException("Max function must have one variable function and 0.0 as arguments.");
			
			function = innerFunction;
		}
		else
			throw new IllegalArgumentException("Unexpected function.");
		
		/* Processes the function */
		if (function instanceof FunctionSum) {
			for (Iterator<FunctionSummand> itr = ((FunctionSum) function).iterator(); itr.hasNext(); ) {
				FunctionSummand summand = itr.next();
				FunctionSingleton term = summand.getTerm();
				if (term instanceof AtomFunctionVariable && !term.isConstant()) {
					addVariable((AtomFunctionVariable) term);
					coeffs.add(summand.getCoefficient());
				}
				else if (term.isConstant()) {
					constant += summand.getValue();
				}
				else
					throw new IllegalArgumentException("Unexpected summand.");
			}
		}
		else
			throw new IllegalArgumentException("Inner function must be a FunctionSum.");
		
		/* Sets up MOSEK problem if necessary */
		if (alwaysIterative || (x.size() > 2 || (x.size() > 1 && DistributionType.quadratic.equals(reasoner.getDistributionType())))) {
		
			/* Assigns indices to problem components */
			xVar = new int[x.size()];
			xSlackVar = new int[x.size()];
			xUpperBoundCon = new int[x.size()];
			for (int i = 0; i < xVar.length; i++) {
				xVar[i] = i;
				xSlackVar[i] = i+xVar.length;
				xUpperBoundCon[i] = i;
			}
			featureVar = 2 * x.size();
			hingeSlackVar = featureVar + 1;
			
			augLagVar = new int[x.size() + 2];
			for (int i = 0; i < augLagVar.length; i++) {
				augLagVar[i] = hingeSlackVar + i + 1;
			}
			
			sqFeatureVar = new int[2];
			sqFeatureVar[0] = augLagVar[augLagVar.length-1] + 1;
			sqFeatureVar[1] = sqFeatureVar[0] + 1;
			
			augLagCon = new int[x.size() + 1];
			for (int i = 0; i < augLagCon.length; i++) {
				augLagCon[i] = xUpperBoundCon.length + i;
			}
			
			hingeCon = augLagCon[augLagCon.length - 1] + 1;
			sqFeatureCon = hingeCon + 1;
			
			try {
				/* Rows in constraint matrix */
				int m = 2 * x.size() + 2;
				if (DistributionType.quadratic.equals(reasoner.getDistributionType()))
					m += 1;
				/* Columns in constraint matrix, i.e., variables */
				int n = 3 * x.size() + 4;
				if (DistributionType.quadratic.equals(reasoner.getDistributionType()))
					n += 2;
				
				/* Initializes task */
				task = new Task(env, m, n);
				task.putcfix(0.0);
				task.append(mosek.Env.accmode.con, m);
				task.append(mosek.Env.accmode.var, n);
	
				/* Sets task parameters */
				task.putdouparam(mosek.Env.dparam.intpnt_tol_rel_gap, 1e-8);
				task.putintparam(mosek.Env.iparam.intpnt_num_threads, 1);
//				task.putintparam(mosek.Env.iparam.presolve_use, mosek.Env.presolvemode.off.value);
				
				/* Sets up the variables and their upper bounds */
				for (int i = 0; i < x.size(); i++) {
					task.putbound(Env.accmode.var, xVar[i], Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					task.putbound(Env.accmode.var, xSlackVar[i], Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					task.putavec(mosek.Env.accmode.con, i, new int[] {xVar[i], xSlackVar[i]}, new double[] {1.0, 1.0});
					task.putbound(mosek.Env.accmode.con, i, Env.boundkey.fx, 1.0, 1.0);
				}
				
				/* Sets the hinge constraint */
				task.putbound(Env.accmode.var, hingeSlackVar, Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
				int[] indices = new int[x.size() + 2];
				for (int i = 0; i < x.size(); i++)
					indices[i] = xVar[i];
				indices[x.size()] = featureVar;
				indices[x.size() + 1] = hingeSlackVar;
				double[] rowCoeffs = new double[indices.length];
				for (int i = 0; i < x.size(); i++)
					rowCoeffs[i] = coeffs.get(i);
				rowCoeffs[x.size()] = -1.0;
				rowCoeffs[x.size() + 1] = 1.0;
				task.putavec(mosek.Env.accmode.con, hingeCon, indices, rowCoeffs);
				task.putbound(mosek.Env.accmode.con, hingeCon, Env.boundkey.fx, -1 * constant, -1 * constant);
				
				/* Squares the potential if needed */
				if (DistributionType.quadratic.equals(reasoner.getDistributionType())) {
					int[] potentialRSOCIndices = new int[] {sqFeatureVar[0], sqFeatureVar[1], featureVar};
					task.putbound(Env.accmode.var, sqFeatureVar[0], Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					task.putbound(Env.accmode.var, sqFeatureVar[1], Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					task.putbound(Env.accmode.var, featureVar, Env.boundkey.fr, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
					
					task.appendcone(Env.conetype.rquad, 0.0, potentialRSOCIndices);
					
					task.putcj(sqFeatureVar[0], groundKernel.getWeight().getWeight());
	
					task.putavec(mosek.Env.accmode.con, sqFeatureCon, new int[] {sqFeatureVar[1]}, new double[] {1.0});
					task.putbound(mosek.Env.accmode.con, sqFeatureCon, Env.boundkey.fx, 0.5, 0.5);
				}
				/* Else, sets the objective coefficient of the right component */
				else if (DistributionType.linear.equals(reasoner.getDistributionType())) {
					task.putbound(Env.accmode.var, featureVar, Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					task.putcj(featureVar, groundKernel.getWeight().getWeight());
				}
				else
					throw new IllegalStateException("Unsupported distribution type: " + reasoner.getDistributionType());
				
				/* Sets the augmented Lagrange term */
				for (int i = 0; i < x.size(); i++) {
					task.putavec(mosek.Env.accmode.con, augLagCon[i+1], new int[] {xVar[i], augLagVar[i+2]}, new double[] {1.0, -1.0});
					task.putbound(Env.accmode.var, augLagVar[i+2], Env.boundkey.fr, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				}
				task.putbound(Env.accmode.var, augLagVar[0], Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
				task.putbound(Env.accmode.var, augLagVar[1], Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
				task.appendcone(Env.conetype.rquad, 0.0, augLagVar);
				task.putcj(augLagVar[0], reasoner.stepSize/2);
				task.putavec(mosek.Env.accmode.con, augLagCon[0], new int[] {augLagVar[1]}, new double[] {1.0});
				task.putbound(mosek.Env.accmode.con, augLagCon[0], Env.boundkey.fx, 0.5, 0.5);
			} catch (mosek.MosekException e) {
				/* Catch both Error and Warning */ 
				throw new AssertionError(e);
			}
		}
	}

	@Override
	protected void minimize() {
		double bound;
		if (!alwaysIterative && (!pessimistic || x.size() == 1 || x.size() == 2 && DistributionType.linear.equals(reasoner.getDistributionType()))) {
			double total;
			double a[];
			
			/* Tries to solve without MOSEK first */
			a = new double[x.size()];
			
			/* Tries box projection first */
			total = constant;
			for (int i = 0; i < a.length; i++) {
				a[i] = reasoner.z.get(zIndices.get(i)) - y.get(i) / reasoner.stepSize;
				
				if (a[i] < 0)
					a[i] = 0;
				else if (a[i] > 1)
					a[i] = 1;
				
				total += coeffs.get(i) * a[i];
			}
			
			if (total <= 0) {
				if (DistributionType.quadratic.equals(reasoner.getDistributionType())) {
					if (x.size() == 1)
						reasoner.quadUnarySolvedZero++;
					else if (x.size() == 2)
						reasoner.quadPairwiseSolvedZero++;
					else
						reasoner.quadHigherOrderSolvedZero++;
				}
				else {
					if (x.size() == 1)
						reasoner.linearUnarySolvedZero++;
					else if (x.size() == 2)
						reasoner.linearPairwiseSolvedZero++;
					else
						reasoner.linearHigherOrderSolvedZero++;
				}
				
				for (int i = 0; i < x.size(); i++)
					x.set(i, a[i]);
				return;
			}
			
			if (DistributionType.linear.equals(reasoner.getDistributionType())) {
				total = constant;
				for (int i = 0; i < x.size(); i++) {
					a[i] = reasoner.stepSize * reasoner.z.get(zIndices.get(i)) - y.get(i) - weight * coeffs.get(i);
					a[i] /= reasoner.stepSize;
					
					if (a[i] < 0.0)
						a[i] = 0.0;
					else if (a[i] > 1.0)
						a[i] = 1.0;
					
					total += coeffs.get(i) * a[i];
				}
				
				if (total >= 0) {
					if (x.size() == 1)
						reasoner.linearUnarySolvedFunction++;
					else if (x.size() == 2)
						reasoner.linearPairwiseSolvedFunction++;
					else
						reasoner.linearHigherOrderSolvedFunction++;
					
					for (int i = 0; i < x.size(); i++)
						x.set(i, a[i]);
					return;
				}
				else {
					// TODO: Assumes that domain and hinge intersect
					if (x.size() == 1) {
						reasoner.linearUnarySolvedIntersection++;
						x.set(0, -1 * constant / coeffs.get(0));
						return;
					}
					// TODO: Assumes that domain and hinge intersect
					else if (x.size() == 2) {
						reasoner.linearPairwiseSolvedIntersection++;
						double min = 0.0, max = 1.0;
						a[0] = reasoner.stepSize * reasoner.z.get(zIndices.get(0)) - y.get(0);
						a[0] -= reasoner.stepSize * coeffs.get(0) / coeffs.get(1) * (constant / coeffs.get(1) + reasoner.z.get(zIndices.get(1)) - y.get(1));
						a[0] /= reasoner.stepSize * (1 + coeffs.get(0) * coeffs.get(0) / coeffs.get(1) / coeffs.get(1));
						
						a[1] = -1 * constant / coeffs.get(1);
						if (a[1] < 0.0) {
							min = -1 * constant / coeffs.get(0);
						}
						else if (a[1] > 1.0) {
							min = (-1 * coeffs.get(1) - constant) / coeffs.get(0);
						}
						a[1] = (-1 * coeffs.get(0) - constant) / coeffs.get(1);
						if (a[1] < 0.0) {
							max = -1 * constant / coeffs.get(0);
						}
						else if (a[1] > 1.0) {
							max = (-1 * coeffs.get(1) - constant) / coeffs.get(0);
						}
						if (min > max) {
							double temp = max;
							max = min;
							min = temp;
						}
						
						if (a[0] < min) {
							a[0] = min;
						}
						else if (a[0] > max) {
							a[0] = max;
						}
						
						a[1] = (-1 * coeffs.get(0) * a[0] - constant) / coeffs.get(1);
						
						x.set(0, a[0]);
						x.set(1, a[1]);
						return;
					}
				}
			}
			else if (DistributionType.quadratic.equals(reasoner.getDistributionType())) {
				if (x.size() == 1) {
					a[0] = reasoner.stepSize * reasoner.z.get(zIndices.get(0)) - y.get(0) - 2 * weight * coeffs.get(0) * constant;
					a[0] /= 2 * weight * coeffs.get(0) * coeffs.get(0) + reasoner.stepSize;
					
					if (a[0] < 0.0)
						a[0] = 0.0;
					else if (a[0] > 1.0)
						a[0] = 1.0;
					
					// TODO: Assumes that domain and hinge intersect
					if (coeffs.get(0) * a[0] + constant < 0) {
						reasoner.quadUnarySolvedIntersection++;
						a[0] = -1 * constant / coeffs.get(0);
					}
					else {
						reasoner.quadUnarySolvedFunction++;
					}
					
					x.set(0, a[0]);
					return;
				}
			}
		}
			
		try {
			if (DistributionType.quadratic.equals(reasoner.getDistributionType())) {
				if (x.size() == 2)
					reasoner.quadPairwiseSolvedIterative++;
				else
					reasoner.quadHigherOrderSolvedIterative++;
			}
			else {
				reasoner.linearHigherOrderIterative++;
			}
			
			/* Updates problem */
			for (int i = 0; i < x.size(); i++) {
				bound = reasoner.z.get(zIndices.get(i)) - y.get(i) / reasoner.stepSize;
				task.putbound(mosek.Env.accmode.con, augLagCon[i+1], Env.boundkey.fx, bound, bound);
			}
			
			task.putobjsense(mosek.Env.objsense.minimize); 
			task.optimize(); 
			mosek.Env.solsta solsta[] = new mosek.Env.solsta[1];
			mosek.Env.prosta prosta[] = new mosek.Env.prosta[1];
			task.getsolutionstatus(mosek.Env.soltype.itr, prosta, solsta);
			
			double[] solution = new double[x.size()];
			task.getsolutionslice(mosek.Env.soltype.itr, /* Interior solution. */  
									mosek.Env.solitem.xx, /* Which part of solution. */  
									0, /* Index of first variable. */  
									x.size(), /* Index of last variable+1 */  
									solution);
			
			switch(solsta[0]) {
			case optimal:
			case near_optimal:
				/* Stores solution */
				for (int i = 0; i < x.size(); i++)
					x.set(i, solution[i]);
				break;
			case dual_infeas_cer:
			case prim_infeas_cer:
			case near_dual_infeas_cer:
			case near_prim_infeas_cer:
				throw new IllegalStateException("Infeasible.");
			case unknown:
				throw new IllegalStateException("Unknown solution status.");
			default:
				throw new IllegalStateException("Other solution status.");
			}
		} catch (mosek.MosekException e) {
			/* Catch both Error and Warning */ 
			throw new AssertionError(e);
		}
	}

}
