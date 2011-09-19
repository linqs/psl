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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.list.tdouble.DoubleArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.optimizer.conic.program.graph.Graph;
import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.memory.MemoryGraph;

/**
 * Stores information about the primal and dual forms of a conic program.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConicProgram {
	
	private static final Logger log = LoggerFactory.getLogger(ConicProgram.class);
	
	private Graph graph;
	
	// Property type names for all nodes
	static final String NODE_TYPE = "nodeType";
	
	// Property type names for variable nodes
	static final String VAR_VALUE = "varValue";
	static final String VAR_DUAL_VALUE = "varDualValue";
	static final String OBJ_COEFF = "objCoeff";
	
	// Property type names for linear constraint nodes
	static final String LC_VALUE = "lcValue";
	static final String LAGRANGE = "lagrange";
	
	// Relationship type names
	static final String CONE_REL = "coneRel";
	static final String SOC_N_REL = "socpNRel";
	static final String LC_REL = "lcRel";
	
	// Property type names for lcRel relationships
	static final String LC_REL_COEFF = "lcRelCoeff";

	private int numNNOC;
	private int numSOC;
	private int numRSOC;
	
	private boolean checkedOut;
	
	private SparseDoubleMatrix2D A;
	private DenseDoubleMatrix1D x;
	private DenseDoubleMatrix1D b;
	private DenseDoubleMatrix1D w;
	private DenseDoubleMatrix1D s;
	private DenseDoubleMatrix1D c;
	private Map<Variable, Integer> varMap;
	private Map<LinearConstraint, Integer> lcMap;
	
	// Default starting capacities
	protected static final int defaultStartingVarCapacity = 1000;
	
	// Error messages
	private static final String UNEXPECTED_SENDER = "Unexpected sender type.";
	private static final String UNEXPECTED_DATA = "Unexpected data.";
	
	public ConicProgram() {

		graph = new MemoryGraph();
		
		graph.createPropertyType(NODE_TYPE, NodeType.class);
		graph.createPropertyType(VAR_VALUE, Double.class);
		graph.createPropertyType(VAR_DUAL_VALUE, Double.class);
		graph.createPropertyType(OBJ_COEFF, Double.class);
		graph.createPropertyType(LC_VALUE, Double.class);
		graph.createPropertyType(LAGRANGE, Double.class);
		
		graph.createRelationshipType(CONE_REL);
		graph.createRelationshipType(SOC_N_REL);
		graph.createRelationshipType(LC_REL);
		
		graph.createPropertyType(LC_REL_COEFF, Double.class);
		
		numNNOC = 0;
		numSOC = 0;
		numRSOC = 0;
		
		checkedOut = false;
	}
	
	Graph getGraph() {
		return graph;
	}
	
	public boolean containsOnly(Collection<ConeType> types) {
		return !((numNNOC() > 0 && !types.contains(ConeType.NonNegativeOrthantCone))
				|| (numSOC() > 0 && !types.contains(ConeType.SecondOrderCone))
				|| (numRSOC() > 0 && !types.contains(ConeType.RotatedSecondOrderCone)));
	}
	
	public NonNegativeOrthantCone createNonNegativeOrthantCone() {
		verifyCheckedIn();
		return new NonNegativeOrthantCone(this);
	}
	
	public Set<NonNegativeOrthantCone> getNonNegativeOrthantCones() {
		Set<NonNegativeOrthantCone> cones = new HashSet<NonNegativeOrthantCone>();
		for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.nnoc)) {
			cones.add((NonNegativeOrthantCone) Entity.createEntity(this, n));
		}
		return cones;
	}
	
	public SecondOrderCone createSecondOrderCone(int n) {
		verifyCheckedIn();
		return new SecondOrderCone(this, n);
	}
	
	public Set<SecondOrderCone> getSecondOrderCones() {
		Set<SecondOrderCone> cones = new HashSet<SecondOrderCone>();
		for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.soc)) {
			cones.add((SecondOrderCone) Entity.createEntity(this, n));
		}
		return cones;
	}
	
	public Set<Cone> getCones() {
		Set<Cone> cones = new HashSet<Cone>();
		cones.addAll(getNonNegativeOrthantCones());
		cones.addAll(getSecondOrderCones());
		return cones;
	}
	
	public LinearConstraint createConstraint() {
		verifyCheckedIn();
		return new LinearConstraint(this);
	}
	
	public Set<LinearConstraint> getConstraints() {
		Set<LinearConstraint> lc = new HashSet<LinearConstraint>();
		for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.lc)) {
			lc.add(new LinearConstraint(this, n));
		}
		return lc;
	}
	
	public void checkOutMatrices() {
		verifyCheckedIn();
		Variable var;
		int i, j;
		Set<Node> vars;
		varMap = new HashMap<Variable, Integer>();
		lcMap = new HashMap<LinearConstraint, Integer>();
		
		vars = graph.getNodesByAttribute(NODE_TYPE, NodeType.var);
		
		/* Collects variables */
		i = 0;
		for (Node n : vars) {
			var = (Variable) Entity.createEntity(this, n);
			if (!varMap.containsKey(var)){
				varMap.put(var, i++);
			}
		}
		
		/* Collects linear constraints */
		j = 0;
		for (Variable v : varMap.keySet())
			for (LinearConstraint lc : v.getLinearConstraints())
				if (!lcMap.containsKey(lc))
					lcMap.put((LinearConstraint) lc, j++);
		
		/* Initializes data matrices */
		A = new SparseDoubleMatrix2D(lcMap.size(), varMap.size());
		x = new DenseDoubleMatrix1D(varMap.size());
		b = new DenseDoubleMatrix1D(lcMap.size());
		w = new DenseDoubleMatrix1D(lcMap.size());
		s = new DenseDoubleMatrix1D(varMap.size());
		c = new DenseDoubleMatrix1D(varMap.size());
		
		/* Constructs A, b, and w */
		for (Map.Entry<LinearConstraint, Integer> lc : lcMap.entrySet()) {
			for (Entry<Variable, Double> v : lc.getKey().getVariables().entrySet()) {
				A.set(lc.getValue(), varMap.get(v.getKey()), v.getValue());
			}
			w.set(lc.getValue(), lc.getKey().getLagrange());
			b.set(lc.getValue(), lc.getKey().getConstrainedValue());
		}
		
		/* Constructs x, s, and c */
		for (Map.Entry<Variable, Integer> v : varMap.entrySet()) {
			x.set(v.getValue(), v.getKey().getValue());
			s.set(v.getValue(), v.getKey().getDualValue());
			c.set(v.getValue(), v.getKey().getObjectiveCoefficient());
		}
		
		checkedOut = true;
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
	}
	
	public Map<Variable, Integer> getVarMap() {
		verifyCheckedOut();
		return varMap;
	}
	
	public Map<LinearConstraint, Integer> getLcMap() {
		verifyCheckedOut();
		return lcMap;
	}
	
	public SparseDoubleMatrix2D getA() {
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
	
	public int index(Variable v) {
		verifyCheckedOut();
		return varMap.get(v);
	}
	
	public int index(LinearConstraint lc) {
		verifyCheckedOut();
		return lcMap.get(lc);
	}
	
	public int numCones() {
		return numNNOC() + numSOC() + numRSOC();
	}
	
	public int numNNOC() {
		return numNNOC;
	}
	
	public int numSOC() {
		return numSOC;
	}
	
	public int numRSOC() {
		return numRSOC;
	}
	
	void verifyCheckedOut() {
		if (!checkedOut)
			throw new IllegalAccessError("Matrices are not checked out.");
	}
	
	void verifyCheckedIn() {
		if (checkedOut)
			throw new IllegalAccessError("Matrices are not checked in.");
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
	
	public double primalInfeasibility() {
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		boolean checkInWhenFinished = false;
		if (!checkedOut) {
			checkOutMatrices();
			checkInWhenFinished = true;
		}
		double inf = alg.norm2(alg.mult(A, x).assign(b, DoubleFunctions.minus));
		if (checkInWhenFinished) checkInMatrices();
		return inf;
	}
	
	public double dualInfeasibility() {
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		boolean checkInWhenFinished = false;
		if (!checkedOut) {
			checkOutMatrices();
			checkInWhenFinished = true;
		}
		double inf = alg.norm2(A.zMult(w, s.copy(), 1.0, 1.0, true).assign(c, DoubleFunctions.minus));
		if (checkInWhenFinished) checkInMatrices();
		return inf;
	}
	
	void notify(ConicProgramEvent e, Entity sender, Object... data) {
		switch (e) {
		case NNOCCreated:
		case NNOCDeleted:
			if (sender instanceof NonNegativeOrthantCone) {
				switch (e) {
				case NNOCCreated:
					numNNOC++;
					break;
				case NNOCDeleted:
					numNNOC--;
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
					numSOC++;
					break;
				case SOCDeleted:
					numSOC--;
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
				case ConValueChanged:
					/* Intentionally blank */
					break;
				case ConDeleted:
					/* Intentionally blank */
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
	}
}
