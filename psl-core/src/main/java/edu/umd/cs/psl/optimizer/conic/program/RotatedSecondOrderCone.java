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
package edu.umd.cs.psl.optimizer.conic.program;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;

public class RotatedSecondOrderCone extends Cone {
	
	private Set<Variable> vars;
	private Variable varN;
	private Variable varNMinus1;
	
	RotatedSecondOrderCone(ConicProgram p, int n) {
		super(p);
		if (n < 3)
			throw new IllegalArgumentException("Rotated second-order cones must have at least three dimensions.");
		vars = new HashSet<Variable>();
		Variable var = null;
		for (int i = 0; i < n-2; i++) {
			var = new Variable(p, this);
			vars.add(var);
		}
		varNMinus1 = new Variable(p, this);
		vars.add(varNMinus1);
		varNMinus1.setValue(1.5 * (n-2));
		varNMinus1.setDualValue(1.5 * (n-2));
		varN = new Variable(p, this);
		vars.add(varN);
		varN.setValue(1.5 * (n-2));
		varN.setDualValue(1.5 * (n-2));
		p.notify(ConicProgramEvent.RSOCCreated, this);
	}
	
	public int getN() {
		return vars.size();
	}
	
	public Set<Variable> getVariables() {
		return new HashSet<Variable>(vars);
	}
	
	public Variable getNthVariable() {
		return varN;
	}
	public Variable getNMinus1stVariable() {
		return varNMinus1;
	}
	
	@Override
	public final void delete() {
		program.verifyCheckedIn();
		program.notify(ConicProgramEvent.RSOCDeleted, this);
		for (Variable v : getVariables()) {
			v.delete();
		}
		vars = null;
		varN = null;
		varNMinus1 = null;
	}
	
	public void setBarrierGradient(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix1D g) {
//		Set<Variable> vars = getVariables();
//		int[] indices = new int[vars.size()];
//		Variable varN = getNthVariable();
//		vars.remove(varN);
//		int i = 0;
//		for (Variable v : vars) {
//			indices[i++] = varMap.get(v);
//		}
//		indices[i] = varMap.get(varN);
//		DoubleMatrix1D xSel = x.viewSelection(indices);
//		DoubleMatrix1D gSel = g.viewSelection(indices);
//		double coeff = (double) 2 / (Math.pow(xSel.get(i), 2) - xSel.zDotProduct(xSel, 0, i));
//		gSel.assign(xSel).assign(DoubleFunctions.mult(coeff));
//		gSel.set(i, gSel.get(i) * -1);
	}
	
	public void setBarrierHessian(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix2D H) {
//		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
//		Set<Variable> vars = getVariables();
//		int[] indices = new int[vars.size()];
//		Variable varN = getNthVariable();
//		vars.remove(varN);
//		int i = 0;
//		for (Variable v : vars) {
//			indices[i++] = varMap.get(v);
//		}
//		indices[i] = varMap.get(varN);
//		DoubleMatrix1D xSel = x.viewSelection(indices).copy();
//		xSel.set(i, xSel.get(i)*-1);
//		DoubleMatrix2D HSel = H.viewSelection(indices, indices);
//		double coeff = (double) 2 / (Math.pow(xSel.get(i), 2) - xSel.zDotProduct(xSel, 0, i));
//		HSel.assign(alg.multOuter(xSel, xSel, null).assign(DoubleFunctions.mult(Math.pow(coeff, 2))));
//		for (int j = 0; j < i; j++)
//			HSel.set(j, j, HSel.get(j, j) + coeff);
//		HSel.set(i, i, HSel.get(i,i) - coeff);
	}
	
