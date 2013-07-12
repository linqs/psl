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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cern.colt.list.tdouble.DoubleArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;

import com.google.common.collect.ImmutableSet;

/**
 * Stores information about the primal and dual forms of a conic program.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConicProgram {
	
	private Set<NonNegativeOrthantCone> NNOCs;
	private Set<SecondOrderCone> SOCs;
	private Set<RotatedSecondOrderCone> RSOCs;
	
	private int numVars;
	
	private Set<LinearConstraint> cons;
	
	private boolean checkedOut;
	
	private Set<ConicProgramListener> listeners;
	
	private SparseCCDoubleMatrix2D A;
	private DenseDoubleMatrix1D x;
	private DenseDoubleMatrix1D b;
	private DenseDoubleMatrix1D w;
	private DenseDoubleMatrix1D s;
	private DenseDoubleMatrix1D c;
	private Map<Variable, Integer> varMap;
	private Map<LinearConstraint, Integer> lcMap;
	
	private int nextID;
	
	// Error messages
	private static final String UNEXPECTED_SENDER = "Unexpected sender type.";
	private static final String UNEXPECTED_DATA = "Unexpected data.";
	
	public ConicProgram() {
		NNOCs = new HashSet<NonNegativeOrthantCone>();
		SOCs = new HashSet<SecondOrderCone>();
		RSOCs = new HashSet<RotatedSecondOrderCone>();
		
		numVars = 0;
		
		cons = new HashSet<LinearConstraint>();
		
		checkedOut = false;
		
		listeners = new HashSet<ConicProgramListener>();
		
		nextID = 0;
	}
	
	int getNextID() {
		return nextID++;
	}
	
	public Collection<ConeType> getConeTypes() {
		Set<ConeType> types = new HashSet<ConeType>();
		if (getNumNNOC() > 0) types.add(ConeType.NonNegativeOrthantCone);
		if (gtNumSOC() > 0) types.add(ConeType.SecondOrderCone);
		if (getNumRSOC() > 0) types.add(ConeType.RotatedSecondOrderCone);
		return types;
	}
	
	public NonNegativeOrthantCone createNonNegativeOrthantCone() {
		verifyCheckedIn();
		return new NonNegativeOrthantCone(this);
	}
	
	public Set<NonNegativeOrthantCone> getNonNegativeOrthantCones() {
		return Collections.unmodifiableSet(NNOCs);
	}
	
	public SecondOrderCone createSecondOrderCone(int n) {
		verifyCheckedIn();
		return new SecondOrderCone(this, n);
	}
	
	public Set<SecondOrderCone> getSecondOrderCones() {
		return Collections.unmodifiableSet(SOCs);
	}
	
	public RotatedSecondOrderCone createRotatedSecondOrderCone(int n) {
		verifyCheckedIn();
		return new RotatedSecondOrderCone(this, n);
	}
	
	public Set<RotatedSecondOrderCone> getRotatedSecondOrderCones() {
		return Collections.unmodifiableSet(RSOCs);
	}
	
	public Set<Cone> getCones() {
		return ImmutableSet.<Cone>builder().addAll(NNOCs).addAll(SOCs).addAll(RSOCs).build();
	}
	
	public LinearConstraint createConstraint() {
		verifyCheckedIn();
		return new LinearConstraint(this);
	}
	
	public Set<LinearConstraint> getConstraints() {
		return new HashSet<LinearConstraint>(this.cons);
	}
	
	public void checkOutMatrices() {
		verifyCheckedIn();
		Variable var;
		int i, j;
		varMap = new HashMap<Variable, Integer>();
		lcMap = new HashMap<LinearConstraint, Integer>();
		
		/* Collects variables */
		i = 0;
		
		/* NNOCs */
		for (NonNegativeOrthantCone cone : NNOCs) {
			var = cone.getVariable();
			if (!varMap.containsKey(var)){
				varMap.put(var, i++);
			}
		}
		
		/* SOCs */
		for (SecondOrderCone cone : SOCs)
			for (Variable v : cone.getVariables())
				if (!varMap.containsKey(v))
					varMap.put(v, i++);
		
		/* RSOCs */
		for (RotatedSecondOrderCone cone : RSOCs)
			for (Variable v : cone.getVariables())
				if (!varMap.containsKey(v))
					varMap.put(v, i++);
		
		/* Collects linear constraints */
		j = 0;
		for (LinearConstraint con : cons)
			if (!lcMap.containsKey(con))
				lcMap.put((LinearConstraint) con, j++);
				
		
		/* Initializes data matrices */
		SparseDoubleMatrix2D Atemp = new SparseDoubleMatrix2D(lcMap.size(), varMap.size(), lcMap.size()*4, 0.2, 0.5);
		x = new DenseDoubleMatrix1D(varMap.size());
		b = new DenseDoubleMatrix1D(lcMap.size());
		w = new DenseDoubleMatrix1D(lcMap.size());
		s = new DenseDoubleMatrix1D(varMap.size());
		c = new DenseDoubleMatrix1D(varMap.size());
		
		/* Constructs A, b, and w */
		for (Map.Entry<LinearConstraint, Integer> lc : lcMap.entrySet()) {
			for (Entry<Variable, Double> v : lc.getKey().getVariables().entrySet()) {
				Atemp.set(lc.getValue(), varMap.get(v.getKey()), v.getValue());
			}
			w.set(lc.getValue(), lc.getKey().getLagrange());
			b.set(lc.getValue(), lc.getKey().getConstrainedValue());
		}
		
		if (Atemp.rows() > 0)
			A = Atemp.getColumnCompressed(false);
		else
			A = new SparseCCDoubleMatrix2D(0, 0);
		
		/* Constructs x, s, and c */
		for (Map.Entry<Variable, Integer> v : varMap.entrySet()) {
			x.set(v.getValue(), v.getKey().getValue());
			s.set(v.getValue(), v.getKey().getDualValue());
			c.set(v.getValue(), v.getKey().getObjectiveCoefficient());
		}
		
		checkedOut = true;
		
		notify(ConicProgramEvent.MatricesCheckedOut, null, (Object[]) null);
	}
	
	public void checkInMatrices() {
		verifyCheckedOut();
		for (Map.Entry<Variable, Integer> v : varMap.entrySet()) {
			v.getKey().setValue(x.get(v.getValue()));
			v.getKey().setDualValue(s.get(v.getValue()));
		}
		for (Map.Entry<LinearConstraint, Integer> lc : lcMap.entrySet())
			lc.getKey().setLagrange(w.get(lc.getValue()));
		checkedOut = false;
				
		notify(ConicProgramEvent.MatricesCheckedIn, null, (Object[]) null);
	}
	
	public Map<Variable, Integer> getVarMap() {
		verifyCheckedOut();
		return Collections.unmodifiableMap(varMap);
	}
	
	public Map<LinearConstraint, Integer> getLcMap() {
		verifyCheckedOut();
		return Collections.unmodifiableMap(lcMap);
	}
	
	public SparseCCDoubleMatrix2D getA() {
		verifyCheckedOut();
		return A;
	}
	
	public DenseDoubleMatrix1D getX() {
		verifyCheckedOut();
		return x;
	}
	
	public DenseDoubleMatrix1D getB() {
		verifyCheckedOut();
		return b;
	}
	
	public DenseDoubleMatrix1D getW() {
		verifyCheckedOut();
		return w;
	}
	
	public DenseDoubleMatrix1D getS() {
		verifyCheckedOut();
		return s;
	}
	
	public DenseDoubleMatrix1D getC() {
		verifyCheckedOut();
		return c;
	}
	
	public int getIndex(Variable v) {
		verifyCheckedOut();
		return varMap.get(v);
	}
	
	public int getIndex(LinearConstraint lc) {
		verifyCheckedOut();
		return lcMap.get(lc);
	}
	
	public int getNumCones() {
		return getNumNNOC() + gtNumSOC() + getNumRSOC();
	}
	
	public int getNumNNOC() {
		return NNOCs.size();
	}
	
	public int gtNumSOC() {
		return SOCs.size();
	}
	
	public int getNumRSOC() {
		return RSOCs.size();
	}
	
	public int getNumVariables() {
		return numVars;
	}
	
	public int getNumLinearConstraints() {
		return cons.size();
	}
	
	public void verifyCheckedOut() {
		if (!checkedOut)
			throw new IllegalStateException("Matrices are not checked out.");
	}
	
	public void verifyCheckedIn() {
		if (checkedOut)
			throw new IllegalStateException("Matrices are not checked in.");
	}
	
	public void trimUnrestrictedVariablePairs() {
		boolean checkInWhenFinished = false;
		if (!checkedOut) {
			checkOutMatrices();
			checkInWhenFinished = true;
		}
		boolean pair, trim = true;
		DoubleMatrix1D col1, col2, row;
		IntArrayList col1Indices = new IntArrayList()
				, col2Indices = new IntArrayList()
				, rowIndices = new IntArrayList()
				;
		DoubleArrayList col1Values = new DoubleArrayList()
				, col2Values = new DoubleArrayList()
				, rowValues = new DoubleArrayList()
				;
		while (trim) {
			double[] max = x.getMaxLocation();
			int maxIndex = (int) max[1];
			if (max[0] > 10e4) {
				col1 = A.viewColumn((int) max[1]);
				col1.getNonZeros(col1Indices, col1Values);
				col1Indices.sort();
				row = A.viewRow(col1Indices.get(0));
				row.getNonZeros(rowIndices, rowValues);
				for (int i = 0; i < rowIndices.size(); i++) {
					if (rowIndices.get(i) != maxIndex && c.get(maxIndex) * -1 == c.get(rowIndices.get(i))) {
						col2 = A.viewColumn(rowIndices.get(i));
						if (col1Indices.size() == col2.cardinality()) {
							col2.getNonZeros(col2Indices, col2Values);
							col2Indices.sort();
							pair = true;
							for (int j = 0; j < col1Indices.size(); j++) {
								if (col1Indices.get(j) != col2Indices.get(j)
										|| col1.get(col1Indices.get(j)) * col2.get(col2Indices.get(j)) >= 0) {
									pair = false;
									break;
								}
							}
							if (pair) {
								if (x.get(rowIndices.get(i)) > x.get(maxIndex) / 3) {
									x.set(maxIndex, x.get(maxIndex) - x.get(rowIndices.get(i)) + 1);
									x.set(rowIndices.get(i), 1);
									break;
								}
							}
						}
					}
					if (i == rowIndices.size() - 1) trim = false;
				}
			}
			else trim = false;
		}
		
		if (checkInWhenFinished) checkInMatrices();
	}
	
	public double getPrimalInfeasibility() {
		return getPrimalInfeasibility(false);
	}
	
	public double getPrimalInfeasibility(boolean requireInterior) {
		verifyCheckedOut();
		
		double value;
		
		for (NonNegativeOrthantCone cone : NNOCs) {
			value = x.get(getIndex(cone.getVariable()));
			if (value < 0.0 || (requireInterior && value == 0.0))
				return Double.POSITIVE_INFINITY;
		}
		
		for (SecondOrderCone cone : SOCs) {
			value = 0.0;
			for (Variable v : cone.getVariables()) {
				if (!v.equals(cone.getNthVariable())) {
					value += Math.pow(x.get(getIndex(v)), 2);
				} 
			}
			value = Math.sqrt(value);
			value = x.get(getIndex(cone.getNthVariable())) - value;
			if (value < 0.0 || (requireInterior && value == 0.0))
				return Double.POSITIVE_INFINITY;
		}
		
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		double inf = alg.norm2(alg.mult(A, x).assign(b, DoubleFunctions.minus));
		
		return inf;
	}
	
	public double getDualInfeasibility() {
		return getDualInfeasibility(false);
	}
	
	public double getDualInfeasibility(boolean requireInterior) {
		verifyCheckedOut();
		
		double value;
		
		for (NonNegativeOrthantCone cone : NNOCs) {
			value = s.get(getIndex(cone.getVariable()));
			if (value < 0.0 || (requireInterior && value == 0.0))
				return Double.POSITIVE_INFINITY;
		}
		
		for (SecondOrderCone cone : SOCs) {
			value = 0.0;
			for (Variable v : cone.getVariables()) {
				if (!v.equals(cone.getNthVariable())) {
					value += Math.pow(s.get(getIndex(v)), 2);
				} 
			}
			value = Math.sqrt(value);
			value = s.get(getIndex(cone.getNthVariable())) - value;
			if (value < 0.0 || (requireInterior && value == 0.0))
				return Double.POSITIVE_INFINITY;
		}
		
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		double inf = alg.norm2(A.zMult(w, s.copy(), 1.0, 1.0, true).assign(c, DoubleFunctions.minus));
		
		return inf;
	}
	
	public void registerForConicProgramEvents(ConicProgramListener l) {
		listeners.add(l);
	}
	
	public void unregisterForConicProgramEvents(ConicProgramListener l) {
		listeners.remove(l);
	}
	
	void notify(ConicProgramEvent e, Entity sender, Object... data) {
		switch (e) {
		case NNOCCreated:
		case NNOCDeleted:
			if (sender instanceof NonNegativeOrthantCone) {
				switch (e) {
				case NNOCCreated:
					NNOCs.add((NonNegativeOrthantCone) sender);
					numVars++;
					break;
				case NNOCDeleted:
					NNOCs.remove((NonNegativeOrthantCone) sender);
					numVars--;
					break;
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case SOCCreated:
		case SOCDeleted:
			if (sender instanceof SecondOrderCone) {
				switch (e) {
				case SOCCreated:
					SOCs.add((SecondOrderCone) sender);
					numVars += ((SecondOrderCone) sender).getN();
					break;
				case SOCDeleted:
					SOCs.remove((SecondOrderCone) sender);
					numVars -= ((SecondOrderCone) sender).getN();
					break;
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case RSOCCreated:
		case RSOCDeleted:
			if (sender instanceof RotatedSecondOrderCone) {
				switch (e) {
				case RSOCCreated:
					RSOCs.add((RotatedSecondOrderCone) sender);
					numVars += ((RotatedSecondOrderCone) sender).getN();
					break;
				case RSOCDeleted:
					RSOCs.remove((RotatedSecondOrderCone) sender);
					numVars -= ((RotatedSecondOrderCone) sender).getN();
					break;
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case ObjCoeffChanged:
			if (sender instanceof Variable) {
				/* Intentionally blank */
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case ConCreated:
		case ConValueChanged:
		case ConDeleted:
			if (sender instanceof LinearConstraint) {
				switch(e) {
				case ConCreated:
					cons.add((LinearConstraint) sender);
					break;
				case ConValueChanged:
					/* Intentionally blank */
					break;
				case ConDeleted:
					cons.remove((LinearConstraint) sender);
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case VarAddedToCon:
		case VarRemovedFromCon:
			if (sender instanceof LinearConstraint && data.length > 0 && data[0] instanceof Variable) {
				/* Intentionally blank */
			}
			else if (sender instanceof LinearConstraint)
				throw new IllegalArgumentException(UNEXPECTED_DATA);
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		}
		
		for (ConicProgramListener l : listeners)
			l.notify(this, e, sender, data);
	}
}
