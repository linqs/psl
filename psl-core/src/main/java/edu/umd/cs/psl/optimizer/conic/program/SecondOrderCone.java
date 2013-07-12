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
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.jet.math.tdouble.DoubleFunctions;

public class SecondOrderCone extends Cone {
	
	private Set<Variable> vars;
	private Variable varN;
	
	SecondOrderCone(ConicProgram p, int n) {
		super(p);
		if (n < 2)
			throw new IllegalArgumentException("Second-order cones must have at least two dimensions.");
		vars = new HashSet<Variable>();
		Variable var = null;
		for (int i = 0; i < n; i++) {
			var = new Variable(p, this);
			vars.add(var);
		}
		varN = var;
		var.setValue(1.5 * (n-1));
		var.setDualValue(1.5 * (n-1));
		p.notify(ConicProgramEvent.SOCCreated, this);
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
	
	public Set<Variable> getInnerVariables() {
		Set<Variable> set = new HashSet<Variable>(vars);
		set.remove(varN);
		return set;
	}
	
	@Override
	public final void delete() {
		program.verifyCheckedIn();
		program.notify(ConicProgramEvent.SOCDeleted, this);
		for (Variable v : getVariables()) {
			v.delete();
		}
		vars = null;
		varN = null;
	}
	
	public void setBarrierGradient(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix1D g) {
		Set<Variable> vars = getVariables();
		int[] indices = new int[vars.size()];
		Variable varN = getNthVariable();
		vars.remove(varN);
		int i = 0;
		for (Variable v : vars) {
			indices[i++] = varMap.get(v);
		}
		indices[i] = varMap.get(varN);
		DoubleMatrix1D xSel = x.viewSelection(indices);
		DoubleMatrix1D gSel = g.viewSelection(indices);
		double coeff = (double) 2 / (Math.pow(xSel.get(i), 2) - xSel.zDotProduct(xSel, 0, i));
		gSel.assign(xSel).assign(DoubleFunctions.mult(coeff));
		gSel.set(i, gSel.get(i) * -1);
	}
	
	public void setBarrierHessian(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix2D H) {
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		Set<Variable> vars = getVariables();
		int[] indices = new int[vars.size()];
		Variable varN = getNthVariable();
		vars.remove(varN);
		int i = 0;
		for (Variable v : vars) {
			indices[i++] = varMap.get(v);
		}
		indices[i] = varMap.get(varN);
		DoubleMatrix1D xSel = x.viewSelection(indices).copy();
		xSel.set(i, xSel.get(i)*-1);
		DoubleMatrix2D HSel = H.viewSelection(indices, indices);
		double coeff = (double) 2 / (Math.pow(xSel.get(i), 2) - xSel.zDotProduct(xSel, 0, i));
		HSel.assign(alg.multOuter(xSel, xSel, null).assign(DoubleFunctions.mult(Math.pow(coeff, 2))));
		for (int j = 0; j < i; j++)
			HSel.set(j, j, HSel.get(j, j) + coeff);
		HSel.set(i, i, HSel.get(i,i) - coeff);
	}
	
	public void setBarrierHessianInv(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix2D Hinv) {
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		Set<Variable> vars = getVariables();
		int[] indices = new int[vars.size()];
		Variable varN = getNthVariable();
		vars.remove(varN);
		int i = 0;
		for (Variable v : vars) {
			indices[i++] = varMap.get(v);
		}
		indices[i] = varMap.get(varN);
		DoubleMatrix1D xSel = x.viewSelection(indices).copy();
		xSel.set(i, xSel.get(i)*-1);
		DoubleMatrix2D HSel = Hinv.viewSelection(indices, indices);
		double coeff = (double) 2 / (Math.pow(xSel.get(i), 2) - xSel.zDotProduct(xSel, 0, i));
		HSel.assign(alg.multOuter(xSel, xSel, null).assign(DoubleFunctions.mult(Math.pow(coeff, 2))));
		for (int j = 0; j < i; j++)
			HSel.set(j, j, HSel.get(j, j) + coeff);
		HSel.set(i, i, HSel.get(i,i) - coeff);
		HSel.assign(alg.inverse(HSel));
	}

	@Override
	public boolean isInterior(Map<Variable, Integer> varMap, DoubleMatrix1D x) {
		Set<Variable> vars = getVariables();
		int[] indices = new int[vars.size()];
		Variable varN = getNthVariable();
		vars.remove(varN);
		int i = 0;
		for (Variable v : vars) {
			indices[i++] = varMap.get(v);
		}
		indices[i] = varMap.get(varN);
		DoubleMatrix1D xSel = x.viewSelection(indices);
		return xSel.get(i) > Math.sqrt(xSel.zDotProduct(xSel, 0, i)) + 0.05;
	}

	@Override
	public void setInteriorDirection(Map<Variable, Integer> varMap, DoubleMatrix1D x,
			DoubleMatrix1D d) {
		Set<Variable> vars = getVariables();
		int[] indices = new int[vars.size()];
		Variable varN = getNthVariable();
		vars.remove(varN);
		int i = 0;
		for (Variable v : vars) {
			indices[i++] = varMap.get(v);
		}
		indices[i] = varMap.get(varN);
		DoubleMatrix1D xSel = x.viewSelection(indices);
		DoubleMatrix1D dSel = d.viewSelection(indices);
		dSel.assign(0.0);
		if (xSel.get(i) <= Math.sqrt(xSel.zDotProduct(xSel, 0, i)) + 0.05)
			dSel.set(i, Math.sqrt(xSel.zDotProduct(xSel, 0, i)) + 0.25 - xSel.get(i));
	}

	@Override
	public double getMaxStep(Map<Variable, Integer> varMap, DoubleMatrix1D x,
			DoubleMatrix1D dx) {
		Variable varN = getNthVariable();
		double xN = x.get(varMap.get(varN));
		double dxN = dx.get(varMap.get(varN));

		Set<Variable> vars = getVariables();
		int[] indices = new int[vars.size()-1];
		vars.remove(varN);
		int i = 0;
		for (Variable v : vars) {
			indices[i++] = varMap.get(v);
		}
		DoubleMatrix1D x1NMinus1 = x.viewSelection(indices).copy();
		DoubleMatrix1D dx1NMinus1 = dx.viewSelection(indices).copy();
		
		DoubleMatrix1D current = x1NMinus1.copy();
		current.assign(dx1NMinus1, DoubleFunctions.plus);
		
		double alpha = 1.0;
		
		while (xN + dxN <= Math.sqrt(current.zDotProduct(current))) {
			alpha *= 0.9;
			dxN *= 0.9;
			current.assign(x1NMinus1).assign(dx1NMinus1, DoubleFunctions.plusMultSecond(alpha));
		}
		
		return alpha;
	}
}