	public void setBarrierHessianInv(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix2D Hinv) {
//		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
//		Set<Variable> vars = getVariables();
//		int[] indices = new int[vars.size()];
//		Variable varN = getNthVariable();
//		vars.remove(varN);
//		int i = 0;
//		for (Variable v : vars) {
//			indices[i++] = varMap.get(v);
//		}
//		indices[i] = varMap.get(varN);
//		DoubleMatrix1D xSel = x.viewSelection(indices).copy();
//		xSel.set(i, xSel.get(i)*-1);
//		DoubleMatrix2D HSel = Hinv.viewSelection(indices, indices);
//		double coeff = (double) 2 / (Math.pow(xSel.get(i), 2) - xSel.zDotProduct(xSel, 0, i));
//		HSel.assign(alg.multOuter(xSel, xSel, null).assign(DoubleFunctions.mult(Math.pow(coeff, 2))));
//		for (int j = 0; j < i; j++)
//			HSel.set(j, j, HSel.get(j, j) + coeff);
//		HSel.set(i, i, HSel.get(i,i) - coeff);
//		HSel.assign(alg.inverse(HSel));
	}

	@Override
	public boolean isInterior(Map<Variable, Integer> varMap, DoubleMatrix1D x) {
//		Set<Variable> vars = getVariables();
//		int[] indices = new int[vars.size()];
//		Variable varN = getNthVariable();
//		vars.remove(varN);
//		int i = 0;
//		for (Variable v : vars) {
//			indices[i++] = varMap.get(v);
//		}
//		indices[i] = varMap.get(varN);
//		DoubleMatrix1D xSel = x.viewSelection(indices);
//		return xSel.get(i) > Math.sqrt(xSel.zDotProduct(xSel, 0, i)) + 0.05;
		return false;
	}

	@Override
	public void setInteriorDirection(Map<Variable, Integer> varMap, DoubleMatrix1D x,
			DoubleMatrix1D d) {
//		Set<Variable> vars = getVariables();
//		int[] indices = new int[vars.size()];
//		Variable varN = getNthVariable();
//		vars.remove(varN);
//		int i = 0;
//		for (Variable v : vars) {
//			indices[i++] = varMap.get(v);
//		}
//		indices[i] = varMap.get(varN);
//		DoubleMatrix1D xSel = x.viewSelection(indices);
//		DoubleMatrix1D dSel = d.viewSelection(indices);
//		dSel.assign(0.0);
//		if (xSel.get(i) <= Math.sqrt(xSel.zDotProduct(xSel, 0, i)) + 0.05)
//			dSel.set(i, Math.sqrt(xSel.zDotProduct(xSel, 0, i)) + 0.25 - xSel.get(i));
	}

	@Override
	public double getMaxStep(Map<Variable, Integer> varMap, DoubleMatrix1D x,
			DoubleMatrix1D dx) {
//		Set<Variable> vars = getVariables();
//		int[] indices = new int[vars.size()];
//		Variable varN = getNthVariable();
//		vars.remove(varN);
//		int i = 0;
//		for (Variable v : vars) {
//			indices[i++] = varMap.get(v);
//		}
//		indices[i] = varMap.get(varN);
//		DoubleMatrix1D xSel = x.viewSelection(indices);
//		DoubleMatrix1D dxSel = dx.viewSelection(indices);
//		
//		double a = Math.pow(dxSel.get(i), 2) - dxSel.zDotProduct(dxSel, 0, i);
//		double b = 2*(dxSel.get(i)*xSel.get(i) - dxSel.zDotProduct(xSel, 0, i));
//		double c = Math.pow(xSel.get(i), 2) - xSel.zDotProduct(xSel, 0, i);
//		
//		double discriminant = Math.pow(b, 2) - 4 * a * c;
//		if (discriminant > 0) {
//			double sol1 = (-1 * b + Math.sqrt(discriminant)) / (2 * a);
//			double sol2 = (-1 * b - Math.sqrt(discriminant)) / (2 * a);
//			
//			if (sol1 > 0 && sol2 > 0)
//				return Math.min(sol1, sol2) * .95;
//			else if (sol1 > 0)
//				return sol1 * .95;
//			else if (sol2 > 0)
//				return sol2 * .95;
//			else
//				return 1.0;
//		}
//		else {
//			double stepSize = 1.0;
//			while (a * Math.pow(stepSize, 2) + b * stepSize + c <= 0 && stepSize > 0)
//				stepSize *= 0.5;
//			
//			if (stepSize > 0)
//				return stepSize;
//			else
//				throw new IllegalStateException("Stuck.");
//		}
		return 0.0;
	}
}

