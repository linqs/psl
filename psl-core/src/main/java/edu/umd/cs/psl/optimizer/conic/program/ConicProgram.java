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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.list.tdouble.DoubleArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleLUDecompositionQuick;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleQRDecomposition;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleQRDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.optimizer.conic.program.graph.Graph;
import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.Relationship;
import edu.umd.cs.psl.optimizer.conic.program.graph.Traversal;
import edu.umd.cs.psl.optimizer.conic.program.graph.TraversalEvaluator;
import edu.umd.cs.psl.optimizer.conic.program.graph.cosi.COSIGraph;

/**
 * Stores information about the primal and dual forms of a conic program.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConicProgram {
	
	private static final Logger log = LoggerFactory.getLogger(ConicProgram.class);
	
	private Graph graph;
	
	private Set<LinearConstraint> primalInfeasible;
	private Set<Variable> dualInfeasible;
	private Map<LinearConstraint, IsolatedStructure> primalIsolated;
	private Map<Variable, LinearConstraint> dualIsolated;
	
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
	static final String LC_REL = "lcRel";
	
	// Property type names for lcRel relationships
	static final String LC_REL_COEFF = "lcRelCoeff";

	private boolean madeFeasibleOnce;
	private int v;
	
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

		graph = new COSIGraph();
		
		primalInfeasible = new HashSet<LinearConstraint>();
		dualInfeasible = new HashSet<Variable>();
		primalIsolated = new HashMap<LinearConstraint, IsolatedStructure>();
		dualIsolated = new HashMap<Variable, LinearConstraint>(defaultStartingVarCapacity);
		
		graph.createPropertyType(NODE_TYPE, NodeType.class);
		graph.createPropertyType(VAR_VALUE, Double.class);
		graph.createPropertyType(VAR_DUAL_VALUE, Double.class);
		graph.createPropertyType(OBJ_COEFF, Double.class);
		graph.createPropertyType(LC_VALUE, Double.class);
		graph.createPropertyType(LAGRANGE, Double.class);
		
		graph.createRelationshipType(CONE_REL);
		graph.createRelationshipType(LC_REL);
		
		graph.createPropertyType(LC_REL_COEFF, Double.class);
		
		madeFeasibleOnce = false;
		v = 0;
		
		checkedOut = false;
	}
	
	Graph getGraph() {
		return graph;
	}
	
	public NonNegativeOrthantCone createNonNegativeOrthantCone() {
		verifyCheckedIn();
		return new NonNegativeOrthantCone(this);
	}
	
	public Set<NonNegativeOrthantCone> getNonNegativeOrthantCones() {
		Set<NonNegativeOrthantCone> cones = new HashSet<NonNegativeOrthantCone>();
		for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.nnoc)) {
			cones.add(new NonNegativeOrthantCone(this, n));
		}
		return cones;
	}
	
	public Set<Cone> getCones() {
		Set<Cone> cones = new HashSet<Cone>();
		cones.addAll(getNonNegativeOrthantCones());
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
	
	public int getV() {
		return v;
	}
	
	int index(Variable v) {
		verifyCheckedOut();
		return varMap.get(v);
	}
	
	int index(LinearConstraint lc) {
		verifyCheckedOut();
		return lcMap.get(lc);
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
	
	public void makeFeasible() {
		verifyCheckedIn();
		makePrimalFeasible();
		log.debug("Found primal feasible point.");
		makeDualFeasible();
		log.debug("Found dual feasible point.");
		madeFeasibleOnce = true;
	}
	
	private void makePrimalFeasible() {
		LinearConstraint lc;
		IsolatedStructure iso;
		Node node;
		Map<LinearConstraint, Integer> lcInit = new HashMap<LinearConstraint, Integer>();
		Map<Variable, Integer> varInit = new HashMap<Variable, Integer>();
		
		final Set<IsolatedStructure> isoNeedsInit = new HashSet<IsolatedStructure>();
		
		Traversal trav = graph.getTraversal().addRelationshipType(CONE_REL)
				.addRelationshipType(LC_REL).setEvaluator(new TraversalEvaluator() {
			@Override
			public boolean nextNode(Node n) {
				Entity e = Entity.createEntity(ConicProgram.this, n);
				if (e instanceof LinearConstraint) {
					IsolatedStructure iso = getPrimalIsolatedStructure((LinearConstraint) e);
					if (iso != null) {
						isoNeedsInit.add(iso);
						return false;
					}
				}
				return true;
			}

			@Override
			public void nextRelationship(Relationship r) { }
			
		});

		int i,j;
		Iterator<Node> itr;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		DoubleMatrix1D x, dp, intDir, feasibilityCheck;
		SparseDoubleMatrix2D A;
		DoubleMatrix2D nullity;
		DenseDoubleLUDecompositionQuick lu = new DenseDoubleLUDecompositionQuick();
		Set<Cone> cones = new HashSet<Cone>();
		
		if (!madeFeasibleOnce)
			 for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.lc)) {
				 primalInfeasible.add((LinearConstraint) Entity.createEntity(this, n));
			 }
		
		log.debug("Finding primal feasible point: {} constraints marked", primalInfeasible.size());
		
		while (!primalInfeasible.isEmpty()) {
			log.trace("{} primal constraints left to initialize.", primalInfeasible.size());
			lc = primalInfeasible.iterator().next();
			iso = getPrimalIsolatedStructure(lc);
			if (iso != null) {
				isoNeedsInit.add(iso);
				primalInfeasible.remove(lc);
			}
			else {
				i = 0;
				lcInit.clear();
				j = 0;
				varInit.clear();
				cones.clear();
				itr = trav.traverse(lc.getNode());
				
				/* Collects linear constraints to be initialized */
				while (itr.hasNext()) {
					node = itr.next();
					if (NodeType.lc.equals(node.getAttribute(NODE_TYPE))) {
						primalInfeasible.remove(lc);
						lc = (LinearConstraint) Entity.createEntity(this, node);
						if (!lcInit.containsKey(lc)){
							lcInit.put(lc, i++);
						}
					}
				}
				
				/* Collects variables to be initialized */
				for (LinearConstraint con : lcInit.keySet())
					for (Variable v : con.getVariables().keySet())
						if (!varInit.containsKey(v)) {
							varInit.put((Variable) v, j++);
							cones.add((Cone) ((Variable) v).getCone());
						}
				
				/* Initializes data matrices */
				A = (SparseDoubleMatrix2D) DoubleFactory2D.sparse.make(lcInit.size(), varInit.size());
				x = DoubleFactory1D.dense.make(varInit.size());
				
				/* Constructs A, b, and AstarA */
				for (Map.Entry<LinearConstraint, Integer> con : lcInit.entrySet()) {
					for (Entry<Variable, Double> v : con.getKey().getVariables().entrySet()) {
						/*
						 * This monstrosity of a statement sets an element of A to its
						 * coefficient. The element is specified by the constraint index
						 * (the value of the lcInit entry) and the variable index
						 * (the value stored in varInit with the same key as the
						 * key of v). The value of v is the coefficient.
						 */
						A.set(con.getValue(), varInit.get(v.getKey()), v.getValue());
					}
					x.set(con.getValue(), con.getKey().getConstrainedValue());
				}
				
				/* Uses decompositions to find general and particular solutions to Ax = b */
				DenseDoubleQRDecomposition denseQR = new DenseDoubleQRDecomposition(new DenseDoubleMatrix2D(A.rows(), A.columns()).assign(A).viewDice());
				nullity = denseQR.getQ(false).viewPart(0, A.rows(), A.columns(), A.columns()-A.rows());
				
				SparseDoubleQRDecomposition qr = new SparseDoubleQRDecomposition(A.getColumnCompressed(false), 0);
				qr.solve(x);
				
				lu.decompose(alg.mult(nullity.viewDice(), nullity));
				intDir = x.copy();
				feasibilityCheck = x.viewSelection(DoubleFunctions.isLess(0.025));
				// TODO: Add check for getting stuck
				while (feasibilityCheck.size() > 0) {
					for (Cone c : cones) {
						((Cone) c).setInteriorDirection(varInit, x, intDir);
					}
					dp = alg.mult(nullity.viewDice(), intDir);
					lu.solve(dp);
					x.assign(alg.mult(nullity, dp), DoubleFunctions.plus);
					feasibilityCheck = x.viewSelection(DoubleFunctions.isLess(0.025));
				}

				/* Finalize initialization */
				for (Map.Entry<Variable, Integer> v : varInit.entrySet())
					v.getKey().setValue(x.get(v.getValue()));
			}
		}
		
		/* Initializes the isolated structures */
		for (IsolatedStructure toInit : isoNeedsInit) {
			toInit.makePrimalFeasible();
		}
	}
	
	private void makeDualFeasible() {
		Variable var;
		LinearConstraint iso;
		Node node;
		Map<LinearConstraint, Integer> lcInit = new HashMap<LinearConstraint, Integer>();
		Map<Variable, Integer> varInit = new HashMap<Variable, Integer>();
		
		final Set<LinearConstraint> isoNeedsInit = new HashSet<LinearConstraint>();
		
		Traversal trav = graph.getTraversal().addRelationshipType(LC_REL).setEvaluator(new TraversalEvaluator() {
			@Override
			public boolean nextNode(Node n) {
				Entity e = Entity.createEntity(ConicProgram.this, n);
				if (e instanceof Variable) {
					LinearConstraint iso = getDualIsolatedConstraint((Variable) e);
					if (iso != null) {
						isoNeedsInit.add(iso);
						return false;
					}
				}
				return true;
			}

			@Override
			public void nextRelationship(Relationship r) { }
			
		});

		int i,j;
		Iterator<Node> itr;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		DoubleMatrix1D w, dw, s, ds, c, r;
		DoubleMatrix2D A;
		DenseDoubleLUDecompositionQuick lu = new DenseDoubleLUDecompositionQuick();
		double stepSize;
		
		if (!madeFeasibleOnce) {
			 for (Node n : graph.getNodesByAttribute(NODE_TYPE, NodeType.var)) {
				 dualInfeasible.add((Variable) Entity.createEntity(this, n));
			 }
		}

		log.debug("Finding dual feasible point: {} constraints marked.", dualInfeasible.size());
		
		while (!dualInfeasible.isEmpty()) {
			log.trace("{} dual constraints left to initialize.", dualInfeasible.size());
			var = dualInfeasible.iterator().next();
			iso = getDualIsolatedConstraint(var);
			if (iso != null) {
				makeDualFeasible(iso);
				for (Variable v : iso.getVariables().keySet())
					dualInfeasible.remove(v);
			}
			else {
				i = 0;
				varInit.clear();
				j = 0;
				lcInit.clear();
				itr = trav.traverse(var.getNode());
				
				/* Collects variables to be initialized */
				while (itr.hasNext()) {
					node = itr.next();
					if (NodeType.var.equals(node.getAttribute(NODE_TYPE))) {
						var = (Variable) Entity.createEntity(this, node);
						dualInfeasible.remove(var);
						if (!varInit.containsKey(var)){
							varInit.put(var, i++);
						}
					}
				}
			
				/* Collects linear constraints to be initialized */
				for (Variable v : varInit.keySet()) {
					for (LinearConstraint lc : v.getLinearConstraints()) {
						if (!lcInit.containsKey(lc)) {
							lcInit.put((LinearConstraint) lc, j++);
						}
					}
				}
				
				/* Adds isolated linear constraints and any additional variables */
				for (LinearConstraint lc : isoNeedsInit) {
					lcInit.put(lc, j++);
					for (Variable v : lc.getVariables().keySet()) {
						if (!varInit.containsKey(v)) {
							varInit.put(v, i++);
							dualInfeasible.remove(v);
						}
					}
				}
				
				/* Initializes data matrices */
				A = DoubleFactory2D.sparse.make(lcInit.size(), varInit.size());
				w = DoubleFactory1D.dense.make(lcInit.size());
				s = DoubleFactory1D.dense.make(varInit.size());
				c = DoubleFactory1D.dense.make(varInit.size());
				r = DoubleFactory1D.dense.make(varInit.size());
				
				/* Constructs A and w */
				for (Map.Entry<LinearConstraint, Integer> lc : lcInit.entrySet()) {
					for (Map.Entry<Variable, Double> v : lc.getKey().getVariables().entrySet()) {
						if (varInit.containsKey(v.getKey())) {
							A.set(lc.getValue(), varInit.get(v.getKey()), v.getValue());
						}
					}
					w.set(lc.getValue(), lc.getKey().getLagrange());
				}
				
				lu.decompose(alg.mult(A, A.viewDice()));
				
				/* Constructs s and c */
				for (Map.Entry<Variable, Integer> v : varInit.entrySet()) {
					s.set(v.getValue(), v.getKey().getDualValue());
					double temp = 0.0;
					for (LinearConstraint lc : v.getKey().getLinearConstraints())
						if (!lcInit.containsKey(lc))
							temp += lc.getVariables().get(v.getKey()) * lc.getLagrange();
					c.set(v.getValue(), v.getKey().getObjectiveCoefficient() - temp);
				}
				
				/* Initializes r */
				r = alg.mult(A.viewDice(), w).assign(s, DoubleFunctions.plus).assign(c, DoubleFunctions.minus);
				
				boolean wStep = true;
				// TODO: Add check for getting stuck
				while (alg.norm2(r) > 10e-8) {
					dw = alg.mult(A, r);
					lu.solve(dw);
					
					if (wStep) {
						wStep = false;
						dw = alg.mult(A, r);
						lu.solve(dw);
						w.assign(dw, DoubleFunctions.minus);
					}
					else {
						wStep = true;
						ds = r;
						stepSize = 1;
						for (int k = 0; k < s.size(); k++) {
							if (s.get(k) - ds.get(k) * stepSize < 10e-8) {
								while (s.get(k) - ds.get(k) * stepSize < 10e-8) {
									stepSize *= .67;
									if (stepSize < 10e-2)
										s.set(k, s.get(k) + .5);
								}
							}
						}
						s.assign(ds, DoubleFunctions.minusMult(stepSize));
					}
					
					r = alg.mult(A.viewDice(), w).assign(s, DoubleFunctions.plus).assign(c, DoubleFunctions.minus);
				}
				
				/* Finalize initialization */
				for (Map.Entry<Variable, Integer> v : varInit.entrySet()) {
					v.getKey().setDualValue(s.get(v.getValue()));
				}
				for (Map.Entry<LinearConstraint, Integer> lc : lcInit.entrySet())
					lc.getKey().setLagrange(w.get(lc.getValue()));
				
				isoNeedsInit.clear();
			}
		}
	}

	private void makeDualFeasible(LinearConstraint lc) {
		double temp;
		for (Map.Entry<Variable, Double> e : lc.getVariables().entrySet()) {
			temp = 0.0;
			for (LinearConstraint con : e.getKey().getLinearConstraints()) {
				temp += con.getVariables().get(e.getKey()) * con.getLagrange();
			}
			if (e.getKey().getObjectiveCoefficient() - temp - 0.05 < 0) {
				temp -= lc.getVariables().get(e.getKey()) * lc.getLagrange();
				temp = e.getKey().getObjectiveCoefficient() - temp - 0.25;
				lc.setLagrange(temp / lc.getVariables().get(e.getKey()));
			}
		}
		
		for (Variable v : lc.getVariables().keySet()) {
			temp = 0.0;
			for (LinearConstraint con : v.getLinearConstraints()) {
				temp += con.getVariables().get(v) * con.getLagrange();
			}
			v.setDualValue(v.getObjectiveCoefficient() - temp);
			if (!v.isDualFeasible()) {
				log.error("Variable dual infeasible after isolated initialization.");
			}
		}
	}

	private IsolatedStructure getPrimalIsolatedStructure(LinearConstraint lc) {
		IsolatedStructure iso = primalIsolated.get(lc);
		if (iso != null)
			return iso;
		
		//if (Boolean.TRUE.equals(node.getAttribute(ConicProgram.CHECK_ISOLATED))) {
		/* Checks for linear isolated structure */
		Variable	positiveSlack = null,
					negativeSlack = null;
		for (Entry<Variable, Double> e : lc.getVariables().entrySet()) {
			if (e.getKey().getLinearConstraints().size() == 1
					&& e.getKey().getCone() instanceof NonNegativeOrthantCone) {
				if (e.getValue() < 0) {
					negativeSlack = e.getKey();
					if (positiveSlack != null)
						break;
				}
				else if (e.getValue() > 0) {
					positiveSlack = e.getKey();
					if (negativeSlack != null)
						break;
				}
			}
		}
		
		if (positiveSlack != null && negativeSlack != null) {
			iso = new LinearIsolatedStructure(lc, positiveSlack, negativeSlack);
			primalIsolated.put(lc, iso);
			return iso;
		}
		
		/* If execution reaches this line, then no isolated structure could be found */
		//for (Node n : node.getProperties(ConicProgram.CHECK_ISOLATED))
		//	n.delete();
		//}
		
		return null;
	}
	
	private LinearConstraint getDualIsolatedConstraint(Variable v) {
		LinearConstraint lc = dualIsolated.get(v);
		if (lc != null)
			return lc;

		boolean posCoeff, isolated;
		for (LinearConstraint con : v.getLinearConstraints()) {
			isolated = true;
			if (con.getVariables().get(v) != 0.0) {
				posCoeff = (con.getVariables().get(v) > 0.0) ? true : false;
				for (Double coeff : con.getVariables().values()) {
					isolated = isolated  && (((posCoeff && coeff > 0.0) ? true : false) || ((!posCoeff && coeff < 0.0)));
				}
				if (isolated) {
					dualIsolated.put(v, con);
					return con;
				}
			}
		}
		return null;
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
				NonNegativeOrthantCone nnoc = (NonNegativeOrthantCone) sender;
				switch (e) {
				case NNOCCreated:
					v++;
					if (madeFeasibleOnce) dualInfeasible.add(nnoc.getVariable());
					break;
				case NNOCDeleted:
					v--;
					dualInfeasible.remove(nnoc.getVariable());
					dualIsolated.remove(nnoc.getVariable());
					break;
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case ObjCoeffChanged:
			if (sender instanceof Variable) {
				Variable var = (Variable) sender;
				if (madeFeasibleOnce) dualInfeasible.add(var);
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case ConCreated:
		case ConValueChanged:
		case ConDeleted:
			if (sender instanceof LinearConstraint) {
				LinearConstraint lc = (LinearConstraint) sender;
				switch(e) {
				case ConCreated:
				case ConValueChanged:
					if (madeFeasibleOnce) primalInfeasible.add(lc);
					break;
				case ConDeleted:
					primalInfeasible.remove(lc);
					primalIsolated.remove(lc);
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case VarAddedToCon:
		case VarRemovedFromCon:
			if (sender instanceof LinearConstraint && data.length > 0 && data[0] instanceof Variable) {
				LinearConstraint lc = (LinearConstraint) sender;
				Variable var = (Variable) data[0];
				if (madeFeasibleOnce) {
					primalInfeasible.add(lc);
					dualInfeasible.add(var);
				}
				primalIsolated.remove(lc);
				if (lc.equals(dualIsolated.get(var))) {
					dualIsolated.remove(var);
				}
				if (ConicProgramEvent.VarAddedToCon.equals(e)) {
					for (Variable v : lc.getVariables().keySet()) {
						if (lc.equals(dualIsolated.get(v))) {
							dualIsolated.remove(v);
						}
					}
				}
			}
			else if (sender instanceof LinearConstraint)
				throw new IllegalArgumentException(UNEXPECTED_DATA);
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		}
	}
}
